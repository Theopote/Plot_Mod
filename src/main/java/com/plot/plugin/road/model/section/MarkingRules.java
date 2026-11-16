package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;

/**
 * 横断面标线解析辅助。
 */
public final class MarkingRules {
    private MarkingRules() {
    }

    public static CenterLineStyle resolveCenterLineStyle(Markings markings, RoadSystemConfig defaults) {
        return resolveCenterLineStyle(markings, defaults, Integer.MAX_VALUE);
    }

    /**
     * @param laneCount 单车道时强制无中央标线（无可分隔对象）。
     */
    public static CenterLineStyle resolveCenterLineStyle(
            Markings markings,
            RoadSystemConfig defaults,
            int laneCount) {
        if (laneCount <= 1) {
            return CenterLineStyle.NONE;
        }
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
        if (laneCount <= 1) {
            return false;
        }
        if (markings == null) {
            return true;
        }
        if (markings.getLaneDividers() != null) {
            return markings.getLaneDividers();
        }
        return true;
    }
}
