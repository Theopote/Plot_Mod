package com.plot.plugin.earthwork;

import java.util.List;

/**
 * 土方平衡纯函数工具（不依赖 World）
 */
public final class EarthworkBalanceUtils {
    private static final int DEFAULT_ELEVATION = 64;

    private EarthworkBalanceUtils() {
    }

    public static int findBalancedElevation(List<Integer> groundHeightSamples, float fillFactor) {
        if (groundHeightSamples == null || groundHeightSamples.isEmpty()) {
            return DEFAULT_ELEVATION;
        }

        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int height : groundHeightSamples) {
            minZ = Math.min(minZ, height);
            maxZ = Math.max(maxZ, height);
        }

        int lo = minZ;
        int hi = maxZ;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (balanceDiff(groundHeightSamples, mid, fillFactor) > 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }

        int bestZ = lo;
        long bestAbs = Math.abs(balanceDiff(groundHeightSamples, lo, fillFactor));
        if (lo - 1 >= minZ) {
            long prevAbs = Math.abs(balanceDiff(groundHeightSamples, lo - 1, fillFactor));
            if (prevAbs < bestAbs) {
                bestZ = lo - 1;
            }
        }
        return bestZ;
    }

    public static long computeCutVolume(List<Integer> groundHeightSamples, int targetElevation) {
        if (groundHeightSamples == null || groundHeightSamples.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (int height : groundHeightSamples) {
            if (height > targetElevation) {
                total += height - targetElevation;
            }
        }
        return total;
    }

    public static long computeFillVolume(List<Integer> groundHeightSamples, int targetElevation) {
        if (groundHeightSamples == null || groundHeightSamples.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (int height : groundHeightSamples) {
            if (height < targetElevation) {
                total += targetElevation - height;
            }
        }
        return total;
    }

    public static long computeFillVolumeRequired(List<Integer> groundHeightSamples, int targetElevation, float fillFactor) {
        return Math.round(computeFillVolume(groundHeightSamples, targetElevation) * fillFactor);
    }

    /**
     * 计算平衡差值（挖方量 - 填方量）
     *
     * 注意：填土系数不应在此处应用。
     * 填土系数（fillFactor）表示填土的松散系数，用于估算需要的原土体积。
     * 但在Minecraft中，我们放置的是实际方块，不是原土体积。
     * 因此平衡计算应基于实际方块数，不应用填土系数。
     *
     * @deprecated fillFactor参数已不再使用，为保持API兼容性暂时保留
     */
    @Deprecated
    static long balanceDiff(List<Integer> groundHeightSamples, int targetElevation, float fillFactor) {
        // 修复：不再应用填土系数，保持方块数平衡
        return computeCutVolume(groundHeightSamples, targetElevation)
            - computeFillVolume(groundHeightSamples, targetElevation);
    }
}
