package com.plot.plugin.road.model;

import com.plot.plugin.road.station.RoadDesignDirectionAuthority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 沿道路拓扑链对分段排序。
 * <p>
 * {@link Road} 内部用有序列表保存分段；求交打断、拆分、重分配后存储顺序可能与几何链不一致。
 * UI、split、里程等逻辑应使用本类，而非 {@link Road#getSegmentIds()}。
 * <p>
 * Design-chain start uses persisted {@link Road#getDesignEntryNodeId()} /
 * {@link Road#getDesignEntryEdgeId()} via {@link RoadDesignDirectionAuthority}.
 */
public final class RoadSegmentOrdering {

    private RoadSegmentOrdering() {
    }

    public static List<String> orderedSegmentIds(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return List.of();
        }
        List<String> segmentIds = new ArrayList<>(road.getOrderedSegmentIds());
        if (segmentIds.size() <= 1) {
            if (segmentIds.size() == 1) {
                RoadDesignDirectionAuthority.ensureBound(network, road);
            }
            return segmentIds;
        }

        Map<String, RoadEdge> edgesById = new HashMap<>();
        for (String segmentId : segmentIds) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge != null) {
                edgesById.put(segmentId, edge);
            }
        }
        if (edgesById.isEmpty()) {
            return segmentIds;
        }

        RoadDesignDirectionAuthority.ResolvedStart start =
            RoadDesignDirectionAuthority.resolveStart(network, road);
        if (start.entryNodeId() == null) {
            return segmentIds;
        }

        List<String> ordered = new ArrayList<>(edgesById.size());
        Set<String> visited = new HashSet<>();
        var adjacency = start.nodeToEdgeIds();
        String currentNodeId = start.entryNodeId();
        String currentEdgeId = RoadDesignDirectionAuthority.firstEdgeAtNode(
            adjacency, currentNodeId, start.preferredEntryEdgeId(), visited);

        while (currentEdgeId != null) {
            visited.add(currentEdgeId);
            ordered.add(currentEdgeId);
            RoadEdge edge = edgesById.get(currentEdgeId);
            if (edge == null) {
                break;
            }
            String nextNodeId = edge.getStartNodeId().equals(currentNodeId)
                ? edge.getEndNodeId()
                : edge.getStartNodeId();
            currentNodeId = nextNodeId;
            currentEdgeId = RoadDesignDirectionAuthority.firstEdgeAtNode(
                adjacency, currentNodeId, null, visited);
        }

        for (String segmentId : segmentIds) {
            if (!visited.contains(segmentId)) {
                ordered.add(segmentId);
            }
        }
        return ordered;
    }

    public static void applyTopologicalOrder(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return;
        }
        road.reorderSegments(orderedSegmentIds(network, road));
    }

    /** 对网络内全部逻辑道路同步拓扑链顺序到 {@link Road#getOrderedSegmentIds()} 存储。 */
    public static void applyTopologicalOrderToAllRoads(RoadNetwork network) {
        if (network == null) {
            return;
        }
        for (Road road : network.getRoads().values()) {
            applyTopologicalOrder(network, road);
        }
    }
}
