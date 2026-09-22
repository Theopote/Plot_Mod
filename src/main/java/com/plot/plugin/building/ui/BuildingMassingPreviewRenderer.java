package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Generate Tab 轴测体量预览：Footprint 挤出 + 真实层高比例 + 问题高亮。 */
public final class BuildingMassingPreviewRenderer {
    private static final float MIN_MAP_HEIGHT = 180f;
    private static final float PADDING = 12f;
    /** 水平面 (x+y) 项相对 (x-y) 的压缩，经典 2:1 轴测。 */
    private static final double ISO_PLANAR_Y = 0.5;
    private static final int COLOR_FILL_LOW = 0x668CB4FF;
    private static final int COLOR_FILL_HIGH = 0xCC1A4F99;
    private static final int COLOR_SKIPPED_FILL = 0x55AAAAAA;
    private static final int COLOR_SKIPPED_SIDE = 0x33888888;

    private BuildingMassingPreviewRenderer() {
    }

    public record Model(
            List<BuildingFootprint> buildings,
            Set<String> warningBuildingIds,
            Set<String> skippedBuildingIds,
            Set<String> selectedBuildingIds,
            int minFloors,
            int maxFloors,
            String emptyHint) {
    }

    public static void render(String childId, Model model, Consumer<String> onBuildingClicked) {
        float mapWidth = Math.max(160f, ImGui.getContentRegionAvailX());
        float mapHeight = mapHeightForWidth(mapWidth);
        ImGui.beginChild(childId, 0, mapHeight, true, ImGuiWindowFlags.NoScrollbar);

        float width = ImGui.getContentRegionAvail().x;
        float height = ImGui.getContentRegionAvail().y;
        if (width < 1f || height < 1f) {
            ImGui.endChild();
            return;
        }

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + height, PluginUiColors.MAP_BG);
        drawList.addRect(origin.x, origin.y, origin.x + width, origin.y + height, PluginUiColors.PANEL_BORDER);

        List<BuildingFootprint> buildings = model.buildings();
        if (buildings == null || buildings.isEmpty()) {
            drawCenteredHint(drawList, origin.x, origin.y, width, height, model.emptyHint());
            ImGui.dummy(width, height);
            ImGui.endChild();
            return;
        }

        IsoViewport viewport = buildViewport(buildings, origin.x, origin.y, width, height);
        Set<String> warnings = model.warningBuildingIds() != null ? model.warningBuildingIds() : Set.of();
        Set<String> skipped = model.skippedBuildingIds() != null ? model.skippedBuildingIds() : Set.of();
        Set<String> selected = model.selectedBuildingIds() != null ? model.selectedBuildingIds() : Set.of();
        int floorSpan = Math.max(1, model.maxFloors() - model.minFloors());

        List<BuildingFootprint> drawOrder = new ArrayList<>(buildings);
        drawOrder.sort(Comparator.comparingDouble(building -> {
            Vec2d center = centroid(building.getOuterPoints());
            return center.x + center.y;
        }));

        for (BuildingFootprint building : drawOrder) {
            if (building == null || building.getOuterPoints().size() < 3) {
                continue;
            }
            boolean isWarning = warnings.contains(building.getId());
            boolean isSkipped = skipped.contains(building.getId());
            boolean isSelected = selected.contains(building.getId());
            int topColor = isSkipped
                ? COLOR_SKIPPED_FILL
                : heightFillColor(building.getFloors(), model.minFloors(), floorSpan);
            int sideColor = isSkipped ? COLOR_SKIPPED_SIDE : darken(topColor, 0.62f);
            double heightBlocks = isSkipped ? 0.5 : buildingHeightBlocks(building);
            drawIsometricMass(
                drawList,
                building.getOuterPoints(),
                heightBlocks,
                viewport,
                topColor,
                sideColor);
            int borderColor = isWarning
                ? PluginUiColors.WARNING
                : isSelected
                    ? PluginUiColors.ACCENT_BLUE
                    : PluginUiColors.PANEL_BORDER;
            float borderThickness = isSelected || isWarning ? 2.4f : 1.4f;
            drawTopOutline(drawList, building.getOuterPoints(), heightBlocks, viewport, borderColor, borderThickness);
            if (isWarning) {
                drawWarningBadge(drawList, building, heightBlocks, viewport);
            }
            drawFloorLabel(drawList, building, heightBlocks, viewport);
        }

