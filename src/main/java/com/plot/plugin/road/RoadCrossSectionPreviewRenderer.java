package com.plot.plugin.road;

import com.plot.core.material.BlockColorRegistry;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadsideEdgeMode;
import com.plot.plugin.road.pipeline.crosssection.ResolvedBridgeStructure;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 道路横断面示意预览（ImGui 绘制，不依赖 World）。
 */
public final class RoadCrossSectionPreviewRenderer {
    private static final float PREVIEW_HEIGHT = 160f;
    private static final int COLOR_GROUND = 0xFF5C8A48;
    private static final int COLOR_GROUND_LINE = 0xFF3D5C32;
    private static final int COLOR_LABEL = 0xFFAAAAAA;
    private static final int COLOR_BORDER = 0xFF606060;
    /** 与 {@code PluginUiColors.PANEL_BG_DARK} 对齐的深色预览底。 */
    private static final int COLOR_BG = 0xFF141414;
    private static final int COLOR_MARKING = 0xFFE8E8E8;
    /** 预览材质色缓存，避免拖滑条时反复 resolveBlockId。 */
    private static final Map<String, Integer> MATERIAL_COLOR_CACHE = new HashMap<>();

    private RoadCrossSectionPreviewRenderer() {
    }

    public static void render(RoadSystemConfig config) {
        ImGui.text(PlotI18n.tr("plugin.road.cross_section_preview"));
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
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

        CrossSectionLayout layout = CrossSectionLayout.fromConfig(config);
        drawCrossSection(drawList, layout, x0, y0, width);

        ImGui.dummy(width, PREVIEW_HEIGHT);
    }

    public static void renderMini(
            ImDrawList drawList,
            CrossSectionLayout layout,
            float x,
            float y,
            float width,
            float height) {
        renderMini(drawList, layout, x, y, width, height, MiniRenderOptions.standard());
    }

    public static void renderMini(
            ImDrawList drawList,
            CrossSectionLayout layout,
            float x,
            float y,
            float width,
            float height,
            MiniRenderOptions options) {
        drawCrossSection(drawList, layout, x, y, width, height, options);
    }

    /**
     * 预设卡片副标题：仅描述横断面（宽度、车道等），不含纵坡——纵坡在生成阶段单独调整。
     */
    public static String formatPresetCaption(RoadStyle style, CrossSectionLayout layout) {
        if (style == null || layout == null) {
            return "";
        }
        int lanes = style.resolveLaneCount();
        int totalHardWidth = Math.round(layout.totalWidthBlocks());
        if (lanes > 1) {
            return PlotI18n.tr("plugin.road.preset_cross_section_caption", lanes, totalHardWidth);
        }
        return PlotI18n.tr("plugin.road.preset_carriageway_width", totalHardWidth);
    }

    private static void drawCrossSection(
            ImDrawList drawList,
            CrossSectionLayout layout,
            float x0,
            float y0,
            float width) {
        drawCrossSection(drawList, layout, x0, y0, width, RoadCrossSectionPreviewRenderer.PREVIEW_HEIGHT, MiniRenderOptions.standard());
    }

