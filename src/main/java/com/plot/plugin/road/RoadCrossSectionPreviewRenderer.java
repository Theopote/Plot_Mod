package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路横断面示意预览（ImGui 绘制，不依赖 World）。
 */
public final class RoadCrossSectionPreviewRenderer {
    private static final float PREVIEW_HEIGHT = 160f;
    private static final int COLOR_GROUND = 0xFF5C8A48;
    private static final int COLOR_GROUND_LINE = 0xFF3D5C32;
    private static final int COLOR_LABEL = 0xFFAAAAAA;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_BG = 0xFF2A2A2A;
    private static final int COLOR_MARKING = 0xFFE8E8E8;

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
        drawCrossSection(drawList, layout, x0, y0, width, PREVIEW_HEIGHT);

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
            float width,
            float height) {
        drawCrossSection(drawList, layout, x0, y0, width, height, MiniRenderOptions.standard());
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

        if (layout.includeSlopeBatter) {
            float leftEdgeX = roadCenterX - layout.leftOuterHardEdgeFromCenterBlocks() * scale;
            float rightEdgeX = roadCenterX + layout.rightOuterHardEdgeFromCenterBlocks() * scale;

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
        }

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
            float fillRun = layout.includeSlopeBatter ? (float) Math.ceil(3 * Math.max(0, layout.fillSlopeRatio)) : 0;
            float cutRun = layout.includeSlopeBatter && options.drawCutSlope
                ? (float) Math.ceil(3 * Math.max(0, layout.cutSlopeRatio)) : 0;
            float availableWidth = Math.max(1f, width - padding * 2f);
            float scale = Math.min(availableWidth / (totalBlocks + fillRun + cutRun),
                Math.max(1f, height - padding * 2f) / 7f);
            float fillVert = 3 * scale;
            // 有挖方时为上方台阶预留空间，避免 cutTop 画出卡片。
            float cutHeadroom = cutRun > 0 ? (options.compactSlopes ? 1.2f * scale : 3f * scale) : 0f;
            float deckY = y0 + padding + scale + cutHeadroom;
            float deckH = 2 * scale;
            float deckBottom = deckY + deckH;
            float groundY = deckBottom + fillVert;
            float cutTopY = cutRun > 0
                ? Math.max(y0 + 1f, deckY - cutHeadroom)
                : deckY;
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

    private static float compactBatterRun(float run, MiniRenderOptions options, float height) {
        if (run <= 0f) {
            return 0f;
        }
        if (!options.compactSlopes) {
            return run;
        }
        return Math.clamp(height * 0.2f, 6f, run);
    }

    private static float resolveCutTopY(
            float deckY,
            float deckH,
            float deckBottom,
            float fillVertDrop,
            float y0,
            MiniRenderOptions options) {
        if (options.compactSlopes) {
            float rise = Math.min(deckH * 0.35f, fillVertDrop * 0.4f);
            return Math.max(y0 + 1f, deckY - rise);
        }
        return deckY - Math.max(10f, fillVertDrop * 0.5f);
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
        if (layout.medianBlocks > 0f) {
            float medianPx = layout.medianBlocks * (roadWidthPx / Math.max(1f, layout.roadBlocks));
            float medianX = roadStartX + roadWidthPx * 0.5f - medianPx * 0.5f;
            drawList.addRectFilled(medianX, deckY, medianX + medianPx, deckY + deckH, layout.medianColor);
            drawList.addRect(medianX, deckY, medianX + medianPx, deckY + deckH, COLOR_BORDER);
        }
        int markingColor = layout.markingColor != 0 ? layout.markingColor : COLOR_MARKING;
        for (Float ratio : layout.markingLineRatios) {
            if (ratio == null) {
                continue;
            }
            float x = roadStartX + roadWidthPx * Math.clamp(ratio, 0f, 1f);
            drawList.addLine(x, deckY + 1f, x, deckY + deckH - 1f, markingColor, 1.8f);
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
        int count = Math.min(512, Math.max(1, (int) Math.ceil(blocks)));
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
        if (Math.abs(horizontalRun) < 1f || slopeRatio <= 0f) {
            return;
        }
        float verticalDrop = Math.abs(groundY - attachY);
        if (verticalDrop < 1f) {
            return;
        }
        float endX = edgeX + horizontalRun;
        float cell = blockSize;
        int columns = Math.min(128, Math.max(1, (int) Math.ceil(Math.abs(horizontalRun) / cell)));
        float step = horizontalRun / columns;
        // ImGui Y 向下：groundY > attachY 为填方（往下），groundY < attachY 为挖方（往上）。
        boolean cutSlope = groundY < attachY;
        for (int i = 0; i < columns; i++) {
            float a = edgeX + step * i;
            float b = a + step;
            float face = batterFaceY(attachY, groundY, slopeRatio, i, cell, cutSlope);
            float minY;
            float maxY;
            if (cutSlope) {
                // 挖方：贴路面顶起坡，从坡面画到路面，第 0 列即有一块（无空隙）。
                minY = face;
                maxY = attachY;
            } else {
                // 填方：从坡面往下填到地面，越向外越低。
                minY = Math.min(face, groundY);
                maxY = Math.max(face, groundY);
            }
            if (maxY - minY < 1f) {
                continue;
            }
            for (float y = minY; y < maxY; y += cell) {
                drawVoxelFace(drawList, Math.min(a, b), y, Math.max(a, b), Math.min(maxY, y + cell), color);
            }
        }

        if (drawLabel) {
            String label = SlopeFormatUtils.formatRatio(slopeRatio);
            float labelX = horizontalRun < 0f ? endX + 2f : edgeX + 2f;
            drawList.addText(labelX, attachY + 2f, COLOR_LABEL, label);
        }
    }

    /**
     * 第 {@code columnIndex} 列坡面顶/底边的屏幕 Y。
     * 填方随列外移下降；挖方随列外移上升，且第 0 列至少升高 1 格以免留空。
     */
    static float batterFaceY(
            float attachY,
            float targetY,
            float slopeRatio,
            int columnIndex,
            float cell,
            boolean cutSlope) {
        if (slopeRatio <= 0f || cell <= 0f || columnIndex < 0) {
            return attachY;
        }
        float direction = Math.signum(targetY - attachY);
        if (direction == 0f) {
            return attachY;
        }
        float riseBlocks;
        if (cutSlope) {
            // i=0 → 1 格，贴路边且底边与路面平齐；再向外递增。
            riseBlocks = Math.min(3, Math.max(1, Math.round((columnIndex + 1) / slopeRatio)));
        } else {
            riseBlocks = Math.min(3, Math.round(columnIndex / slopeRatio));
        }
        float face = attachY + direction * riseBlocks * cell;
        if (direction < 0f) {
            return Math.max(face, targetY);
        }
        return Math.min(face, targetY);
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

        /** 预设卡片：短挖方三角，边坡外扩压到卡片内。 */
        public static MiniRenderOptions presetCard() {
            return new MiniRenderOptions(2f, false, false, true, true, 0.06f, 0.42f, 0.72f);
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
        public final float medianBlocks;
        public final int medianColor;
        public final List<Float> markingLineRatios;
        public final int markingColor;

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
                float maxSlopePercent,
                float minSlopePercent,
                int roadColor,
                int sidewalkColor,
                int bikeColor,
                int shoulderColor,
                int fillSlopeColor,
                int cutSlopeColor,
                float medianBlocks,
                int medianColor,
                List<Float> markingLineRatios,
                int markingColor) {
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
            this.maxSlopePercent = maxSlopePercent;
            this.minSlopePercent = minSlopePercent;
            this.roadColor = roadColor;
            this.sidewalkColor = sidewalkColor;
            this.bikeColor = bikeColor;
            this.shoulderColor = shoulderColor;
            this.fillSlopeColor = fillSlopeColor;
            this.cutSlopeColor = cutSlopeColor;
            this.medianBlocks = medianBlocks;
            this.medianColor = medianColor;
            this.markingLineRatios = markingLineRatios != null ? List.copyOf(markingLineRatios) : List.of();
            this.markingColor = markingColor;
        }

        public static CrossSectionLayout fromResolved(ResolvedCrossSection section, float maxSlopePercent) {
            return fromResolved(section, maxSlopePercent, -1f);
        }

        public static CrossSectionLayout fromResolved(
                ResolvedCrossSection section,
                float maxSlopePercent,
                float minSlopePercent) {
            float road = Math.max(1, section.carriagewayWidth);
            float shoulder = section.includeShoulder ? Math.max(0, section.shoulderWidth) : 0f;
            float bike = section.includeBikeLane ? Math.max(0, section.bikeLaneWidth) : 0f;
            float sidewalk = section.includeSidewalk ? Math.max(0, section.sidewalkWidth) : 0f;
            List<Float> markingRatios = buildMarkingRatios(section, road);
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
                maxSlopePercent,
                minSlopePercent,
                colorForMaterial(section.carriagewayMaterial.getPrimaryMaterial(), 0xFF707070),
                colorForMaterial(section.sidewalkMaterial, 0xFF989898),
                colorForMaterial(section.bikeLaneMaterial, 0xFF6FA8D8),
                colorForMaterial(section.shoulderMaterial, 0xFFB8A070),
                colorForMaterial(section.fillSlopeMaterial, 0xFFB8A070),
                colorForMaterial(section.cutSlopeMaterial, 0xFF808080),
                section.includeMedian ? section.medianWidth : 0f,
                colorForMaterial(section.medianMaterial, 0xFF6FA856),
                markingRatios,
                colorForMaterial(section.markingMaterial, COLOR_MARKING)
            );
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
            double half = roadWidth / 2.0;
            if (section.laneDividers) {
                for (Double offset : section.laneDividerOffsets) {
                    if (offset != null) {
                        ratios.add((float) ((offset + half) / roadWidth));
                    }
                }
            }
            if (section.includeMedian && section.medianWidth > 0) {
                if (section.centerLineStyle == CenterLineStyle.DOUBLE_SOLID) {
                    double edge = RoadDimensionUtils.halfExtentFromCenter(section.medianWidth) + 0.5;
                    ratios.add((float) ((half - edge) / roadWidth));
                    ratios.add((float) ((half + edge) / roadWidth));
                }
            } else if (section.centerLineStyle == CenterLineStyle.SINGLE_DASHED) {
                ratios.add(0.5f);
            } else if (section.centerLineStyle == CenterLineStyle.DOUBLE_SOLID) {
                ratios.add(0.45f);
                ratios.add(0.55f);
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
            var section = style.toCrossSection(themeId);
            // Show the style's own slope setting, including silent urban presets,
            // without changing their inheritance behavior during generation.
            section.getSlopeBatter().setEnabled(style.resolveIncludeSlopeBatter());
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
     * 预览色：先匹配黑/灰/白/青等具体混凝土，再回退到泛化材质族。
     * 同时扫原始材质 key 与解析后的 block id，避免别名落到石头色。
     */
    static int colorForMaterial(String material, int fallback) {
        if (material == null || material.isBlank()) {
            return fallback;
        }
        String blockId = RoadMaterialUtils.resolveBlockId(material);
        String id = ((blockId != null ? blockId : "") + " " + material).toLowerCase();
        if (id.contains("blackstone") || id.contains("black_") || id.contains("asphalt")) {
            return 0xFF3A3A3A;
        }
        if (id.contains("gray_concrete") || id.contains("grey_concrete")) {
            return 0xFF6E6E6E;
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
        if (id.contains("blue_")) {
            return 0xFF3A6EC8;
        }
        if (id.contains("concrete")) {
            return 0xFFB0B0B0;
        }
        if (id.contains("grass") || id.contains("moss") || id.contains("green")) {
            return 0xFF6FA856;
        }
        if (id.contains("dirt_path") || id.contains("gravel") || id.contains("sand")
            || id.contains("dirt") || id.contains("mud")) {
            return 0xFFB8A070;
        }
        if (id.contains("snow") || id.contains("ice")) {
            return 0xFFD0E8F0;
        }
        if (id.contains("brick") || id.contains("terracotta") || id.contains("plank")) {
            return 0xFF9A5A40;
        }
        if (id.contains("stone") || id.contains("cobble") || id.contains("andesite")
            || id.contains("deepslate") || id.contains("basalt")) {
            return 0xFF808080;
        }
        if (id.contains("iron")) {
            return 0xFFC0C0C0;
        }
        return fallback;
    }
}
