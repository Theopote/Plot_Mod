package com.plot.plugin.road.geometry;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.ResolvedCrossSection;

/**
 * 道路走廊横向包络（方块数）：画布叠加、土方与路基清理共用。
 */
public final class RoadCorridorWidth {
    private RoadCorridorWidth() {
    }

    /** 硬质路面半宽（行车道 + 外侧条带 + 排水沟）。 */
    public static double pavementHalfWidthBlocks(ResolvedCrossSection section) {
        if (section == null) {
            return 0.0;
        }
        double halfWidth = section.carriagewayHalfWidth() + section.outerBandWidth();
        if (section.includeDrain) {
            halfWidth += 1.0;
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

    /** 路基挖填包络总宽（方块数）。 */
    public static int gradingEnvelopeWidthBlocks(ResolvedCrossSection section) {
        if (section == null) {
            return 0;
        }
        int width = section.carriagewayWidth + section.outerBandBlockCount() * 2;
        if (section.includeDrain) {
            width += 2;
        }
        return Math.max(1, width);
    }

    /** 植被/附着物清理包络总宽（方块数）。 */
    public static int decorationClearWidthBlocks(ResolvedCrossSection section, RoadSystemConfig config) {
        int width = gradingEnvelopeWidthBlocks(section);
        if (section != null && section.includeSlopeBatter) {
            width += slopeBatterMarginBlocks(section, config) * 2;
        }
        return Math.max(1, width);
    }

    static int slopeBatterMarginBlocks(ResolvedCrossSection section, RoadSystemConfig config) {
        int maxCutHeight = config != null ? Math.max(1, config.getTunnelThreshold()) : 4;
        float ratio = section != null && section.cutSlopeRatio > 0f
            ? section.cutSlopeRatio
            : 1.0f;
        ratio = Math.max(0.5f, ratio);
        return Math.min(16, Math.max(2, (int) Math.ceil(maxCutHeight * ratio) + 2));
    }
}
