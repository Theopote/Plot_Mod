package com.plot.plugin.road.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 校验单条 {@link Road} 是否满足拓扑不变量。
 * <p>
 * 当前策略：所有 Road 隐含 {@link RoadTopologyMode#LINEAR} — 必须连通、无分叉；
 * 闭合环与存储顺序问题仅报告 warning，不阻断操作。
 *
 * @see docs/decisions/0004-road-topology-invariant.md
 */
public final class RoadTopologyInvariantValidator {

    private RoadTopologyInvariantValidator() {
    }

    public static List<RoadTopologyViolation> validate(RoadNetwork network) {
        if (network == null) {
            return List.of();
        }
        List<RoadTopologyViolation> violations = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            violations.addAll(validateRoad(network, road));
        }
        return List.copyOf(violations);
    }

    public static List<RoadTopologyViolation> validateRoad(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return List.of();
        }
        List<RoadTopologyViolation> violations = new ArrayList<>();
        String roadId = road.getId();

        RoadSubgraph subgraph = RoadSubgraph.build(network, road);
        if (subgraph.edgeIds().size() <= 1) {
            return List.of();
        }

        if (subgraph.componentCount() > 1) {
            violations.add(new RoadTopologyViolation(roadId, RoadTopologyViolationKind.ROAD_DISCONNECTED));
            return List.copyOf(violations);
        }

        if (subgraph.hasBranching()) {
            violations.add(new RoadTopologyViolation(roadId, RoadTopologyViolationKind.ROAD_BRANCHING));
            return List.copyOf(violations);
        }

        if (subgraph.endpointCount() == 0) {
            violations.add(new RoadTopologyViolation(roadId, RoadTopologyViolationKind.ROAD_CYCLE));
        }

        if (hasOrderMismatch(network, road, subgraph)) {
            violations.add(new RoadTopologyViolation(roadId, RoadTopologyViolationKind.ROAD_ORDER_MISMATCH));
        }

        return List.copyOf(violations);
    }

    public static Map<RoadTopologyViolationKind, Integer> countByKind(RoadNetwork network) {
        EnumMap<RoadTopologyViolationKind, Integer> counts = new EnumMap<>(RoadTopologyViolationKind.class);
        for (RoadTopologyViolationKind kind : RoadTopologyViolationKind.values()) {
            counts.put(kind, 0);
        }
        for (RoadTopologyViolation violation : validate(network)) {
            counts.merge(violation.kind(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    public static int countRoadsWithShapeViolations(RoadNetwork network) {
        if (network == null) {
            return 0;
        }
        Set<String> roadIds = new HashSet<>();
        for (RoadTopologyViolation violation : validate(network)) {
            if (violation.kind() == RoadTopologyViolationKind.ROAD_ORDER_MISMATCH) {
                continue;
            }
            roadIds.add(violation.roadId());
        }
        return roadIds.size();
    }

    private static boolean hasOrderMismatch(RoadNetwork network, Road road, RoadSubgraph subgraph) {
        if (!subgraph.isChainLike()) {
            return false;
        }
        List<String> stored = road.getOrderedSegmentIds();
        List<String> topological = RoadSegmentOrdering.orderedSegmentIds(network, road);
        return !stored.equals(topological);
    }

    private record RoadSubgraph(
            Set<String> edgeIds,
            Map<String, List<String>> nodeToEdgeIds,
            int componentCount,
            int endpointCount,
            boolean hasBranching) {

        static RoadSubgraph build(RoadNetwork network, Road road) {
            Map<String, List<String>> nodeToEdgeIds = new HashMap<>();
            Set<String> edgeIds = new HashSet<>();
            for (String segmentId : road.getOrderedSegmentIds()) {
                RoadEdge edge = network.getEdge(segmentId);
                if (edge == null) {
                    continue;
                }
                edgeIds.add(segmentId);
                nodeToEdgeIds
                    .computeIfAbsent(edge.getStartNodeId(), ignored -> new ArrayList<>())
                    .add(segmentId);
                nodeToEdgeIds
                    .computeIfAbsent(edge.getEndNodeId(), ignored -> new ArrayList<>())
                    .add(segmentId);
            }

            int componentCount = connectedComponentCount(nodeToEdgeIds, edgeIds, network);
            int endpoints = 0;
            boolean hasBranching = false;
            for (List<String> incident : nodeToEdgeIds.values()) {
                int degree = 0;
                for (String edgeId : incident) {
                    if (edgeIds.contains(edgeId)) {
                        degree++;
                    }
                }
                if (degree > 2) {
                    hasBranching = true;
                } else if (degree == 1) {
                    endpoints++;
                }
            }

            return new RoadSubgraph(
                edgeIds,
                nodeToEdgeIds,
                componentCount,
                endpoints,
                hasBranching
            );
        }

        boolean isChainLike() {
            return componentCount <= 1 && !hasBranching;
        }

        private static int connectedComponentCount(
                Map<String, List<String>> nodeToEdgeIds,
                Set<String> roadEdgeIds,
                RoadNetwork network) {
            Set<String> visitedEdges = new HashSet<>();
            int components = 0;
            for (String seedEdgeId : roadEdgeIds) {
                if (visitedEdges.contains(seedEdgeId)) {
                    continue;
                }
                components++;
                bfsComponent(seedEdgeId, nodeToEdgeIds, roadEdgeIds, network, visitedEdges);
            }
            return components;
        }

        private static void bfsComponent(
                String seedEdgeId,
                Map<String, List<String>> nodeToEdgeIds,
                Set<String> roadEdgeIds,
                RoadNetwork network,
                Set<String> visitedEdges) {
            Queue<String> queue = new ArrayDeque<>();
            queue.add(seedEdgeId);
            visitedEdges.add(seedEdgeId);

            while (!queue.isEmpty()) {
                String edgeId = queue.poll();
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                for (String nodeId : List.of(edge.getStartNodeId(), edge.getEndNodeId())) {
                    List<String> incident = nodeToEdgeIds.get(nodeId);
                    if (incident == null) {
                        continue;
                    }
                    for (String nextEdgeId : incident) {
                        if (!roadEdgeIds.contains(nextEdgeId) || visitedEdges.contains(nextEdgeId)) {
                            continue;
                        }
                        visitedEdges.add(nextEdgeId);
                        queue.add(nextEdgeId);
                    }
                }
            }
        }
    }
}
