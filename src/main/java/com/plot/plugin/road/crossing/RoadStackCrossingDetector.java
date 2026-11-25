package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * 检测单条道路 plan 中心线的真自交点（非相邻线段交叉）。
 */
public final class RoadStackCrossingDetector {
    private static final double INTERSECTION_EPSILON = RoadCrossingDetectionSupport.INTERSECTION_EPSILON;
    private static final double MIN_STATION_SEPARATION = 0.5;

    private RoadStackCrossingDetector() {
    }

    public static List<RoadStackCrossing> detectAll(RoadNetwork network) {
        if (network == null) {
            return List.of();
        }
        Map<String, RoadStackCrossing> deduped = new LinkedHashMap<>();
        RoadCrossingDetectionSupport.DetectionSession session =
            new RoadCrossingDetectionSupport.DetectionSession(network);
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
        return detectForRoad(new RoadCrossingDetectionSupport.DetectionSession(network), road);
    }

    /**
     * Groups of self-intersections that share the same plan position with three or more
     * distinct station passes. Materializers may not support these yet; they must not be dropped.
     */
    public static List<MultiLayerStackWarning> multiLayerWarnings(List<RoadStackCrossing> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        Map<String, List<RoadStackCrossing>> byPosition = new LinkedHashMap<>();
        for (RoadStackCrossing stack : stacks) {
            if (stack.position() == null) {
                continue;
            }
            String key = stack.roadId()
                + "|" + Math.round(stack.position().x * 100.0)
                + "," + Math.round(stack.position().y * 100.0);
            byPosition.computeIfAbsent(key, ignored -> new ArrayList<>()).add(stack);
        }
        List<MultiLayerStackWarning> warnings = new ArrayList<>();
        for (List<RoadStackCrossing> group : byPosition.values()) {
            Set<Long> stations = new LinkedHashSet<>();
            for (RoadStackCrossing stack : group) {
                stations.add(Math.round(stack.stationA() * 100.0));
                stations.add(Math.round(stack.stationB() * 100.0));
            }
            if (stations.size() >= 3 && group.size() >= 2) {
                RoadStackCrossing sample = group.getFirst();
                warnings.add(new MultiLayerStackWarning(
                    sample.roadId(),
                    sample.position().copy(),
                    stations.size(),
                    group.size()));
            }
        }
        return List.copyOf(warnings);
    }

    public record MultiLayerStackWarning(
            String roadId,
            Vec2d position,
            int passCount,
            int pairCount) {
    }

    private static List<RoadStackCrossing> detectForRoad(
            RoadCrossingDetectionSupport.DetectionSession session,
            Road road) {
        RoadCrossingDetectionSupport.CachedRoadGeometry geometry = session.geometryFor(road);
        if (!geometry.valid()) {
            return List.of();
        }
        List<Vec2d> centerline = geometry.points();
        if (centerline.size() < 4) {
            return List.of();
        }
        boolean isLoop = road.getTopologyMode() == RoadTopologyMode.LOOP;
        int lastSegmentIndex = centerline.size() - 2;
        double[] cumulative = cumulativeDistances(centerline);
        double detectionLoopLength = cumulative[centerline.size() - 1];
        double canonicalLoopLength = isLoop
            ? RoadStationing.canonicalLength(session.network(), road)
            : detectionLoopLength;
        Map<String, RoadStackCrossing> detected = new LinkedHashMap<>();
        for (int i = 0; i < centerline.size() - 1; i++) {
            Vec2d a1 = centerline.get(i);
            Vec2d a2 = centerline.get(i + 1);
            for (int j = i + 2; j < centerline.size() - 1; j++) {
                boolean wrapAdjacent = isLoop && i == 0 && j == lastSegmentIndex;
                Vec2d b1 = centerline.get(j);
                Vec2d b2 = centerline.get(j + 1);
                for (Vec2d hit : GeometryUtils.segmentIntersection(a1, a2, b1, b2)) {
                    if (wrapAdjacent && isLoopClosureEndpoint(centerline, hit)) {
                        // Topological first/last adjacency — not a real self-crossing.
                        continue;
                    }
                    if (RoadCrossingDetectionSupport.isPhysicalOpenEndpoint(
                            session.network(), road, hit)) {
                        continue;
                    }
                    double localA = localStation(cumulative, i, a1, a2, hit);
                    double localB = localStation(cumulative, j, b1, b2, hit);
                    if (stationSeparation(localA, localB, detectionLoopLength, isLoop)
                            < MIN_STATION_SEPARATION) {
                        continue;
                    }
                    OptionalDouble chainageA = session.toCanonicalChainage(
                        road, geometry, i, hit, localA);
                    OptionalDouble chainageB = session.toCanonicalChainage(
                        road, geometry, j, hit, localB);
                    if (chainageA.isEmpty() || chainageB.isEmpty()) {
                        continue;
                    }
                    double canonicalA = isLoop
                        ? RoadStackCrossing.normalizeLoopStation(
                            chainageA.getAsDouble(), canonicalLoopLength)
                        : chainageA.getAsDouble();
                    double canonicalB = isLoop
                        ? RoadStackCrossing.normalizeLoopStation(
                            chainageB.getAsDouble(), canonicalLoopLength)
                        : chainageB.getAsDouble();
                    if (stationSeparation(canonicalA, canonicalB, canonicalLoopLength, isLoop)
                            < MIN_STATION_SEPARATION) {
                        continue;
                    }
                    RoadStackCrossing stack = CrossingGradeClassifier.classifyStack(
                        session.network(),
                        road.getId(),
                        canonicalA,
                        canonicalB,
                        hit.copy());
                    detected.putIfAbsent(stack.stableKey(canonicalLoopLength, isLoop), stack);
                }
            }
        }
        return List.copyOf(detected.values());
    }

    /**
     * True when {@code hit} is only the normal LOOP closure vertex (first ≈ last).
     * Does not filter true self-crossings that happen to lie elsewhere on wrap-adjacent segments.
     */
    private static boolean isLoopClosureEndpoint(List<Vec2d> centerline, Vec2d hit) {
        if (centerline == null || centerline.size() < 2 || hit == null) {
            return false;
        }
        Vec2d first = centerline.getFirst();
        Vec2d last = centerline.getLast();
        boolean closed = RoadGeometryUtils.pointsNear(first, last, INTERSECTION_EPSILON);
        if (closed) {
            return RoadGeometryUtils.pointsNear(hit, first, INTERSECTION_EPSILON);
        }
        // Open polyline of a LOOP: first and last are still the physical join ends.
        return RoadGeometryUtils.pointsNear(hit, first, INTERSECTION_EPSILON)
            || RoadGeometryUtils.pointsNear(hit, last, INTERSECTION_EPSILON);
    }

    private static double stationSeparation(
            double stationA,
            double stationB,
            double loopLength,
            boolean isLoop) {
        double delta = Math.abs(stationA - stationB);
        if (!isLoop || loopLength <= INTERSECTION_EPSILON) {
            return delta;
        }
        return Math.min(delta, Math.max(0.0, loopLength - delta));
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
}
