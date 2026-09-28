package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Compiles {@link FlatVerticalIntent} into an evaluable {@link RoadVerticalAlignment}. */
public final class FlatProfileCompiler {

    private static final double EPSILON = 1e-6;

    private record JunctionConstraint(String nodeId, double station, double elevation) { }

    private record InfluenceInterval(double start, double end) { }

    private FlatProfileCompiler() {
    }

    public static RoadVerticalAlignment compile(
            RoadNetwork network,
            Road road,
            FlatVerticalIntent intent,
            double maxGradePercent) {
        if (network == null || road == null || intent == null) {
            return null;
        }
        double roadLength = RoadStationing.canonicalLength(network, road);
        if (!Double.isFinite(roadLength) || roadLength <= EPSILON) {
            return VerticalProfileDesignRules.flatAlignment(1.0, intent.getBaseElevation());
        }

        double base = intent.getBaseElevation();
        Map<String, Double> junctionStations = collectJunctionStations(network, road, intent);
        List<JunctionConstraint> constraints = new ArrayList<>();
        for (Map.Entry<String, Double> entry : junctionStations.entrySet()) {
            double elevation = resolveJunctionElevation(entry.getKey(), base, intent, network);
            if (Math.abs(elevation - base) > EPSILON || intent.hasOverride(entry.getKey())) {
                constraints.add(new JunctionConstraint(entry.getKey(), entry.getValue(), elevation));
            }
        }
        constraints.sort(Comparator.comparingDouble(JunctionConstraint::station));

        List<PointOfVerticalIntersection> pvis = new ArrayList<>();
        pvis.add(PointOfVerticalIntersection.of(0.0, base));
        double cursor = 0.0;
        for (List<JunctionConstraint> cluster
                : clusterConstraints(constraints, roadLength, base, maxGradePercent)) {
            cursor = compileCluster(pvis, cursor, roadLength, base, maxGradePercent, cluster);
        }
        if (roadLength > cursor + EPSILON) {
            appendFlatSegment(pvis, cursor, roadLength, base);
        } else if (!pvis.isEmpty()
                && Math.abs(pvis.getLast().getStation() - roadLength) > EPSILON) {
            mergeOrReplace(pvis, roadLength, base, false);
        }

        return new RoadVerticalAlignment(simplify(pvis));
    }

    private static List<List<JunctionConstraint>> clusterConstraints(
            List<JunctionConstraint> constraints,
            double roadLength,
            double base,
            double maxGradePercent) {
        List<List<JunctionConstraint>> clusters = new ArrayList<>();
        List<JunctionConstraint> current = null;
        InfluenceInterval currentInterval = null;
        for (JunctionConstraint constraint : constraints) {
            InfluenceInterval interval = influenceInterval(
                constraint, roadLength, base, maxGradePercent);
            if (current == null) {
                current = new ArrayList<>();
                current.add(constraint);
                currentInterval = interval;
                continue;
            }
            if (currentInterval.end() + EPSILON >= interval.start()) {
                current.add(constraint);
                currentInterval = new InfluenceInterval(
                    currentInterval.start(),
                    Math.max(currentInterval.end(), interval.end()));
            } else {
                clusters.add(current);
                current = new ArrayList<>();
                current.add(constraint);
                currentInterval = interval;
            }
        }
        if (current != null) {
            clusters.add(current);
        }
        return clusters;
    }

    private static InfluenceInterval influenceInterval(
            JunctionConstraint constraint,
            double roadLength,
            double base,
            double maxGradePercent) {
        double required = VerticalProfileDesignRules.requiredRunLength(
            Math.abs(constraint.elevation() - base), maxGradePercent);
        boolean atEnd = Math.abs(constraint.station() - roadLength) <= EPSILON;
        double runBefore = constraint.station();
        double runAfter = atEnd ? 0.0 : roadLength - constraint.station();
        double transitionIn = cappedTransitionLength(
            runBefore, Math.max(runAfter, runBefore), required);
        double transitionOut = atEnd
            ? 0.0
            : cappedTransitionLength(runAfter, runBefore, required);
        double start = Math.max(0.0, constraint.station() - transitionIn);
        double end = atEnd ? roadLength : Math.min(roadLength, constraint.station() + transitionOut);
        return new InfluenceInterval(start, end);
    }

    private static double compileCluster(
            List<PointOfVerticalIntersection> pvis,
            double cursor,
            double roadLength,
            double base,
            double maxGradePercent,
            List<JunctionConstraint> cluster) {
        if (cluster.isEmpty()) {
            return cursor;
        }
        JunctionConstraint first = cluster.getFirst();
        JunctionConstraint last = cluster.getLast();

        if (first.station() <= cursor + EPSILON) {
            mergeOrReplace(pvis, first.station(), first.elevation(), true);
            double nextCursor = Math.max(cursor, first.station());
            for (int i = 1; i < cluster.size(); i++) {
                JunctionConstraint constraint = cluster.get(i);
                mergeOrReplace(pvis, constraint.station(), constraint.elevation(), true);
                nextCursor = Math.max(nextCursor, constraint.station());
            }
            return nextCursor;
        }

        double runBefore = first.station() - cursor;
        double requiredFirst = VerticalProfileDesignRules.requiredRunLength(
            Math.abs(first.elevation() - base), maxGradePercent);
        double transitionIn = cappedTransitionLength(
            runBefore, Math.max(roadLength - first.station(), runBefore), requiredFirst);

        boolean lastAtEnd = Math.abs(last.station() - roadLength) <= EPSILON;
        double runAfter = lastAtEnd ? 0.0 : roadLength - last.station();
        double requiredLast = VerticalProfileDesignRules.requiredRunLength(
            Math.abs(last.elevation() - base), maxGradePercent);
        double transitionOut = lastAtEnd
            ? 0.0
            : cappedTransitionLength(runAfter, last.station() - cursor, requiredLast);

        double clusterStart = Math.max(cursor, first.station() - transitionIn);
        double clusterEnd = lastAtEnd ? roadLength : Math.min(roadLength, last.station() + transitionOut);

        if (clusterStart > cursor + EPSILON) {
            appendFlatSegment(pvis, cursor, clusterStart, base);
        }
        if (clusterStart + EPSILON < first.station()) {
            mergeOrReplace(pvis, clusterStart, base, false);
        }
        mergeOrReplace(pvis, first.station(), first.elevation(), true);
        for (int i = 1; i < cluster.size(); i++) {
            JunctionConstraint constraint = cluster.get(i);
            mergeOrReplace(pvis, constraint.station(), constraint.elevation(), true);
        }
        if (!lastAtEnd && clusterEnd > last.station() + EPSILON) {
            mergeOrReplace(pvis, clusterEnd, base, false);
            return clusterEnd;
        }
        return lastAtEnd ? roadLength : Math.max(cursor, last.station());
    }

