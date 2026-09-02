package com.plot.plugin.earthwork.solver;
import com.plot.plugin.earthwork.model.EarthMaterialProperties;

import java.util.List;

/**
 * 土方平衡纯函数工具（不依赖 World）
 */
public final class EarthworkBalanceUtils {
    private static final int DEFAULT_ELEVATION = 64;

    private EarthworkBalanceUtils() {
    }

    public static int findBalancedElevation(List<Integer> groundHeightSamples, EarthMaterialProperties materials) {
        EarthMaterialProperties safeMaterials = materials != null ? materials : EarthMaterialProperties.DEFAULT;
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
            if (computeBalanceDiff(groundHeightSamples, mid, safeMaterials) > 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }

        int bestZ = lo;
        long bestAbs = Math.abs(computeBalanceDiff(groundHeightSamples, lo, safeMaterials));
        if (lo - 1 >= minZ) {
            long prevAbs = Math.abs(computeBalanceDiff(groundHeightSamples, lo - 1, safeMaterials));
            if (prevAbs < bestAbs) {
                bestZ = lo - 1;
            }
        }
        return bestZ;
    }

    /**
     * 道路纵断面等模块沿用的旧参数入口；{@code fillFactor} 为历史松散系数，内部转换为材料属性。
     */
    public static int findBalancedElevation(List<Integer> groundHeightSamples, float fillFactor) {
        return findBalancedElevation(groundHeightSamples, EarthMaterialProperties.fromLegacyFillFactor(fillFactor));
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

    /**
     * 挖方可形成的填方压实方量。
     */
    public static long computeCompactedFillSupplyFromCut(
            List<Integer> groundHeightSamples,
            int targetElevation,
            EarthMaterialProperties materials) {
        EarthMaterialProperties safeMaterials = materials != null ? materials : EarthMaterialProperties.DEFAULT;
        return Math.round(computeCutVolume(groundHeightSamples, targetElevation)
            * safeMaterials.effectiveCutToCompactedFillRatio());
    }

    public static long computeBalanceDiff(List<Integer> groundHeightSamples, int targetElevation) {
        return computeBalanceDiff(groundHeightSamples, targetElevation, EarthMaterialProperties.DEFAULT);
    }

    /**
     * 平衡差值 = 挖方可形成压实方 - 填方压实需求。
     */
    public static long computeBalanceDiff(
            List<Integer> groundHeightSamples,
            int targetElevation,
            EarthMaterialProperties materials) {
        return computeCompactedFillSupplyFromCut(groundHeightSamples, targetElevation, materials)
            - computeFillVolume(groundHeightSamples, targetElevation);
    }
}
