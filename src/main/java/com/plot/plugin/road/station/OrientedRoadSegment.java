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
 */
public record OrientedRoadSegment(
        String edgeId,
        boolean forward,
        String entryNodeId,
        String exitNodeId,
        double startStation,
        double length,
        double geometryChainStart,
        double geometryChainEnd) {

    private static final double EPSILON = 1e-6;

    public OrientedRoadSegment(
            String edgeId,
            boolean forward,
            String entryNodeId,
            String exitNodeId,
            double startStation,
            double length) {
        this(edgeId, forward, entryNodeId, exitNodeId, startStation, length, 0.0, length);
    }

    public double endStation() {
        return startStation + length;
    }

    public boolean isPartialSlice() {
        return geometryChainStart > EPSILON
            || geometryChainEnd < length - EPSILON;
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
     * Interior seam cuts are not topology nodes: a partial slice whose chain end sits mid-edge
     * does not report the far topology node; {@code geometryChainStart > 0} similarly hides entry.
     */
    public OptionalDouble roadStationAtNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return OptionalDouble.empty();
        }
        if (forward) {
            if (entryNodeId.equals(nodeId) && geometryChainStart <= EPSILON) {
                return OptionalDouble.of(startStation);
            }
            // Post-seam forward tails (start>0) end at the edge exit node.
            // Pre-seam heads end at the seam — not a topology node — even though exitNodeId
            // still names the edge's exit; require the slice to reach the geometric edge end.
            if (exitNodeId.equals(nodeId) && reachesForwardExitNode()) {
                return OptionalDouble.of(endStation());
            }
        } else {
            // Reverse storage: chain entry is at geometric end; chain exit at geometric start.
            if (entryNodeId.equals(nodeId) && reachesReverseEntryNode()) {
                return OptionalDouble.of(startStation);
            }
            if (exitNodeId.equals(nodeId) && geometryChainStart <= EPSILON) {
                return OptionalDouble.of(endStation());
            }
        }
        return OptionalDouble.empty();
    }

    private boolean reachesForwardExitNode() {
        if (geometryChainStart > EPSILON) {
            // Interior seam → edge-end tail: chain exit is the topology exit node.
            return true;
        }
        // Full edge from geom 0: endStation is the exit node. A LOOP pre-seam head also has
        // start=0 with end=length(=seam), but then a sibling post-seam slice owns the real exit;
        // callers iterate all slices and the post-seam match wins. Keep full-edge behavior.
        return geometryChainEnd >= length + geometryChainStart - EPSILON;
    }

    private boolean reachesReverseEntryNode() {
        if (geometryChainStart > EPSILON) {
            // Reverse pre-seam (geom seam→full): chain start is topology entry (geom end).
            return true;
        }
        // Full reverse edge (0→length) includes geom end at chain start.
        return geometryChainEnd >= length - EPSILON;
    }

    public OrientedRoadSegment withStartStation(double newStartStation) {
        return new OrientedRoadSegment(
            edgeId, forward, entryNodeId, exitNodeId,
            newStartStation, length, geometryChainStart, geometryChainEnd);
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
