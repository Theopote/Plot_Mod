package com.plot.plugin.road.model;

/**
 * 逻辑道路拓扑模式（下一阶段数据模型；当前所有 Road 隐含 {@link #LINEAR}）。
 *
 * @see docs/decisions/0004-road-topology-invariant.md
 */
public enum RoadTopologyMode {
    /** 连续 open chain：连通、节点度数 ≤ 2、恰有两个端点 */
    LINEAR,
    /** 闭合环：连通、节点度数均为 2（如 Ring Road） */
    LOOP
}