    private static void drawCrossSection(
            ImDrawList drawList,
            CrossSectionLayout layout,
            float x0,
            float y0,
            float width,
            float height,
            MiniRenderOptions options) {
        MiniRenderOptions renderOptions = options != null ? options : MiniRenderOptions.standard();
        PreviewGeometry geometry = PreviewGeometry.forBounds(
            layout, x0, y0, width, height, renderOptions);
        if (geometry == null) {
            return;
        }

        float padding = renderOptions.padding;
        float deckY = geometry.deckY();
        float deckH = geometry.deckH();
        float deckBottom = geometry.deckBottom();
        float groundY = geometry.groundY();
        float groundBottom = padding > 0f ? y0 + height - padding : y0 + height;
        float scale = geometry.scale();
        float cursorX = geometry.cursorX();
        float roadCenterX = geometry.roadCenterX();

        float groundLeft = x0 + padding;
        float groundRight = x0 + width - padding;
        drawList.addLine(groundLeft, groundY, groundRight, groundY, COLOR_GROUND_LINE, 1.5f);
        drawList.addRectFilled(groundLeft, groundY, groundRight, groundBottom, COLOR_GROUND);

        cursorX = drawBand(drawList, layout.leftSidewalkBlocks, cursorX, deckY, deckBottom, scale, layout.sidewalkColor);
        cursorX = drawBand(drawList, layout.leftBikeBlocks, cursorX, deckY, deckBottom, scale, layout.bikeColor);
        cursorX = drawBand(drawList, layout.leftShoulderBlocks, cursorX, deckY, deckBottom, scale, layout.shoulderColor);
        float roadStartX = cursorX;
        cursorX = drawBand(drawList, layout.roadBlocks, cursorX, deckY, deckBottom, scale, layout.roadColor);
        float roadWidthPx = layout.roadBlocks * scale;
        drawRoadMarkings(drawList, layout, roadStartX, roadWidthPx, deckY, deckH);
        cursorX = drawBand(drawList, layout.rightShoulderBlocks, cursorX, deckY, deckBottom, scale, layout.shoulderColor);
        cursorX = drawBand(drawList, layout.rightBikeBlocks, cursorX, deckY, deckBottom, scale, layout.bikeColor);
        drawBand(drawList, layout.rightSidewalkBlocks, cursorX, deckY, deckBottom, scale, layout.sidewalkColor);

        float leftEdgeX = roadCenterX - layout.leftOuterHardEdgeFromCenterBlocks() * scale;
        float rightEdgeX = roadCenterX + layout.rightOuterHardEdgeFromCenterBlocks() * scale;
        if (layout.includeSlopeBatter) {
            drawBatterSlope(
                drawList,
                leftEdgeX,
                deckBottom,
                groundY,
                -geometry.leftBatterPx(),
                layout.fillSlopeRatio,
                layout.fillSlopeColor,
                scale,
                renderOptions.drawSlopeLabels
            );
            if (renderOptions.drawCutSlope) {
                // 挖方贴路面顶（deckY）起坡，避免从路底起算造成悬空与空隙。
                drawBatterSlope(
                    drawList,
                    rightEdgeX,
                    deckY,
                    geometry.cutTopY(),
                    geometry.rightBatterPx(),
                    layout.cutSlopeRatio,
                    layout.cutSlopeColor,
                    scale,
                    renderOptions.drawSlopeLabels
                );
            }
        } else if (layout.includeRetainingWall) {
            drawRetainingWallColumn(
                drawList,
                leftEdgeX - scale,
                deckBottom,
                groundY,
                layout.retainingWallColor,
                scale,
                renderOptions.drawSlopeLabels);
            if (renderOptions.drawCutSlope) {
                drawRetainingWallColumn(
                    drawList,
                    rightEdgeX,
                    deckY,
                    geometry.cutTopY(),
                    layout.retainingWallColor,
                    scale,
                    renderOptions.drawSlopeLabels);
            }
        }

        drawBridgeFurniture(
            drawList, layout, roadStartX, leftEdgeX, rightEdgeX, deckY, deckBottom, groundY, scale);

        if (renderOptions.drawOverlayLabels) {
            String gradeLabel = layout.gradeOverlayLabel();
            if (gradeLabel != null && !gradeLabel.isBlank()) {
                float gradeWidth = ImGui.calcTextSize(gradeLabel).x;
                drawList.addText(
                    x0 + width - padding - gradeWidth,
                    y0 + padding * 0.5f,
                    COLOR_LABEL,
                    gradeLabel);
            }

            String label = PlotI18n.tr("plugin.road.cross_section_scale", Math.round(layout.totalWidthBlocks()));
            drawList.addText(x0 + padding, y0 + padding * 0.5f, COLOR_LABEL, label);
        }
    }

    /**
     * 预设卡片等紧凑预览的布局：计入边坡外扩，避免左右/上方被 Child 裁切。
     */
    static PreviewGeometry previewGeometryForTests(
            CrossSectionLayout layout,
            float width,
            float height,
            MiniRenderOptions options) {
        return PreviewGeometry.forBounds(layout, 0f, 0f, width, height, options);
    }

    static final class PreviewGeometry {
        private final float deckY;
        private final float deckH;
        private final float deckBottom;
        private final float groundY;
        private final float cutTopY;
        private final float scale;
        private final float cursorX;
        private final float roadCenterX;
        private final float visualWidth;
        private final float visualLeft;
        private final float visualRight;
        private final float topY;
        private final float leftBatterPx;
        private final float rightBatterPx;

        private PreviewGeometry(
                float deckY,
                float deckH,
                float deckBottom,
                float groundY,
                float cutTopY,
                float scale,
                float cursorX,
                float roadCenterX,
                float visualWidth,
                float visualLeft,
                float visualRight,
                float topY,
                float leftBatterPx,
                float rightBatterPx) {
            this.deckY = deckY;
            this.deckH = deckH;
            this.deckBottom = deckBottom;
            this.groundY = groundY;
            this.cutTopY = cutTopY;
            this.scale = scale;
            this.cursorX = cursorX;
            this.roadCenterX = roadCenterX;
            this.visualWidth = visualWidth;
            this.visualLeft = visualLeft;
            this.visualRight = visualRight;
            this.topY = topY;
            this.leftBatterPx = leftBatterPx;
            this.rightBatterPx = rightBatterPx;
        }

