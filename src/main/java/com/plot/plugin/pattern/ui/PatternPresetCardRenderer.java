package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternPreset;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/** 图案预设卡片：缩略图 + 标签 + 选中高亮。 */
final class PatternPresetCardRenderer {
    /** 略宽以容纳中文预设名，列数仍由 {@link PatternPresetPanel} 自适应。 */
    static final float CARD_WIDTH = 112f;
    static final float CARD_HEIGHT = 118f;
    private static final float PREVIEW_HEIGHT = 80f;
    private static final float LABEL_PADDING = 4f;
    private static final int COLOR_BG_SELECTED = 0xFF263238;
    private static final int COLOR_LABEL = 0xFFE8E8E8;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;
    private PatternPresetCardRenderer() {
    }

    static boolean renderPresetCard(
            PatternPreset preset,
            PatternPreviewBuckets buckets,
            boolean selected) {
        if (preset == null) {
            return false;
        }

        String buttonId = "##pattern_preset_card_" + preset.getId().replace('/', '_');
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + CARD_WIDTH;
        float y1 = y0 + CARD_HEIGHT;

        int bg = selected ? COLOR_BG_SELECTED : PluginUiColors.MAP_BG;
        drawList.addRectFilled(x0, y0, x1, y1, bg);
        int borderColor = selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.PANEL_BORDER;
        float borderThickness = selected ? 2f : 1f;
        drawList.addRect(x0, y0, x1, y1, borderColor, 4f, 0, borderThickness);

        float previewY1 = y0 + PREVIEW_HEIGHT;
        PatternPreviewRasterDraw.drawBuckets(
            drawList,
            buckets,
            x0 + 2f,
            y0 + 2f,
            x1 - 2f,
            previewY1 - 2f,
            "plugin.pattern.preset_preview_empty");

        float labelY = previewY1 + LABEL_PADDING;
        int labelColor = selected ? COLOR_LABEL : COLOR_LABEL_DIM;
        drawCenteredLabel(drawList, preset.getDisplayName(), x0, labelY, labelColor);

        ImGui.invisibleButton(buttonId, CARD_WIDTH, CARD_HEIGHT);
        if (ImGui.isItemHovered()) {
            String description = preset.getDisplayDescription();
            if (description != null && !description.isBlank()) {
                ImGui.setTooltip(description);
            } else {
                ImGui.setTooltip(preset.getDisplayName());
            }
        }
        return ImGui.isItemClicked(0);
    }

    private static void drawCenteredLabel(
            ImDrawList drawList,
            String label,
            float x,
            float y,
            int color) {
        if (label == null || label.isBlank()) {
            return;
        }
        float maxW = CARD_WIDTH - LABEL_PADDING * 2f;
        String clipped = clipText(label, maxW);
        float textW = ImGui.calcTextSize(clipped).x;
        drawList.addText(x + (CARD_WIDTH - textW) * 0.5f, y, color, clipped);
    }

    private static String clipText(String text, float maxWidth) {
        if (ImGui.calcTextSize(text).x <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        for (int end = text.length(); end > 0; end--) {
            String candidate = text.substring(0, end) + ellipsis;
            if (ImGui.calcTextSize(candidate).x <= maxWidth) {
                return candidate;
            }
        }
        return ellipsis;
    }
}
