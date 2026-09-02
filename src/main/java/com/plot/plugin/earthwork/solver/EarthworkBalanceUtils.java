package com.plot.plugin.earthwork.solver;
import com.plot.core.material.MaterialConversionModel;

import java.util.List;

/**
 * 土方平衡纯函数工具（不依赖 World）
 */
public final class EarthworkBalanceUtils {
    private static final int DEFAULT_ELEVATION = 64;

    /**
     * 带权重的地面样本：weight 表示该格点在平衡求解中的方量权重（默认 1）。
     */
    public record BalanceSample(int groundHeight, int weight) {
        public BalanceSample {
            if (weight < 1) {
                weight = 1;
            }
        }

        public BalanceSample(int groundHeight) {
            this(groundHeight, 1);
        }
    }

    private EarthworkBalanceUtils() {
    }

    public static int findBalancedElevation(List<Integer> groundHeightSamples, MaterialConversionModel materials) {
        if (groundHeightSamples == null || groundHeightSamples.isEmpty()) {
            return findBalancedElevationWeighted(List.of(), materials);
        }
        List<BalanceSample> samples = groundHeightSamples.stream()
            .map(BalanceSample::new)
            .toList();
        return findBalancedElevationWeighted(samples, materials);
    }

    public static int findBalancedElevationWeighted(
            List<BalanceSample> samples,
            MaterialConversionModel materials) {
        MaterialConversionModel safeMaterials = materials != null ? materials : MaterialConversionModel.DEFAULT;
        if (samples == null || samples.isEmpty()) {
            return DEFAULT_ELEVATION;
        }

        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BalanceSample sample : samples) {
            minZ = Math.min(minZ, sample.groundHeight());
            maxZ = Math.max(maxZ, sample.groundHeight());
        }

        int lo = minZ;
        int hi = maxZ;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (computeBalanceDiffWeighted(samples, mid, safeMaterials) > 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }

        int bestZ = lo;
        long bestAbs = Math.abs(computeBalanceDiffWeighted(samples, lo, safeMaterials));
        if (lo - 1 >= minZ) {
            long prevAbs = Math.abs(computeBalanceDiffWeighted(samples, lo - 1, safeMaterials));
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
        return findBalancedElevation(groundHeightSamples, MaterialConversionModel.fromLegacyFillFactor(fillFactor));
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
            MaterialConversionModel materials) {
        MaterialConversionModel safeMaterials = materials != null ? materials : MaterialConversionModel.DEFAULT;
        return Math.round(computeCutVolume(groundHeightSamples, targetElevation)
            * safeMaterials.effectiveCutToCompactedFillRatio());
    }

    public static long computeBalanceDiff(List<Integer> groundHeightSamples, int targetElevation) {
        return computeBalanceDiff(groundHeightSamples, targetElevation, MaterialConversionModel.DEFAULT);
    }

    /**
     * 平衡差值 = 挖方可形成压实方 - 填方压实需求。
     */
    public static long computeBalanceDiff(
            List<Integer> groundHeightSamples,
            int targetElevation,
            MaterialConversionModel materials) {
        return computeCompactedFillSupplyFromCut(groundHeightSamples, targetElevation, materials)
            - computeFillVolume(groundHeightSamples, targetElevation);
    }

    /**
     * 加权平衡差值：每格挖填深度乘以 {@link BalanceSample#weight()}。
     */
    public static long computeBalanceDiffWeighted(
            List<BalanceSample> samples,
            int targetElevation,
            MaterialConversionModel materials) {
        MaterialConversionModel safeMaterials = materials != null ? materials : MaterialConversionModel.DEFAULT;
        if (samples == null || samples.isEmpty()) {
            return 0L;
        }
        long cutVolume = 0L;
        long fillVolume = 0L;
        for (BalanceSample sample : samples) {
            int weight = sample.weight();
            int height = sample.groundHeight();
            if (height > targetElevation) {
                cutVolume += (long) (height - targetElevation) * weight;
            } else if (height < targetElevation) {
                fillVolume += (long) (targetElevation - height) * weight;
            }
        }
        long supply = Math.round(cutVolume * safeMaterials.effectiveCutToCompactedFillRatio());
        return supply - fillVolume;
    }
}
