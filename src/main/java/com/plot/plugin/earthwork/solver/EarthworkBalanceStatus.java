package com.plot.plugin.earthwork.solver;

/**
 * 自动平衡求解结果状态（与残余挖填差值分离，避免用 0 同时表示「已平衡」与「未运行」）。
 */
public enum EarthworkBalanceStatus {
    /** 本轮未请求/未运行自动平衡搜索。 */
    NOT_REQUESTED,
    /** 搜索后残余 |Cut−Fill| 为 0。 */
    BALANCED,
    /** 已搜索且残余 &gt; 0，但未检测到固定标高约束主导。 */
    BEST_EFFORT,
    /** 已搜索且残余 &gt; 0，存在固定建筑/道路/锁定标高，求解受约束。 */
    CONSTRAINT_LIMITED
}
