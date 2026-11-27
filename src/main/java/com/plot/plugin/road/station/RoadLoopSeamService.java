package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 闭环剖面开口点：默认选择、链上投影与遍历起点解析。 */
public final class RoadLoopSeamService {
    private static final double EPSILON = 1e-6;

    private RoadLoopSeamService() {
    }

    public static RoadLoopSeam computeDefault(RoadNetwork network, Road road) {
        return computeDefault(network, road, null);
    }

    /**
     * 确定性默认 seam：优先源路径首点投影到 centerline，否则 min(X)→min(Y) 节点。
     */
    public static RoadLoopSeam computeDefault(RoadNetwork network, Road road, List<Vec2d> sourcePoints) {
        if (network == null || road == null) {
            throw new IllegalArgumentException("network and road required");
        }
        if (sourcePoints != null && !sourcePoints.isEmpty()) {
            SeamProjection projection = projectOntoOrientedChain(network, road, sourcePoints.getFirst());
            if (projection != null) {
                return RoadLoopSeam.onSegment(
                    projection.position(),
                    projection.segmentId(),
                    projection.geometryLocalFraction());
            }
        }
        String nodeId = fallbackNodeId(network, road);
        if (nodeId == null) {
            throw new IllegalStateException("unable to resolve loop seam for road " + road.getId());
        }
        RoadNode node = network.getNode(nodeId);
        return RoadLoopSeam.at(node.getPosition());
    }

    public static Vec2d overlayPosition(RoadNetwork network, Road road) {
        if (road == null || road.getLoopSeam() == null) {
            return null;
        }
        SeamProjection projection = resolveFromSeam(network, road, road.getLoopSeam());
        if (projection != null && projection.position() != null) {
            return projection.position().copy();
        }
        return road.getLoopSeam().position() != null ? road.getLoopSeam().position().copy() : null;
    }

