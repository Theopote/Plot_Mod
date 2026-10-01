package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.RoadVerticalMode;

/** 纵断面图中 guide 线的语义：地形趋势 vs 自动引导线。 */
public enum ProfileChartGuideSemantics {
    TERRAIN_TREND,
    GUIDE_LINE,
    NONE;

    public static ProfileChartGuideSemantics fromVerticalMode(RoadVerticalMode mode) {
        if (mode == null) {
            return NONE;
        }
        if (mode == RoadVerticalMode.FIT_TERRAIN) {
            return TERRAIN_TREND;
        }
        if (mode == RoadVerticalMode.AUTO_SMOOTH) {
            return GUIDE_LINE;
        }
        return NONE;
    }
}
