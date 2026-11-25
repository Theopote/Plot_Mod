package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** 检测道路 plan 中心线的真实线段交点（不含端点邻近启发）。 */
public final class RoadCrossingDetector {
    /** 几何求交/端点落在线段内部判定容差；与 Connect 吸附容差分离。 */
    private static final double INTERSECTION_EPSILON = RoadCrossingDetectionSupport.INTERSECTION_EPSILON;
    /** 合并同一物理交点的重复检测；与稳定匹配容差分离。 */
    private static final double DEDUP_TOLERANCE = INTERSECTION_EPSILON;

    private RoadCrossingDetector() {
    }

    public static List<RoadCrossing> detectAll(RoadNetwork network) {
        if (network == null) {
            return List.of();
        }
        RoadCrossingDetectionSupport.DetectionSession session =
            new RoadCrossingDetectionSupport.DetectionSession(network);
        List<Road> roads = new ArrayList<>(network.getRoads().values());
        Map<String, RoadCrossing> deduped = new LinkedHashMap<>();
        for (int i = 0; i < roads.size(); i++) {
            Road roadA = roads.get(i);
            RoadCrossingDetectionSupport.CachedRoadGeometry geomA = session.geometryFor(roadA);
            if (!geomA.valid()) {
                continue;
            }
            for (int j = i + 1; j < roads.size(); j++) {
                Road roadB = roads.get(j);
                if (roadA.getId() == null || roadA.getId().equals(roadB.getId())) {
                    continue;
                }
                RoadCrossingDetectionSupport.CachedRoadGeometry geomB = session.geometryFor(roadB);
                if (!geomB.valid() || !RoadCrossingDetectionSupport.aabbMayIntersect(geomA, geomB)) {
                    continue;
                }
                for (Vec2d point : findSegmentIntersections(session, roadA, roadB, geomA, geomB)) {
                    RoadCrossing crossing = toCrossing(session, roadA, roadB, point);
                    if (crossing != null) {
                        deduped.putIfAbsent(dedupeKey(crossing), crossing);
                    }
                }
            }
        }
        return List.copyOf(deduped.values());
    }

    private static List<Vec2d> findSegmentIntersections(
            RoadCrossingDetectionSupport.DetectionSession session,
            Road roadA,
            Road roadB,
            RoadCrossingDetectionSupport.CachedRoadGeometry geomA,
            RoadCrossingDetectionSupport.CachedRoadGeometry geomB) {
        List<Vec2d> centerlineA = geomA.points();
        List<Vec2d> centerlineB = geomB.points();
        if (centerlineA.size() < 2 || centerlineB.size() < 2) {
            return List.of();
        }
        PolylineShape polyA = geomA.polyline();
        PolylineShape polyB = geomB.polyline();
        if (polyA == null || polyB == null) {
            return List.of();
        }
        List<Vec2d> candidates = new ArrayList<>(polyA.getIntersectionsWith(polyB));
        collectEndpointOnInteriorContacts(session, roadA, centerlineA, roadB, centerlineB, candidates);

        List<Vec2d> filtered = new ArrayList<>();
        for (Vec2d point : candidates) {
            if (RoadCrossingDetectionSupport.isPhysicalOpenEndpoint(session.network(), roadA, point)
                    && RoadCrossingDetectionSupport.isPhysicalOpenEndpoint(session.network(), roadB, point)) {
                continue;
            }
            filtered.add(point.copy());
        }
        return deduplicatePoints(filtered);
    }

    /** 一端点落在另一道路内部（T 形接点）时，线段求交可能无结果。 */
    private static void collectEndpointOnInteriorContacts(
            RoadCrossingDetectionSupport.DetectionSession session,
            Road roadA,
            List<Vec2d> centerlineA,
            Road roadB,
            List<Vec2d> centerlineB,
            List<Vec2d> out) {
        collectOpenEndsOnOtherInterior(session, roadA, centerlineA, roadB, centerlineB, out);
        collectOpenEndsOnOtherInterior(session, roadB, centerlineB, roadA, centerlineA, out);
    }

    private static void collectOpenEndsOnOtherInterior(
            RoadCrossingDetectionSupport.DetectionSession session,
            Road sourceRoad,
            List<Vec2d> sourceCenterline,
            Road targetRoad,
            List<Vec2d> targetCenterline,
            List<Vec2d> out) {
        if (sourceCenterline.size() < 2 || targetCenterline.size() < 2) {
            return;
        }
        for (Vec2d endpoint : openEndpointCandidates(session, sourceRoad, sourceCenterline)) {
            if (liesOnInterior(targetRoad, targetCenterline, endpoint)) {
                out.add(endpoint.copy());
            }
        }
    }