        static PreviewGeometry forBounds(
                CrossSectionLayout layout,
                float x0,
                float y0,
                float width,
                float height,
                MiniRenderOptions options) {
            float leftFromCenter = layout.leftDisplayedFromCenterBlocks();
            float rightFromCenter = layout.rightDisplayedFromCenterBlocks();
            float totalBlocks = leftFromCenter + rightFromCenter;
            if (totalBlocks <= 0f || width <= 0f || height <= 0f) {
                return null;
            }

            float padding = options.padding;
            float fillRun;
            float cutRun;
            if (layout.includeSlopeBatter) {
                fillRun = (float) Math.ceil(3 * Math.max(0, layout.fillSlopeRatio));
                cutRun = options.drawCutSlope
                    ? (float) Math.ceil(3 * Math.max(0, layout.cutSlopeRatio)) : 0;
            } else if (layout.includeRetainingWall) {
                fillRun = 1f;
                cutRun = options.drawCutSlope ? 1f : 0f;
            } else {
                fillRun = 0f;
                cutRun = 0f;
            }
            // 填/挖竖直示意高度必须是整格，避免坡面落在半格上。
            int fillRiseCells = fillRun > 0 ? 3 : 0;
            // 勾选桥墩但无填方时，仍预留净空格子以便画出墩柱。
            if (fillRiseCells == 0
                    && layout.bridgeFurniture != null
                    && layout.bridgeFurniture.showPillars()) {
                fillRiseCells = 3;
                fillRun = Math.max(fillRun, 1f);
            }
            int cutRiseCells = cutRun > 0 ? (options.compactSlopes ? 2 : 3) : 0;
            int medianRiseCells = layout.medianBlocks > 0f
                ? Math.max(1, Math.round(layout.medianHeightBlocks))
                : 0;
            // 顶留白 1 格 + 隔离带 + 挖方 + 路面 2 格 + 填方，按此分配竖直缩放，避免裁切。
            int verticalCells = 1 + medianRiseCells + cutRiseCells + 2 + fillRiseCells;
            float availableWidth = Math.max(1f, width - padding * 2f);
            float availableHeight = Math.max(1f, height - padding * 2f);
            float scale = Math.min(
                availableWidth / (totalBlocks + fillRun + cutRun),
                availableHeight / Math.max(1, verticalCells));
            float fillVert = fillRiseCells * scale;
            float cutHeadroom = cutRiseCells * scale;
            float medianHeadroom = medianRiseCells * scale;
            float deckY = y0 + padding + scale + cutHeadroom + medianHeadroom;
            float deckH = 2 * scale;
            float deckBottom = deckY + deckH;
            float groundY = deckBottom + fillVert;
            float cutTopY = cutRun > 0 ? deckY - cutHeadroom : deckY;
            float leftBatterPx = fillRun * scale;
            float rightBatterPx = cutRun * scale;
            float visualWidth = totalBlocks * scale + leftBatterPx + rightBatterPx;
            float visualLeft = x0 + (width - visualWidth) * 0.5f;
            float visualRight = visualLeft + visualWidth;
            float roadCenterX = visualLeft + leftBatterPx + leftFromCenter * scale;
            float cursorX = visualLeft + leftBatterPx;
            float topY = Math.min(deckY, cutTopY);

            return new PreviewGeometry(
                deckY,
                deckH,
                deckBottom,
                groundY,
                cutTopY,
                scale,
                cursorX,
                roadCenterX,
                visualWidth,
                visualLeft,
                visualRight,
                topY,
                leftBatterPx,
                rightBatterPx);
        }

        float deckY() {
            return deckY;
        }

        float deckH() {
            return deckH;
        }

        float deckBottom() {
            return deckBottom;
        }

        float groundY() {
            return groundY;
        }

        float cutTopY() {
            return cutTopY;
        }

        float scale() {
            return scale;
        }

        float cursorX() {
            return cursorX;
        }

        float roadCenterX() {
            return roadCenterX;
        }

        float visualWidth() {
            return visualWidth;
        }

        float visualLeft() {
            return visualLeft;
        }

        float visualRight() {
            return visualRight;
        }

        float topY() {
            return topY;
        }

        float leftBatterPx() {
            return leftBatterPx;
        }

        float rightBatterPx() {
            return rightBatterPx;
        }
    }

    private static void drawRoadMarkings(
            ImDrawList drawList,
            CrossSectionLayout layout,
            float roadStartX,
            float roadWidthPx,
            float deckY,
            float deckH) {
        if (roadWidthPx <= 0f) {
            return;
        }
        float scale = roadWidthPx / Math.max(1f, layout.roadBlocks);
        int roadBlocks = Math.max(1, Math.round(layout.roadBlocks));
        float half = layout.roadBlocks / 2f;
        // 分隔占格：按偏移落到整列，画满一格（与人行道条带同为方块示意，而非细线）。
        int markingColor = layout.markingColor != 0 ? layout.markingColor : COLOR_MARKING;
        for (Float ratio : layout.markingLineRatios) {
            if (ratio == null) {
                continue;
            }
            double offset = ratio * layout.roadBlocks - half;
            int column = (int) Math.floor(offset + half);
            if (column < 0 || column >= roadBlocks) {
                continue;
            }
            float left = roadStartX + column * scale;
            float right = left + scale;
            float split = deckY + deckH * 0.5f;
            drawVoxelFace(drawList, left, split, right, deckY + deckH, 0xFF73777A);
            drawVoxelFace(drawList, left, deckY, right, split, markingColor);
        }
        // 路表中央隔离带：居中叠在路面顶上，宽度按奇偶对齐后的格数，高度 1–3。
        if (layout.medianBlocks > 0f && layout.medianHeightBlocks > 0f) {
            int medianCols = Math.max(1, Math.round(layout.medianBlocks));
            int heightCols = Math.max(1, Math.round(layout.medianHeightBlocks));
            float medianPx = medianCols * scale;
            float medianX = roadStartX + (roadWidthPx - medianPx) * 0.5f;
            for (int row = 0; row < heightCols; row++) {
                float top = deckY - (row + 1) * scale;
                float bottom = deckY - row * scale;
                for (int col = 0; col < medianCols; col++) {
                    float left = medianX + col * scale;
                    float right = left + scale;
                    drawVoxelFace(drawList, left, top, right, bottom, layout.medianColor);
                }
            }
        }
    }

    private static float drawBand(
            ImDrawList drawList,
            float blocks,
            float x,
            float topY,
            float bottomY,
            float scale,
            int color) {
        if (blocks <= 0f) {
            return x;
        }
        float w = blocks * scale;
        float split = topY + (bottomY - topY) * 0.5f;
        int count = Math.clamp((int) Math.ceil(blocks), 1, 512);
        for (int i = 0; i < count; i++) {
            float left = x + i * scale;
            float right = Math.min(x + w, left + scale);
            drawVoxelFace(drawList, left, split, right, bottomY, 0xFF73777A);
            drawVoxelFace(drawList, left, topY, right, split, color);
        }
        return x + w;
    }

