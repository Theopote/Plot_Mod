package com.plot.plugin.road.station;

import java.util.OptionalDouble;

/**
 * 道路链上的定向分段：除拓扑顺序外，还携带沿链行进方向。
 * <p>
 * {@code forward == false} 表示几何方向与链方向相反；生成时须用
 * {@link com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry#chainLeftNormal}
 * 解析相对链的 LEFT/RIGHT，而非几何 {@code leftNormal}。
 * <p>
 * 闭环 seam 可将一条 Edge 逻辑拆成两个 slice（{@code geometryChainStart/End}），
 * 使 station 0 落在用户点击的 × 位置而非 Edge 入口节点。
 * {@code fullEdgeLength} is the storage edge length so topology-node ownership is not
 * inferred from the slice's own {@code length}.
 */
public record OrientedRoadSegment(
        String edgeId,
        boolean forward,
        String entryNodeId,
        String exitNodeId,
        double startStation,
        double length,
        double geometryChainStart,
        double geometryChainEnd,
        double fullEdgeLength) {

    private static final double EPSILON = 1e-6;

    public OrientedRoadSegment(
            String edgeId,
            boolean forward,
            String entryNodeId,
            String exitNodeId,
            double startStation,
            double length) {
        this(edgeId, forward, entryNodeId, exitNodeId, startStation, length, 0.0, length, length);
    }

    public OrientedRoadSegment(
            String edgeId,
            boolean forward,
            String entryNodeId,
            String exitNodeId,
            double startStation,
            double length,
            double geometryChainStart,
            double geometryChainEnd) {
        this(
            edgeId,
            forward,
            entryNodeId,
            exitNodeId,
            startStation,
            length,
            geometryChainStart,
            geometryChainEnd,
            Math.max(geometryChainEnd, length));
    }

    public double endStation() {
        return startStation + length;
    }

    public boolean isPartialSlice() {
        double full = effectiveFullEdgeLength();
        return geometryChainStart > EPSILON
            || geometryChainEnd < full - EPSILON;
    }

    /** True when {@code geometryLocalDistance} lies on this traversal slice (no clamp). */
    public boolean containsGeometryLocal(double geometryLocalDistance) {
        return Double.isFinite(geometryLocalDistance)
            && geometryLocalDistance >= geometryChainStart - EPSILON
            && geometryLocalDistance <= geometryChainEnd + EPSILON;
    }

    /**
     * 边内几何局部距离（从几何 start 起）→ 链局部距离（从链入口起）。
     */
    public double chainLocalFromGeometryLocal(double geometryLocalDistance) {
        double clamped = clampGeometryLocal(geometryLocalDistance);
        return forward ? clamped - geometryChainStart : geometryChainEnd - clamped;
    }

    /**
     * 链局部距离（从链入口起）→ 边内几何局部距离（从几何 start 起）。
     */
    public double geometryLocalFromChainLocal(double chainLocalDistance) {
        double clamped = clampChainLocal(chainLocalDistance);
        return forward ? geometryChainStart + clamped : geometryChainEnd - clamped;
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
     * Topology-node station on this slice only.
     * Interior seam cuts are not topology nodes.
     */
    public OptionalDouble roadStationAtNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return OptionalDouble.empty();
        }
        if (forward) {
            if (entryNodeId.equals(nodeId) && coversGeometryStartNode()) {
                return OptionalDouble.of(startStation);
            }
            if (exitNodeId.equals(nodeId) && coversGeometryEndNode()) {
                return OptionalDouble.of(endStation());
            }
        } else {
            if (entryNodeId.equals(nodeId) && coversGeometryEndNode()) {
                return OptionalDouble.of(startStation);
            }
            if (exitNodeId.equals(nodeId) && coversGeometryStartNode()) {
                return OptionalDouble.of(endStation());
            }
        }
        return OptionalDouble.empty();
    }

    private boolean coversGeometryStartNode() {
        return geometryChainStart <= EPSILON;
    }

    private boolean coversGeometryEndNode() {
        return geometryChainEnd >= effectiveFullEdgeLength() - EPSILON;
    }

    private double effectiveFullEdgeLength() {
        return fullEdgeLength > EPSILON ? fullEdgeLength : Math.max(geometryChainEnd, length);
    }

    public OrientedRoadSegment withStartStation(double newStartStation) {
        return new OrientedRoadSegment(
            edgeId, forward, entryNodeId, exitNodeId,
            newStartStation, length, geometryChainStart, geometryChainEnd, fullEdgeLength);
    }

    private double clampGeometryLocal(double geometryLocalDistance) {
        if (!Double.isFinite(geometryLocalDistance)) {
            return geometryChainStart;
        }
        return Math.max(geometryChainStart, Math.min(geometryLocalDistance, geometryChainEnd));
    }

    private double clampChainLocal(double chainLocalDistance) {
        if (!Double.isFinite(chainLocalDistance)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(chainLocalDistance, length));
    }
}
