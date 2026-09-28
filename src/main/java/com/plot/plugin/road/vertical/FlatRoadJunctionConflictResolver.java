package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;

/** Detects and explicitly resolves conflicts between flat roads and shared junction elevations. */
public final class FlatRoadJunctionConflictResolver {
    private static final double EPSILON = 1e-6;

    public record Conflict(String roadId, String nodeId, double roadElevation, double junctionElevation) { }

    private FlatRoadJunctionConflictResolver() { }

    public static List<Conflict> find(RoadNetwork network) {
        if (network == null) return List.of();
        List<Conflict> conflicts = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            if (road.getVerticalMode() != RoadVerticalMode.FLAT
                    || !RoadStationing.isStationable(network, road)) continue;
            FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
            if (intent == null) continue;
            double flatElevation = intent.getBaseElevation();
            for (String nodeId : VerticalAlignmentJunctionSynchronizer
                    .junctionStations(network, road).keySet()) {
                RoadNode node = network.getNode(nodeId);
                if (node != null && node.getManualElevation() != null
                        && Math.abs(node.getManualElevation() - flatElevation) > EPSILON) {
                    conflicts.add(new Conflict(
                        road.getId(), nodeId, flatElevation, node.getManualElevation()));
                }
            }
        }
        return List.copyOf(conflicts);
    }

    /** Makes a flat road follow its junction only when all constrained junctions agree. */
    public static int makeRoadsFlatAtJunctionElevation(RoadNetwork network) {
        List<String> conflictRoadIds = find(network).stream().map(Conflict::roadId).distinct().toList();
        int changed = 0;
        for (String roadId : conflictRoadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) continue;
            if (road.getVerticalMode() != RoadVerticalMode.FLAT
                    || !RoadStationing.isStationable(network, road)) continue;
            Double target = null;
            boolean incompatible = false;
            for (String nodeId : VerticalAlignmentJunctionSynchronizer
                    .junctionStations(network, road).keySet()) {
                RoadNode node = network.getNode(nodeId);
                if (node == null || node.getManualElevation() == null) continue;
                if (target == null) target = node.getManualElevation();
                else if (Math.abs(target - node.getManualElevation()) > EPSILON) incompatible = true;
            }
            if (!incompatible && target != null) {
                FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
                if (intent == null) continue;
                intent.setBaseElevation(target);
                for (String nodeId : intent.getIntersectionOverrides().keySet()) {
                    intent.removeOverride(nodeId);
                }
                FlatVerticalIntentSupport.syncCompiledAlignment(
                    network, road, road.getMaxSlope() != null ? road.getMaxSlope() : 8.0);
                changed++;
            }
        }
        return changed;
    }

    /** Writes junction overrides so conflicting flat roads can meet at shared elevations. */
    public static int allowConflictingRoadsToSlope(RoadNetwork network) {
        List<String> roadIds = find(network).stream().map(Conflict::roadId).distinct().toList();
        int changed = 0;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) continue;
            for (Conflict conflict : find(network)) {
                if (!conflict.roadId().equals(roadId)) continue;
                FlatVerticalIntentSupport.applyJunctionElevation(
                    network,
                    road,
                    conflict.nodeId(),
                    conflict.junctionElevation(),
                    road.getMaxSlope() != null ? road.getMaxSlope() : 8.0);
            }
            changed++;
        }
        return changed;
    }
}