    private static void drawVoxelFace(ImDrawList draw, float x0, float y0, float x1, float y1, int color) {
        draw.addRectFilled(x0, y0, x1, y1, color);
        if (x1 - x0 >= 3f && y1 - y0 >= 3f) {
            draw.addLine(x0 + 1, y0 + 1, x1 - 1, y0 + 1, 0x45FFFFFF, 1f);
            draw.addLine(x1 - 1, y0 + 1, x1 - 1, y1 - 1, 0x50000000, 1f);
        }
        draw.addRect(x0, y0, x1, y1, 0x85000000);
    }

    private static void drawBridgeFurniture(
            ImDrawList drawList,
            CrossSectionLayout layout,
            float roadStartX,
            float leftEdgeX,
            float rightEdgeX,
            float deckY,
            float deckBottom,
            float groundY,
            float scale) {
        CrossSectionLayout.BridgeFurniturePreview furniture = layout.bridgeFurniture;
        if (furniture == null || scale <= 1e-6f) {
            return;
        }
        if (furniture.showPillars()) {
            int roadWidth = Math.max(1, Math.round(layout.roadBlocks));
            int diameter = Math.max(1, furniture.pierDiameterBlocks());
            int roadMin = RoadDimensionUtils.minLateralOffset(roadWidth);
            if (furniture.pierStyle() == BridgePierStyle.DOUBLE) {
                // pierOffsetBlocks = 柱间空隙；换算为左右柱心后再画，保证预览与参数一致。
                int gap = Math.max(1, furniture.pierOffsetBlocks());
                int[] centers = RoadDimensionUtils.doublePierCenters(gap, diameter);
                drawPierFootprint(
                    drawList, roadStartX, roadMin, centers[0], diameter, deckBottom, groundY, scale,
                    furniture.pierColor());
                drawPierFootprint(
                    drawList, roadStartX, roadMin, centers[1], diameter, deckBottom, groundY, scale,
                    furniture.pierColor());
            } else {
                drawPierFootprint(
                    drawList, roadStartX, roadMin, 0, diameter, deckBottom, groundY, scale,
                    furniture.pierColor());
            }
        }
        if (furniture.showGuardrail()) {
            int height = Math.max(1, furniture.guardrailHeightBlocks());
            for (int i = 0; i < height; i++) {
                float y1 = deckY - i * scale;
                float y0 = y1 - scale;
                drawVoxelFace(drawList, leftEdgeX - scale, y0, leftEdgeX, y1, furniture.guardrailColor());
                drawVoxelFace(drawList, rightEdgeX, y0, rightEdgeX + scale, y1, furniture.guardrailColor());
            }
        }
    }

    /**
     * 与世界生成相同的横向偏移语义：{@code centerLateralOffset + [min..max](diameter)}，
     * 再映射到行车道左缘网格，避免相对几何中心的半格错位。
     */
    private static void drawPierFootprint(
            ImDrawList drawList,
            float roadStartX,
            int roadMinLateral,
            int centerLateralOffset,
            int diameterBlocks,
            float deckBottom,
            float groundY,
            float scale,
            int color) {
        for (int absolute : RoadDimensionUtils.pierFootprintLaterals(centerLateralOffset, diameterBlocks)) {
            // 相对行车道左缘的整数格索引，与 drawBand 路面格子一一对齐。
            float x0 = roadStartX + (absolute - roadMinLateral) * scale;
            drawRetainingWallColumn(drawList, x0, deckBottom, groundY, color, scale, false);
        }
    }

    private static void drawRetainingWallColumn(
            ImDrawList drawList,
            float x0,
            float attachY,
            float endY,
            int color,
            float cell,
            boolean drawLabel) {
        if (cell <= 1e-6f || Math.abs(endY - attachY) < cell * 0.5f) {
            return;
        }
        int cells = Math.max(1, Math.round(Math.abs(endY - attachY) / cell));
        boolean upward = endY < attachY;
        for (int r = 0; r < cells; r++) {
            float y1;
            float y0;
            if (upward) {
                y1 = attachY - r * cell;
                y0 = y1 - cell;
            } else {
                y0 = attachY + r * cell;
                y1 = y0 + cell;
            }
            drawVoxelFace(drawList, x0, y0, x0 + cell, y1, color);
        }
        if (drawLabel) {
            String label = PlotI18n.tr("plugin.road.preview.retaining_wall");
            float labelY = upward
                ? (attachY + endY) * 0.5f - ImGui.getTextLineHeight() * 0.5f
                : attachY + Math.max(2f, cell * 0.2f);
            drawList.addText(x0 + 2f, labelY, COLOR_LABEL, label);
        }
    }

