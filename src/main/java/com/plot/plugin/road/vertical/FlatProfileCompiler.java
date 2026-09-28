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

/** Compiles {@link FlatVerticalIntent} into an evaluable {@link RoadVerticalAlignment}. */
public final class FlatProfileCompiler {

    private static final double EPSILON = 1e-6;

    private record JunctionConstraint(String nodeId, double station, double elevation) { }

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
        Map<String, Double> junctionStations = collectJunctionStations(network, road);
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
        for (JunctionConstraint constraint : constraints) {
            if (constraint.station() <= cursor + EPSILON) {
                mergeOrReplace(pvis, constraint.station(), constraint.elevation(), true);
                continue;
            }
            if (Math.abs(constraint.elevation() - base) <= EPSILON) {
                appendFlatSegment(pvis, cursor, constraint.station(), base);
                cursor = constraint.station();
                continue;
            }
            double runBefore = constraint.station() - cursor;
            double runAfter = roadLength - constraint.station();
            double required = VerticalProfileDesignRules.requiredRunLength(
                Math.abs(constraint.elevation() - base), maxGradePercent);
            double transitionIn = cappedTransitionLength(runBefore, runAfter, required);
            double transitionOut = cappedTransitionLength(runAfter, runBefore, required);

            double rampStart = constraint.station() - transitionIn;
            double rampEnd = constraint.station() + transitionOut;
            if (rampStart > cursor + EPSILON) {
                appendFlatSegment(pvis, cursor, rampStart, base);
            }
            mergeOrReplace(
                pvis,
                constraint.station(),
                constraint.elevation(),
                true);
            if (rampEnd < roadLength - EPSILON) {
                appendFlatSegment(pvis, constraint.station(), rampEnd, base);
                cursor = rampEnd;
            } else {
                mergeOrReplace(pvis, roadLength, base, false);
                cursor = roadLength;
            }
        }
        if (roadLength > cursor + EPSILON) {
            appendFlatSegment(pvis, cursor, roadLength, base);
        } else if (!pvis.isEmpty()
                && Math.abs(pvis.getLast().getStation() - roadLength) > EPSILON) {
            mergeOrReplace(pvis, roadLength, base, false);
        }

        return new RoadVerticalAlignment(simplify(pvis));
    }

    private static Map<String, Double> collectJunctionStations(RoadNetwork network, Road road) {
        Map<String, Double> stations = new LinkedHashMap<>(
            VerticalAlignmentJunctionSynchronizer.junctionStations(network, road));
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            addGradeSeparatedStation(network, stations, segment.entryNodeId(), segment.startStation());
            addGradeSeparatedStation(network, stations, segment.exitNodeId(), segment.endStation());
        }
        return stations;
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
