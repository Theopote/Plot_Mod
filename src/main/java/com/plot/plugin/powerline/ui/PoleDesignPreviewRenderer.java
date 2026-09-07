package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.ConductorAttachment;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.design.structure.TowerStructureGeometry;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImInt;

/**
 * 杆塔预览（侧视 / 正视切换）。
 */
public final class PoleDesignPreviewRenderer {
    private static final float PREVIEW_HEIGHT = 140f;
    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_WIRE = 0xFF9E9E9E;
    private static final int COLOR_BRACE = 0xFF78909C;
    private static final int COLOR_ARM = 0xFF546E7A;

    private static int previewView = 0;

    private PoleDesignPreviewRenderer() {
    }

    public static void render(PoleDesign design) {
        ImGui.text(PlotI18n.tr("plugin.powerline.design.preview"));
        String[] views = {
            PlotI18n.tr("plugin.powerline.design.preview_side"),
            PlotI18n.tr("plugin.powerline.design.preview_front")
        };
        ImInt viewIndex = new ImInt(previewView);
        ImGui.setNextItemWidth(120);
        if (ImGui.combo("##preview_view", viewIndex, views)) {
            previewView = viewIndex.get();
        }

        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f || design == null) {
            return;
        }

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + PREVIEW_HEIGHT;

        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER);

        int totalHeight = Math.max(1, design.totalHeight());
        float scale = (PREVIEW_HEIGHT - 16f) / totalHeight;
        float centerX = x0 + width * 0.5f;
        float baseY = y1 - 8f;

        if (design.hasTowerStructure()) {
            renderTowerStructure(design.getTowerStructure(), drawList, centerX, baseY, scale, previewView == 1);
        } else {
            renderLegacyLayers(design, drawList, centerX, baseY, scale);
        }

        renderAttachments(design, drawList, centerX, baseY, scale, previewView == 1);
        ImGui.dummy(width, PREVIEW_HEIGHT);
    }

    private static void renderLegacyLayers(
            PoleDesign design,
            ImDrawList drawList,
            float centerX,
            float baseY,
            float scale) {
        float currentTop = baseY;
        for (PoleLayer layer : design.getLayers()) {
            float layerHeightPx = layer.getHeight() * scale;
            float top = currentTop - layerHeightPx;
            int color = colorForMaterial(layer.getMaterial());
            switch (layer.getShape()) {
                case COLUMN -> {
                    float columnWidth = Math.max(8f, 12f);
                    drawList.addRectFilled(
                        centerX - columnWidth * 0.5f,
                        top,
                        centerX + columnWidth * 0.5f,
                        currentTop,
                        color);
                }
                case CROSSARM -> {
                    float armWidth = layer.getCrossarmLength() * scale * 6f;
                    drawList.addRectFilled(
                        centerX - armWidth * 0.5f,
                        top,
                        centerX + armWidth * 0.5f,
                        currentTop,
                        color);
                    drawList.addLine(centerX - armWidth * 0.5f, top, centerX + armWidth * 0.5f, top, COLOR_WIRE, 1.5f);
                }
                case CAP -> drawList.addRectFilled(centerX - 6f, top, centerX + 6f, currentTop, color);
                default -> { }
            }
            currentTop = top;
        }
    }

    private static void renderTowerStructure(
            TowerStructureDesign structure,
            ImDrawList drawList,
            float centerX,
            float baseY,
            float scale,
            boolean frontView) {
        java.util.List<TowerStation> stations = structure.sortedStations();
        for (int i = 1; i < stations.size(); i++) {
            TowerStation lower = stations.get(i - 1);
            TowerStation upper = stations.get(i);
            for (int corner = 0; corner < TowerStructureGeometry.CORNER_COUNT; corner++) {
                float xLower = axisCoord(lower, corner, scale, centerX, frontView);
                float yLower = baseY - (float) lower.getHeight() * scale;
                float xUpper = axisCoord(upper, corner, scale, centerX, frontView);
                float yUpper = baseY - (float) upper.getHeight() * scale;
                drawList.addLine(xLower, yLower, xUpper, yUpper, COLOR_BRACE, 1.5f);
            }
        }

        for (TowerStation station : stations) {
            float y = baseY - (float) station.getHeight() * scale;
            float left = centerX - horizontalExtent(station, frontView) * scale * 6f;
            float right = centerX + horizontalExtent(station, frontView) * scale * 6f;
            drawList.addLine(left, y, right, y, COLOR_BRACE, 1f);
        }

        for (TowerArm arm : structure.getArms()) {
            float y = baseY - (float) arm.getBaseHeight() * scale;
            float reach = (float) arm.getLateralReach() * scale * 6f;
            drawList.addLine(centerX - reach, y, centerX + reach, y, COLOR_ARM, 2f);
        }
    }

    private static float axisCoord(TowerStation station, int corner, float scale, float centerX, boolean frontView) {
        double extent = frontView
            ? TowerStructureGeometry.cornerLongitudinal(corner, station.getHalfDepth())
            : TowerStructureGeometry.cornerLateral(corner, station.getHalfWidth());
        return centerX + (float) extent * scale * 6f;
    }

    private static float horizontalExtent(TowerStation station, boolean frontView) {
        return (float) (frontView ? station.getHalfDepth() : station.getHalfWidth());
    }

    private static void renderAttachments(
            PoleDesign design,
            ImDrawList drawList,
            float centerX,
            float baseY,
            float scale,
            boolean frontView) {
        for (ConductorAttachment attachment : design.getAttachments()) {
            if (!attachment.isEnabled()) {
                continue;
            }
            float markerY = baseY - (float) attachment.getVerticalOffset() * scale;
            float offset = frontView
                ? (float) attachment.getLongitudinalOffset()
                : (float) attachment.getLateralOffset();
            float markerX = centerX + offset * scale * 6f;
            drawList.addCircleFilled(markerX, markerY, 4f, 0xFFFFD54F);
            drawList.addText(markerX + 6f, markerY - 6f, 0xFFFFFFFF, attachment.getName());
        }
    }

    private static int colorForMaterial(MaterialMix mix) {
        String key = mix != null && mix.getPrimaryMaterial() != null
            ? mix.getPrimaryMaterial()
            : "default";
        int hash = key.hashCode();
        int r = 90 + (hash & 0x4F);
        int g = 90 + ((hash >> 8) & 0x4F);
        int b = 90 + ((hash >> 16) & 0x4F);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}
