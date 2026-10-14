package com.plot.plugin.road.ui;

import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * Mask-accurate tunnel cross-section preview (voxel grid), aligned with
 * {@link com.plot.plugin.powerline.ui.PowerLineStyleCardRenderer} card layout.
 */
public final class TunnelCrossSectionPreviewRenderer {
    /** Representative road width for UI preview (blocks). */
    public static final int PREVIEW_ENVELOPE_WIDTH = 5;
    /** Fixed road elevation anchor inside the preview mask. */
    public static final int PREVIEW_ROAD_Y = 16;

    public static final float PRESET_CARD_WIDTH = 96f;
    public static final float PRESET_CARD_HEIGHT = 118f;
    private static final float PRESET_PREVIEW_HEIGHT = 78f;
    private static final float LIVE_PREVIEW_HEIGHT = 120f;
    private static final float LABEL_PADDING = 4f;

    private static final int COLOR_AIR = 0x552A3545;
    private static final int COLOR_LINING = 0xFF8B7355;
    private static final int COLOR_ROAD = 0xFF555555;
    private static final int COLOR_MOUNTAIN = 0x33446B3A;
    private static final int COLOR_LABEL = 0xFFE8E8E8;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;

    private TunnelCrossSectionPreviewRenderer() {
    }

    /** Live preview in {@link TunnelStyleEditor}; updates as sliders move. */
    public static void renderLivePreview(
            TunnelShape shape,
            int clearHeight,
            int sideClearance,
            int liningThickness) {
        float width = Math.max(PRESET_CARD_WIDTH * 3f + 8f, ImGui.getContentRegionAvailX());
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + LIVE_PREVIEW_HEIGHT;

        drawList.addRectFilled(x0, y0, x1, y1, PluginUiColors.MAP_BG, 4f);
        drawList.addRect(x0, y0, x1, y1, PluginUiColors.PANEL_BORDER, 4f, 0, 1f);
        drawCrossSection(
            drawList,
            x0 + 4f,
            y0 + 4f,
            x1 - 4f,
            y1 - 4f,
            shape,
            clearHeight,
            sideClearance,
            liningThickness,
            true);
        ImGui.dummy(width, LIVE_PREVIEW_HEIGHT);
        ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.preview_legend"));
    }

    /** Three shape preset cards in a row; returns true when selection changes. */
    public static boolean renderShapePresetRow(TunnelShape selectedShape, java.util.function.Consumer<TunnelShape> onSelect) {
        boolean changed = false;
        ImGui.beginGroup();
        for (TunnelShape shape : TunnelShape.values()) {
            if (renderShapePresetCard(shape, shape == selectedShape)) {
                onSelect.accept(shape);
                changed = true;
            }
            if (shape != TunnelShape.values()[TunnelShape.values().length - 1]) {
                ImGui.sameLine(0f, 6f);
            }
        }
        ImGui.endGroup();
        return changed;
    }

