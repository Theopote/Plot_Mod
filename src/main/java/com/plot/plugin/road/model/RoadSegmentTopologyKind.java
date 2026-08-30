package com.plot.plugin.road.model;

/**
 * 单条 {@link Road} 内分段诱导子图的拓扑形态。
 *
 * @see RoadSegmentTopologyAnalyzer
 * @see docs/decisions/0003-road-segment-ordering-linear-chain-assumption.md
 */
public enum RoadSegmentTopologyKind {
    /** 简单 open chain（0–1 段，或度数为 1 的端点恰有两个） */
    SIMPLE_CHAIN,
    /** 闭合环：子图连通且所有节点度数均为 2 */
    LOOP,
    /** 分叉：子图连通但存在度数 &gt; 2 的节点 */
    FORK,
    /** 多连通分量：分段不属于同一连通子图 */
    DISCONNECTED
}
