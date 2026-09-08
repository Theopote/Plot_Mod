package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/** 样式 Tab 的杆塔风格卡片（缩略图 + 标签 + 选中高亮）。 */
public final class PowerLineStyleCardRenderer {
    public static final float CARD_WIDTH = 108f;
    public static final float CARD_HEIGHT = 112f;
    private static final float PREVIEW_HEIGHT = 76f;
    private static final float LABEL_PADDING = 4f;

    private static final int COLOR_BG = 0xFF1E1E1E;
    private static final int COLOR_BG_SELECTED = 0xFF263238;
    private static final int COLOR_BORDER = 0xFF484848;
    private static final int COLOR_SELECTED_RING = 0xFF4DA6FF;
    private static final int COLOR_LABEL = 0xFFE8E8E8;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;

    public enum StylePreviewKind {
        WOOD,
        LATTICE,
        ADAPTIVE
    }

    private PowerLineStyleCardRenderer() {
    }

    /**
     * 绘制可点击的风格卡片；返回是否被点击。
     */
    public static boolean renderStyleCard(String familyId, String label, boolean selected) {
        String buttonId = "##powerline_style_card_" + familyKey(familyId);
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + CARD_WIDTH;
        float y1 = y0 + CARD_HEIGHT;

        int bg = selected ? COLOR_BG_SELECTED : COLOR_BG;
        drawList.addRectFilled(x0, y0, x1, y1, bg);
        int borderColor = selected ? COLOR_SELECTED_RING : COLOR_BORDER;
        float borderThickness = selected ? 2f : 1f;
        drawList.addRect(x0, y0, x1, y1, borderColor, 4f, 0, borderThickness);

        float previewY1 = y0 + PREVIEW_HEIGHT;
        drawPreview(drawList, familyId, x0 + 2f, y0 + 2f, x1 - 2f, previewY1 - 2f);

        float labelY = previewY1 + LABEL_PADDING;
        int labelColor = selected ? COLOR_LABEL : COLOR_LABEL_DIM;
        drawCenteredLabel(drawList, label, x0, labelY, CARD_WIDTH, COLOR_LABEL_DIM, labelColor);

        ImGui.invisibleButton(buttonId, CARD_WIDTH, CARD_HEIGHT);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(label);
        }
        return ImGui.isItemClicked(0);
    }

    public static StylePreviewKind previewKindFor(String familyId) {
        if (familyId == null || familyId.isBlank()) {
            return StylePreviewKind.WOOD;
        }
        if (TowerFamily.GRADED_LATTICE_3_PHASE_ID.equals(familyId)) {
            return StylePreviewKind.ADAPTIVE;
        }
        return StylePreviewKind.LATTICE;
    }

    public static float cardWidth() {
        return CARD_WIDTH;
    }

    public static float cardHeight() {
        return CARD_HEIGHT;
    }

    private static void drawPreview(
            ImDrawList drawList,
            String familyId,
            float x0,
            float y0,
            float x1,
            float y1) {
        StylePreviewKind kind = previewKindFor(familyId);
        switch (kind) {
            case WOOD -> drawWoodPreview(drawList, x0, y0, x1, y1);
            case LATTICE -> drawDesignPreview(drawList, TowerFamilyDesignPresets.latticeSuspension(), x0, y0, x1, y1);
            case ADAPTIVE -> drawAdaptivePreview(drawList, x0, y0, x1, y1);
            default -> { }
        }
    }

    private static void drawWoodPreview(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        PoleDesign design = PoleDesignCatalog.simpleWoodPole();
        PoleDesignPreviewRenderer.drawThumbnail(design, drawList, x0, y0, x1, y1, false);
        float wireY = y0 + (y1 - y0) * 0.38f;
        drawList.addLine(x0 + 8f, wireY, x1 - 8f, wireY, 0xFF9E9E9E, 1.2f);
    }

    private static void drawDesignPreview(
            ImDrawList drawList,
            PoleDesign design,
            float x0,
            float y0,
            float x1,
            float y1) {
        PoleDesignPreviewRenderer.drawThumbnail(design, drawList, x0, y0, x1, y1, false);
    }

    private static void drawAdaptivePreview(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        float mid = (x0 + x1) * 0.5f;
        drawDesignPreview(
            drawList,
            TowerFamilyDesignPresets.latticeSuspensionSmall(),
            x0 + 2f,
            y0,
            mid - 1f,
            y1);
        drawDesignPreview(
            drawList,
            TowerFamilyDesignPresets.latticeSuspensionTall(),
            mid + 1f,
            y0,
            x1 - 2f,
            y1);
        float wireY = y0 + (y1 - y0) * 0.32f;
        drawList.addLine(x0 + 6f, wireY, x1 - 6f, wireY, 0xFF9E9E9E, 1f);
    }

    private static void drawCenteredLabel(
            ImDrawList drawList,
            String text,
            float x,
            float y,
            float width,
            int dimColor,
            int brightColor) {
        if (text == null || text.isBlank()) {
            return;
        }
        float textW = ImGui.calcTextSize(text).x;
        float maxW = width - LABEL_PADDING * 2f;
        if (textW > maxW && text.length() > 3) {
            String clipped = clipLabel(text, maxW);
            textW = ImGui.calcTextSize(clipped).x;
            drawList.addText(x + (width - textW) * 0.5f, y, dimColor, clipped);
            return;
        }
        drawList.addText(x + (width - textW) * 0.5f, y, brightColor, text);
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

    private static String familyKey(String familyId) {
        return familyId == null || familyId.isBlank() ? "wood" : familyId.replace('/', '_');
    }
}
