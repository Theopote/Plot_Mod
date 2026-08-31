package com.plot.plugin.road.station;

import java.util.OptionalDouble;

/**
 * 道路链上的定向分段：除拓扑顺序外，还携带沿链行进方向。
 * <p>
 * {@code forward == false} 表示几何方向与链方向相反；生成时须用
 * {@link com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry#chainLeftNormal}
 * 解析相对链的 LEFT/RIGHT，而非几何 {@code leftNormal}。
 */
public record OrientedRoadSegment(
        String edgeId,
        boolean forward,
        String entryNodeId,
        String exitNodeId,
        double startStation,
        double length) {

    private static final double EPSILON = 1e-6;

    public double endStation() {
        return startStation + length;
    }

    /**
     * 边内几何局部距离（从几何 start 起）→ 链局部距离（从链入口起）。
     */
    public double chainLocalFromGeometryLocal(double geometryLocalDistance) {
        double clamped = clampGeometryLocal(geometryLocalDistance);
        return forward ? clamped : length - clamped;
    }

    /**
     * 链局部距离（从链入口起）→ 边内几何局部距离（从几何 start 起）。
     */
    public double geometryLocalFromChainLocal(double chainLocalDistance) {
        double clamped = clampChainLocal(chainLocalDistance);
        return forward ? clamped : length - clamped;
    }

    /**
     * 几何局部距离 → 道路桩号。
     */
    public double roadStationAtGeometryLocal(double geometryLocalDistance) {
        return startStation + chainLocalFromGeometryLocal(geometryLocalDistance);
    }

    /**
     * 链局部距离 → 道路桩号。
     */
    public double roadStationAtChainLocal(double chainLocalDistance) {
        return startStation + clampChainLocal(chainLocalDistance);
    }

    /**
     * 道路桩号 → 几何局部距离；超出该分段桩号区间时返回 empty。
     */
    public OptionalDouble geometryLocalAtRoadStation(double roadStation) {
        if (!Double.isFinite(roadStation)) {
            return OptionalDouble.empty();
        }
        double chainLocal = roadStation - startStation;
        if (chainLocal < -EPSILON || chainLocal > length + EPSILON) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(geometryLocalFromChainLocal(chainLocal));
    }

    /**
     * 端点节点处道路桩号；非端点返回 empty。
     */
    public OptionalDouble roadStationAtNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return OptionalDouble.empty();
        }
        if (entryNodeId.equals(nodeId)) {
            return OptionalDouble.of(startStation);
        }
        if (exitNodeId.equals(nodeId)) {
            return OptionalDouble.of(endStation());
        }
        return OptionalDouble.empty();
    }

    private double clampGeometryLocal(double geometryLocalDistance) {
        if (!Double.isFinite(geometryLocalDistance)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(geometryLocalDistance, length));
    }

    private double clampChainLocal(double chainLocalDistance) {
        if (!Double.isFinite(chainLocalDistance)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(chainLocalDistance, length));
    }
}