    public static boolean renderShapePresetCard(TunnelShape shape, boolean selected) {
        if (shape == null) {
            return false;
        }
        String buttonId = "##tunnel_shape_card_" + shape.name();
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + PRESET_CARD_WIDTH;
        float y1 = y0 + PRESET_CARD_HEIGHT;

        int bg = selected ? 0xFF263238 : PluginUiColors.MAP_BG;
        drawList.addRectFilled(x0, y0, x1, y1, bg, 4f);
        int borderColor = selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.PANEL_BORDER;
        float borderThickness = selected ? 2f : 1f;
        drawList.addRect(x0, y0, x1, y1, borderColor, 4f, 0, borderThickness);

        float previewBottom = y0 + PRESET_PREVIEW_HEIGHT;
        drawCrossSection(
            drawList,
            x0 + 3f,
            y0 + 3f,
            x1 - 3f,
            previewBottom - 3f,
            shape,
            presetDefaults().clearHeight(),
            presetDefaults().sideClearance(),
            presetDefaults().liningThickness(),
            false);

        String label = PlotI18n.tr(shapeLabelKey(shape));
        float labelY = previewBottom + LABEL_PADDING;
        int labelColor = selected ? COLOR_LABEL : COLOR_LABEL_DIM;
        drawCenteredLabel(drawList, label, x0, labelY, labelColor);

        ImGui.invisibleButton(buttonId, PRESET_CARD_WIDTH, PRESET_CARD_HEIGHT);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.tunnel.shape_preset_tooltip", label));
        }
        return ImGui.isItemClicked(0);
    }

    static void drawCrossSection(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            TunnelShape shape,
            int clearHeight,
            int sideClearance,
            int liningThickness,
            boolean showMountain) {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            shape != null ? shape : TunnelShape.ARCH,
            PREVIEW_ENVELOPE_WIDTH,
            Math.max(TunnelStyle.MIN_SIDE_CLEARANCE, sideClearance),
            Math.max(TunnelStyle.MIN_CLEAR_HEIGHT, clearHeight),
            Math.max(TunnelStyle.MIN_LINING_THICKNESS, liningThickness),
            PREVIEW_ROAD_Y,
            false);

        int minLat = Integer.MAX_VALUE;
        int maxLat = Integer.MIN_VALUE;
        for (int lateral = -12; lateral <= 12; lateral++) {
            for (int y = mask.minY(); y <= mask.maxY(); y++) {
                if (mask.kindAt(lateral, y) != TunnelCrossSectionMask.CellKind.OUTSIDE) {
                    minLat = Math.min(minLat, lateral);
                    maxLat = Math.max(maxLat, lateral);
                }
            }
        }
        if (minLat == Integer.MAX_VALUE) {
            return;
        }

        int cols = maxLat - minLat + 1;
        int rows = mask.maxY() - mask.minY() + 1;
        float pad = 2f;
        float availW = Math.max(1f, x1 - x0 - pad * 2f);
        float availH = Math.max(1f, y1 - y0 - pad * 2f);
        float block = Math.min(availW / cols, availH / rows);
        block = Math.max(2f, (float) Math.floor(block));

        float gridW = cols * block;
        float gridH = rows * block;
        final float originX = x0 + (x1 - x0 - gridW) * 0.5f;
        final float originY = y0 + (y1 - y0 - gridH) * 0.5f;
        final int lateralMin = minLat;
        final float blockSize = block;
        final int maskMaxY = mask.maxY();

        if (showMountain) {
            drawList.addRectFilled(x0, y0, x1, originY, COLOR_MOUNTAIN, 2f);
        }

        mask.forEach((lateral, y, kind) -> {
            if (kind == TunnelCrossSectionMask.CellKind.OUTSIDE) {
                return;
            }
            int col = lateral - lateralMin;
            int row = maskMaxY - y;
            float bx = originX + col * blockSize;
            float by = originY + row * blockSize;
            int color = kind == TunnelCrossSectionMask.CellKind.AIR ? COLOR_AIR : COLOR_LINING;
            if (y == PREVIEW_ROAD_Y && Math.abs(lateral) <= PREVIEW_ENVELOPE_WIDTH / 2) {
                color = COLOR_ROAD;
            }
            drawList.addRectFilled(bx, by, bx + blockSize - 0.5f, by + blockSize - 0.5f, color, 1f);
        });

        float roadRowTop = originY + (maskMaxY - PREVIEW_ROAD_Y) * blockSize;
        drawList.addLine(
            originX,
            roadRowTop + block,
            originX + gridW,
            roadRowTop + block,
            COLOR_ROAD,
            1.5f);
    }

    static String shapeLabelKey(TunnelShape shape) {
        return switch (shape != null ? shape : TunnelShape.ARCH) {
            case RECTANGULAR -> "plugin.road.tunnel.shape.rectangular";
            case ARCH -> "plugin.road.tunnel.shape.arch";
            case HORSESHOE -> "plugin.road.tunnel.shape.horseshoe";
        };
    }

    private static ResolvedTunnelStyle presetDefaults() {
        return ResolvedTunnelStyle.defaults();
    }

    private static void drawCenteredLabel(ImDrawList drawList, String text, float cardX, float y, int color) {
        if (text == null || text.isBlank()) {
            return;
        }
        float textW = ImGui.calcTextSize(text).x;
        drawList.addText(cardX + (PRESET_CARD_WIDTH - textW) * 0.5f, y, color, text);
    }
}