    private static List<Vec2d> openEndpointCandidates(
            RoadCrossingDetectionSupport.DetectionSession session,
            Road road,
            List<Vec2d> centerline) {
        if (road.getTopologyMode() == RoadTopologyMode.LOOP) {
            // LOOP has no physical open ends; still allow polyline ends for T-contacts onto other roads
            // only when they are not used as mutual open-endpoint exclusion (handled separately).
            // A branch tip on a loop uses the BRANCH open end, not the loop end.
            return List.of();
        }
        List<Vec2d> ends = new ArrayList<>(2);
        List<OrientedRoadSegment> segments = RoadStationing.orientedSegments(session.network(), road);
        if (!segments.isEmpty()) {
            addNodePosition(session, segments.getFirst().entryNodeId(), ends);
            addNodePosition(session, segments.getLast().exitNodeId(), ends);
        }
        if (ends.isEmpty()) {
            ends.add(centerline.getFirst());
            ends.add(centerline.getLast());
        }
        return ends;
    }

    private static void addNodePosition(
            RoadCrossingDetectionSupport.DetectionSession session,
            String nodeId,
            List<Vec2d> out) {
        var node = session.network().getNode(nodeId);
        if (node == null || node.getPosition() == null) {
            return;
        }
        for (Vec2d existing : out) {
            if (RoadGeometryUtils.pointsNear(existing, node.getPosition(), INTERSECTION_EPSILON)) {
                return;
            }
        }
        out.add(node.getPosition().copy());
    }

    private static boolean liesOnInterior(Road targetRoad, List<Vec2d> centerline, Vec2d point) {
        for (int i = 0; i < centerline.size() - 1; i++) {
            Vec2d start = centerline.get(i);
            Vec2d end = centerline.get(i + 1);
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(start, end, point);
            if (projected.distance(point) > INTERSECTION_EPSILON) {
                continue;
            }
            boolean nearPolylineEnd =
                RoadGeometryUtils.pointsNear(centerline.getFirst(), projected, INTERSECTION_EPSILON)
                    || RoadGeometryUtils.pointsNear(centerline.getLast(), projected, INTERSECTION_EPSILON);
            if (nearPolylineEnd) {
                // LOOP open polyline ends are seam artefacts — treat as interior.
                if (targetRoad != null && targetRoad.getTopologyMode() == RoadTopologyMode.LOOP) {
                    return true;
                }
                continue;
            }
            return true;
        }
        return false;
    }

    private static RoadCrossing toCrossing(
            RoadCrossingDetectionSupport.DetectionSession session,
            Road roadA,
            Road roadB,
            Vec2d position) {
        if (roadA == null || roadB == null) {
            return null;
        }
        OptionalDouble chainageA = RoadStationing.chainageAtPosition(
            session.network(), roadA, position, session.samplesByRoadId());
        OptionalDouble chainageB = RoadStationing.chainageAtPosition(
            session.network(), roadB, position, session.samplesByRoadId());
        if (chainageA.isEmpty() || chainageB.isEmpty()) {
            return null;
        }
        double stationA = chainageA.getAsDouble();
        double stationB = chainageB.getAsDouble();
        String aId = roadA.getId();
        String bId = roadB.getId();
        if (aId.compareTo(bId) > 0) {
            return CrossingGradeClassifier.classifyTwoRoad(
                session.network(), bId, stationB, aId, stationA, position);
        }
        return CrossingGradeClassifier.classifyTwoRoad(
            session.network(), aId, stationA, bId, stationB, position);
    }

    private static String dedupeKey(RoadCrossing crossing) {
        return crossing.roadAId()
            + "|" + Math.round(crossing.stationA() * 100.0)
            + "|" + crossing.roadBId()
            + "|" + Math.round(crossing.stationB() * 100.0);
    }

    private static List<Vec2d> deduplicatePoints(List<Vec2d> points) {
        List<Vec2d> unique = new ArrayList<>();
        for (Vec2d point : points) {
            boolean exists = false;
            for (Vec2d existing : unique) {
                if (RoadGeometryUtils.pointsNear(existing, point, DEDUP_TOLERANCE)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                unique.add(point.copy());
            }
        }
        return unique;
    }
}
