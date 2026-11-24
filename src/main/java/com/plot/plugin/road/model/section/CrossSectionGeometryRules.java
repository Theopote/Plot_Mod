package com.plot.plugin.road.model.section;

import com.plot.plugin.road.RoadDimensionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 横断面几何辅助：中央隔离带在路表之上，不占用行车道宽。
 * 隔离带宽度/横向位置由中央标线或中心缝车道分隔线推导，与桥墩无关。
 */
public final class CrossSectionGeometryRules {
    private CrossSectionGeometryRules() {
    }

    /** 行车道最小宽度只需覆盖车道格；隔离带不计入。 */
    public static int requiredCarriagewayWidth(int laneCount, int ignoredMedianWidth) {
        return Math.max(1, laneCount);
    }

    /**
     * 隔离带派生宽度：有中央标线用其中心占格；否则有多车道路分隔时用 1；否则 0（不可启用）。
     */
    public static int derivedMedianWidth(
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int laneCount) {
        int lanes = Math.max(1, laneCount);
        if (lanes <= 1) {
            return 0;
        }
        int centerBlocks = CarriagewayWidthRules.centerLineBlocks(centerStyle);
        if (centerBlocks > 0) {
            return centerBlocks;
        }
        return laneDividers ? 1 : 0;
    }

    public static boolean canEnableMedian(
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int laneCount) {
        return derivedMedianWidth(centerStyle, laneDividers, laneCount) > 0;
    }

    /**
     * 隔离带横向列范围 {@code [min, max]}（含），与中心缝标线/分隔偏移对齐。
     * 无可用偏移时退回相对几何中心的 {@link RoadDimensionUtils#minLateralOffset}。
     */
    public static int[] medianLateralRange(
            List<Double> laneDividerOffsets,
            int medianWidth) {
        int width = Math.max(0, medianWidth);
        if (width <= 0) {
            return new int[] {0, -1};
        }
        if (laneDividerOffsets == null || laneDividerOffsets.isEmpty()) {
            return new int[] {
                RoadDimensionUtils.minLateralOffset(width),
                RoadDimensionUtils.maxLateralOffset(width)
            };
        }
        List<Double> ranked = new ArrayList<>(laneDividerOffsets.size());
        for (Double offset : laneDividerOffsets) {
            if (offset != null) {
                ranked.add(offset);
            }
        }
        if (ranked.isEmpty()) {
            return new int[] {
                RoadDimensionUtils.minLateralOffset(width),
                RoadDimensionUtils.maxLateralOffset(width)
            };
        }
        ranked.sort(Comparator.comparingDouble(Math::abs));
        int take = Math.min(width, ranked.size());
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < take; i++) {
            int col = (int) Math.floor(ranked.get(i));
            min = Math.min(min, col);
            max = Math.max(max, col);
        }
        // 偏移不足时按宽度向两侧扩展，保持锚点居中。
        while (max - min + 1 < width) {
            if (Math.abs(min - 1) <= Math.abs(max + 1)) {
                min--;
            } else {
                max++;
            }
        }
        return new int[] {min, max};
    }

    /** 请求宽度在派生语义下是否合法（必须等于派生宽度，或未启用）。 */
    public static boolean isMedianFit(
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int laneCount,
            int medianWidth) {
        if (medianWidth <= 0) {
            return true;
        }
        return medianWidth == derivedMedianWidth(centerStyle, laneDividers, laneCount);
    }

    /**
     * @deprecated 隔离带宽度已改为由标线派生；保留签名以免旧调用断裂，忽略 carriageway/requested。
     */
    @Deprecated
    public static int clampMedianWidth(
            int carriagewayWidth,
            int laneCount,
            int requestedMedian) {
        if (requestedMedian <= 0) {
            return 0;
        }
        // 旧调用无标线上下文时，无法派生；退回请求值的粗钳制。
        return Math.max(0, Math.min(2, requestedMedian));
    }

    /** 隔离带不挤占行车道：lane-only 宽等于行车道宽。 */
    public static int laneOnlyWidth(int carriagewayWidth, int laneCount, int ignoredMedianWidth) {
        return Math.max(Math.max(1, laneCount), Math.max(1, carriagewayWidth));
    }
}
