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
    private static final float PREVIEW_HEIGHT = 72f;
    private static final int COLOR_GROUND = 0xFF5C8A48;
    private static final int COLOR_GROUND_LINE = 0xFF3D5C32;
    private static final int COLOR_DRAINAGE = 0xFF4A4A4A;
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

        CrossSectionLayout layout = CrossSectionLayout.fromResolved(
            ResolvedCrossSection.fromConfig(config),
            config.getMaxSlope());
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

    public static String formatPresetCaption(CrossSectionLayout layout) {
        String scale = PlotI18n.tr("plugin.road.cross_section_scale", Math.round(layout.totalWidthBlocks()));
        if (layout.maxSlopePercent <= 0f) {
            return scale;
        }
        String grade = PlotI18n.tr(
            "plugin.road.cross_section_grade",
            SlopeFormatUtils.formatPercent(layout.maxSlopePercent)
        );
        return scale + ", " + grade;
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

        if (renderOptions.drawDrainage) {
            cursorX = drawBand(drawList, layout.drainageBlocks, cursorX, deckBottom, groundY, scale, COLOR_DRAINAGE);
        }
        cursorX = drawBand(drawList, layout.leftSidewalkBlocks, cursorX, deckY, deckBottom, scale, layout.sidewalkColor);
        cursorX = drawBand(drawList, layout.leftBikeBlocks, cursorX, deckY, deckBottom, scale, layout.bikeColor);
        cursorX = drawBand(drawList, layout.leftShoulderBlocks, cursorX, deckY, deckBottom, scale, layout.shoulderColor);
        float roadStartX = cursorX;
        cursorX = drawBand(drawList, layout.roadBlocks, cursorX, deckY, deckBottom, scale, layout.roadColor);
        float roadWidthPx = layout.roadBlocks * scale;
        drawRoadMarkings(drawList, layout, roadStartX, roadWidthPx, deckY, deckH);
        cursorX = drawBand(drawList, layout.rightShoulderBlocks, cursorX, deckY, deckBottom, scale, layout.shoulderColor);
        cursorX = drawBand(drawList, layout.rightBikeBlocks, cursorX, deckY, deckBottom, scale, layout.bikeColor);
        cursorX = drawBand(drawList, layout.rightSidewalkBlocks, cursorX, deckY, deckBottom, scale, layout.sidewalkColor);
        if (renderOptions.drawDrainage) {
            drawBand(drawList, layout.drainageBlocks, cursorX, deckBottom, groundY, scale, COLOR_DRAINAGE);
        }

        if (layout.includeSlopeBatter) {
            float leftEdgeX = roadCenterX - layout.leftOuterHardEdgeFromCenterBlocks() * scale;
            float rightEdgeX = roadCenterX + layout.rightOuterHardEdgeFromCenterBlocks() * scale;

            drawBatterSlope(
                drawList,
                leftEdgeX,
                deckBottom,
                groundY,
                -1,
                layout.fillSlopeRatio,
                layout.fillSlopeColor,
                layout.fillSlopeRatio > 0f,
                renderOptions.drawSlopeLabels
            );
            if (renderOptions.drawCutSlope) {
                drawBatterSlope(
                    drawList,
                    rightEdgeX,
                    deckBottom,
                    geometry.cutTopY(),
                    1,
                    layout.cutSlopeRatio,
                    layout.cutSlopeColor,
                    layout.cutSlopeRatio > 0f,
                    renderOptions.drawSlopeLabels
                );
            }
        }

        if (renderOptions.drawOverlayLabels) {
            if (layout.maxSlopePercent > 0f) {
                String gradeLabel = PlotI18n.tr(
                    "plugin.road.cross_section_grade",
                    SlopeFormatUtils.formatPercent(layout.maxSlopePercent)
                );
                drawList.addText(x0 + width - padding - 72f, y0 + padding * 0.5f, COLOR_LABEL, gradeLabel);
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
                float topY) {
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
        }

        static PreviewGeometry forBounds(
                CrossSectionLayout layout,
                float x0,
                float y0,
                float width,
                float height,
                MiniRenderOptions options) {
            float totalBlocks = layout.totalWidthBlocks();
            if (totalBlocks <= 0f || width <= 0f || height <= 0f) {
                return null;
            }

            float padding = options.padding;
            float deckY = y0 + height * options.deckYRatio;
            float deckH = height * options.deckHRatio;
            float deckBottom = deckY + deckH;
            float groundY = y0 + height * options.groundYRatio;
            float fillVertDrop = Math.max(0f, groundY - deckBottom);
            float cutTopY = resolveCutTopY(deckY, deckH, deckBottom, fillVertDrop, y0, options);
            float cutVertDrop = Math.max(0f, deckBottom - cutTopY);

            float leftBatterPx = layout.includeSlopeBatter && layout.fillSlopeRatio > 0f
                ? fillVertDrop * layout.fillSlopeRatio
                : 0f;
            float rightBatterPx = layout.includeSlopeBatter && options.drawCutSlope && layout.cutSlopeRatio > 0f
                ? cutVertDrop * layout.cutSlopeRatio
                : 0f;
            float availableWidth = Math.max(1f, width - padding * 2f);
            float scale = (availableWidth - leftBatterPx - rightBatterPx) / totalBlocks;
            if (scale <= 0f) {
                scale = availableWidth / totalBlocks;
            }

            float leftOuterBlocks = layout.leftOuterHardEdgeFromCenterBlocks();
            float rightOuterBlocks = layout.rightOuterHardEdgeFromCenterBlocks();
            float visualWidth = totalBlocks * scale + leftBatterPx + rightBatterPx;
            float visualLeft = x0 + (width - visualWidth) * 0.5f;
            float visualRight = visualLeft + visualWidth;
            float roadCenterX = visualLeft + leftBatterPx + leftOuterBlocks * scale;
            float cursorX = roadCenterX - totalBlocks * 0.5f * scale;
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
                topY);
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
            float x = roadStartX + roadWidthPx * Math.max(0f, Math.min(1f, ratio));
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
        drawList.addRectFilled(x, topY, x + w, bottomY, color);
        drawList.addRect(x, topY, x + w, bottomY, COLOR_BORDER);
        return x + w;
    }

    private static void drawBatterSlope(
            ImDrawList drawList,
            float edgeX,
            float deckBottom,
            float groundY,
            int horizontalSign,
            float slopeRatio,
            int color,
            boolean enabled,
            boolean drawLabel) {
        if (!enabled || slopeRatio <= 0f) {
            return;
        }
        float verticalDrop = Math.abs(groundY - deckBottom);
        if (verticalDrop < 1f) {
            return;
        }
        float horizontalRun = verticalDrop * slopeRatio * horizontalSign;
        float endX = edgeX + horizontalRun;
        drawList.addTriangleFilled(edgeX, deckBottom, endX, groundY, edgeX, groundY, color);
        drawList.addLine(edgeX, deckBottom, endX, groundY, COLOR_BORDER, 1.2f);

        if (drawLabel) {
            String label = SlopeFormatUtils.formatRatio(slopeRatio);
            float labelX = horizontalSign < 0 ? endX + 2f : edgeX + 2f;
            drawList.addText(labelX, deckBottom + 2f, COLOR_LABEL, label);
        }
    }

    public static final class MiniRenderOptions {
        public final float padding;
        public final boolean drawOverlayLabels;
        public final boolean drawSlopeLabels;
        public final boolean compactSlopes;
        public final boolean drawDrainage;
        public final boolean drawCutSlope;
        public final float deckYRatio;
        public final float deckHRatio;
        public final float groundYRatio;

        private MiniRenderOptions(
                float padding,
                boolean drawOverlayLabels,
                boolean drawSlopeLabels,
                boolean compactSlopes,
                boolean drawDrainage,
                boolean drawCutSlope,
                float deckYRatio,
                float deckHRatio,
                float groundYRatio) {
            this.padding = padding;
            this.drawOverlayLabels = drawOverlayLabels;
            this.drawSlopeLabels = drawSlopeLabels;
            this.compactSlopes = compactSlopes;
            this.drawDrainage = drawDrainage;
            this.drawCutSlope = drawCutSlope;
            this.deckYRatio = deckYRatio;
            this.deckHRatio = deckHRatio;
            this.groundYRatio = groundYRatio;
        }

        public static MiniRenderOptions standard() {
            return new MiniRenderOptions(8f, true, true, false, true, true, 0.28f, 0.22f, 0.72f);
        }

        /** 预设卡片：隐藏排水沟、不画挖方三角，只保留填方示意。 */
        public static MiniRenderOptions presetCard() {
            return new MiniRenderOptions(2f, false, false, true, false, false, 0.06f, 0.42f, 0.72f);
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
        public final float drainageBlocks;
        public final boolean includeShoulder;
        public final float shoulderBlocks;
        public final float fillSlopeRatio;
        public final float cutSlopeRatio;
        public final boolean includeSlopeBatter;
        public final float maxSlopePercent;
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
                float drainageBlocks,
                boolean includeShoulder,
                float shoulderBlocks,
                float fillSlopeRatio,
                float cutSlopeRatio,
                boolean includeSlopeBatter,
                float maxSlopePercent,
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
            this.drainageBlocks = drainageBlocks;
            this.includeShoulder = includeShoulder;
            this.shoulderBlocks = shoulderBlocks;
            this.fillSlopeRatio = fillSlopeRatio;
            this.cutSlopeRatio = cutSlopeRatio;
            this.includeSlopeBatter = includeSlopeBatter;
            this.maxSlopePercent = maxSlopePercent;
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

        private static CrossSectionLayout create(
                float roadBlocks,
                float leftShoulderBlocks,
                float rightShoulderBlocks,
                float leftSidewalkBlocks,
                float rightSidewalkBlocks,
                float leftBikeBlocks,
                float rightBikeBlocks,
                float drainageBlocks,
                boolean includeShoulder,
                float shoulderBlocks,
                float fillSlopeRatio,
                float cutSlopeRatio,
                float maxSlopePercent,
                int roadColor,
                int sidewalkColor,
                int bikeColor,
                int shoulderColor) {
            return new CrossSectionLayout(
                roadBlocks,
                leftShoulderBlocks,
                rightShoulderBlocks,
                leftSidewalkBlocks,
                rightSidewalkBlocks,
                leftBikeBlocks,
                rightBikeBlocks,
                drainageBlocks,
                includeShoulder,
                shoulderBlocks,
                fillSlopeRatio,
                cutSlopeRatio,
                fillSlopeRatio > 0f || cutSlopeRatio > 0f,
                maxSlopePercent,
                roadColor,
                sidewalkColor,
                bikeColor,
                shoulderColor,
                shoulderColor,
                shoulderColor,
                0f,
                0,
                List.of(),
                0
            );
        }

        public static CrossSectionLayout fromResolved(ResolvedCrossSection section, float maxSlopePercent) {
            float road = Math.max(1, section.carriagewayWidth);
            float shoulder = section.includeShoulder ? Math.max(0, section.shoulderWidth) : 0f;
            float bike = section.includeBikeLane ? Math.max(0, section.bikeLaneWidth) : 0f;
            float sidewalk = section.includeSidewalk ? Math.max(0, section.sidewalkWidth) : 0f;
            float drainage = section.includeDrain ? 0.5f : 0f;
            List<Float> markingRatios = buildMarkingRatios(section, road);
            return new CrossSectionLayout(
                road,
                shoulder, shoulder,
                sidewalk, sidewalk,
                bike, bike,
                drainage,
                section.includeShoulder,
                shoulder,
                section.includeSlopeBatter ? section.fillSlopeRatio : 0f,
                section.includeSlopeBatter ? section.cutSlopeRatio : 0f,
                section.includeSlopeBatter,
                maxSlopePercent,
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
            if (section.centerLineStyle == CenterLineStyle.SINGLE_DASHED) {
                ratios.add(0.5f);
            } else if (section.centerLineStyle == CenterLineStyle.DOUBLE_SOLID) {
                ratios.add(0.45f);
                ratios.add(0.55f);
            }
            return ratios;
        }

        public static CrossSectionLayout fromConfig(RoadSystemConfig config) {
            return fromResolved(ResolvedCrossSection.fromConfig(config), config.getMaxSlope());
        }

        public static CrossSectionLayout fromStyle(RoadStyle style) {
            return fromStyle(style, null);
        }

        public static CrossSectionLayout fromStyle(RoadStyle style, String themeId) {
            if (style == null) {
                return fromResolved(ResolvedCrossSection.fromConfig(new RoadSystemConfig("preview")), 10.0f);
            }
            RoadSystemConfig defaults = new RoadSystemConfig("preview");
            return fromResolved(
                ResolvedCrossSection.resolve(style.toCrossSection(themeId), defaults),
                style.maxSlope > 0f ? style.maxSlope : defaults.getMaxSlope()
            );
        }

        /** @deprecated 使用 {@link #fromStyle(RoadStyle)} */
        @Deprecated
        public static CrossSectionLayout fromPreset(RoadStyle preset) {
            return fromStyle(preset);
        }

        public float totalWidthBlocks() {
            return roadBlocks
                + leftShoulderBlocks + rightShoulderBlocks
                + leftBikeBlocks + rightBikeBlocks
                + leftSidewalkBlocks + rightSidewalkBlocks
                + drainageBlocks * 2f;
        }

        public float centerOffsetBlocks(float scale) {
            float left = drainageBlocks + leftSidewalkBlocks + leftBikeBlocks + leftShoulderBlocks;
            return left * scale;
        }

        /** 从道路中心到左侧最外侧硬质路面外缘的横向距离（方块）。 */
        public float leftOuterHardEdgeFromCenterBlocks() {
            return roadBlocks / 2f + leftShoulderBlocks + leftBikeBlocks + leftSidewalkBlocks;
        }

        /** 从道路中心到右侧最外侧硬质路面外缘的横向距离（方块）。 */
        public float rightOuterHardEdgeFromCenterBlocks() {
            return roadBlocks / 2f + rightShoulderBlocks + rightBikeBlocks + rightSidewalkBlocks;
        }

        private static int colorForMaterial(String material, int fallback) {
            String blockId = RoadMaterialUtils.resolveBlockId(material);
            if (blockId == null) {
                return fallback;
            }
            String id = blockId.toLowerCase();
            if (id.contains("white") || id.contains("concrete") || id.contains("quartz")) {
                return 0xFFD8D8D8;
            }
            if (id.contains("black") || id.contains("asphalt") || id.contains("gray_concrete")) {
                return 0xFF404040;
            }
            if (id.contains("gravel") || id.contains("sand") || id.contains("dirt")) {
                return 0xFFB8A070;
            }
            if (id.contains("grass") || id.contains("green")) {
                return 0xFF6FA856;
            }
            if (id.contains("stone") || id.contains("cobble")) {
                return 0xFF808080;
            }
            if (id.contains("brick") || id.contains("terracotta")) {
                return 0xFF9A5A40;
            }
            return fallback;
        }
    }
}
