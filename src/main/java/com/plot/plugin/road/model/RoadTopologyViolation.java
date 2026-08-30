package com.plot.plugin.road.model;

/**
 * 单条 {@link Road} 的拓扑不变量违反记录。
 */
public record RoadTopologyViolation(String roadId, RoadTopologyViolationKind kind) {
}
