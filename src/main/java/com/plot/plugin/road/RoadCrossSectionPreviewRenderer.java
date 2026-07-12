package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

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
        cursorX = drawBand(drawList, layout.leftShoulderBlocks, cursorX, deckY, deckY + deckH, scale, layout.shoulderColor);
        cursorX = drawBand(drawList, layout.roadBlocks, cursorX, deckY, deckY + deckH, scale, layout.roadColor);
        cursorX = drawBand(drawList, layout.rightShoulderBlocks, cursorX, deckY, deckY + deckH, scale, layout.shoulderColor);
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

        String label = PlotI18n.tr("plugin.road.cross_section_scale", (int) Math.round(totalBlocks));
        drawList.addText(x0 + padding, y0 + padding * 0.5f, COLOR_LABEL, label);
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
        float endY = groundY;
        drawList.addTriangleFilled(edgeX, deckBottom, endX, endY, edgeX, endY, color);
        drawList.addLine(edgeX, deckBottom, endX, endY, COLOR_BORDER, 1.2f);

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
        public final float drainageBlocks;
        public final boolean includeShoulder;
        public final float shoulderBlocks;
        public final float fillSlopeRatio;
        public final float cutSlopeRatio;
        public final float maxSlopePercent;
        public final int roadColor;
        public final int sidewalkColor;
        public final int shoulderColor;

        private CrossSectionLayout(
                float roadBlocks,
                float leftShoulderBlocks,
                float rightShoulderBlocks,
                float leftSidewalkBlocks,
                float rightSidewalkBlocks,
                float drainageBlocks,
                boolean includeShoulder,
                float shoulderBlocks,
                float fillSlopeRatio,
                float cutSlopeRatio,
                float maxSlopePercent,
                int roadColor,
                int sidewalkColor,
                int shoulderColor) {
            this.roadBlocks = roadBlocks;
            this.leftShoulderBlocks = leftShoulderBlocks;
            this.rightShoulderBlocks = rightShoulderBlocks;
            this.leftSidewalkBlocks = leftSidewalkBlocks;
            this.rightSidewalkBlocks = rightSidewalkBlocks;
            this.drainageBlocks = drainageBlocks;
            this.includeShoulder = includeShoulder;
            this.shoulderBlocks = shoulderBlocks;
            this.fillSlopeRatio = fillSlopeRatio;
            this.cutSlopeRatio = cutSlopeRatio;
            this.maxSlopePercent = maxSlopePercent;
            this.roadColor = roadColor;
            this.sidewalkColor = sidewalkColor;
            this.shoulderColor = shoulderColor;
        }

        public static CrossSectionLayout fromConfig(RoadSystemConfig config) {
            float road = Math.max(1, config.getRoadWidth());
            float shoulder = config.isIncludeShoulder() ? Math.max(0, config.getShoulderWidth()) : 0f;
            float sidewalk = config.isIncludeSidewalk() ? Math.max(0, config.getSidewalkWidth()) : 0f;
            float drainage = config.isIncludeDrainage() ? 0.5f : 0f;
            return new CrossSectionLayout(
                road,
                shoulder, shoulder,
                sidewalk, sidewalk,
                drainage,
                config.isIncludeShoulder(),
                shoulder,
                config.isIncludeShoulder() ? config.getFillSlopeRatio() : 0f,
                config.isIncludeShoulder() ? config.getCutSlopeRatio() : 0f,
                config.getMaxSlope(),
                colorForMaterial(config.getSelectedMaterial(), 0xFF707070),
                colorForMaterial(config.getSelectedSidewalkMaterial(), 0xFF989898),
                colorForMaterial(config.getFillSlopeMaterial(), 0xFFB8A070)
            );
        }

        public static CrossSectionLayout fromPreset(RoadSystemConfig.RoadPreset preset) {
            float road = Math.max(1, preset.width);
            float shoulder = preset.includeShoulder ? Math.max(0, preset.shoulderWidth) : 0f;
            float sidewalk = preset.hasSidewalk ? Math.max(0, preset.sidewalkWidth) : 0f;
            float drainage = preset.includeDrainage ? 0.5f : 0f;
            String roadMat = preset.roadMaterial != null ? preset.roadMaterial : RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
            String swMat = preset.sidewalkMaterial != null ? preset.sidewalkMaterial : roadMat;
            return new CrossSectionLayout(
                road,
                shoulder, shoulder,
                sidewalk, sidewalk,
                drainage,
                preset.includeShoulder,
                shoulder,
                preset.includeShoulder ? 1.5f : 0f,
                preset.includeShoulder ? 1.0f : 0f,
                10.0f,
                colorForMaterial(roadMat, 0xFF707070),
                colorForMaterial(swMat, 0xFF989898),
                colorForMaterial("material.plot.gravel", 0xFFB8A070)
            );
        }

        public float totalWidthBlocks() {
            return roadBlocks
                + leftShoulderBlocks + rightShoulderBlocks
                + leftSidewalkBlocks + rightSidewalkBlocks
                + drainageBlocks * 2f;
        }

        public float centerOffsetBlocks(float scale) {
            float left = drainageBlocks + leftSidewalkBlocks + leftShoulderBlocks;
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