    /**
     * Whether a LOOP seam can be resolved onto the road chain. Prefers segment hint; does not
     * paper over a bad hint with nearest-point fallback alone.
     */
    public static boolean isSeamResolvable(RoadNetwork network, Road road) {
        if (network == null || road == null || road.getLoopSeam() == null) {
            return false;
        }
        RoadLoopSeam seam = road.getLoopSeam();
        Vec2d position = seam.position();
        if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y)) {
            return false;
        }
        if (seam.segmentHintId() != null) {
            RoadEdge hinted = network.getEdge(seam.segmentHintId());
            if (hinted == null || !road.getOrderedSegmentIds().contains(seam.segmentHintId())) {
                return false;
            }
            if (seam.localFraction() == null
                    || !Double.isFinite(seam.localFraction())
                    || seam.localFraction() < -EPSILON
                    || seam.localFraction() > 1.0 + EPSILON) {
                return false;
            }
        }
        SeamProjection projection = resolveFromSeam(network, road, seam);
        if (projection == null || projection.position() == null) {
            return false;
        }
        return position.distance(projection.position())
            <= com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE;
    }

    /**
     * 解析闭环链遍历起点节点（用于 {@link RoadStationing} 旋转）。
     */
    public static String resolveChainStartNodeId(RoadNetwork network, Road road, RoadLoopSeam seam) {
        if (network == null || road == null || seam == null) {
            return null;
        }
        if (seam.segmentHintId() != null && seam.localFraction() != null) {
            RoadEdge hinted = network.getEdge(seam.segmentHintId());
            if (hinted != null) {
                // localFraction is geometry-storage; convert to chain orientation for node pick.
                boolean forward = orientedForwardAtSegment(network, road, seam.segmentHintId(), seam.localFraction());
                double geometryFraction = seam.localFraction();
                double chainFraction = forward ? geometryFraction : 1.0 - geometryFraction;
                if (chainFraction <= EPSILON) {
                    return forward ? hinted.getStartNodeId() : hinted.getEndNodeId();
                }
                if (chainFraction >= 1.0 - EPSILON) {
                    return forward ? hinted.getEndNodeId() : hinted.getStartNodeId();
                }
                return forward ? hinted.getStartNodeId() : hinted.getEndNodeId();
            }
        }
        SeamProjection projection = projectOntoOrientedChain(network, road, seam.position());
        if (projection == null) {
            return fallbackNodeId(network, road);
        }
        if (projection.localFraction() <= EPSILON) {
            return projection.entryNodeId();
        }
        if (projection.localFraction() >= 1.0 - EPSILON) {
            return projection.exitNodeId();
        }
        return projection.entryNodeId();
    }

    /**
     * 闭环沿程分段：以 seam 为 station 0，必要时将首/尾 Edge 逻辑拆成 slice。
     */
    public static List<OrientedRoadSegment> buildLoopOrientedSegments(RoadNetwork network, Road road) {
        List<OrientedRoadSegment> base = RoadStationing.buildBaseOrientedSegments(network, road);
        if (base.isEmpty() || road == null || road.getLoopSeam() == null) {
            return base;
        }
        SeamProjection projection = resolveSeamProjection(network, road, road.getLoopSeam());
        if (projection == null) {
            return base;
        }

        int seamIndex = -1;
        double seamChainLocal = 0.0;
        OrientedRoadSegment seamSegment = null;
        for (int i = 0; i < base.size(); i++) {
            OrientedRoadSegment segment = base.get(i);
            if (projection.chainStation() <= segment.endStation() + EPSILON) {
                seamIndex = i;
                seamSegment = segment;
                seamChainLocal = projection.chainStation() - segment.startStation();
                break;
            }
        }
        if (seamSegment == null) {
            return base;
        }

        RoadEdge seamEdge = network.getEdge(seamSegment.edgeId());
        if (seamEdge == null) {
            return base;
        }
        double fullEdgeLength = seamEdge.getLength();
        double geometryLocalAtSeam = seamSegment.geometryLocalFromChainLocal(seamChainLocal);
        boolean seamAtEntry = seamChainLocal <= EPSILON;
        boolean seamAtExit = seamChainLocal >= seamSegment.length() - EPSILON;

        List<OrientedRoadSegment> rotated = new ArrayList<>(base.size() + 1);
        double station = 0.0;

        if (seamAtEntry) {
            for (int k = 0; k < base.size(); k++) {
                OrientedRoadSegment original = base.get((seamIndex + k) % base.size());
                rotated.add(original.withStartStation(station));
                station += original.length();
            }
        } else if (seamAtExit) {
            int startIndex = (seamIndex + 1) % base.size();
            for (int k = 0; k < base.size(); k++) {
                OrientedRoadSegment original = base.get((startIndex + k) % base.size());
                rotated.add(original.withStartStation(station));
                station += original.length();
            }
        } else {
            double tailLength = seamSegment.length() - seamChainLocal;
            // forward：seam→链尾对应几何 [seam, end]；反向边几何随 chainLocal 递减，区间对调。
            double postSeamGeomStart;
            double postSeamGeomEnd;
            double preSeamGeomStart;
            double preSeamGeomEnd;
            if (seamSegment.forward()) {
                postSeamGeomStart = geometryLocalAtSeam;
                postSeamGeomEnd = fullEdgeLength;
                preSeamGeomStart = 0.0;
                preSeamGeomEnd = geometryLocalAtSeam;
            } else {
                postSeamGeomStart = 0.0;
                postSeamGeomEnd = geometryLocalAtSeam;
                preSeamGeomStart = geometryLocalAtSeam;
                preSeamGeomEnd = fullEdgeLength;
            }
            rotated.add(new OrientedRoadSegment(
                seamSegment.edgeId(),
                seamSegment.forward(),
                seamSegment.entryNodeId(),
                seamSegment.exitNodeId(),
                station,
                tailLength,
                postSeamGeomStart,
                postSeamGeomEnd,
                fullEdgeLength));
            station += tailLength;

            for (int k = 1; k < base.size(); k++) {
                OrientedRoadSegment original = base.get((seamIndex + k) % base.size());
                rotated.add(original.withStartStation(station));
                station += original.length();
            }

            rotated.add(new OrientedRoadSegment(
                seamSegment.edgeId(),
                seamSegment.forward(),
                seamSegment.entryNodeId(),
                seamSegment.exitNodeId(),
                station,
                seamChainLocal,
                preSeamGeomStart,
                preSeamGeomEnd,
                fullEdgeLength));
        }
        return List.copyOf(rotated);
    }

    /**
     * Resolve seam onto the unrotated oriented chain, preferring {@code segmentHintId}/
     * {@code localFraction} when present so self-cross positions do not pick the wrong pass.
     */
    public static SeamProjection resolveFromSeam(RoadNetwork network, Road road, RoadLoopSeam seam) {
        if (network == null || road == null || seam == null) {
            return null;
        }
        return resolveSeamProjection(network, road, seam);
    }

    private static SeamProjection resolveSeamProjection(RoadNetwork network, Road road, RoadLoopSeam seam) {
        if (seam.segmentHintId() != null && seam.localFraction() != null) {
            for (OrientedRoadSegment segment : RoadStationing.buildBaseOrientedSegments(network, road)) {
                if (!segment.edgeId().equals(seam.segmentHintId())) {
                    continue;
                }
                RoadEdge edge = network.getEdge(seam.segmentHintId());
                if (edge == null) {
                    break;
                }
                // localFraction is geometry-storage domain (same as RoadStationing.physicalSeamStation).
                double geometryLength = edge.getLength();
                double geometryLocal = seam.localFraction() * geometryLength;
                double chainLocal = segment.chainLocalFromGeometryLocal(geometryLocal);
                Vec2d position = pointOnEdge(edge, geometryLocal);
                if (position == null) {
                    break;
                }
                double chainFraction = segment.length() > EPSILON ? chainLocal / segment.length() : 0.0;
                return new SeamProjection(
                    position,
                    seam.segmentHintId(),
                    segment.entryNodeId(),
                    segment.exitNodeId(),
                    chainFraction,
                    seam.localFraction(),
                    segment.startStation() + chainLocal);
            }
        }
        return projectOntoOrientedChain(network, road, seam.position());
    }

    private static Vec2d pointOnEdge(RoadEdge edge, double geometryLocal) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return null;
        }
        double remaining = Math.max(0.0, geometryLocal);
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            double segmentLength = start.distance(end);
            if (remaining <= segmentLength + EPSILON) {
                if (segmentLength <= EPSILON) {
                    return start.copy();
                }
                double t = remaining / segmentLength;
                return new Vec2d(
                    start.x + (end.x - start.x) * t,
                    start.y + (end.y - start.y) * t);
            }
            remaining -= segmentLength;
        }
        return points.getLast().copy();
    }

    /**
     * Seam-migrate a point station. Invalid input returns {@link Double#NaN}
     * (never a legal zero as failure sentinel).
     */
    public static double rotateLoopStation(double oldStation, double shift, double loopLength) {
        return CyclicStationTransform.rotateByShift(oldStation, shift, loopLength);
    }

    private static String fallbackNodeId(RoadNetwork network, Road road) {
        String best = null;
        Vec2d bestPos = null;
        for (String segmentId : road.getOrderedSegmentIds()) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                continue;
            }
            for (String nodeId : List.of(edge.getStartNodeId(), edge.getEndNodeId())) {
                RoadNode node = network.getNode(nodeId);
                if (node == null || node.getPosition() == null) {
                    continue;
                }
                Vec2d pos = node.getPosition();
                if (bestPos == null || comparePosition(pos, bestPos) < 0) {
                    bestPos = pos;
                    best = nodeId;
                }
            }
        }
        return best;
    }

    private static boolean orientedForwardAtSegment(
            RoadNetwork network,
            Road road,
            String segmentId,
            double localFraction) {
        for (OrientedRoadSegment segment : RoadStationing.orientedSegmentsWithoutLoopRotation(network, road)) {
            if (segment.edgeId().equals(segmentId)) {
                return segment.forward();
            }
        }
        RoadEdge edge = network.getEdge(segmentId);
        return edge != null;
    }

    public static SeamProjection projectForPick(RoadNetwork network, Road road, Vec2d query) {
        return projectForPick(network, road, query, null);
    }

    /**
     * Canvas seam pick. Prefers {@code preferredEdgeId} when it belongs to the road.
     * Distance filter applies to global and preferred alike; far nearest returns null.
     * Self-crossing dual-pass ties that cannot be distinguished are rejected ({@code null}).
     */
    public static SeamProjection projectForPick(
            RoadNetwork network,
            Road road,
            Vec2d query,
            String preferredEdgeId) {
        if (query == null || network == null || road == null) {
            return null;
        }
        String preferred = preferredEdgeId;
        if (preferred != null && !road.getOrderedSegmentIds().contains(preferred)) {
            preferred = null;
        }
        double pickTol = com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE;
        List<ScoredProjection> near = collectNearProjections(network, road, query, null, pickTol);
        if (near.isEmpty()) {
            return null;
        }
        List<ScoredProjection> distinct = dedupePasses(near);
        if (distinct.size() >= 2) {
            // Prefer edge only disambiguates among near-tolerance survivors of distinct passes.
            if (preferred != null) {
                List<ScoredProjection> onPreferred = new ArrayList<>();
                for (ScoredProjection scored : distinct) {
                    if (preferred.equals(scored.projection().segmentId())) {
                        onPreferred.add(scored);
                    }
                }
                if (onPreferred.size() == 1) {
                    return onPreferred.getFirst().projection();
                }
                if (onPreferred.isEmpty()) {
                    return null;
                }
            }
            return null;
        }
        SeamProjection best = distinct.getFirst().projection();
        if (preferred != null && preferred.equals(best.segmentId())) {
            return best;
        }
        if (preferred != null) {
            List<ScoredProjection> preferredNear =
                collectNearProjections(network, road, query, preferred, pickTol);
            List<ScoredProjection> preferredDistinct = dedupePasses(preferredNear);
            if (preferredDistinct.size() == 1
                    && preferredDistinct.getFirst().distance()
                        <= distinct.getFirst().distance() + pickTol) {
                return preferredDistinct.getFirst().projection();
            }
        }
        return best;
    }

    /**
     * @param localFraction chain-local fraction along the oriented segment (traversal)
     * @param geometryLocalFraction storage-geometry fraction along the edge (persist this)
     */
    public record SeamProjection(
            Vec2d position,
            String segmentId,
            String entryNodeId,
            String exitNodeId,
            double localFraction,
            double geometryLocalFraction,
            double chainStation) {
    }

    private record ScoredProjection(SeamProjection projection, double distance) {
    }

    /**
     * Resolve/overlay projection: nearest in-tolerance pass wins.
     * Unlike {@link #projectForPick}, dual-pass self-crossings are not rejected —
     * callers need a concrete seam station even when XY is ambiguous.
     */
    private static SeamProjection projectOntoOrientedChain(RoadNetwork network, Road road, Vec2d query) {
        List<ScoredProjection> near = collectNearProjections(
            network, road, query, null, com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE);
        if (near.isEmpty()) {
            return null;
        }
        List<ScoredProjection> distinct = dedupePasses(near);
        return distinct.isEmpty() ? null : distinct.getFirst().projection();
    }

    private static List<ScoredProjection> collectNearProjections(
            RoadNetwork network,
            Road road,
            Vec2d query,
            String edgeFilter,
            double pickTol) {
        List<ScoredProjection> near = new ArrayList<>();
        if (query == null) {
            return near;
        }
        for (OrientedRoadSegment segment : RoadStationing.orientedSegmentsWithoutLoopRotation(network, road)) {
            if (edgeFilter != null && !edgeFilter.equals(segment.edgeId())) {
                continue;
            }
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                continue;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            if (points.size() < 2) {
                continue;
            }
            double edgeLength = edge.getLength();
            double[] prefix = new double[points.size()];
            prefix[0] = 0.0;
            for (int i = 1; i < points.size(); i++) {
                prefix[i] = prefix[i - 1] + points.get(i - 1).distance(points.get(i));
            }
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d start = points.get(i);
                Vec2d end = points.get(i + 1);
                Vec2d projected = RoadGeometryUtils.projectPointOnSegment(start, end, query);
                double distance = projected.distance(query);
                if (distance > pickTol) {
                    continue;
                }
                double geometryLocal = prefix[i] + start.distance(projected);
                double chainLocal = segment.chainLocalFromGeometryLocal(geometryLocal);
                double chainFraction = segment.length() > EPSILON ? chainLocal / segment.length() : 0.0;
                double geometryFraction = edgeLength > EPSILON ? geometryLocal / edgeLength : 0.0;
                near.add(new ScoredProjection(
                    new SeamProjection(
                        projected.copy(),
                        segment.edgeId(),
                        segment.entryNodeId(),
                        segment.exitNodeId(),
                        chainFraction,
                        geometryFraction,
                        segment.startStation() + chainLocal),
                    distance));
            }
        }
        near.sort((a, b) -> {
            int byDist = Double.compare(a.distance(), b.distance());
            return byDist != 0 ? byDist : comparePosition(a.projection().position(), b.projection().position());
        });
        return near;
    }

    /** Collapse same-pass candidates; keep nearest per distinct physical pass. */
    private static List<ScoredProjection> dedupePasses(List<ScoredProjection> near) {
        List<ScoredProjection> distinct = new ArrayList<>();
        for (ScoredProjection scored : near) {
            boolean merged = false;
            for (int i = 0; i < distinct.size(); i++) {
                if (samePickPass(scored.projection(), distinct.get(i).projection())) {
                    if (scored.distance() + EPSILON < distinct.get(i).distance()) {
                        distinct.set(i, scored);
                    }
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                distinct.add(scored);
            }
        }
        distinct.sort(Comparator.comparingDouble(ScoredProjection::distance));
        return distinct;
    }

    private static boolean samePickPass(SeamProjection a, SeamProjection b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.segmentId().equals(b.segmentId())
                && Math.abs(a.geometryLocalFraction() - b.geometryLocalFraction()) <= 1e-3) {
            return true;
        }
        // Adjacent edges meeting at a shared topo node are one physical pass.
        if (sharesTopoNode(a, b)) {
            boolean aAtNode = a.localFraction() <= EPSILON || a.localFraction() >= 1.0 - EPSILON;
            boolean bAtNode = b.localFraction() <= EPSILON || b.localFraction() >= 1.0 - EPSILON;
            if (aAtNode && bAtNode && a.position().distance(b.position())
                    <= com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE) {
                return true;
            }
        }
        // Near-coincident XY with near-equal chain stations → same pass (incl. LOOP 0≈L).
        if (a.position().distance(b.position())
                <= com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE
                && Math.abs(a.chainStation() - b.chainStation())
                    <= com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE) {
            return true;
        }
        return false;
    }

    private static boolean sharesTopoNode(SeamProjection a, SeamProjection b) {
        return a.entryNodeId().equals(b.entryNodeId())
            || a.entryNodeId().equals(b.exitNodeId())
            || a.exitNodeId().equals(b.entryNodeId())
            || a.exitNodeId().equals(b.exitNodeId());
    }

    private static int comparePosition(Vec2d left, Vec2d right) {
        int byX = Double.compare(left.x, right.x);
        return byX != 0 ? byX : Double.compare(left.y, right.y);
    }

}
