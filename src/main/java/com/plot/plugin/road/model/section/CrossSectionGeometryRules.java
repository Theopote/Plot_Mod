package com.plot.plugin.road.model.section;

import com.plot.plugin.road.RoadDimensionUtils;

import java.util.List;

/**
 * 横断面几何辅助：中央隔离带叠在中央标线占格之上，不占用行车道宽、不落在行驶路面。
 * 宽度由中央标线派生（单黄/铁轨 1、双黄 2，上限 2）；仅有同向白线时不可启用。
 */
public final class CrossSectionGeometryRules {
    /** 隔离带最大占格：与双黄中央标线一致。 */
    public static final int MAX_MEDIAN_WIDTH_BLOCKS = 2;

    private CrossSectionGeometryRules() {
    }

    /** 行车道最小宽度只需覆盖车道格；隔离带不计入。 */
    public static int requiredCarriagewayWidth(int laneCount, int ignoredMedianWidth) {
        return Math.max(1, laneCount);
    }

    /**
     * 隔离带派生宽度：仅随中央标线占格（≤{@link #MAX_MEDIAN_WIDTH_BLOCKS}）；
     * 无中央标线时为 0（同向车道分隔线不足以落座）。
     * {@code laneDividers} 保留参数以兼容旧调用，不再参与派生。
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
        if (centerBlocks <= 0) {
            return 0;
        }
        return Math.min(MAX_MEDIAN_WIDTH_BLOCKS, centerBlocks);
    }

    public static boolean canEnableMedian(
            CenterLineStyle centerStyle,
            boolean laneDividers,
            int laneCount) {
        return derivedMedianWidth(centerStyle, laneDividers, laneCount) > 0;
    }

    /**
     * 中央标线占格 {@code [min, max]}（含）：按与 {@link LaneGroup#resolveLaneDividerOffsets}
     * 相同的车道缝推进得到，保证奇数车道时中央缝不在几何 0 也能命中（不会误把侧向白线当中央）。
     * 无中央标线时返回 {@code [0, -1]}。
     */
    public static int[] centerMarkingLateralRange(
            List<Integer> laneWidths,
            CenterLineStyle centerStyle,
            boolean laneDividers) {
        int centerBlocks = CarriagewayWidthRules.centerLineBlocks(centerStyle);
        if (centerBlocks <= 0 || laneWidths == null || laneWidths.size() <= 1) {
            return new int[] {0, -1};
        }
        int lanes = laneWidths.size();
        int total = CarriagewayWidthRules.derivedCarriagewayWidth(
            laneWidths, centerStyle, laneDividers, 0);
        double cursor = RoadDimensionUtils.minLateralOffset(total);
        int centerSeam = (lanes - 1) / 2;
        for (int i = 0; i < lanes - 1; i++) {
            cursor += Math.max(0, laneWidths.get(i) != null ? laneWidths.get(i) : 0);
            boolean isCenterSeam = i == centerSeam;
            if (isCenterSeam) {
                int min = (int) Math.floor(cursor + 0.5);
                int max = (int) Math.floor(cursor + centerBlocks - 0.5);
                return new int[] {min, max};
            }
            int seamBlocks = laneDividers ? 1 : 0;
            cursor += seamBlocks;
        }
        return new int[] {0, -1};
    }

    /**
     * 隔离带横向列范围：与中央标线占格一致（{@link #centerMarkingLateralRange}）。
     * {@code laneDividerOffsets} 保留兼容，不再用于“靠近 0 取列”（奇数车道会误判）。
     */
    public static int[] medianLateralRange(
            List<Double> laneDividerOffsets,
            int medianWidth) {
        int width = Math.clamp(medianWidth, 0, MAX_MEDIAN_WIDTH_BLOCKS);
        if (width <= 0) {
            return new int[] {0, -1};
        }
        // 无车道拓扑时退回路面居中；权威路径请用 centerMarkingLateralRange(laneWidths,…)。
        return new int[] {
            RoadDimensionUtils.minLateralOffset(width),
            RoadDimensionUtils.maxLateralOffset(width)
        };
    }

    /** 隔离带列范围：绑定中央标线占格。 */
    public static int[] medianLateralRange(
            List<Integer> laneWidths,
            CenterLineStyle centerStyle,
            boolean laneDividers) {
        return centerMarkingLateralRange(laneWidths, centerStyle, laneDividers);
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
     * @deprecated 隔离带宽度已改为由中央标线派生；保留签名以免旧调用断裂，忽略 carriageway/requested。
     */
    @Deprecated
    public static int clampMedianWidth(
            int carriagewayWidth,
            int laneCount,
            int requestedMedian) {
        if (requestedMedian <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_MEDIAN_WIDTH_BLOCKS, requestedMedian));
    }

    /** 隔离带不挤占行车道：lane-only 宽等于行车道宽。 */
    public static int laneOnlyWidth(int carriagewayWidth, int laneCount, int ignoredMedianWidth) {
        return Math.max(Math.max(1, laneCount), Math.max(1, carriagewayWidth));
    }
}
