package com.plot.plugin.road.station;

/**
 * 道路设计链方向：从链入口节点到链出口节点。
 * <p>
 * 与 {@link RoadEdge} 的 start/end 存储方向无关；由拓扑链遍历推导。
 * 整路反向（{@code reverseRoad}）会交换 entry/exit 并镜像 canonical 桩号。
 */
public record RoadDesignDirection(String entryNodeId, String exitNodeId) {

}
