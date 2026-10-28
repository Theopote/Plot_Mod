package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.HashMap;
import java.util.Map;

/**
 * 画布挖填热力图：红挖、蓝填、绿几乎不动；缩放足够时在格内画 +/-，
 * 并在各区域质心标注净挖填符号。
 */
public final class EarthworkCutFillHeatmapRenderer {
    private static final int MAX_CELLS = 800;
    private static final int CUT = ImColor.rgba(220, 64, 64, 140);
    private static final int FILL = ImColor.rgba(64, 140, 230, 140);
    private static final int UNCHANGED = ImColor.rgba(72, 176, 96, 55);
    private static final int CUT_LABEL = ImColor.rgba(255, 120, 120, 230);
    private static final int FILL_LABEL = ImColor.rgba(120, 190, 255, 230);
    private static final float MIN_ZOOM_FOR_CELL_LABEL = 4.5f;
    private static final float[] SCREEN = new float[2];

    private EarthworkCutFillHeatmapRenderer() {
    }

    public static void render(ImDrawList drawList, CanvasCamera camera, DesignTerrainGrid grid) {
        render(drawList, camera, grid, null);
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            DesignTerrainGrid grid,
            EarthworkProject project) {
        if (drawList == null || camera == null || grid == null || grid.cellCount() == 0) {
            return;
        }
        int count = grid.cellCount();
        int stride = count > MAX_CELLS ? (int) Math.ceil(count / (double) MAX_CELLS) : 1;
        float cell = Math.max(1.5f, camera.getZoom());
        float displayW = ImGui.getIO().getDisplaySizeX();
        float displayH = ImGui.getIO().getDisplaySizeY();
        boolean drawCellLabels = camera.getZoom() >= MIN_ZOOM_FOR_CELL_LABEL;
        Map<String, ZoneNet> zoneNets = new HashMap<>();
        int index = 0;
        for (DesignTerrainCell terrainCell : grid.cells().values()) {
            if (terrainCell == null || !terrainCell.participatesInEarthwork()) {
                continue;
            }
            int deltaY = terrainCell.deltaY();
            accumulateZoneNet(zoneNets, terrainCell.zoneId(), deltaY, terrainCell.center());
            if ((index++ % stride) != 0) {
                continue;
            }
            Vec2d center = terrainCell.center();
            if (center == null) {
                continue;
            }
            camera.worldToScreen(center.x, center.y, SCREEN);
            float x = SCREEN[0];
            float y = SCREEN[1];
            if (x + cell < 0 || y + cell < 0 || x - cell > displayW || y - cell > displayH) {
                continue;
            }
            drawList.addRectFilled(x - cell * 0.5f, y - cell * 0.5f, x + cell * 0.5f, y + cell * 0.5f, colorFor(deltaY));
            if (drawCellLabels && deltaY != 0) {
                String label = deltaY > 0 ? "+" : "-";
                ImVec2 size = ImGui.calcTextSize(label);
                drawList.addText(
                    x - size.x * 0.5f,
                    y - size.y * 0.5f,
                    deltaY > 0 ? FILL_LABEL : CUT_LABEL,
                    label);
            }
        }
        renderRegionNetLabels(drawList, camera, project, zoneNets, displayW, displayH);
    }

    private static void accumulateZoneNet(
            Map<String, ZoneNet> zoneNets,
            String zoneId,
            int deltaY,
            Vec2d center) {
        if (zoneId == null || zoneId.isBlank() || center == null || deltaY == 0) {
            return;
        }
        ZoneNet net = zoneNets.computeIfAbsent(zoneId, id -> new ZoneNet());
        if (deltaY > 0) {
            net.fill += deltaY;
        } else {
            net.cut += -deltaY;
        }
        net.sumX += center.x;
        net.sumY += center.y;
        net.samples++;
    }

    private static void renderRegionNetLabels(
            ImDrawList drawList,
            CanvasCamera camera,
            EarthworkProject project,
            Map<String, ZoneNet> zoneNets,
            float displayW,
            float displayH) {
        if (zoneNets.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ZoneNet> entry : zoneNets.entrySet()) {
            ZoneNet net = entry.getValue();
            if (net.samples <= 0) {
                continue;
            }
            long netVolume = net.fill - net.cut;
            if (netVolume == 0) {
                continue;
            }
            double cx;
            double cy;
            GradingRegion region = project != null ? project.getRegion(entry.getKey()) : null;
            if (region != null && region.getOuterPoints().size() >= 3) {
                cx = 0;
                cy = 0;
                for (Vec2d point : region.getOuterPoints()) {
                    cx += point.x;
                    cy += point.y;
                }
                cx /= region.getOuterPoints().size();
                cy /= region.getOuterPoints().size();
            } else {
                cx = net.sumX / net.samples;
                cy = net.sumY / net.samples;
            }
            camera.worldToScreen(cx, cy, SCREEN);
            float x = SCREEN[0];
            float y = SCREEN[1];
            if (x < 0 || y < 0 || x > displayW || y > displayH) {
                continue;
            }
            String label = netVolume > 0 ? "+" : "-";
            ImVec2 size = ImGui.calcTextSize(label);
            float pad = 4f;
            int bg = netVolume > 0
                ? ImColor.rgba(32, 72, 120, 200)
                : ImColor.rgba(120, 40, 40, 200);
            drawList.addRectFilled(
                x - size.x * 0.5f - pad,
                y - size.y * 0.5f - pad,
                x + size.x * 0.5f + pad,
                y + size.y * 0.5f + pad,
                bg,
                3f);
            drawList.addText(
                x - size.x * 0.5f,
                y - size.y * 0.5f,
                netVolume > 0 ? FILL_LABEL : CUT_LABEL,
                label);
        }
    }

    static int colorFor(int deltaY) {
        if (deltaY > 0) {
            return FILL;
        }
        if (deltaY < 0) {
            return CUT;
        }
        return UNCHANGED;
    }

    private static final class ZoneNet {
        long cut;
        long fill;
        double sumX;
        double sumY;
        int samples;
    }
}
