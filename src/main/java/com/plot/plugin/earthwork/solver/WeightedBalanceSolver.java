package com.plot.plugin.earthwork.solver;

import com.plot.core.material.MaterialConversionModel;

import java.util.List;

/**
 * 基于全格点几何方量的加权平衡求解：按每格挖填深度加权，而非简单「格数均分」。
 */
public final class WeightedBalanceSolver {

    private WeightedBalanceSolver() {
    }

    /**
     * 求统一竖向偏移 ΔY，使几何挖填净变化量最接近 {@code volumeIntent}。
     * <p>
     * 约定与 {@link ZoneAllocationBalanceAdjuster} 一致：正 intent 对应正向 offset
     * （抬升设计面 → 减少挖方）；目标满足 {@code netChange(offset) ≈ -volumeIntent}。
     */
    public static int findVerticalOffsetForVolumeIntent(
            List<SiteWideBalanceAdjuster.CellSample> samples,
            long volumeIntent,
            MaterialConversionModel materials) {
        if (samples == null || samples.isEmpty() || volumeIntent == 0L) {
            return 0;
        }
        int radius = computeSearchRadius(samples);
        long bestError = Long.MAX_VALUE;
        int bestOffset = 0;
        for (int offset = -radius; offset <= radius; offset++) {
            long netChange = computeGeometricNetChange(samples, offset);
            long error = Math.abs(netChange + volumeIntent);
            if (error < bestError) {
                bestError = error;
                bestOffset = offset;
            }
        }
        return bestOffset;
    }

    /**
     * 几何挖方增量减填方增量（ΔY 施加于全部样本目标面）。
     */
    public static long computeGeometricNetChange(
            List<SiteWideBalanceAdjuster.CellSample> samples,
            int deltaY) {
        if (samples == null || samples.isEmpty()) {
            return 0L;
        }
        long cutDelta = 0L;
        long fillDelta = 0L;
        for (SiteWideBalanceAdjuster.CellSample sample : samples) {
            int groundY = sample.existingGroundY();
            int before = sample.rawDesignTargetY();
            int after = before + deltaY;
            cutDelta += Math.max(0, groundY - after) - Math.max(0, groundY - before);
            fillDelta += Math.max(0, after - groundY) - Math.max(0, before - groundY);
        }
        return cutDelta - fillDelta;
    }

    private static int computeSearchRadius(List<SiteWideBalanceAdjuster.CellSample> samples) {
        int maxDepth = 0;
        for (SiteWideBalanceAdjuster.CellSample sample : samples) {
            maxDepth = Math.max(maxDepth, Math.abs(sample.existingGroundY() - sample.rawDesignTargetY()));
        }
        return Math.min(128, Math.max(16, maxDepth + 16));
    }
}
