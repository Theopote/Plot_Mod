package com.plot.plugin.earthwork.solver;

/**
 * 自动平衡求解结果状态（与残余挖填差值分离，避免用 0 同时表示「已平衡」与「未运行」）。
 */
public enum EarthworkBalanceStatus {
    /** 本轮未请求/未运行自动平衡搜索。 */
    NOT_REQUESTED,
    /** 搜索后残余 |Cut−Fill| 为 0。 */
    BALANCED,
    /** 已搜索且残余 &gt; 0，场地无固定标高约束标记。 */
    BEST_EFFORT,
    /**
     * 已搜索且残余 &gt; 0，且场地存在固定建筑/道路/锁定标高。
     * 仅为诊断提示（存在约束），不证明残余就是这些约束造成的；
     * 另见搜索是否触界、最优偏移等字段。
     */
    CONSTRAINT_LIMITED
}