    private static void drawBatterSlope(
            ImDrawList drawList,
            float edgeX,
            float attachY,
            float groundY,
            float horizontalRun,
            float slopeRatio,
            int color,
            float blockSize,
            boolean drawLabel) {
        float cell = blockSize;
        if (cell <= 1e-6f || slopeRatio <= 0f || Math.abs(horizontalRun) < cell * 0.5f) {
            return;
        }
        // 严格整格网格：列宽=cell，升高为整数格，禁止半格错位。
        int columns = Math.clamp(Math.round(Math.abs(horizontalRun) / cell), 1, 128);
        int maxRiseCells = Math.max(1, Math.round(Math.abs(groundY - attachY) / cell));
        float dirX = Math.signum(horizontalRun);
        boolean cutSlope = groundY < attachY;
        for (int i = 0; i < columns; i++) {
            int riseCells = batterRiseCells(slopeRatio, i, maxRiseCells, cutSlope);
            if (riseCells <= 0 && cutSlope) {
                continue;
            }
            float x0 = edgeX + dirX * i * cell;
            float x1 = x0 + dirX * cell;
            float left = Math.min(x0, x1);
            float right = Math.max(x0, x1);
            if (cutSlope) {
                // 挖方：底边贴路面顶，向上堆 riseCells 整格（与填方贴路底向下对称）。
                for (int r = 0; r < riseCells; r++) {
                    float y1 = attachY - r * cell;
                    float y0 = y1 - cell;
                    drawVoxelFace(drawList, left, y0, right, y1, color);
                }
            } else {
                // 填方：坡面在 attachY 以下 riseCells 处，再填到地面（整格）。
                int fillCells = Math.max(0, maxRiseCells - riseCells);
                for (int r = 0; r < fillCells; r++) {
                    float y0 = attachY + (riseCells + r) * cell;
                    float y1 = y0 + cell;
                    drawVoxelFace(drawList, left, y0, right, y1, color);
                }
            }
        }

        if (drawLabel) {
            String label = cutSlope
                ? PlotI18n.tr("plugin.road.preview.cut_batter", SlopeFormatUtils.formatRatio(slopeRatio))
                : PlotI18n.tr("plugin.road.preview.fill_batter", SlopeFormatUtils.formatRatio(slopeRatio));
            float endX = edgeX + dirX * columns * cell;
            float spanLeft = Math.min(edgeX, endX);
            float spanRight = Math.max(edgeX, endX);
            float labelWidth = ImGui.calcTextSize(label).x;
            float labelX = spanLeft + 2f;
            if (labelX + labelWidth > spanRight) {
                labelX = Math.max(spanLeft, spanRight - labelWidth);
            }
            float labelY = cutSlope
                ? (attachY + groundY) * 0.5f - ImGui.getTextLineHeight() * 0.5f
                : attachY + Math.max(2f, cell * 0.2f);
            drawList.addText(labelX, labelY, COLOR_LABEL, label);
        }
    }

    /**
     * 第 {@code columnIndex} 列相对贴边高程的整格升高/下降数。
     * 填方 i=0 为 0（贴路底外侧整列填实）；挖方 i=0 为 1（贴路面顶外侧升高一格）。
     */
    static int batterRiseCells(float slopeRatio, int columnIndex, int maxRiseCells, boolean cutSlope) {
        if (slopeRatio <= 0f || columnIndex < 0 || maxRiseCells <= 0) {
            return 0;
        }
        int rise;
        if (cutSlope) {
            rise = Math.max(1, Math.round((columnIndex + 1) / slopeRatio));
        } else {
            rise = Math.round(columnIndex / slopeRatio);
        }
        return Math.min(maxRiseCells, rise);
    }

    /**
     * 第 {@code columnIndex} 列坡面顶/底边的屏幕 Y（整格）。
     */
    static float batterFaceY(
            float attachY,
            float targetY,
            float slopeRatio,
            int columnIndex,
            float cell,
            boolean cutSlope) {
        if (cell <= 0f) {
            return attachY;
        }
        int maxRise = Math.max(1, Math.round(Math.abs(targetY - attachY) / cell));
        int rise = batterRiseCells(slopeRatio, columnIndex, maxRise, cutSlope);
        float direction = Math.signum(targetY - attachY);
        if (direction == 0f) {
            return attachY;
        }
        return attachY + direction * rise * cell;
    }

    public static final class MiniRenderOptions {
        public final float padding;
        public final boolean drawOverlayLabels;
        public final boolean drawSlopeLabels;
        public final boolean compactSlopes;
        public final boolean drawCutSlope;
        public final float deckYRatio;
        public final float deckHRatio;
        public final float groundYRatio;

        private MiniRenderOptions(
                float padding,
                boolean drawOverlayLabels,
                boolean drawSlopeLabels,
                boolean compactSlopes,
                boolean drawCutSlope,
                float deckYRatio,
                float deckHRatio,
                float groundYRatio) {
            this.padding = padding;
            this.drawOverlayLabels = drawOverlayLabels;
            this.drawSlopeLabels = drawSlopeLabels;
            this.compactSlopes = compactSlopes;
            this.drawCutSlope = drawCutSlope;
            this.deckYRatio = deckYRatio;
            this.deckHRatio = deckHRatio;
            this.groundYRatio = groundYRatio;
        }

        public static MiniRenderOptions standard() {
            return new MiniRenderOptions(8f, true, true, false, true, 0.28f, 0.22f, 0.72f);
        }

        /**
         * 快速设计大预览：总宽/纵坡已在标题栏，图内不重复；保留坡比标注，尽量放大断面。
         */
        public static MiniRenderOptions quickDesign() {
            return new MiniRenderOptions(6f, false, true, false, true, 0.28f, 0.22f, 0.72f);
        }

        /** 预设卡片：挖/填均为三层整格，标注坡比；外扩压到卡片内。 */
        public static MiniRenderOptions presetCard() {
            return new MiniRenderOptions(4f, false, true, false, true, 0.06f, 0.42f, 0.72f);
        }
    }

