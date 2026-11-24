package com.plot.plugin.road.model.section;

import com.plot.plugin.road.RoadDimensionUtils;

import java.util.ArrayList;
import java.util.Comparator;
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
     * 隔离带横向列范围 {@code [min, max]}（含），严格落在中央标线占格上。
     * 取最靠近中心的标线偏移；若偏移过散（跨度大于宽度）则退回几何中心，避免盖住行驶路面。
     * 双黄仅有一条中心偏移时，向两侧扩满宽度（仍不超过标线占格数）。
     */
    public static int[] medianLateralRange(
            List<Double> laneDividerOffsets,
            int medianWidth) {
        int width = Math.clamp(medianWidth, 0, MAX_MEDIAN_WIDTH_BLOCKS);
        if (width <= 0) {
            return new int[] {0, -1};
        }
        // 与路面格子一致：宽 1→[0,0]，宽 2→[0,1]（RoadDimensionUtils 偶数居中）。
        int[] markingCentered = new int[] {
            RoadDimensionUtils.minLateralOffset(width),
            RoadDimensionUtils.maxLateralOffset(width)
        };
        if (laneDividerOffsets == null || laneDividerOffsets.isEmpty()) {
            return markingCentered;
        }
        List<Double> ranked = new ArrayList<>(laneDividerOffsets.size());
        for (Double offset : laneDividerOffsets) {
            if (offset != null) {
                ranked.add(offset);
            }
        }
        if (ranked.isEmpty()) {
            return markingCentered;
        }
        ranked.sort(Comparator.comparingDouble(Math::abs));
        // 只用最靠近中心的 width 条偏移（对应中央标线列）；白线侧缝不参与。
        int take = Math.min(width, ranked.size());
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < take; i++) {
            int col = (int) Math.floor(ranked.get(i));
            min = Math.min(min, col);
            max = Math.max(max, col);
        }
        // 偏移过散则说明取到了侧向白线，改用路面居中范围，避免隔离带盖住行驶面。
        if (max - min + 1 > width) {
            return markingCentered;
        }
        // 双黄等需要 2 格但只解析到 1 个中心偏移时，就地扩满标线占格。
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
