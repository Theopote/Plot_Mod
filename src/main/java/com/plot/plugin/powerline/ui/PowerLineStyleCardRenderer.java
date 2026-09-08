package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.style.PowerLineStylePack;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/** 样式 Tab 的杆塔风格卡片（缩略图 + 标签 + 选中高亮）。 */
public final class PowerLineStyleCardRenderer {
    public static final float CARD_WIDTH = 96f;
    public static final float CARD_HEIGHT = 108f;
    private static final float PREVIEW_HEIGHT = 72f;
    private static final float LABEL_PADDING = 4f;

    private static final int COLOR_BG = 0xFF1E1E1E;
    private static final int COLOR_BG_SELECTED = 0xFF263238;
    private static final int COLOR_BORDER = 0xFF484848;
    private static final int COLOR_SELECTED_RING = 0xFF4DA6FF;
    private static final int COLOR_LABEL = 0xFFE8E8E8;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;

    private PowerLineStyleCardRenderer() {
    }

    /**
     * 绘制可点击的风格卡片；返回是否被点击。
     */
    public static boolean renderStyleCard(PowerLineStylePack pack, String label, boolean selected) {
        if (pack == null) {
            return false;
        }
        String buttonId = "##powerline_style_card_" + pack.getId().replace('/', '_');
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
        drawPackPreview(drawList, pack, x0 + 2f, y0 + 2f, x1 - 2f, previewY1 - 2f);

        float labelY = previewY1 + LABEL_PADDING;
        int labelColor = selected ? COLOR_LABEL : COLOR_LABEL_DIM;
        drawCenteredLabel(drawList, label, x0, labelY, CARD_WIDTH, COLOR_LABEL_DIM, labelColor);

        ImGui.invisibleButton(buttonId, CARD_WIDTH, CARD_HEIGHT);
        if (ImGui.isItemHovered()) {
            renderPackTooltip(pack, label);
        }
        return ImGui.isItemClicked(0);
    }

