package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;

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
            return segmentIds;
        }

        Map<String, RoadEdge> edgesById = new HashMap<>();
        Map<String, List<String>> nodeToEdgeIds = new HashMap<>();
        for (String segmentId : segmentIds) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                continue;
            }
            edgesById.put(segmentId, edge);
            nodeToEdgeIds.computeIfAbsent(edge.getStartNodeId(), ignored -> new ArrayList<>()).add(segmentId);
            nodeToEdgeIds.computeIfAbsent(edge.getEndNodeId(), ignored -> new ArrayList<>()).add(segmentId);
        }
        if (edgesById.isEmpty()) {
            return segmentIds;
        }

        String startNodeId = findChainStart(network, nodeToEdgeIds);
        if (startNodeId == null) {
            return segmentIds;
        }

        List<String> ordered = new ArrayList<>(edgesById.size());
        Set<String> visited = new HashSet<>();
        String currentNodeId = startNodeId;
        String currentEdgeId = firstUnvisitedEdge(nodeToEdgeIds.get(startNodeId), visited);

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
            currentEdgeId = firstUnvisitedEdge(nodeToEdgeIds.get(nextNodeId), visited);
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

    private static String findChainStart(RoadNetwork network, Map<String, List<String>> nodeToEdgeIds) {
        String endpoint = null;
        Vec2d endpointPos = null;
        for (Map.Entry<String, List<String>> entry : nodeToEdgeIds.entrySet()) {
            if (entry.getValue().size() != 1) {
                continue;
            }
            RoadNode node = network.getNode(entry.getKey());
            if (node == null || node.getPosition() == null) {
                continue;
            }
            Vec2d pos = node.getPosition();
            if (endpointPos == null || comparePosition(pos, endpointPos) < 0) {
                endpointPos = pos;
                endpoint = entry.getKey();
            }
        }
        if (endpoint != null) {
            return endpoint;
        }

        String fallback = null;
        Vec2d fallbackPos = null;
        for (String nodeId : nodeToEdgeIds.keySet()) {
            RoadNode node = network.getNode(nodeId);
            if (node == null || node.getPosition() == null) {
                continue;
            }
            Vec2d pos = node.getPosition();
            if (fallbackPos == null || comparePosition(pos, fallbackPos) < 0) {
                fallbackPos = pos;
                fallback = nodeId;
            }
        }
        return fallback;
    }

    private static int comparePosition(Vec2d left, Vec2d right) {
        int byX = Double.compare(left.x, right.x);
        return byX != 0 ? byX : Double.compare(left.y, right.y);
    }

    private static String firstUnvisitedEdge(List<String> edgeIds, Set<String> visited) {
        if (edgeIds == null) {
            return null;
        }
        for (String edgeId : edgeIds) {
            if (!visited.contains(edgeId)) {
                return edgeId;
            }
        }
        return null;
    }
}