        ImGui.invisibleButton("##massing_map_hit", width, height);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.building.generate.massing_map_hint"));
        }
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(0) && onBuildingClicked != null) {
            ImVec2 mouse = ImGui.getMousePos();
            Vec2d world = viewport.screenToGround(mouse.x, mouse.y);
            String hit = hitTestBuilding(buildings, world.x, world.y);
            if (hit != null) {
                onBuildingClicked.accept(hit);
            }
        }

        ImGui.endChild();
    }

    private static double buildingHeightBlocks(BuildingFootprint building) {
        return building.getFloors() * building.getFloorHeight();
    }

    private static void drawIsometricMass(
            ImDrawList drawList,
            List<Vec2d> points,
            double heightBlocks,
            IsoViewport viewport,
            int topColor,
            int sideColor) {
        int count = points.size();
        float[] baseX = new float[count];
        float[] baseY = new float[count];
        float[] topX = new float[count];
        float[] topY = new float[count];
        for (int i = 0; i < count; i++) {
            Vec2d point = points.get(i);
            baseX[i] = viewport.projectX(point.x, point.y, 0.0);
            baseY[i] = viewport.projectY(point.x, point.y, 0.0);
            topX[i] = viewport.projectX(point.x, point.y, heightBlocks);
            topY[i] = viewport.projectY(point.x, point.y, heightBlocks);
        }

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            if (!isSideVisible(points.get(i), points.get(next))) {
                continue;
            }
            drawQuad(
                drawList,
                baseX[i], baseY[i],
                baseX[next], baseY[next],
                topX[next], topY[next],
                topX[i], topY[i],
                sideColor);
        }

        ImVec2[] topPoints = new ImVec2[count];
        for (int i = 0; i < count; i++) {
            topPoints[i] = new ImVec2(topX[i], topY[i]);
        }
        drawList.addConvexPolyFilled(topPoints, topPoints.length, topColor);
    }

    private static void drawTopOutline(
            ImDrawList drawList,
            List<Vec2d> points,
            double heightBlocks,
            IsoViewport viewport,
            int color,
            float thickness) {
        int count = points.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % count);
            drawList.addLine(
                viewport.projectX(start.x, start.y, heightBlocks),
                viewport.projectY(start.x, start.y, heightBlocks),
                viewport.projectX(end.x, end.y, heightBlocks),
                viewport.projectY(end.x, end.y, heightBlocks),
                color,
                thickness);
        }
    }

    /** 东南视角：外墙法线朝向观察者时绘制该侧面。 */
    private static boolean isSideVisible(Vec2d start, Vec2d end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        return dx > dy;
    }

    private static void drawQuad(
            ImDrawList drawList,
            float x0, float y0,
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,
            int color) {
        drawList.addQuadFilled(x0, y0, x1, y1, x2, y2, x3, y3, color);
    }

    private static int heightFillColor(int floors, int minFloors, int floorSpan) {
        float t = (floors - minFloors) / (float) Math.max(1, floorSpan);
        t = Math.clamp(t, 0f, 1f);
        return lerpColor(COLOR_FILL_LOW, COLOR_FILL_HIGH, t);
    }

    private static int darken(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpColor(int from, int to, float t) {
        int a0 = (from >> 24) & 0xFF;
        int r0 = (from >> 16) & 0xFF;
        int g0 = (from >> 8) & 0xFF;
        int b0 = from & 0xFF;
        int a1 = (to >> 24) & 0xFF;
        int r1 = (to >> 16) & 0xFF;
        int g1 = (to >> 8) & 0xFF;
        int b1 = to & 0xFF;
        int a = (int) (a0 + (a1 - a0) * t);
        int r = (int) (r0 + (r1 - r0) * t);
        int g = (int) (g0 + (g1 - g0) * t);
        int b = (int) (b0 + (b1 - b0) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void drawFloorLabel(
            ImDrawList drawList,
            BuildingFootprint building,
            double heightBlocks,
            IsoViewport viewport) {
        if (viewport.planarScale < 2.5) {
            return;
        }
        Vec2d center = centroid(building.getOuterPoints());
        String label = PlotI18n.tr("plugin.building.generate.map_floors", building.getFloors());
        ImVec2 size = ImGui.calcTextSize(label);
        float x = viewport.projectX(center.x, center.y, heightBlocks) - size.x * 0.5f;
        float y = viewport.projectY(center.x, center.y, heightBlocks) - size.y * 0.5f;
        drawList.addText(x, y, ImGui.getColorU32(ImGuiCol.Text), label);
    }

    private static void drawWarningBadge(
            ImDrawList drawList,
            BuildingFootprint building,
            double heightBlocks,
            IsoViewport viewport) {
        Vec2d center = centroid(building.getOuterPoints());
        float x = viewport.projectX(center.x, center.y, heightBlocks);
        float y = viewport.projectY(center.x, center.y, heightBlocks) - 12f;
        drawList.addText(x - 4f, y - 10f, PluginUiColors.WARNING, "!");
    }

    private static Vec2d centroid(List<Vec2d> points) {
        double x = 0;
        double y = 0;
        for (Vec2d point : points) {
            x += point.x;
            y += point.y;
        }
        int count = Math.max(1, points.size());
        return new Vec2d(x / count, y / count);
    }

    private static String hitTestBuilding(List<BuildingFootprint> buildings, double worldX, double worldY) {
        String best = null;
        double bestArea = Double.MAX_VALUE;
        for (BuildingFootprint building : buildings) {
            if (!containsPoint(building.getOuterPoints(), worldX, worldY)) {
                continue;
            }
            double area = Math.abs(building.computeArea());
            if (area < bestArea) {
                bestArea = area;
                best = building.getId();
            }
        }
        return best;
    }

    private static boolean containsPoint(List<Vec2d> polygon, double x, double y) {
        boolean inside = false;
        int count = polygon.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            Vec2d pi = polygon.get(i);
            Vec2d pj = polygon.get(j);
            boolean intersect = ((pi.y > y) != (pj.y > y))
                && (x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y + 1e-12) + pi.x);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static IsoViewport buildViewport(
            List<BuildingFootprint> buildings,
            float x,
            float y,
            float width,
            float height) {
        double minScreenX = Double.POSITIVE_INFINITY;
        double minScreenY = Double.POSITIVE_INFINITY;
        double maxScreenX = Double.NEGATIVE_INFINITY;
        double maxScreenY = Double.NEGATIVE_INFINITY;

        for (BuildingFootprint building : buildings) {
            double heightBlocks = buildingHeightBlocks(building);
            for (Vec2d point : building.getOuterPoints()) {
                for (double z : new double[] {0.0, heightBlocks}) {
                    double sx = point.x - point.y;
                    double sy = (point.x + point.y) * ISO_PLANAR_Y - z;
                    minScreenX = Math.min(minScreenX, sx);
                    maxScreenX = Math.max(maxScreenX, sx);
                    minScreenY = Math.min(minScreenY, sy);
                    maxScreenY = Math.max(maxScreenY, sy);
                }
            }
        }

        double spanX = Math.max(maxScreenX - minScreenX, 1.0);
        double spanY = Math.max(maxScreenY - minScreenY, 1.0);
        float innerWidth = Math.max(1f, width - PADDING * 2f);
        float innerHeight = Math.max(1f, height - PADDING * 2f);
        double scale = Math.min(innerWidth / spanX, innerHeight / spanY);
        float offsetX = x + PADDING + (float) ((innerWidth - spanX * scale) * 0.5) - (float) (minScreenX * scale);
        float offsetY = y + PADDING + (float) ((innerHeight - spanY * scale) * 0.5) - (float) (minScreenY * scale);
        return new IsoViewport(scale, scale, offsetX, offsetY);
    }

    private static float mapHeightForWidth(float width) {
        return Math.max(MIN_MAP_HEIGHT, width * 0.62f);
    }

    private static void drawCenteredHint(
            ImDrawList drawList,
            float x,
            float y,
            float width,
            float height,
            String text) {
        ImVec2 size = ImGui.calcTextSize(text);
        drawList.addText(
            x + (width - size.x) * 0.5f,
            y + (height - size.y) * 0.5f,
            PluginUiColors.HINT_GRAY,
            text);
    }

    private record IsoViewport(double planarScale, double verticalScale, float offsetX, float offsetY) {
        float projectX(double worldX, double worldY, double worldZ) {
            return (float) (offsetX + (worldX - worldY) * planarScale);
        }

        float projectY(double worldX, double worldY, double worldZ) {
            return (float) (offsetY + (worldX + worldY) * ISO_PLANAR_Y * planarScale - worldZ * verticalScale);
        }

        Vec2d screenToGround(float screenX, float screenY) {
            double nx = (screenX - offsetX) / planarScale;
            double ny = (screenY - offsetY) / (planarScale * ISO_PLANAR_Y);
            return new Vec2d((nx + ny) * 0.5, (ny - nx) * 0.5);
        }
    }
}
