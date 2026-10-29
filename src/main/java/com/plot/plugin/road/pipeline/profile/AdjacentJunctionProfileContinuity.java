package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects abnormal elevation cliffs on edges that directly connect two junction nodes
 * (degree ≥ 3). Used as a post-resolve check so preview/build can warn or block.
 */
public final class AdjacentJunctionProfileContinuity {
    private AdjacentJunctionProfileContinuity() {
    }

    public record Violation(
            String edgeId,
            String fromNodeId,
            String toNodeId,
            int fromY,
            int toY,
            int maxRiseBlocks,
            int actualRiseBlocks,
            boolean blocking) {
        public String detail() {
            return "adjacent junctions " + fromNodeId + "@" + fromY
                + " → " + toNodeId + "@" + toY
                + " rise=" + actualRiseBlocks + " > budget=" + maxRiseBlocks
                + " on edge " + edgeId
                + (blocking ? " (soft nodes; continuity failed)" : " (hard locks retained)");
        }
    }

    public static List<Violation> find(
            RoadNetwork network,
            Map<String, Integer> elevations,
            RoadSystemConfig config) {
        List<Violation> violations = new ArrayList<>();
        if (network == null || elevations == null || elevations.isEmpty()) {
            return violations;
        }
        float fallbackSlope = config != null ? config.getMaxSlope() : 8.0f;
        for (RoadEdge edge : network.getEdges().values()) {
            RoadNode start = network.getNode(edge.getStartNodeId());
            RoadNode end = network.getNode(edge.getEndNodeId());
            if (start == null || end == null) {
                continue;
            }
            if (start.getDegree() < 3 || end.getDegree() < 3) {
                continue;
            }
            Integer startY = elevations.get(start.getId());
            Integer endY = elevations.get(end.getId());
            if (startY == null || endY == null) {
                continue;
            }
            double lengthBlocks = Math.max(1.0, edge.getLength());
            Road road = network.getRoad(edge.getRoadId());
            float maxSlopePercent = road != null
                ? road.getEffectiveMaxSlope(config != null ? config : new RoadSystemConfig("adj"))
                : fallbackSlope;
            int maxRise = Math.max(1, (int) Math.ceil(lengthBlocks * maxSlopePercent / 100.0 - 1e-9));
            int actualRise = Math.abs(startY - endY);
            if (actualRise <= maxRise) {
                continue;
            }
            boolean blocking = !isHardElevationAnchor(network, start, road)
                && !isHardElevationAnchor(network, end, road);
            violations.add(new Violation(
                edge.getId(),
                start.getId(),
                end.getId(),
                startY,
                endY,
                maxRise,
                actualRise,
                blocking));
        }
        return violations;
    }

    private static boolean isHardElevationAnchor(RoadNetwork network, RoadNode node, Road edgeRoad) {
        if (node.getManualElevation() != null) {
            return true;
        }
        if (edgeRoad != null && isDesignedRoad(network, edgeRoad)) {
            return true;
        }
        for (String roadId : network.getDistinctRoadIdsAtNode(node.getId())) {
            Road road = network.getRoad(roadId);
            if (road != null && isDesignedRoad(network, road)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDesignedRoad(RoadNetwork network, Road road) {
        return road.getVerticalMode() == RoadVerticalMode.FLAT
            || ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road);
    }
}
