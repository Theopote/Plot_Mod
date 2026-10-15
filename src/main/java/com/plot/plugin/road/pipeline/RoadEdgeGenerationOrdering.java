package com.plot.plugin.road.pipeline;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Underpass edges generate before elevated edges so lower layers are finalized first.
 */
public final class RoadEdgeGenerationOrdering {
    private RoadEdgeGenerationOrdering() {
    }

    public static List<RoadEdge> sortForGeneration(RoadNetwork network, List<RoadEdge> edges) {
        if (network == null || edges == null || edges.isEmpty()) {
            return edges != null ? List.copyOf(edges) : List.of();
        }
        List<RoadEdge> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator
            .comparingInt((RoadEdge edge) -> priority(network, edge))
            .thenComparing(RoadEdge::getId, Comparator.nullsLast(String::compareTo)));
        return sorted;
    }

    private static int priority(RoadNetwork network, RoadEdge edge) {
        if (edge == null) {
            return 0;
        }
        int priority = 0;
        priority = Math.max(priority, endpointPriority(network, network.getNode(edge.getStartNodeId()), edge));
        priority = Math.max(priority, endpointPriority(network, network.getNode(edge.getEndNodeId()), edge));
        return priority;
    }

    private static int endpointPriority(RoadNetwork network, RoadNode node, RoadEdge edge) {
        if (node == null || edge == null) {
            return 0;
        }
        if (node.isStackGradeSeparated()) {
            return node.isStackUpperEdge(edge.getId()) ? 100 : 0;
        }
        if (!node.isGradeSeparated() || edge.getRoadId() == null) {
            return 0;
        }
        String elevatedRoadId = node.getElevatedRoadId();
        if (elevatedRoadId != null && !elevatedRoadId.isBlank()) {
            return elevatedRoadId.equals(edge.getRoadId()) ? 100 : 0;
        }
        return 0;
    }
}
