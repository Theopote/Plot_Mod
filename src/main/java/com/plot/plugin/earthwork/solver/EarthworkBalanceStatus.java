package com.plot.plugin.earthwork.solver;

/**
 * 自动平衡求解结果状态（与残余挖填差值分离，避免用 0 同时表示「已平衡」「未运行」「求解失败」）。
 */
public enum EarthworkBalanceStatus {
    /** 本轮未请求/未运行自动平衡搜索。 */
    NOT_REQUESTED,
    /** 搜索后找到有效解，且残余 |Cut−Fill| 为 0。 */
    BALANCED,
    /** 已搜索且找到有效解，残余 &gt; 0，场地无固定标高约束标记。 */
    BEST_EFFORT,
    /**
     * 已搜索且找到有效解，残余 &gt; 0，且场地存在固定建筑/道路/锁定标高。
     * 仅为诊断提示（存在约束），不证明残余就是这些约束造成的；
     * 另见搜索是否触界、最优偏移等字段。
     */
    CONSTRAINT_LIMITED,
    /**
     * 已执行搜索，但没有任何候选返回有效目标值（例如全部为 {@link Long#MAX_VALUE}）。
     * 不得将此情况记为残余 0 / {@link #BALANCED}。
     */
    SEARCH_FAILED;

    /**
     * @param ran                     是否实际运行了平衡搜索
     * @param hasValidSolution        是否至少有一个有效候选目标
     * @param residualImbalance       有效解的残余 |Cut−Fill|；无效解时忽略
     * @param hasFixedElevationConstraints 场地是否存在不可调设计面
     */
    public static EarthworkBalanceStatus classify(
            boolean ran,
            boolean hasValidSolution,
            long residualImbalance,
            boolean hasFixedElevationConstraints) {
        if (!ran) {
            return NOT_REQUESTED;
        }
        if (!hasValidSolution) {
            return SEARCH_FAILED;
        }
        if (residualImbalance <= 0L) {
            return BALANCED;
        }
        return hasFixedElevationConstraints ? CONSTRAINT_LIMITED : BEST_EFFORT;
    }
}
