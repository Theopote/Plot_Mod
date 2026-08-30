package com.plot.plugin.road.model;

/**
 * {@link Road} 拓扑不变量违反类型。
 *
 * @see RoadTopologyInvariantValidator
 * @see docs/decisions/0004-road-topology-invariant.md
 */
public enum RoadTopologyViolationKind {
    /** 同一 Road 内分段属于多个连通分量 */
    ROAD_DISCONNECTED,
    /** Road 子图内存在度数 &gt; 2 的节点（Y 形分叉应拆成多条 Road 在节点汇合） */
    ROAD_BRANCHING,
    /** 闭合环；当前默认按 {@link RoadTopologyMode#LINEAR} 校验时为 warning */
    ROAD_CYCLE,
    /** {@link Road#getOrderedSegmentIds()} 存储顺序与 {@link RoadSegmentOrdering} 拓扑序不一致 */
    ROAD_ORDER_MISMATCH
}
