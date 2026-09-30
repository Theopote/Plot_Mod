package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
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
        List<RoadEdge> edges = new ArrayList<>(network.getEdges().values());
        Map<String, RoadCrossing> deduped = new LinkedHashMap<>();
        for (int i = 0; i < edges.size(); i++) {
            RoadEdge edgeA = edges.get(i);
            for (int j = i + 1; j < edges.size(); j++) {
                RoadEdge edgeB = edges.get(j);
                if (edgeA.getRoadId() == null || edgeA.getRoadId().equals(edgeB.getRoadId())) {
                    continue;
                }
                for (Vec2d point : findSegmentIntersections(network, edgeA, edgeB)) {
                    RoadCrossing crossing = toCrossing(network, edgeA, edgeB, point);
                    if (crossing != null) {
                        deduped.putIfAbsent(dedupeKey(crossing), crossing);
                    }
                }
            }
        }
        return List.copyOf(deduped.values());
    }

    private static List<Vec2d> findSegmentIntersections(
            RoadNetwork network,
            RoadEdge edgeA,
            RoadEdge edgeB) {
        List<Vec2d> centerlineA = RoadPlanGeometry.resolveEdgeCenterline(network, edgeA);
        List<Vec2d> centerlineB = RoadPlanGeometry.resolveEdgeCenterline(network, edgeB);
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
            RoadNetwork network,
            RoadEdge edgeA,
            RoadEdge edgeB,
            Vec2d position) {
        Road roadA = network.getRoad(edgeA.getRoadId());
        Road roadB = network.getRoad(edgeB.getRoadId());
        if (roadA == null || roadB == null) {
            return null;
        }
        OptionalDouble chainageA = RoadStationing.chainageAtPosition(network, roadA, position);
        OptionalDouble chainageB = RoadStationing.chainageAtPosition(network, roadB, position);
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
}
