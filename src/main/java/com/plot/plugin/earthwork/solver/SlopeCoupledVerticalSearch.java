package com.plot.plugin.earthwork.solver;

import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;

/**
 * 边坡耦合的竖向离散搜索。
 * <p>
 * 平台升降会改变坡高 → 日照线/坡脚 → 参与土方的面积 → Cut/Fill，因此不能对
 * {@code targetY += offset} 做一维二分。正确循环是：
 * <pre>
 * Design Variables (ΔY)
 *       ↓
 * Build Design Terrain（平台）
 *       ↓
 * Resolve Daylight / Slope
 *       ↓
 * Volume
 *       ↓
 * Objective → 选最优候选
 * </pre>
 * MVP：对 Minecraft 体素尺度枚举 {@code ΔY ∈ [-halfRange, +halfRange]}（默认 7 档），
 * 每个候选由调用方完整重建坡面后再评估目标。
 */
public final class SlopeCoupledVerticalSearch {

    /** MVP 默认半幅：ΔY = -3 … +3（共 7 个候选）。 */
    public static final int DEFAULT_HALF_RANGE = 3;

    private SlopeCoupledVerticalSearch() {
    }

    /**
     * 对给定统一竖向偏移重建整场设计面（含坡面/日照）后返回目标值；越小越好。
     */
    @FunctionalInterface
    public interface UniformCandidateEvaluator {
        long evaluateAfterRebuild(int candidateUniformOffset);
    }

    public record SearchResult(int bestOffset, long bestObjective, int candidatesEvaluated) {
    }

    /**
     * 枚举 {@code [-halfRange, +halfRange]}，每个候选经 {@code evaluator} 完整重建后比较目标。
     * 目标相同时偏好 |ΔY| 更小；再相同偏好 ΔY 更接近 0 的非负侧（先扫到的）。
     */
    public static SearchResult searchUniform(int halfRange, UniformCandidateEvaluator evaluator) {
        if (evaluator == null) {
            return new SearchResult(0, 0L, 0);
        }
        int range = Math.max(0, halfRange);
        int bestOffset = 0;
        long bestObjective = Long.MAX_VALUE;
        int evaluated = 0;
        for (int dy = -range; dy <= range; dy++) {
            long objective = evaluator.evaluateAfterRebuild(dy);
            evaluated++;
            if (objective < bestObjective
                || (objective == bestObjective && Math.abs(dy) < Math.abs(bestOffset))) {
                bestObjective = objective;
                bestOffset = dy;
            }
        }
        return new SearchResult(bestOffset, bestObjective, evaluated);
    }

    public static SearchResult searchUniform(UniformCandidateEvaluator evaluator) {
        return searchUniform(DEFAULT_HALF_RANGE, evaluator);
    }

    /**
     * 默认目标：场地压实挖填不平衡量的绝对值（外运+外进的代理）。
     * 必须在<strong>已含坡面/日照</strong>的最终网格上计算。
     */
    public static long materialImbalanceObjective(DesignTerrainGrid grid, EarthworkSite site) {
        SiteEarthworkReport report = EarthworkOptimizationSolver.collectZoneVolumes(grid, site);
        EarthworkVolumeReport totals = report.totals();
        if (totals == null) {
            return 0L;
        }
        // 外运 + 外进（压实方）——必须在已含坡面/日照的最终网格上计算
        return Math.round(totals.compactedFillSurplus() + totals.compactedFillDeficit());
    }
}
