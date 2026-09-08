package com.plot.plugin.powerline.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/** 杆塔间距密度预设卡片（杆塔分布缩略图 + 标签）。 */
public final class PowerLineSpacingCardRenderer {
    public static final float CARD_WIDTH = 80f;
    public static final float CARD_HEIGHT = 64f;
    private static final float PREVIEW_HEIGHT = 36f;
    private static final float LABEL_PADDING = 3f;

    private static final int COLOR_BG = 0xFF1E1E1E;
    private static final int COLOR_BG_SELECTED = 0xFF263238;
    private static final int COLOR_BORDER = 0xFF484848;
    private static final int COLOR_SELECTED_RING = 0xFF4DA6FF;
    private static final int COLOR_PATH = 0xFF607D8B;
    private static final int COLOR_POLE = 0xFFE0E0E0;
    private static final int COLOR_LABEL = 0xFFE8E8E8;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;

    private PowerLineSpacingCardRenderer() {
    }

    public static boolean renderSpacingCard(
            PowerLineUiPresets.SpacingDensity density,
            String label,
            boolean selected) {
        String buttonId = "##powerline_spacing_card_" + density.name();
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + CARD_WIDTH;
        float y1 = y0 + CARD_HEIGHT;

        drawCardShell(drawList, x0, y0, x1, y1, selected);
        float previewBottom = y0 + PREVIEW_HEIGHT;
        drawSpacingPreview(drawList, density, x0 + 6f, y0 + 6f, x1 - 6f, previewBottom - 4f);

        float labelY = previewBottom + LABEL_PADDING;
        int labelColor = selected ? COLOR_LABEL : COLOR_LABEL_DIM;
        drawCenteredLabel(drawList, label, x0, labelY, CARD_WIDTH, labelColor);

        ImGui.invisibleButton(buttonId, CARD_WIDTH, CARD_HEIGHT);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(label);
        }
        return ImGui.isItemClicked(0);
    }

    static void drawSpacingPreview(
            ImDrawList drawList,
            PowerLineUiPresets.SpacingDensity density,
            float x0,
            float y0,
            float x1,
            float y1) {
        float midY = (y0 + y1) * 0.5f;
        drawList.addLine(x0, midY, x1, midY, COLOR_PATH, 1.5f);

        int poleCount = poleCountFor(density);
        for (int i = 0; i < poleCount; i++) {
            float t = poleCount == 1 ? 0.5f : (float) i / (poleCount - 1);
            float x = x0 + (x1 - x0) * t;
            drawList.addLine(x, midY - 8f, x, midY + 2f, COLOR_POLE, 2f);
            drawList.addCircleFilled(x, midY - 8f, 2f, COLOR_POLE);
        }
    }

    static int poleCountFor(PowerLineUiPresets.SpacingDensity density) {
        return switch (density) {
            case DENSE -> 6;
            case NORMAL -> 4;
            case SPARSE -> 3;
        };
    }

    private static void drawCardShell(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            boolean selected) {
        int bg = selected ? COLOR_BG_SELECTED : COLOR_BG;
        drawList.addRectFilled(x0, y0, x1, y1, bg);
        int borderColor = selected ? COLOR_SELECTED_RING : COLOR_BORDER;
        float borderThickness = selected ? 2f : 1f;
        drawList.addRect(x0, y0, x1, y1, borderColor, 3f, 0, borderThickness);
    }

    private static void drawCenteredLabel(
            ImDrawList drawList,
            String text,
            float x,
            float y,
            float width,
            int color) {
        if (text == null || text.isBlank()) {
            return;
        }
        float textW = ImGui.calcTextSize(text).x;
        float maxW = width - LABEL_PADDING * 2f;
        if (textW > maxW && text.length() > 3) {
            text = clipLabel(text, maxW);
            textW = ImGui.calcTextSize(text).x;
        }
        drawList.addText(x + (width - textW) * 0.5f, y, color, text);
    }

    private static String clipLabel(String text, float maxWidth) {
        String suffix = "...";
        for (int len = text.length() - 1; len > 0; len--) {
            String candidate = text.substring(0, len) + suffix;
            if (ImGui.calcTextSize(candidate).x <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }
}
