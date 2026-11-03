package com.plot.plugin.road.ui;

import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelLightPlacementResolver;
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
    public static final float PRESET_CARD_GAP = 6f;
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

    /** Screen-space layout of the live voxel grid for hit-testing. */
    public record PreviewGridLayout(
            float originX,
            float originY,
            float block,
            int minLateral,
            int displayMaxY,
            int cols,
            int rows) {
    }

    public record CellHit(int lateral, int y) {
    }

    private TunnelCrossSectionPreviewRenderer() {
    }

    /**
     * Live preview with optional light toggle on left-click.
     *
     * @return true when a light cell was toggled
     */
    public static boolean renderLivePreview(
            TunnelStyle style,
            boolean accentRings,
            int roadEnvelopeWidth,
            boolean lightsInteractive) {
        float width = Math.max(120f, ImGui.getContentRegionAvailX());
        float height = Math.max(LIVE_PREVIEW_HEIGHT, ImGui.getContentRegionAvailY());
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x0 = origin.x;
        float y0 = origin.y;
        float x1 = x0 + width;
        float y1 = y0 + height;
        int envelope = Math.max(1, roadEnvelopeWidth);

        drawList.addRectFilled(x0, y0, x1, y1, PluginUiColors.MAP_BG, 4f);
        drawList.addRect(x0, y0, x1, y1, PluginUiColors.PANEL_BORDER, 4f, 0, 1f);

        PreviewGridLayout layout = drawLiveCrossSection(
            drawList,
            x0 + 4f,
            y0 + 4f,
            x1 - 4f,
            y1 - 4f,
            style,
            envelope);

        ImGui.invisibleButton("##tunnel_live_preview_hit", width, height);
        boolean toggled = false;
        if (lightsInteractive && layout != null && ImGui.isItemClicked(0)) {
            ImVec2 mouse = ImGui.getMousePos();
            CellHit hit = hitTestCell(layout, mouse.x, mouse.y);
            if (hit != null && style != null) {
                toggled = TunnelLightPlacementResolver.toggleLightOnStyle(
                    style, envelope, PREVIEW_ROAD_Y, hit.lateral(), hit.y());
            }
        }
        return toggled;
    }

    /** @deprecated use {@link #renderLivePreview(TunnelStyle, boolean, int, boolean)} */
    @Deprecated
    public static void renderLivePreview(
            TunnelShape shape,
            int clearHeight,
            int sideClearance,
            int liningThickness,
            TunnelLightingMode lightingMode,
            boolean accentRings,
            int roadEnvelopeWidth) {
        TunnelStyle style = new TunnelStyle();
        style.setShape(shape);
        style.setClearHeight(clearHeight);
        style.setSideClearance(sideClearance);
        style.setLiningThickness(liningThickness);
        style.setLightingMode(lightingMode);
        style.setAccentRings(accentRings);
        renderLivePreview(style, accentRings, roadEnvelopeWidth, false);
    }

    /**
     * Shape preset cards wrapped to the available content width.
     * Returns true when selection changes.
     */
    public static boolean renderShapePresetRow(TunnelShape selectedShape, java.util.function.Consumer<TunnelShape> onSelect) {
        boolean changed = false;
        TunnelShape[] shapes = TunnelShape.values();
        int perRow = shapeCardsPerRow(ImGui.getContentRegionAvailX());
        ImGui.beginGroup();
        for (int i = 0; i < shapes.length; i++) {
            TunnelShape shape = shapes[i];
            if (i > 0 && i % perRow != 0) {
                ImGui.sameLine(0f, PRESET_CARD_GAP);
            }
            if (renderShapePresetCard(shape, shape == selectedShape)) {
                onSelect.accept(shape);
                changed = true;
            }
        }
        ImGui.endGroup();
        return changed;
    }

    /** Cards that fit in {@code availX} without horizontal overflow (≥1). */
    public static int shapeCardsPerRow(float availX) {
        float pitch = PRESET_CARD_WIDTH + PRESET_CARD_GAP;
        if (availX <= 0f || pitch <= 0f) {
            return 1;
        }
        return Math.max(1, (int) Math.floor((availX + PRESET_CARD_GAP) / pitch));
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

    static PreviewGridLayout drawLiveCrossSection(
            ImDrawList drawList,
            float x0,
            float y0,
            float x1,
            float y1,
            TunnelStyle style,
            int roadEnvelopeWidth) {
        TunnelSectionDisplayModel model = style != null
            ? TunnelSectionDisplayModel.buildFromStyle(style, Math.max(1, roadEnvelopeWidth), PREVIEW_ROAD_Y)
            : TunnelSectionDisplayModel.build(
                TunnelShape.ARCH,
                Math.max(1, roadEnvelopeWidth),
                1,
                5,
                1,
                PREVIEW_ROAD_Y,
                TunnelLightingMode.NONE,
                false);
        if (model.cells().isEmpty()) {
            return null;
        }

        int cols = Math.max(1, model.cols());
        int rows = Math.max(1, model.rows());
        float pad = 2f;
        float availW = Math.max(1f, x1 - x0 - pad * 2f);
        float availH = Math.max(1f, y1 - y0 - pad * 2f);
        // Prefer fitting the whole section; 3px is a readability preference, not a hard floor.
        float block = Math.min(availW / cols, availH / rows);
        float preferred = (float) Math.floor(block);
        if (preferred >= 3f) {
            block = preferred;
        } else {
            block = Math.max(1f, block);
        }

        float gridW = cols * block;
        float gridH = rows * block;
        final float originX = x0 + (x1 - x0 - gridW) * 0.5f;
        final float originY = y0 + (y1 - y0 - gridH) * 0.5f;

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

        CellHit hover = null;
        if (ImGui.isMouseHoveringRect(x0, y0, x1, y1)) {
            ImVec2 mouse = ImGui.getMousePos();
            hover = hitTestCell(
                new PreviewGridLayout(
                    originX, originY, block, model.minLateral(), model.displayMaxY(), cols, rows),
                mouse.x, mouse.y);
        }

        for (TunnelSectionDisplayModel.Cell cell : model.cells()) {
            int col = cell.lateral() - model.minLateral();
            int row = model.displayMaxY() - cell.y();
            float bx = originX + col * block;
            float by = originY + row * block;
            int color = colorFor(cell.kind());
            drawList.addRectFilled(bx, by, bx + block, by + block, color, 0f);
            drawList.addRect(bx, by, bx + block, by + block, COLOR_CELL_EDGE, 0f, 0, 1f);
            if (hover != null && hover.lateral() == cell.lateral() && hover.y() == cell.y()) {
                drawList.addRect(bx, by, bx + block, by + block, PluginUiColors.ACCENT_BLUE, 0f, 0, 2f);
            }
        }

        return new PreviewGridLayout(
            originX, originY, block, model.minLateral(), model.displayMaxY(), cols, rows);
    }

    /** Legacy signature used by deprecated drawCrossSection. */
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
            boolean accentRings,
            int roadEnvelopeWidth) {
        TunnelStyle style = new TunnelStyle();
        style.setShape(shape);
        style.setClearHeight(clearHeight);
        style.setSideClearance(sideClearance);
        style.setLiningThickness(liningThickness);
        style.setLightingMode(lightingMode);
        style.setAccentRings(accentRings);
        drawLiveCrossSection(drawList, x0, y0, x1, y1, style, roadEnvelopeWidth);
    }

    public static CellHit hitTestCell(PreviewGridLayout layout, float mouseX, float mouseY) {
        if (layout == null || layout.block() <= 0f) {
            return null;
        }
        float localX = mouseX - layout.originX();
        float localY = mouseY - layout.originY();
        if (localX < 0f || localY < 0f) {
            return null;
        }
        int col = (int) Math.floor(localX / layout.block());
        int row = (int) Math.floor(localY / layout.block());
        if (col < 0 || row < 0 || col >= layout.cols() || row >= layout.rows()) {
            return null;
        }
        int lateral = layout.minLateral() + col;
        int y = layout.displayMaxY() - row;
        return new CellHit(lateral, y);
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
    @Deprecated
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
                TunnelLightingMode.NONE, false, PREVIEW_ENVELOPE_WIDTH);
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
