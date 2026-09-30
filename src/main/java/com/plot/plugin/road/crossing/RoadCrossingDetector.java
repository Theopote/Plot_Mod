package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 检测道路 centerline 的真实线段交点（不含端点邻近启发）。 */
public final class RoadCrossingDetector {
    private static final double NODE_TOLERANCE = 0.5;

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
                for (Vec2d point : findSegmentIntersections(edgeA, edgeB)) {
                    RoadCrossing crossing = toCrossing(network, edgeA, edgeB, point);
                    if (crossing != null) {
                        deduped.putIfAbsent(dedupeKey(crossing), crossing);
                    }
                }
            }
        }
        return List.copyOf(deduped.values());
    }

    private static List<Vec2d> findSegmentIntersections(RoadEdge edgeA, RoadEdge edgeB) {
        PolylineShape polyA = new PolylineShape(edgeA.getCenterlinePoints(), false);
        PolylineShape polyB = new PolylineShape(edgeB.getCenterlinePoints(), false);
        List<Vec2d> raw = polyA.getIntersectionsWith(polyB);
        List<Vec2d> filtered = new ArrayList<>();
        for (Vec2d point : raw) {
            if (isNearAnyEndpoint(edgeA, point) && isNearAnyEndpoint(edgeB, point)) {
                continue;
            }
            filtered.add(point.copy());
        }
        return deduplicatePoints(filtered);
    }

    private static boolean isNearAnyEndpoint(RoadEdge edge, Vec2d point) {
        List<Vec2d> points = edge.getCenterlinePoints();
        if (points.isEmpty()) {
            return true;
        }
        if (points.size() == 1) {
            return RoadGeometryUtils.pointsNear(points.getFirst(), point, NODE_TOLERANCE);
        }
        return RoadGeometryUtils.pointsNear(points.getFirst(), point, NODE_TOLERANCE)
            || RoadGeometryUtils.pointsNear(points.getLast(), point, NODE_TOLERANCE);
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
        Double stationA = stationAtPosition(network, roadA, position);
        Double stationB = stationAtPosition(network, roadB, position);
        if (stationA == null || stationB == null) {
            return null;
        }
        String aId = roadA.getId();
        String bId = roadB.getId();
        if (aId.compareTo(bId) > 0) {
            return RoadCrossing.atGrade(bId, stationB, aId, stationA, position);
        }
        return RoadCrossing.atGrade(aId, stationA, bId, stationB, position);
    }

    private static Double stationAtPosition(RoadNetwork network, Road road, Vec2d position) {
        double bestDistance = Double.MAX_VALUE;
        Double bestStation = null;
        for (var segment : RoadStationing.orientedSegments(network, road)) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                continue;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d start = points.get(i);
                Vec2d end = points.get(i + 1);
                Vec2d projected = RoadGeometryUtils.projectPointOnSegment(start, end, position);
                double distance = projected.distance(position);
                if (distance > NODE_TOLERANCE || distance >= bestDistance) {
                    continue;
                }
                double geometryLocal = start.distance(projected);
                for (int j = 0; j < i; j++) {
                    geometryLocal += points.get(j).distance(points.get(j + 1));
                }
                bestDistance = distance;
                bestStation = segment.roadStationAtGeometryLocal(geometryLocal);
            }
        }
        return bestStation;
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
                if (RoadGeometryUtils.pointsNear(existing, point, NODE_TOLERANCE)) {
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
