package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineSagUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.List;

/** 导线垂度预设卡片（曲线缩略图 + 标签）。 */
public final class PowerLineSagCardRenderer {
    public static final float CARD_WIDTH = 80f;
    public static final float CARD_HEIGHT = 64f;
    private static final float PREVIEW_HEIGHT = 36f;
    private static final float LABEL_PADDING = 3f;

    private static final int COLOR_BG = 0xFF1E1E1E;
    private static final int COLOR_BG_SELECTED = 0xFF263238;
    private static final int COLOR_BORDER = 0xFF484848;
    private static final int COLOR_SELECTED_RING = 0xFF4DA6FF;
    private static final int COLOR_POLE = 0xFF8D6E63;
    private static final int COLOR_WIRE = 0xFF9E9E9E;
    private static final int COLOR_LABEL = 0xFFE8E8E8;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;

    private PowerLineSagCardRenderer() {
    }

    public static boolean renderSagCard(
            PowerLineUiPresets.WireSag sag,
            String label,
            boolean selected) {
        String buttonId = "##powerline_sag_card_" + sag.name();
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + CARD_WIDTH;
        float y1 = y0 + CARD_HEIGHT;

        drawCardShell(drawList, x0, y0, x1, y1, selected);
        float previewBottom = y0 + PREVIEW_HEIGHT;
        drawSagPreview(drawList, sag.ratio(), x0 + 4f, y0 + 4f, x1 - 4f, previewBottom - 2f);

        float labelY = previewBottom + LABEL_PADDING;
        int labelColor = selected ? COLOR_LABEL : COLOR_LABEL_DIM;
        drawCenteredLabel(drawList, label, x0, labelY, CARD_WIDTH, labelColor);

        ImGui.invisibleButton(buttonId, CARD_WIDTH, CARD_HEIGHT);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(label);
        }
        return ImGui.isItemClicked(0);
    }

    static void drawSagPreview(
            ImDrawList drawList,
            double sagRatio,
            float x0,
            float y0,
            float x1,
            float y1) {
        float poleTopY = y0 + 4f;
        float poleBottomY = y1 - 2f;
        float leftX = x0 + (x1 - x0) * 0.18f;
        float rightX = x1 - (x1 - x0) * 0.18f;
        float spanLength = 20.0f;

        drawList.addLine(leftX, poleTopY, leftX, poleBottomY, COLOR_POLE, 2f);
        drawList.addLine(rightX, poleTopY, rightX, poleBottomY, COLOR_POLE, 2f);

        List<Double> profile = PowerLineSagUtils.computeSagProfile(spanLength, 0.0, 0.0, sagRatio, 12);
        float wireTopY = poleTopY + 2f;
        float maxSagDepth = (float) (spanLength * sagRatio);
        float sagScale = maxSagDepth > 0.001
            ? Math.min(10f, (poleBottomY - wireTopY - 4f) / maxSagDepth)
            : 1f;

        float prevX = leftX;
        float prevY = wireTopY;
        for (int i = 0; i < profile.size(); i++) {
            double t = (double) i / (profile.size() - 1);
            float x = leftX + (rightX - leftX) * (float) t;
            float sagDrop = (float) (-profile.get(i)) * sagScale;
            float y = wireTopY + sagDrop;
            if (i > 0) {
                drawList.addLine(prevX, prevY, x, y, COLOR_WIRE, 1.8f);
            }
            prevX = x;
            prevY = y;
        }
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
