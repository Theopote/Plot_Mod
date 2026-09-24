package com.plot.plugin.road.centerline;

/**
 * 单条道路的中心线形状不变量违反。
 */
public record RoadCenterlineViolation(String roadId, RoadCenterlineViolationKind kind) {
}