    public static final class CrossSectionLayout {
        public final float roadBlocks;
        public final float leftShoulderBlocks;
        public final float rightShoulderBlocks;
        public final float leftSidewalkBlocks;
        public final float rightSidewalkBlocks;
        public final float leftBikeBlocks;
        public final float rightBikeBlocks;
        public final boolean includeShoulder;
        public final float shoulderBlocks;
        public final float fillSlopeRatio;
        public final float cutSlopeRatio;
        public final boolean includeSlopeBatter;
        public final boolean includeRetainingWall;
        /** 设计/局部纵坡上限（%）；与 {@link #minSlopePercent} 组成展示区间。 */
        public final float maxSlopePercent;
        /**
         * 沿路局部纵坡下限（%）。{@code < 0} 表示未设区间，仅展示 {@link #maxSlopePercent}。
         * 用于分段坡度 override 等「有的地方大、有的地方小」的情况。
         */
        public final float minSlopePercent;
        public final int roadColor;
        public final int sidewalkColor;
        public final int bikeColor;
        public final int shoulderColor;
        public final int fillSlopeColor;
        public final int cutSlopeColor;
        public final int retainingWallColor;
        public final float medianBlocks;
        /** 路表以上隔离带高度（格）。 */
        public final float medianHeightBlocks;
        public final int medianColor;
        public final List<Float> markingLineRatios;
        public final int markingColor;
        /** 桥梁结构示意；{@code null} 表示不画桥墩/护栏。 */
        public final BridgeFurniturePreview bridgeFurniture;

        private CrossSectionLayout(
                float roadBlocks,
                float leftShoulderBlocks,
                float rightShoulderBlocks,
                float leftSidewalkBlocks,
                float rightSidewalkBlocks,
                float leftBikeBlocks,
                float rightBikeBlocks,
                boolean includeShoulder,
                float shoulderBlocks,
                float fillSlopeRatio,
                float cutSlopeRatio,
                boolean includeSlopeBatter,
                boolean includeRetainingWall,
                float maxSlopePercent,
                float minSlopePercent,
                int roadColor,
                int sidewalkColor,
                int bikeColor,
                int shoulderColor,
                int fillSlopeColor,
                int cutSlopeColor,
                int retainingWallColor,
                float medianBlocks,
                float medianHeightBlocks,
                int medianColor,
                List<Float> markingLineRatios,
                int markingColor,
                BridgeFurniturePreview bridgeFurniture) {
            this.roadBlocks = roadBlocks;
            this.leftShoulderBlocks = leftShoulderBlocks;
            this.rightShoulderBlocks = rightShoulderBlocks;
            this.leftSidewalkBlocks = leftSidewalkBlocks;
            this.rightSidewalkBlocks = rightSidewalkBlocks;
            this.leftBikeBlocks = leftBikeBlocks;
            this.rightBikeBlocks = rightBikeBlocks;
            this.includeShoulder = includeShoulder;
            this.shoulderBlocks = shoulderBlocks;
            this.fillSlopeRatio = fillSlopeRatio;
            this.cutSlopeRatio = cutSlopeRatio;
            this.includeSlopeBatter = includeSlopeBatter;
            this.includeRetainingWall = includeRetainingWall;
            this.maxSlopePercent = maxSlopePercent;
            this.minSlopePercent = minSlopePercent;
            this.roadColor = roadColor;
            this.sidewalkColor = sidewalkColor;
            this.bikeColor = bikeColor;
            this.shoulderColor = shoulderColor;
            this.fillSlopeColor = fillSlopeColor;
            this.cutSlopeColor = cutSlopeColor;
            this.retainingWallColor = retainingWallColor;
            this.medianBlocks = medianBlocks;
            this.medianHeightBlocks = Math.max(0f, medianHeightBlocks);
            this.medianColor = medianColor;
            this.markingLineRatios = markingLineRatios != null ? List.copyOf(markingLineRatios) : List.of();
            this.markingColor = markingColor;
            this.bridgeFurniture = bridgeFurniture;
        }

        public static CrossSectionLayout fromResolved(ResolvedCrossSection section, float maxSlopePercent) {
            return fromResolved(section, maxSlopePercent, -1f, null);
        }

        public static CrossSectionLayout fromResolved(
                ResolvedCrossSection section,
                float maxSlopePercent,
                float minSlopePercent) {
            return fromResolved(section, maxSlopePercent, minSlopePercent, null);
        }

