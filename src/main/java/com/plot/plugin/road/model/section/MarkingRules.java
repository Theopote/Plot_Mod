package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;

/**
 * 横断面标线解析辅助。
 */
public final class MarkingRules {
    private MarkingRules() {
    }

    public static CenterLineStyle resolveCenterLineStyle(Markings markings, RoadSystemConfig defaults) {
        if (markings == null) {
            return CenterLineStyle.NONE;
        }
        if (markings.getCenterLineStyle() != null) {
            return markings.getCenterLineStyle();
        }
        if (markings.getCenterLine() != null && markings.getCenterLine()) {
            return CenterLineStyle.SINGLE_DASHED;
        }
        return CenterLineStyle.NONE;
    }

    public static boolean resolveLaneDividers(Markings markings, int laneCount) {
        if (markings == null) {
            return laneCount > 1;
        }
        if (markings.getLaneDividers() != null) {
            return markings.getLaneDividers();
        }
        return laneCount > 1;
    }
}
