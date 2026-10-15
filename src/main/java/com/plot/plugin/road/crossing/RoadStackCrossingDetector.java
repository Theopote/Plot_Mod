package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * 检测单条道路 plan 中心线的真自交点（非相邻线段交叉）。
 */
public final class RoadStackCrossingDetector {
    private static final double INTERSECTION_EPSILON = 1e-5;
    private static final double DEDUP_TOLERANCE = INTERSECTION_EPSILON;
    private static final double MIN_STATION_SEPARATION = 0.5;

    private RoadStackCrossingDetector() {
    }

    public static List<RoadStackCrossing> detectAll(RoadNetwork network) {
        if (network == null) {
            return List.of();
        }
        Map<String, RoadStackCrossing> deduped = new LinkedHashMap<>();
        DetectionSession session = new DetectionSession(network);
        for (Road road : network.getRoads().values()) {
            for (RoadStackCrossing stack : detectForRoad(session, road)) {
                deduped.putIfAbsent(stack.stableKey(), stack);
            }
        }
        return List.copyOf(deduped.values());
    }

    public static List<RoadStackCrossing> detectForRoad(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return List.of();
        }
        return detectForRoad(new DetectionSession(network), road);
    }

    private static List<RoadStackCrossing> detectForRoad(DetectionSession session, Road road) {
        List<Vec2d> centerline = session.planCenterline(road);
        if (centerline.size() < 4) {
            return List.of();
        }
        double[] cumulative = cumulativeDistances(centerline);
        List<RoadStackCrossing> detected = new ArrayList<>();
        for (int i = 0; i < centerline.size() - 1; i++) {
            Vec2d a1 = centerline.get(i);
            Vec2d a2 = centerline.get(i + 1);
            for (int j = i + 2; j < centerline.size() - 1; j++) {
                Vec2d b1 = centerline.get(j);
                Vec2d b2 = centerline.get(j + 1);
                for (Vec2d hit : GeometryUtils.segmentIntersection(a1, a2, b1, b2)) {
                    if (isNearPolylineEndpoint(centerline, hit)) {
                        continue;
                    }
                    double localA = localStation(cumulative, i, a1, a2, hit);
                    double localB = localStation(cumulative, j, b1, b2, hit);
                    if (Math.abs(localA - localB) < MIN_STATION_SEPARATION) {
                        continue;
                    }
                    OptionalDouble chainageA = session.toCanonicalChainage(road, localA);
                    OptionalDouble chainageB = session.toCanonicalChainage(road, localB);
                    if (chainageA.isEmpty() || chainageB.isEmpty()) {
                        continue;
                    }
                    detected.add(RoadStackCrossing.atGrade(
                        road.getId(),
                        chainageA.getAsDouble(),
                        chainageB.getAsDouble(),
                        hit.copy()));
                }
            }
        }
        return deduplicate(detected);
    }

    private static double[] cumulativeDistances(List<Vec2d> centerline) {
        double[] cumulative = new double[centerline.size()];
        for (int i = 1; i < centerline.size(); i++) {
            cumulative[i] = cumulative[i - 1] + centerline.get(i - 1).distance(centerline.get(i));
        }
        return cumulative;
    }

    private static double localStation(
            double[] cumulative,
            int segmentIndex,
            Vec2d start,
            Vec2d end,
            Vec2d hit) {
        double segmentLength = start.distance(end);
        if (segmentLength <= INTERSECTION_EPSILON) {
            return cumulative[segmentIndex];
        }
        double along = start.distance(hit);
        return cumulative[segmentIndex] + along;
    }

    private static boolean isNearPolylineEndpoint(List<Vec2d> centerline, Vec2d point) {
        if (centerline.isEmpty()) {
            return true;
        }
        return RoadGeometryUtils.pointsNear(centerline.getFirst(), point, INTERSECTION_EPSILON)
            || RoadGeometryUtils.pointsNear(centerline.getLast(), point, INTERSECTION_EPSILON);
    }

    private static List<RoadStackCrossing> deduplicate(List<RoadStackCrossing> stacks) {
        List<RoadStackCrossing> unique = new ArrayList<>();
        for (RoadStackCrossing stack : stacks) {
            boolean exists = false;
            for (RoadStackCrossing existing : unique) {
                if (!stack.roadId().equals(existing.roadId())) {
                    continue;
                }
                if (stack.position() == null || existing.position() == null) {
                    continue;
                }
                if (RoadGeometryUtils.pointsNear(stack.position(), existing.position(), DEDUP_TOLERANCE)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                unique.add(stack);
            }
        }
        return unique;
    }

    private static final class DetectionSession {
        private final RoadNetwork network;
        private final Map<String, List<PlanCenterlineSample>> samplesByRoadId = new LinkedHashMap<>();

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
            List<Vec2d> merged = new ArrayList<>();
            for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
                appendJoined(merged, orientedSegmentPoints(segment));
            }
            return List.copyOf(merged);
        }

        private OptionalDouble toCanonicalChainage(Road road, double instanceChainage) {
            if (!RoadStationing.isStationable(network, road)) {
                return OptionalDouble.of(instanceChainage);
            }
            if (!Double.isFinite(instanceChainage)) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(
                RoadStationing.toCanonicalChainage(network, road, instanceChainage));
        }

        private List<PlanCenterlineSample> samplesFor(Road road) {
            return samplesByRoadId.computeIfAbsent(
                road.getId(), id -> RoadPlanGeometry.resolveRoadCenterlineSamples(network, road));
        }

        private List<Vec2d> orientedSegmentPoints(OrientedRoadSegment segment) {
            var edge = network.getEdge(segment.edgeId());
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
                reversed.add(points.get(i).copy());
            }
            return reversed;
        }

        private static void appendJoined(List<Vec2d> target, List<Vec2d> segment) {
            if (segment.isEmpty()) {
                return;
            }
            int startIndex = 0;
            if (!target.isEmpty()
                    && RoadGeometryUtils.pointsNear(target.getLast(), segment.getFirst(), INTERSECTION_EPSILON)) {
                startIndex = 1;
            }
            for (int i = startIndex; i < segment.size(); i++) {
                target.add(segment.get(i).copy());
            }
        }
    }
}