        public static CrossSectionLayout fromResolved(
                ResolvedCrossSection section,
                float maxSlopePercent,
                float minSlopePercent,
                ResolvedBridgeStructure bridgeStructure) {
            float road = Math.max(1, section.carriagewayWidth);
            float shoulder = section.includeShoulder ? Math.max(0, section.shoulderWidth) : 0f;
            float bike = section.includeBikeLane ? Math.max(0, section.bikeLaneWidth) : 0f;
            float sidewalk = section.includeSidewalk ? Math.max(0, section.sidewalkWidth) : 0f;
            List<Float> markingRatios = buildMarkingRatios(section, road);
            BridgeFurniturePreview furniture = BridgeFurniturePreview.from(
                bridgeStructure, Math.round(road));
            return new CrossSectionLayout(
                road,
                shoulder, shoulder,
                sidewalk, sidewalk,
                bike, bike,
                section.includeShoulder,
                shoulder,
                section.includeSlopeBatter ? section.fillSlopeRatio : 0f,
                section.includeSlopeBatter ? section.cutSlopeRatio : 0f,
                section.includeSlopeBatter,
                section.includeRetainingWall,
                maxSlopePercent,
                minSlopePercent,
                colorForMaterial(section.carriagewayMaterial.getPrimaryMaterial(), 0xFF707070),
                colorForMaterial(section.sidewalkMaterial, 0xFF989898),
                colorForMaterial(section.bikeLaneMaterial, 0xFF6FA8D8),
                colorForMaterial(section.shoulderMaterial, 0xFFB8A070),
                colorForMaterial(section.fillSlopeMaterial, 0xFFB8A070),
                colorForMaterial(section.cutSlopeMaterial, 0xFF808080),
                colorForMaterial(section.retainingWallMaterial, 0xFF8A8A82),
                section.includeMedian ? section.medianWidth : 0f,
                section.includeMedian ? Math.max(1, section.medianHeight) : 0f,
                colorForMaterial(
                    section.medianMaterial != null
                        ? section.medianMaterial
                        : RoadParameterLimits.DEFAULT_MEDIAN_MATERIAL,
                    0xFF4FA84F),
                markingRatios,
                colorForMaterial(section.markingMaterial, COLOR_MARKING),
                furniture
            );
        }

        /** 横断面桥梁结构示意（桥墩柱列 / 护栏）。 */
        public record BridgeFurniturePreview(
                boolean showPillars,
                boolean showGuardrail,
                BridgePierStyle pierStyle,
                int pierDiameterBlocks,
                int pierOffsetBlocks,
                int guardrailHeightBlocks,
                int pierColor,
                int guardrailColor) {

            static BridgeFurniturePreview from(ResolvedBridgeStructure bridge, int carriagewayWidth) {
                if (bridge == null) {
                    return null;
                }
                if (!bridge.generatePillars() && !bridge.includeGuardrail()) {
                    return null;
                }
                ResolvedBridgeStructure clamped = bridge.withCarriagewayWidth(carriagewayWidth);
                return new BridgeFurniturePreview(
                    clamped.generatePillars(),
                    clamped.includeGuardrail(),
                    clamped.pierStyle(),
                    clamped.pierDiameterBlocks(),
                    clamped.pierOffsetBlocks(),
                    clamped.guardrailHeightBlocks(),
                    colorForMaterial(clamped.pierMaterial(), 0xFF8A8A82),
                    colorForMaterial(clamped.guardrailMaterial(), 0xFFC4A574));
            }
        }

        /** 横断面图右上角纵坡文案；纵坡沿路常有变化，有区间时显示上下限。 */
        public String gradeOverlayLabel() {
            if (maxSlopePercent <= 0f && minSlopePercent < 0f) {
                return null;
            }
            float hi = Math.max(0f, maxSlopePercent);
            if (minSlopePercent >= 0f && minSlopePercent < hi - 0.05f) {
                return PlotI18n.tr(
                    "plugin.road.cross_section_grade_range",
                    SlopeFormatUtils.formatPercent(minSlopePercent),
                    SlopeFormatUtils.formatPercent(hi));
            }
            if (hi <= 0f) {
                return null;
            }
            return PlotI18n.tr(
                "plugin.road.cross_section_grade",
                SlopeFormatUtils.formatPercent(hi));
        }

        private static List<Float> buildMarkingRatios(ResolvedCrossSection section, float roadWidth) {
            List<Float> ratios = new ArrayList<>();
            if (section == null || roadWidth <= 0f) {
                return ratios;
            }
            double half = roadWidth / 2.0;
            // 与世界生成共用 RoadMarkingPasses，保证占格网格一致。
            for (RoadMarkingPasses.Pass pass : RoadMarkingPasses.fromCrossSection(section)) {
                ratios.add((float) ((pass.offset() + half) / roadWidth));
            }
            return ratios;
        }

        public static CrossSectionLayout fromConfig(RoadSystemConfig config) {
            float grade = config != null ? config.getMaxSlope() : 0f;
            return fromResolved(ResolvedCrossSection.fromConfig(config), grade);
        }

        /** 预设卡片：用风格自身材质（含风格自带 themeId），不套当前道路主题。 */
        public static CrossSectionLayout fromStyle(RoadStyle style) {
            return fromStyle(style, null);
        }

        /** 编辑区大预览：可叠加当前道路/配置主题。 */
        public static CrossSectionLayout fromStyle(RoadStyle style, String themeId) {
            if (style == null) {
                return fromResolved(ResolvedCrossSection.fromConfig(new RoadSystemConfig("preview")), 10.0f);
            }
            RoadSystemConfig defaults = new RoadSystemConfig("preview");
            // 预览全局默认关掉路边处理，避免风格未声明时继承出边坡。
            defaults.setRoadsideEdgeMode(RoadsideEdgeMode.NONE);
            var section = style.toCrossSection(themeId);
            return fromResolved(
                ResolvedCrossSection.resolve(section, defaults),
                0f
            );
        }

        public float totalWidthBlocks() {
            return leftDisplayedFromCenterBlocks() + rightDisplayedFromCenterBlocks();
        }

        /** 从道路中心到左侧最外侧硬质路面外缘的横向距离（方块）。 */
        public float leftOuterHardEdgeFromCenterBlocks() {
            return roadBlocks / 2f + leftShoulderBlocks + leftBikeBlocks + leftSidewalkBlocks;
        }

