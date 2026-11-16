package com.plot.plugin.road.model.section;

import com.plot.plugin.road.RoadParameterLimits;

/**
 * 横断面几何辅助：中央隔离带在路表之上，不占用行车道宽。
 * 宽度钳制与单柱桥墩直径共用奇偶规则。
 */
public final class CrossSectionGeometryRules {
    private CrossSectionGeometryRules() {
    }

    /** 行车道最小宽度只需覆盖车道格；隔离带不计入。 */
    public static int requiredCarriagewayWidth(int laneCount, int ignoredMedianWidth) {
        return Math.max(1, laneCount);
    }

    public static boolean isMedianFit(int carriagewayWidth, int laneCount, int medianWidth) {
        if (medianWidth <= 0) {
            return true;
        }
        int clamped = RoadParameterLimits.clampMedianWidthBlocks(carriagewayWidth, medianWidth);
        return clamped == medianWidth && carriagewayWidth >= requiredCarriagewayWidth(laneCount, 0);
    }

    /**
     * 将请求的隔离带宽度钳到与行车道同奇偶的合法单柱直径范围。
     */
    public static int clampMedianWidth(int carriagewayWidth, int laneCount, int requestedMedian) {
        return RoadParameterLimits.clampMedianWidthBlocks(carriagewayWidth, requestedMedian);
    }

    /** 隔离带不挤占行车道：lane-only 宽等于行车道宽。 */
    public static int laneOnlyWidth(int carriagewayWidth, int laneCount, int ignoredMedianWidth) {
        return Math.max(Math.max(1, laneCount), Math.max(1, carriagewayWidth));
    }
}
