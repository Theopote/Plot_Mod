package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;

import java.util.ArrayList;
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
                    projection.localFraction());
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
        SeamProjection projection = projectOntoOrientedChain(network, road, road.getLoopSeam().position());
        return projection != null ? projection.position().copy() : road.getLoopSeam().position().copy();
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
                boolean forward = orientedForwardAtSegment(network, road, seam.segmentHintId(), seam.localFraction());
                double fraction = seam.localFraction();
                if (fraction <= EPSILON) {
                    return forward ? hinted.getStartNodeId() : hinted.getEndNodeId();
                }
                if (fraction >= 1.0 - EPSILON) {
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
            double headLength = seamChainLocal;
            rotated.add(new OrientedRoadSegment(
                seamSegment.edgeId(),
                seamSegment.forward(),
                seamSegment.entryNodeId(),
                seamSegment.exitNodeId(),
                station,
                tailLength,
                geometryLocalAtSeam,
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
                headLength,
                0.0,
                geometryLocalAtSeam));
        }
        return List.copyOf(rotated);
    }

    private static SeamProjection resolveSeamProjection(RoadNetwork network, Road road, RoadLoopSeam seam) {
        if (seam.segmentHintId() != null && seam.localFraction() != null) {
            for (OrientedRoadSegment segment : RoadStationing.buildBaseOrientedSegments(network, road)) {
                if (!segment.edgeId().equals(seam.segmentHintId())) {
                    continue;
                }
                double chainLocal = seam.localFraction() * segment.length();
                double geometryLocal = segment.geometryLocalFromChainLocal(chainLocal);
                RoadEdge edge = network.getEdge(seam.segmentHintId());
                if (edge == null) {
                    break;
                }
                Vec2d position = pointOnEdge(edge, geometryLocal);
                if (position == null) {
                    break;
                }
                return new SeamProjection(
                    position,
                    seam.segmentHintId(),
                    segment.entryNodeId(),
                    segment.exitNodeId(),
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

    public static double rotateLoopStation(double oldStation, double shift, double loopLength) {
        if (!Double.isFinite(oldStation) || !Double.isFinite(shift) || loopLength <= EPSILON) {
            return 0.0;
        }
        double rotated = oldStation - shift;
        while (rotated < -EPSILON) {
            rotated += loopLength;
        }
        while (rotated > loopLength + EPSILON) {
            rotated -= loopLength;
        }
        if (rotated < 0.0) {
            return 0.0;
        }
        if (rotated > loopLength) {
            return loopLength;
        }
        return rotated;
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
        return projectOntoOrientedChain(network, road, query);
    }

    public record SeamProjection(
            Vec2d position,
            String segmentId,
            String entryNodeId,
            String exitNodeId,
            double localFraction,
            double chainStation) {
    }

    private static SeamProjection projectOntoOrientedChain(RoadNetwork network, Road road, Vec2d query) {
        if (query == null) {
            return null;
        }
        SeamProjection best = null;
        double bestDistance = Double.MAX_VALUE;
        for (OrientedRoadSegment segment : RoadStationing.orientedSegmentsWithoutLoopRotation(network, road)) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                continue;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d start = points.get(i);
                Vec2d end = points.get(i + 1);
                Vec2d projected = RoadGeometryUtils.projectPointOnSegment(start, end, query);
                double distance = projected.distance(query);
                if (distance >= bestDistance) {
                    continue;
                }
                double geometryLocal = start.distance(projected);
                for (int j = 0; j < i; j++) {
                    geometryLocal += points.get(j).distance(points.get(j + 1));
                }
                double chainLocal = segment.chainLocalFromGeometryLocal(geometryLocal);
                double localFraction = segment.length() > EPSILON ? chainLocal / segment.length() : 0.0;
                bestDistance = distance;
                best = new SeamProjection(
                    projected.copy(),
                    segment.edgeId(),
                    segment.entryNodeId(),
                    segment.exitNodeId(),
                    localFraction,
                    segment.startStation() + chainLocal);
            }
        }
        return best;
    }

    private static int comparePosition(Vec2d left, Vec2d right) {
        int byX = Double.compare(left.x, right.x);
        return byX != 0 ? byX : Double.compare(left.y, right.y);
    }

}
