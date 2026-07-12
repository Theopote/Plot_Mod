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
        drawCrossSection(drawList, layout, x, y, width, height);
    }

    private static void drawCrossSection(
            ImDrawList drawList,
            CrossSectionLayout layout,
            float x0,
            float y0,
            float width,
            float height) {
        float padding = 8f;
        float deckY = y0 + height * 0.28f;
        float deckH = height * 0.22f;
        float groundY = y0 + height * 0.72f;

        float totalBlocks = layout.totalWidthBlocks();
        if (totalBlocks <= 0f) {
            return;
        }

        float scale = (width - padding * 2f) / totalBlocks;
        float cursorX = x0 + (width - totalBlocks * scale) * 0.5f;

        drawList.addLine(x0 + padding, groundY, x0 + width - padding, groundY, COLOR_GROUND_LINE, 1.5f);
        drawList.addRectFilled(x0 + padding, groundY, x0 + width - padding, y0 + height - padding, COLOR_GROUND);

        cursorX = drawBand(drawList, layout.drainageBlocks, cursorX, deckY + deckH, groundY, scale, COLOR_DRAINAGE);
        cursorX = drawBand(drawList, layout.leftSidewalkBlocks, cursorX, deckY, deckY + deckH, scale, layout.sidewalkColor);
        cursorX = drawBand(drawList, layout.leftBikeBlocks, cursorX, deckY, deckY + deckH, scale, layout.bikeColor);
        cursorX = drawBand(drawList, layout.leftShoulderBlocks, cursorX, deckY, deckY + deckH, scale, layout.shoulderColor);
        float roadStartX = cursorX;
        cursorX = drawBand(drawList, layout.roadBlocks, cursorX, deckY, deckY + deckH, scale, layout.roadColor);
        float roadWidthPx = layout.roadBlocks * scale;
        drawRoadMarkings(drawList, layout, roadStartX, roadWidthPx, deckY, deckH);
        cursorX = drawBand(drawList, layout.rightShoulderBlocks, cursorX, deckY, deckY + deckH, scale, layout.shoulderColor);
        cursorX = drawBand(drawList, layout.rightBikeBlocks, cursorX, deckY, deckY + deckH, scale, layout.bikeColor);
        cursorX = drawBand(drawList, layout.rightSidewalkBlocks, cursorX, deckY, deckY + deckH, scale, layout.sidewalkColor);
        drawBand(drawList, layout.drainageBlocks, cursorX, deckY + deckH, groundY, scale, COLOR_DRAINAGE);

        if (layout.includeShoulder && layout.shoulderBlocks > 0) {
            float leftShoulderOuterX = x0 + width * 0.5f - layout.centerOffsetBlocks(scale)
                - layout.leftShoulderBlocks * scale;
            float rightShoulderOuterX = x0 + width * 0.5f + layout.centerOffsetBlocks(scale)
                + layout.roadBlocks * scale + layout.rightShoulderBlocks * scale;

            drawBatterSlope(
                drawList,
                leftShoulderOuterX,
                deckY + deckH,
                groundY,
                -1,
                layout.fillSlopeRatio,
                layout.shoulderColor,
                layout.fillSlopeRatio > 0f
            );
            drawBatterSlope(
                drawList,
                rightShoulderOuterX,
                deckY + deckH,
                deckY - Math.max(10f, (groundY - deckY - deckH) * 0.5f),
                1,
                layout.cutSlopeRatio,
                layout.shoulderColor,
                layout.cutSlopeRatio > 0f
            );
        }

        if (layout.maxSlopePercent > 0f) {
            String gradeLabel = PlotI18n.tr(
                "plugin.road.cross_section_grade",
                SlopeFormatUtils.formatPercent(layout.maxSlopePercent)
            );
            drawList.addText(x0 + width - padding - 72f, y0 + padding * 0.5f, COLOR_LABEL, gradeLabel);
        }

        String label = PlotI18n.tr("plugin.road.cross_section_scale", Math.round(totalBlocks));
        drawList.addText(x0 + padding, y0 + padding * 0.5f, COLOR_LABEL, label);
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
            boolean enabled) {
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

        String label = SlopeFormatUtils.formatRatio(slopeRatio);
        float labelX = horizontalSign < 0 ? endX + 2f : edgeX + 2f;
        drawList.addText(labelX, deckBottom + 2f, COLOR_LABEL, label);
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
        public final float maxSlopePercent;
        public final int roadColor;
        public final int sidewalkColor;
        public final int bikeColor;
        public final int shoulderColor;
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
                float maxSlopePercent,
                int roadColor,
                int sidewalkColor,
                int bikeColor,
                int shoulderColor,
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
            this.maxSlopePercent = maxSlopePercent;
            this.roadColor = roadColor;
            this.sidewalkColor = sidewalkColor;
            this.bikeColor = bikeColor;
            this.shoulderColor = shoulderColor;
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
                maxSlopePercent,
                roadColor,
                sidewalkColor,
                bikeColor,
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
                section.fillSlopeRatio,
                section.cutSlopeRatio,
                maxSlopePercent,
                colorForMaterial(section.carriagewayMaterial, 0xFF707070),
                colorForMaterial(section.sidewalkMaterial, 0xFF989898),
                colorForMaterial(section.bikeLaneMaterial, 0xFF6FA8D8),
                colorForMaterial(section.shoulderMaterial, 0xFFB8A070),
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
            if (style == null) {
                return fromResolved(ResolvedCrossSection.fromConfig(new RoadSystemConfig("preview")), 10.0f);
            }
            RoadSystemConfig defaults = new RoadSystemConfig("preview");
            return fromResolved(
                ResolvedCrossSection.resolve(style.toCrossSection(), defaults),
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