    private static void renderPackTooltip(PowerLineStylePack pack, String label) {
        ImGui.beginTooltip();
        ImGui.text(label);
        ImGui.separator();
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 24f);
        ImGui.textWrapped(PlotI18n.tr(pack.getDescriptionKey()));
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }

    public static float cardWidth() {
        return CARD_WIDTH;
    }

    public static float cardHeight() {
        return CARD_HEIGHT;
    }

    public static final float COMPACT_WIDTH = 72f;
    public static final float COMPACT_HEIGHT = 80f;
    private static final float COMPACT_PREVIEW_HEIGHT = 56f;

    /** 只读紧凑风格预览（Build 摘要等）。 */
    public static void renderCompactStylePreview(PowerLineStylePack pack) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + COMPACT_WIDTH;
        float y1 = y0 + COMPACT_HEIGHT;
        drawList.addRectFilled(x0, y0, x1, y1, COLOR_BG);
        drawList.addRect(x0, y0, x1, y1, COLOR_BORDER, 3f, 0, 1f);
        if (pack != null) {
            drawPackPreview(drawList, pack, x0 + 2f, y0 + 2f, x1 - 2f, y0 + COMPACT_PREVIEW_HEIGHT);
        } else {
            drawWoodPreview(drawList, x0 + 2f, y0 + 2f, x1 - 2f, y0 + COMPACT_PREVIEW_HEIGHT);
        }
        ImGui.dummy(COMPACT_WIDTH, COMPACT_HEIGHT);
    }

    private static void drawPackPreview(
            ImDrawList drawList,
            PowerLineStylePack pack,
            float x0,
            float y0,
            float x1,
            float y1) {
        switch (pack.getPreviewKind()) {
            case WOOD -> drawWoodPreview(drawList, x0, y0, x1, y1);
            case URBAN -> drawDesignPreview(drawList, PoleDesignCatalog.urbanConcretePole(), x0, y0, x1, y1);
            case STEEL_POLE -> drawDesignPreview(drawList, PoleDesignCatalog.modernSteelPole(), x0, y0, x1, y1);
            case LATTICE_POLE -> drawDesignPreview(drawList, PoleDesignCatalog.latticeSteelTower(), x0, y0, x1, y1);
            case LATTICE -> drawDesignPreview(
                drawList, TowerFamilyDesignPresets.latticeSuspension(), x0, y0, x1, y1);
            case ADAPTIVE -> drawAdaptivePreview(drawList, x0, y0, x1, y1);
            case TAPERED -> drawDesignPreview(drawList, PoleDesignCatalog.taperedLatticeTower(), x0, y0, x1, y1);
            case COPPER -> drawDesignPreview(drawList, PoleDesignCatalog.fantasyCopperPole(), x0, y0, x1, y1);
            case JAPANESE -> drawJapanesePreview(drawList, x0, y0, x1, y1);
            case WASTELAND_WIND -> drawWastelandWindPreview(drawList, x0, y0, x1, y1);
            case OLD_EUROPEAN -> drawDesignPreview(drawList, PoleDesignCatalog.oldEuropeanPole(), x0, y0, x1, y1);
            case STEAMPUNK -> drawSteampunkPreview(drawList, x0, y0, x1, y1);
            case MODERN_HV_GLASS -> drawModernHvGlassPreview(drawList, x0, y0, x1, y1);
            case SUBURBAN_LAMP -> drawSuburbanLampPreview(drawList, x0, y0, x1, y1);
            default -> drawWoodPreview(drawList, x0, y0, x1, y1);
        }
    }

    private static void drawSteampunkPreview(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        drawDesignPreview(drawList, PoleDesignCatalog.steampunkBrassTower(), x0, y0, x1, y1);
        float cx = x0 + (x1 - x0) * 0.5f;
        float cy = y0 + (y1 - y0) * 0.18f;
        drawList.addCircle(cx, cy, 4f, 0xFFFFD54F, 8, 1.5f);
        float wireY = y0 + (y1 - y0) * 0.32f;
        drawList.addLine(x0 + 6f, wireY, x1 - 6f, wireY, 0xFFB87333, 1.5f);
    }

    private static void drawModernHvGlassPreview(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        drawDesignPreview(drawList, PoleDesignCatalog.modernHvGlassTower(), x0, y0, x1, y1);
        float wireY = y0 + (y1 - y0) * 0.26f;
        float left = x0 + (x1 - x0) * 0.22f;
        float mid = x0 + (x1 - x0) * 0.5f;
        float right = x1 - (x1 - x0) * 0.22f;
        drawList.addLine(left, wireY, right, wireY, 0xFF9E9E9E, 1.2f);
        drawList.addCircleFilled(left, wireY, 3f, 0xFF80D8FF);
        drawList.addCircleFilled(mid, wireY, 3f, 0xFF80D8FF);
        drawList.addCircleFilled(right, wireY, 3f, 0xFF80D8FF);
    }

    private static void drawSuburbanLampPreview(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        drawDesignPreview(drawList, PoleDesignCatalog.suburbanLampPole(), x0, y0, x1, y1);
        float glowX = x0 + (x1 - x0) * 0.5f;
        float glowY = y0 + (y1 - y0) * 0.16f;
        drawList.addCircleFilled(glowX, glowY, 4f, 0xFF4FC3F7);
    }

    private static void drawJapanesePreview(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        drawDesignPreview(drawList, PoleDesignCatalog.japaneseStreetPole(), x0, y0, x1, y1);
        float wireY = y0 + (y1 - y0) * 0.28f;
        drawList.addLine(x0 + 6f, wireY, x1 - 6f, wireY, 0xFFB0BEC5, 1.2f);
    }

    private static void drawWastelandWindPreview(ImDrawList drawList, float x0, float y0, float x1, float y1) {
        drawDesignPreview(drawList, PoleDesignCatalog.wastelandWindTurbine(), x0, y0, x1, y1);
        float hubX = x0 + (x1 - x0) * 0.5f;
        float hubY = y0 + (y1 - y0) * 0.22f;
        drawList.addLine(hubX, hubY, x1 - 4f, hubY - 10f, 0xFFE07040, 2f);
        drawList.addCircleFilled(hubX, hubY, 3f, 0xFF8D6E63);
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
}
