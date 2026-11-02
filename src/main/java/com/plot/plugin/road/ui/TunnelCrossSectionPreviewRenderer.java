package com.plot.plugin.road.ui;

import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelSectionDisplayModel;
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

    public static final float PRESET_CARD_WIDTH = 78f;
    public static final float PRESET_CARD_HEIGHT = 100f;
    private static final float PRESET_PREVIEW_HEIGHT = 64f;
    private static final float LIVE_PREVIEW_HEIGHT = 172f;
    private static final float LABEL_PADDING = 4f;

    private static final int COLOR_AIR = 0xFF2A3545;
    private static final int COLOR_LINING = 0xFF8FA8B8;
    private static final int COLOR_ROAD = 0xFF4A5560;
    private static final int COLOR_MOUNTAIN = 0x33446B3A;
    private static final int COLOR_WALL_LIGHT = 0xFFFFC94A;
    private static final int COLOR_CEILING_LIGHT = 0xFFFFE08A;
    private static final int COLOR_LABEL = 0xFFE8E8E8;
    private static final int COLOR_LABEL_DIM = 0xFFB0B0B0;
    private static final int COLOR_CELL_EDGE = 0x33000000;

    private TunnelCrossSectionPreviewRenderer() {
    }

    /** Live preview in {@link TunnelStyleEditor}; updates as sliders move. */
    public static void renderLivePreview(
            TunnelShape shape,
            int clearHeight,
            int sideClearance,
            int liningThickness,
            TunnelLightingMode lightingMode,
            boolean accentRings) {
        float width = Math.max(PRESET_CARD_WIDTH * 3f + 8f, ImGui.getContentRegionAvailX());
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + LIVE_PREVIEW_HEIGHT;

        drawList.addRectFilled(x0, y0, x1, y1, PluginUiColors.MAP_BG, 4f);
        drawList.addRect(x0, y0, x1, y1, PluginUiColors.PANEL_BORDER, 4f, 0, 1f);
        drawLiveCrossSection(
            drawList,
            x0 + 4f,
            y0 + 4f,
            x1 - 4f,
            y1 - 4f,
            shape,
            clearHeight,
            sideClearance,
            liningThickness,
            lightingMode,
            accentRings);
        ImGui.dummy(width, LIVE_PREVIEW_HEIGHT);
        ImGui.textDisabled(PlotI18n.tr("plugin.road.tunnel.preview_legend"));
    }

    /** Single-row shape cards; returns true when selection changes. */
    public static boolean renderShapePresetRow(TunnelShape selectedShape, java.util.function.Consumer<TunnelShape> onSelect) {
        boolean changed = false;
        TunnelShape[] shapes = TunnelShape.values();
        ImGui.beginGroup();
        for (int i = 0; i < shapes.length; i++) {
            TunnelShape shape = shapes[i];
            if (i > 0) {
                ImGui.sameLine(0f, 6f);
            }
            if (renderShapePresetCard(shape, shape == selectedShape)) {
                onSelect.accept(shape);
                changed = true;
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
        drawPresetCrossSection(
            drawList,
            x0 + 3f,
            y0 + 3f,
            x1 - 3f,
            previewBottom - 3f,
            shape,
            presetDefaults().clearHeight(),
            presetDefaults().sideClearance(),
            presetDefaults().liningThickness());

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

    static void drawLiveCrossSection(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            TunnelShape shape,
            int clearHeight,
            int sideClearance,
            int liningThickness,
            TunnelLightingMode lightingMode,
            boolean accentRings) {
        TunnelSectionDisplayModel model = TunnelSectionDisplayModel.build(
            shape != null ? shape : TunnelShape.ARCH,
            PREVIEW_ENVELOPE_WIDTH,
            Math.max(TunnelStyle.MIN_SIDE_CLEARANCE, sideClearance),
            Math.max(TunnelStyle.MIN_CLEAR_HEIGHT, clearHeight),
            Math.max(TunnelStyle.MIN_LINING_THICKNESS, liningThickness),
            PREVIEW_ROAD_Y,
            lightingMode != null ? lightingMode : TunnelLightingMode.NONE,
            accentRings);
        if (model.cells().isEmpty()) {
            return;
        }

        int cols = Math.max(1, model.cols());
        int rows = Math.max(1, model.rows());
        float pad = 2f;
        float availW = Math.max(1f, x1 - x0 - pad * 2f);
        float availH = Math.max(1f, y1 - y0 - pad * 2f);
        float block = Math.min(availW / cols, availH / rows);
        block = Math.max(3f, (float) Math.floor(block));

        float gridW = cols * block;
        float gridH = rows * block;
        final float originX = x0 + (x1 - x0 - gridW) * 0.5f;
        final float originY = y0 + (y1 - y0 - gridH) * 0.5f;

        // Mountain fill: outside the voxel grid, above the road row.
        int roadRowFromTop = model.displayMaxY() - model.roadY();
        float roadTopY = originY + roadRowFromTop * block;
        if (roadTopY > y0) {
            drawList.addRectFilled(x0, y0, x1, Math.min(roadTopY, originY), COLOR_MOUNTAIN, 2f);
            if (originX > x0) {
                drawList.addRectFilled(x0, Math.max(y0, originY), originX, roadTopY, COLOR_MOUNTAIN, 0f);
            }
            float gridRight = originX + gridW;
            if (gridRight < x1) {
                drawList.addRectFilled(gridRight, Math.max(y0, originY), x1, roadTopY, COLOR_MOUNTAIN, 0f);
            }
        }

        for (TunnelSectionDisplayModel.Cell cell : model.cells()) {
            int col = cell.lateral() - model.minLateral();
            int row = model.displayMaxY() - cell.y();
            float bx = originX + col * block;
            float by = originY + row * block;
            int color = colorFor(cell.kind());
            drawList.addRectFilled(bx, by, bx + block, by + block, color, 0f);
            drawList.addRect(bx, by, bx + block, by + block, COLOR_CELL_EDGE, 0f, 0, 1f);
        }
    }

    /** Compact preset thumbnail: geometry only, no lights/accents, roadY+ clip. */
    static void drawPresetCrossSection(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            TunnelShape shape,
            int clearHeight,
            int sideClearance,
            int liningThickness) {
        TunnelSectionDisplayModel model = TunnelSectionDisplayModel.build(
            shape != null ? shape : TunnelShape.ARCH,
            PREVIEW_ENVELOPE_WIDTH,
            Math.max(TunnelStyle.MIN_SIDE_CLEARANCE, sideClearance),
            Math.max(TunnelStyle.MIN_CLEAR_HEIGHT, clearHeight),
            Math.max(TunnelStyle.MIN_LINING_THICKNESS, liningThickness),
            PREVIEW_ROAD_Y,
            TunnelLightingMode.NONE,
            false);
        if (model.cells().isEmpty()) {
            return;
        }

        int cols = Math.max(1, model.cols());
        int rows = Math.max(1, model.rows());
        float pad = 1f;
        float availW = Math.max(1f, x1 - x0 - pad * 2f);
        float availH = Math.max(1f, y1 - y0 - pad * 2f);
        float block = Math.min(availW / cols, availH / rows);
        block = Math.max(2f, (float) Math.floor(block));

        float gridW = cols * block;
        float gridH = rows * block;
        float originX = x0 + (x1 - x0 - gridW) * 0.5f;
        float originY = y0 + (y1 - y0 - gridH) * 0.5f;

        for (TunnelSectionDisplayModel.Cell cell : model.cells()) {
            int col = cell.lateral() - model.minLateral();
            int row = model.displayMaxY() - cell.y();
            float bx = originX + col * block;
            float by = originY + row * block;
            int color = colorFor(cell.kind());
            drawList.addRectFilled(bx, by, bx + block, by + block, color, 0f);
        }
    }

    /** @deprecated retained for tests that build masks directly; prefer {@link TunnelSectionDisplayModel}. */
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
        if (showMountain) {
            drawLiveCrossSection(
                drawList, x0, y0, x1, y1, shape, clearHeight, sideClearance, liningThickness,
                TunnelLightingMode.NONE, false);
        } else {
            drawPresetCrossSection(
                drawList, x0, y0, x1, y1, shape, clearHeight, sideClearance, liningThickness);
        }
    }

    static String shapeLabelKey(TunnelShape shape) {
        return switch (shape != null ? shape : TunnelShape.ARCH) {
            case D_SHAPE -> "plugin.road.tunnel.shape.d_shape";
            case RECTANGULAR -> "plugin.road.tunnel.shape.rectangular";
            case HORSESHOE -> "plugin.road.tunnel.shape.horseshoe";
            case ELLIPTICAL -> "plugin.road.tunnel.shape.elliptical";
            case CIRCULAR -> "plugin.road.tunnel.shape.circular";
            case ARCH -> "plugin.road.tunnel.shape.arch";
            case PENTAGONAL -> "plugin.road.tunnel.shape.pentagonal";
        };
    }

    private static int colorFor(TunnelSectionDisplayModel.DisplayKind kind) {
        return switch (kind) {
            case AIR -> COLOR_AIR;
            case LINING -> COLOR_LINING;
            case ROAD -> COLOR_ROAD;
            case WALL_LIGHT -> COLOR_WALL_LIGHT;
            case CEILING_LIGHT -> COLOR_CEILING_LIGHT;
            case ACCENT -> 0xFFE07A3D;
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
