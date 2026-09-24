package com.plot.plugin.road.centerline;

/**
 * 单条逻辑道路中心线形状违反类型。
 * <p>
 * 同一 {@link com.plot.plugin.road.model.Road} 内几何自交/重叠/非线性拓扑不会自动形成路口，
 * 应拆分为多条道路后再生成。
 *
 * @see RoadCenterlineShapeValidator
 */
public enum RoadCenterlineViolationKind {
    /** 中心线折线存在真交（如 figure-8、蝴蝶结）。 */
    SELF_INTERSECTION,
    /** 中心线折线存在共线重叠（同一路径重复经过）。 */
    SELF_OVERLAP,
    /** 道路子图非 open chain（内部分叉或隐式闭环）。 */
    NON_LINEAR_ROAD_TOPOLOGY
}