        /** 从道路中心到右侧最外侧硬质路面外缘的横向距离（方块）。 */
        public float rightOuterHardEdgeFromCenterBlocks() {
            return roadBlocks / 2f + rightShoulderBlocks + rightBikeBlocks + rightSidewalkBlocks;
        }

        float leftDisplayedFromCenterBlocks() {
            return leftOuterHardEdgeFromCenterBlocks();
        }

        float rightDisplayedFromCenterBlocks() {
            return rightOuterHardEdgeFromCenterBlocks();
        }
    }

    /**
     * 预览色：先匹配具体方块/染色混凝土，再回退到材质族，保证不同材质在卡片上可辨。
     * 同时扫原始材质 key 与解析后的 block id，避免别名落到石头色。
     */
    static int colorForMaterial(String material, int fallback) {
        if (material == null || material.isBlank()) {
            return fallback;
        }
        Integer cached = MATERIAL_COLOR_CACHE.get(material);
        if (cached != null) {
            return cached;
        }
        // 优先方块色表（与路灯三维预览一致），启发式仅作未知 id 兜底。
        String blockId = RoadMaterialUtils.resolveBlockId(material);
        int registry = BlockColorRegistry.colorFor(blockId != null ? blockId : material);
        int color = (registry & 0x00FFFFFF) != 0 ? registry : resolveMaterialColor(material, fallback);
        if (MATERIAL_COLOR_CACHE.size() < 256) {
            MATERIAL_COLOR_CACHE.put(material, color);
        }
        return color;
    }

    private static int resolveMaterialColor(String material, int fallback) {
        String blockId = RoadMaterialUtils.resolveBlockId(material);
        String id = ((blockId != null ? blockId : "") + " " + material).toLowerCase();
        if (id.contains("blackstone") || id.contains("asphalt")
            || id.contains("black_concrete") || id.contains("black_wool")) {
            return 0xFF3A3A3A;
        }
        if (id.contains("gray_concrete") || id.contains("grey_concrete")) {
            return 0xFF6E6E6E;
        }
        if (id.contains("light_gray_concrete") || id.contains("light_grey_concrete")) {
            return 0xFFA8A8A8;
        }
        if (id.contains("white_") || id.contains("quartz")) {
            return 0xFFD8D8D8;
        }
        if (id.contains("cyan_")) {
            return 0xFF2BB3B3;
        }
        if (id.contains("light_blue")) {
            return 0xFF6FA8D8;
        }
        if (id.contains("yellow_") || id.contains("glowstone") || id.contains("gold")) {
            return 0xFFE0C040;
        }
        if (id.contains("orange_")) {
            return 0xFFD07830;
        }
        if (id.contains("red_sandstone") || id.contains("red_sand")) {
            return 0xFFC07040;
        }
        if (id.contains("red_") || id.contains("netherrack") || id.contains("magma")) {
            return 0xFFA04038;
        }
        if (id.contains("blue_")) {
            return 0xFF3A6EC8;
        }
        if (id.contains("purple_") || id.contains("magenta_")) {
            return 0xFF8A4CB8;
        }
        if (id.contains("pink_")) {
            return 0xFFE090B0;
        }
        if (id.contains("brown_")) {
            return 0xFF8A6040;
        }
        if (id.contains("lime_") || id.contains("green_concrete") || id.contains("green_wool")) {
            return 0xFF5AA848;
        }
        if (id.contains("concrete")) {
            return 0xFFB0B0B0;
        }
        if (id.contains("grass") || id.contains("moss")) {
            return 0xFF6FA856;
        }
        if (id.contains("dirt_path")) {
            return 0xFFC4A46A;
        }
        if (id.contains("coarse_dirt") || id.contains("rooted_dirt")) {
            return 0xFF8B6A45;
        }
        if (id.contains("podzol") || id.contains("mud")) {
            return 0xFF6B4E32;
        }
        if (id.contains("gravel")) {
            return 0xFF9A9A90;
        }
        if (id.contains("smooth_sandstone") || id.contains("sandstone") || id.contains("sand")) {
            return 0xFFD8C078;
        }
        if (id.contains("dirt")) {
            return 0xFFB8A070;
        }
        if (id.contains("snow")) {
            return 0xFFE8F0F4;
        }
        if (id.contains("packed_ice") || id.contains("blue_ice")) {
            return 0xFF78B8E0;
        }
        if (id.contains("ice")) {
            return 0xFFB0D8E8;
        }
        if (id.contains("brick") || id.contains("terracotta") || id.contains("plank")
            || id.contains("oak") || id.contains("spruce") || id.contains("birch")) {
            return 0xFF9A5A40;
        }
        if (id.contains("cobbled_deepslate") || id.contains("polished_deepslate")) {
            return 0xFF4A4A52;
        }
        if (id.contains("deepslate")) {
            return 0xFF3A3A42;
        }
        if (id.contains("polished_andesite") || id.contains("andesite")) {
            return 0xFF8A8A82;
        }
        if (id.contains("diorite")) {
            return 0xFFC8C8C0;
        }
        if (id.contains("granite")) {
            return 0xFFA07058;
        }
        if (id.contains("basalt")) {
            return 0xFF4A4A4E;
        }
        if (id.contains("cobble")) {
            return 0xFF7A7A72;
        }
        if (id.contains("stone_bricks") || id.contains("stone_brick")) {
            return 0xFF75756E;
        }
        if (id.contains("stone")) {
            return 0xFF909088;
        }
        if (id.contains("iron") || id.contains("rail")) {
            return 0xFFC0C0C0;
        }
        return fallback;
    }
}