    private static Map<String, Double> collectJunctionStations(
            RoadNetwork network,
            Road road,
            FlatVerticalIntent intent) {
        Map<String, Double> stations = new LinkedHashMap<>(
            VerticalAlignmentJunctionSynchronizer.junctionStations(network, road));
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            addGradeSeparatedStation(network, stations, segment.entryNodeId(), segment.startStation());
            addGradeSeparatedStation(network, stations, segment.exitNodeId(), segment.endStation());
        }
        if (intent != null) {
            for (String nodeId : intent.getIntersectionOverrides().keySet()) {
                stationAtNode(network, road, nodeId).ifPresent(station -> stations.putIfAbsent(nodeId, station));
            }
        }
        return stations;
    }

    private static OptionalDouble stationAtNode(RoadNetwork network, Road road, String nodeId) {
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            OptionalDouble station = segment.roadStationAtNode(nodeId);
            if (station.isPresent()) {
                return station;
            }
        }
        return OptionalDouble.empty();
    }

    private static void addGradeSeparatedStation(
            RoadNetwork network,
            Map<String, Double> stations,
            String nodeId,
            double station) {
        RoadNode node = network.getNode(nodeId);
        if (node != null && node.isGradeSeparated()) {
            stations.putIfAbsent(nodeId, station);
        }
    }

    static double resolveJunctionElevation(
            String nodeId,
            double base,
            FlatVerticalIntent intent,
            RoadNetwork network) {
        Double override = intent.getIntersectionOverride(nodeId);
        if (override != null) {
            return override;
        }
        RoadNode node = network.getNode(nodeId);
        if (node != null && node.getManualElevation() != null) {
            return node.getManualElevation();
        }
        return base;
    }

    private static double cappedTransitionLength(
            double incomingRun,
            double outgoingRun,
            double required) {
        double suggested = VerticalProfileDesignRules.suggestedTransitionLength(incomingRun, outgoingRun);
        double length = Math.max(suggested, required);
        if (!Double.isFinite(length) || length <= EPSILON) {
            return Math.min(incomingRun, Math.max(required, 0.0));
        }
        return Math.min(length, incomingRun);
    }

    private static void appendFlatSegment(
            List<PointOfVerticalIntersection> pvis,
            double fromStation,
            double toStation,
            double elevation) {
        if (toStation <= fromStation + EPSILON) {
            return;
        }
        mergeOrReplace(pvis, fromStation, elevation, false);
        mergeOrReplace(pvis, toStation, elevation, false);
    }

    private static void mergeOrReplace(
            List<PointOfVerticalIntersection> pvis,
            double station,
            double elevation,
            boolean junctionFixed) {
        for (int i = 0; i < pvis.size(); i++) {
            PointOfVerticalIntersection existing = pvis.get(i);
            if (Math.abs(existing.getStation() - station) <= EPSILON) {
                pvis.set(i, junctionFixed
                    ? new PointOfVerticalIntersection(
                        station, elevation, null, VerticalControlPointConstraint.JUNCTION_FIXED)
                    : PointOfVerticalIntersection.of(station, elevation));
                return;
            }
        }
        pvis.add(junctionFixed
            ? new PointOfVerticalIntersection(
                station, elevation, null, VerticalControlPointConstraint.JUNCTION_FIXED)
            : PointOfVerticalIntersection.of(station, elevation));
        pvis.sort(Comparator.comparingDouble(PointOfVerticalIntersection::getStation));
    }

    private static List<PointOfVerticalIntersection> simplify(List<PointOfVerticalIntersection> pvis) {
        if (pvis.size() < 2) {
            return pvis;
        }
        List<PointOfVerticalIntersection> simplified = new ArrayList<>();
        simplified.add(pvis.getFirst());
        for (int i = 1; i < pvis.size(); i++) {
            PointOfVerticalIntersection current = pvis.get(i);
            PointOfVerticalIntersection previous = simplified.getLast();
            boolean sameStation = Math.abs(previous.getStation() - current.getStation()) <= EPSILON;
            boolean sameElevation = Math.abs(previous.getElevation() - current.getElevation()) <= EPSILON;
            if (sameStation) {
                if (current.getConstraint() == VerticalControlPointConstraint.JUNCTION_FIXED) {
                    simplified.set(simplified.size() - 1, current);
                }
                continue;
            }
            if (sameElevation
                    && simplified.size() >= 2
                    && Math.abs(simplified.get(simplified.size() - 2).getElevation() - current.getElevation())
                        <= EPSILON) {
                simplified.removeLast();
            }
            simplified.add(current);
        }
        return simplified;
    }
}
