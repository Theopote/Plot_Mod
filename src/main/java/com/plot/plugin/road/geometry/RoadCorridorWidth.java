package com.plot.plugin.road.geometry;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

/**
 * 道路走廊横向包络（方块数）：画布叠加、土方与路基清理共用。
 */
public final class RoadCorridorWidth {
    /** 边坡水平外扩与走廊裕量上限（格）。 */
    public static final int MAX_SLOPE_BATTER_RUN_BLOCKS = 32;
    /** 洞门/桥台过渡段 OPEN 清障最大半宽（格）。 */
    public static final int PORTAL_TRANSITION_CLEAR_BAND_BLOCKS = 8;

    private RoadCorridorWidth() {
    }

    /** 硬质路面半宽（行车道 + 外侧条带）。 */
    public static double pavementHalfWidthBlocks(ResolvedCrossSection section) {
        if (section == null) {
            return 0.0;
        }
        return Math.max(0.5, section.carriagewayHalfWidth() + section.outerBandWidth());
    }

    /** 画布路径预览半宽：行车道 + 自行车道/人行道（如有），不含路肩/排水沟/边坡。 */
    public static double canvasPreviewHalfWidthBlocks(ResolvedCrossSection section) {
        if (section == null) {
            return 0.0;
        }
        double halfWidth = section.carriagewayHalfWidth();
        if (section.includeBikeLane && section.bikeLaneWidth > 0) {
            halfWidth += section.bikeLaneWidth;
        }
        if (section.includeSidewalk && section.sidewalkWidth > 0) {
            halfWidth += section.sidewalkWidth;
        }
        return Math.max(0.5, halfWidth);
    }

    /** 画布叠加层半宽：硬质路面 + 可选边坡外缘估计。 */
    public static double overlayHalfWidthBlocks(ResolvedCrossSection section, RoadSystemConfig config) {
        double halfWidth = pavementHalfWidthBlocks(section);
        if (section != null && section.includeSlopeBatter) {
            halfWidth += slopeBatterMarginBlocks(section, config);
        }
        return halfWidth;
    }

    /**
     * 桥面硬质横断面总宽：行车道 + 路肩/自行车道/人行道，不含排水沟。
     * 桥面板、桥台、护栏外缘与桥墩帽统一使用此包络。
     */
    public static int bridgeDeckWidthBlocks(ResolvedCrossSection section) {
        if (section == null) {
            return 1;
        }
        return Math.max(1, section.carriagewayWidth + section.outerBandBlockCount() * 2);
    }

    /** 桥面硬质横断面半宽（自中心线到最外侧条带中心）。 */
    public static double bridgeDeckHalfWidthBlocks(ResolvedCrossSection section) {
        return RoadDimensionUtils.halfExtentFromCenter(bridgeDeckWidthBlocks(section));
    }

    /**
     * 硬质路面包络总宽（行车道 + 外侧条带）。
     * OPEN 走廊清场、隧道/洞门截面必须用此宽度，不得叠边坡裕量。
     */
    public static int pavementEnvelopeWidthBlocks(ResolvedCrossSection section) {
        if (section == null) {
            return 0;
        }
        return Math.max(1, section.carriagewayWidth + section.outerBandBlockCount() * 2);
    }

    /**
     * 硬质路面包络总宽。OPEN 清场与隧道截面走这里；边坡外缘见
     * {@link #earthworkEnvelopeWidthBlocks(ResolvedCrossSection, RoadSystemConfig)}。
     */
    public static int gradingEnvelopeWidthBlocks(ResolvedCrossSection section) {
        return pavementEnvelopeWidthBlocks(section);
    }

    /**
     * 挖填包络总宽：硬质路面 + 启用边坡时的外缘裕量。
     * 城市预设 {@code includeSlopeBatter == false} 时与路面包络相同。
     */
    public static int earthworkEnvelopeWidthBlocks(ResolvedCrossSection section, RoadSystemConfig config) {
        int width = pavementEnvelopeWidthBlocks(section);
        if (width <= 0) {
            return 0;
        }
        if (section.includeSlopeBatter) {
            width += slopeBatterMarginBlocks(section, config) * 2;
        }
        return width;
    }

    /**
     * 植被/附着物清理包络总宽（方块数）。
     * 与挖填包络对齐；不得作为 OPEN {@link com.plot.plugin.road.pipeline.corridor.ClearanceCell}
     * 的横向挖方依据（坡面清场由边坡列自己的坡面标高封顶）。
     */
    public static int decorationClearWidthBlocks(ResolvedCrossSection section, RoadSystemConfig config) {
        return earthworkEnvelopeWidthBlocks(section, config);
    }

    public static int slopeBatterMarginBlocks(ResolvedCrossSection section, RoadSystemConfig config) {
        int maxCutHeight = config != null
            ? Math.max(1, RoadConstructionHeuristics.tunnelThreshold(config))
            : RoadConstructionHeuristics.tunnelThreshold(RoadTerrainStyle.BALANCED);
        float ratio = section != null && section.cutSlopeRatio > 0f
            ? section.cutSlopeRatio
            : 1.0f;
        ratio = Math.max(0.5f, ratio);
        return Math.clamp((int) Math.ceil(maxCutHeight * ratio) + 2, 2, MAX_SLOPE_BATTER_RUN_BLOCKS);
    }
}
