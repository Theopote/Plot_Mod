package com.plot.plugin.powerline.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * 杆塔侧视图预览（ImGui 绘制）。
 */
public final class PoleDesignPreviewRenderer {
    private static final float PREVIEW_HEIGHT = 120f;
    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_WIRE = 0xFF9E9E9E;

    private PoleDesignPreviewRenderer() {
    }

    public static void render(PoleDesign design) {
        ImGui.text(PlotI18n.tr("plugin.powerline.design.preview"));
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
        float currentTop = baseY;

        for (PoleLayer layer : design.getLayers()) {
            float layerHeightPx = layer.getHeight() * scale;
            float top = currentTop - layerHeightPx;
            int color = colorForMaterial(layer.getMaterial());
            switch (layer.getShape()) {
                case COLUMN -> {
                    float columnWidth = Math.max(8f, width * 0.08f);
                    drawList.addRectFilled(
                        centerX - columnWidth * 0.5f,
                        top,
                        centerX + columnWidth * 0.5f,
                        currentTop,
                        color);
                }
                case CROSSARM -> {
                    float armWidth = Math.min(width - 16f, layer.getCrossarmLength() * scale * 6f);
                    drawList.addRectFilled(
                        centerX - armWidth * 0.5f,
                        top,
                        centerX + armWidth * 0.5f,
                        currentTop,
                        color);
                    drawList.addLine(centerX - armWidth * 0.5f, top, centerX + armWidth * 0.5f, top, COLOR_WIRE, 1.5f);
                }
                case CAP -> drawList.addRectFilled(
                    centerX - 6f,
                    top,
                    centerX + 6f,
                    currentTop,
                    color);
                default -> { }
            }
            currentTop = top;
        }

        ImGui.dummy(width, PREVIEW_HEIGHT);
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
