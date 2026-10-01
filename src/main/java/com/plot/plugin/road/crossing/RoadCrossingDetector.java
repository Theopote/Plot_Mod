package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** 检测道路 plan 中心线的真实线段交点（不含端点邻近启发）。 */
public final class RoadCrossingDetector {
    /** 几何求交/端点落在线段内部判定容差；与 Connect 吸附容差分离。 */
    private static final double INTERSECTION_EPSILON = 1e-5;
    /** 合并同一物理交点的重复检测；与稳定匹配容差分离。 */
    private static final double DEDUP_TOLERANCE = INTERSECTION_EPSILON;

    private RoadCrossingDetector() {
    }

    public static List<RoadCrossing> detectAll(RoadNetwork network) {
        if (network == null) {
            return List.of();
        }
        DetectionSession session = new DetectionSession(network);
        List<Road> roads = new ArrayList<>(network.getRoads().values());
        Map<String, RoadCrossing> deduped = new LinkedHashMap<>();
        for (int i = 0; i < roads.size(); i++) {
            Road roadA = roads.get(i);
            for (int j = i + 1; j < roads.size(); j++) {
                Road roadB = roads.get(j);
                if (roadA.getId() == null || roadA.getId().equals(roadB.getId())) {
                    continue;
                }
                for (Vec2d point : findSegmentIntersections(session, roadA, roadB)) {
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
            DetectionSession session,
            Road roadA,
            Road roadB) {
        List<Vec2d> centerlineA = session.planCenterline(roadA);
        List<Vec2d> centerlineB = session.planCenterline(roadB);
        if (centerlineA.size() < 2 || centerlineB.size() < 2) {
            return List.of();
        }
        PolylineShape polyA = new PolylineShape(centerlineA, false);
        PolylineShape polyB = new PolylineShape(centerlineB, false);
        List<Vec2d> candidates = new ArrayList<>(polyA.getIntersectionsWith(polyB));
        collectEndpointOnInteriorContacts(centerlineA, centerlineB, candidates);

        List<Vec2d> filtered = new ArrayList<>();
        for (Vec2d point : candidates) {
            if (isNearAnyEndpoint(centerlineA, point) && isNearAnyEndpoint(centerlineB, point)) {
                continue;
            }
            filtered.add(point.copy());
        }
        return deduplicatePoints(filtered);
    }

    /** 一端点落在另一道路内部（T 形接点）时，线段求交可能无结果。 */
    private static void collectEndpointOnInteriorContacts(
            List<Vec2d> centerlineA,
            List<Vec2d> centerlineB,
            List<Vec2d> out) {
        collectEndpointsOnOtherInterior(centerlineA, centerlineB, out);
        collectEndpointsOnOtherInterior(centerlineB, centerlineA, out);
    }

    private static void collectEndpointsOnOtherInterior(
            List<Vec2d> source,
            List<Vec2d> target,
            List<Vec2d> out) {
        if (source.size() < 2 || target.size() < 2) {
            return;
        }
        for (Vec2d endpoint : List.of(source.getFirst(), source.getLast())) {
            if (liesOnInterior(target, endpoint)) {
                out.add(endpoint.copy());
            }
        }
    }

    private static boolean liesOnInterior(List<Vec2d> centerline, Vec2d point) {
        for (int i = 0; i < centerline.size() - 1; i++) {
            Vec2d start = centerline.get(i);
            Vec2d end = centerline.get(i + 1);
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(start, end, point);
            if (projected.distance(point) > INTERSECTION_EPSILON) {
                continue;
            }
            if (isNearAnyEndpoint(centerline, projected)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean isNearAnyEndpoint(List<Vec2d> centerline, Vec2d point) {
        if (centerline.isEmpty()) {
            return true;
        }
        if (centerline.size() == 1) {
            return RoadGeometryUtils.pointsNear(centerline.getFirst(), point, INTERSECTION_EPSILON);
        }
        return RoadGeometryUtils.pointsNear(centerline.getFirst(), point, INTERSECTION_EPSILON)
            || RoadGeometryUtils.pointsNear(centerline.getLast(), point, INTERSECTION_EPSILON);
    }

    private static RoadCrossing toCrossing(
            DetectionSession session,
            Road roadA,
            Road roadB,
            Vec2d position) {
        if (roadA == null || roadB == null) {
            return null;
        }
        OptionalDouble chainageA = RoadStationing.chainageAtPosition(
            session.network, roadA, position, session.samplesByRoadId);
        OptionalDouble chainageB = RoadStationing.chainageAtPosition(
            session.network, roadB, position, session.samplesByRoadId);
        if (chainageA.isEmpty() || chainageB.isEmpty()) {
            return null;
        }
        double stationA = chainageA.getAsDouble();
        double stationB = chainageB.getAsDouble();
        String aId = roadA.getId();
        String bId = roadB.getId();
        if (aId.compareTo(bId) > 0) {
            return RoadCrossing.atGrade(bId, stationB, aId, stationA, position);
        }
        return RoadCrossing.atGrade(aId, stationA, bId, stationB, position);
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

    private static final class DetectionSession {
        private final RoadNetwork network;
        private final Map<String, List<PlanCenterlineSample>> samplesByRoadId = new HashMap<>();

        private DetectionSession(RoadNetwork network) {
            this.network = network;
        }

        private List<Vec2d> planCenterline(Road road) {
            if (road == null) {
                return List.of();
            }
            if (RoadPlanGeometry.hasDesignAlignment(network, road)) {
                return samplesFor(road).stream().map(PlanCenterlineSample::position).toList();
            }
            return instancePlanCenterline(road);
        }

        private List<PlanCenterlineSample> samplesFor(Road road) {
            return samplesByRoadId.computeIfAbsent(
                road.getId(), id -> RoadPlanGeometry.resolveRoadCenterlineSamples(network, road));
        }

        private List<Vec2d> instancePlanCenterline(Road road) {
            List<Vec2d> merged = new ArrayList<>();
            for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
                appendJoined(merged, orientedSegmentPoints(segment));
            }
            return List.copyOf(merged);
        }

        private List<Vec2d> orientedSegmentPoints(OrientedRoadSegment segment) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                return List.of();
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            if (points.isEmpty()) {
                return List.of();
            }
            if (!segment.isPartialSlice()) {
                return segment.forward() ? List.copyOf(points) : reverseCopy(points);
            }
            List<Vec2d> slice = new ArrayList<>();
            double spacing = 0.25;
            for (double chainLocal = 0.0; chainLocal <= segment.length() + INTERSECTION_EPSILON; chainLocal += spacing) {
                double geometryLocal = segment.geometryLocalFromChainLocal(Math.min(chainLocal, segment.length()));
                Vec2d point = RoadGeometryUtils.pointAtDistance(points, geometryLocal);
                if (point == null) {
                    continue;
                }
                if (slice.isEmpty() || slice.getLast().distance(point) > INTERSECTION_EPSILON) {
                    slice.add(point);
                }
            }
            return List.copyOf(slice);
        }

        private static List<Vec2d> reverseCopy(List<Vec2d> points) {
            List<Vec2d> reversed = new ArrayList<>(points.size());
            for (int i = points.size() - 1; i >= 0; i--) {
                reversed.add(points.get(i));
            }
            return reversed;
        }

        private static void appendJoined(List<Vec2d> merged, List<Vec2d> points) {
            if (points.isEmpty()) {
                return;
            }
            if (merged.isEmpty()) {
                merged.addAll(points);
                return;
            }
            Vec2d last = merged.getLast();
            Vec2d first = points.getFirst();
            if (last.distance(first) <= INTERSECTION_EPSILON) {
                merged.addAll(points.subList(1, points.size()));
            } else {
                merged.addAll(points);
            }
        }
    }
}
