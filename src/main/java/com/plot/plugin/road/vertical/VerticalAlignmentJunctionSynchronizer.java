package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.OrientedRoadSegment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synchronizes manual profile PVIs with shared at-grade junction elevations. */
public final class VerticalAlignmentJunctionSynchronizer {
    private static final double STATION_TOLERANCE = 0.26;
    private VerticalAlignmentJunctionSynchronizer() { }

    public static int synchronize(RoadNetwork network, Road road) {
        if (!canSynchronize(network, road)) {
            return 0;
        }
        Map<String, Double> junctionStations = junctionStations(network, road);
        List<RoadNode> changedNodes = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : road.getVerticalAlignment().getPvis()) {
            for (Map.Entry<String, Double> entry : junctionStations.entrySet()) {
                if (Math.abs(pvi.getStation() - entry.getValue()) > STATION_TOLERANCE) continue;
                RoadNode node = network.getNode(entry.getKey());
                if (node == null) continue;
                Double previous = node.getManualElevation();
                node.setManualElevation(pvi.getElevation());
                if (previous == null || Math.abs(previous - node.getManualElevation()) > 1e-6) {
                    changedNodes.add(node);
                }
            }
        }
        int changed = changedNodes.size();
        for (RoadNode node : changedNodes) {
            for (String roadId : network.getDistinctRoadIdsAtNode(node.getId())) {
                Road connected = network.getRoad(roadId);
                if (connected != null && connected != road) {
                    changed += applySharedJunctionConstraints(network, connected);
                }
            }
        }
        return changed;
    }

    public static int applySharedJunctionConstraints(RoadNetwork network, Road road) {
        if (!canSynchronize(network, road) || road.getVerticalMode() != RoadVerticalMode.MANUAL_PROFILE) {
            return 0;
        }
        List<PointOfVerticalIntersection> pvis = new ArrayList<>(road.getVerticalAlignment().getPvis());
        int changed = 0;
        for (Map.Entry<String, Double> entry : junctionStations(network, road).entrySet()) {
            RoadNode node = network.getNode(entry.getKey());
            if (node == null || node.getManualElevation() == null) continue;
            int index = matchingPviIndex(pvis, entry.getValue());
            if (index >= 0) {
                PointOfVerticalIntersection old = pvis.get(index);
                if (Math.abs(old.getElevation() - node.getManualElevation()) > 1e-6) {
                    pvis.set(index, new PointOfVerticalIntersection(
                        old.getStation(), node.getManualElevation(), old.getCurveLength()));
                    changed++;
                }
            } else {
                pvis.add(PointOfVerticalIntersection.of(entry.getValue(), node.getManualElevation()));
                pvis.sort(java.util.Comparator.comparingDouble(PointOfVerticalIntersection::getStation));
                changed++;
            }
        }
        if (changed > 0) road.setVerticalAlignment(new RoadVerticalAlignment(pvis));
        return changed;
    }

    public static boolean isSharedJunctionAtStation(
            RoadNetwork network, Road road, double station) {
        return junctionStations(network, road).values().stream()
            .anyMatch(value -> Math.abs(value - station) <= STATION_TOLERANCE);
    }

    private static boolean canSynchronize(RoadNetwork network, Road road) {
        return network != null && road != null && road.getVerticalAlignment() != null
            && road.getVerticalAlignment().pviCount() >= 2
            && RoadStationing.isStationable(network, road);
    }

    static Map<String, Double> junctionStations(RoadNetwork network, Road road) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (network == null || road == null || !RoadStationing.isStationable(network, road)) return result;
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            addJunction(network, result, segment.entryNodeId(), segment.startStation());
            addJunction(network, result, segment.exitNodeId(), segment.endStation());
        }
        return result;
    }

    private static void addJunction(
            RoadNetwork network, Map<String, Double> result, String nodeId, double station) {
        RoadNode node = network.getNode(nodeId);
        if (node != null && node.isJunction() && !node.isGradeSeparated()) {
            result.putIfAbsent(nodeId, station);
        }
    }

    static int matchingPviIndex(List<PointOfVerticalIntersection> pvis, double station) {
        for (int i = 0; i < pvis.size(); i++) {
            if (Math.abs(pvis.get(i).getStation() - station) <= STATION_TOLERANCE) return i;
        }
        return -1;
    }
}
