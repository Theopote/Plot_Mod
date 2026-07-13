package com.plot.plugin.road.graph;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 道路网络拓扑只读查询（连通分量、断头路、路口等）。
 */
public final class RoadGraphQueries {
    private final RoadNetwork network;

    private RoadGraphQueries(RoadNetwork network) {
        this.network = network;
    }

    public static RoadGraphQueries of(RoadNetwork network) {
        return new RoadGraphQueries(network);
    }

    public List<GraphComponent> connectedComponents() {
        Set<String> visitedNodes = new HashSet<>();
        List<GraphComponent> components = new ArrayList<>();

        for (RoadNode node : network.getNodes().values()) {
            if (visitedNodes.contains(node.getId())) {
                continue;
            }
            Set<String> componentNodes = new LinkedHashSet<>();
            Set<String> componentEdges = new LinkedHashSet<>();
            collectComponent(node.getId(), visitedNodes, componentNodes, componentEdges);
            if (!componentNodes.isEmpty()) {
                components.add(new GraphComponent(
                    List.copyOf(componentNodes),
                    List.copyOf(componentEdges)
                ));
            }
        }
        return components;
    }

    public GraphComponent componentContaining(String nodeId) {
        if (nodeId == null || network.getNode(nodeId) == null) {
            return new GraphComponent(List.of(), List.of());
        }
        Set<String> visitedNodes = new HashSet<>();
        Set<String> componentNodes = new LinkedHashSet<>();
        Set<String> componentEdges = new LinkedHashSet<>();
        collectComponent(nodeId, visitedNodes, componentNodes, componentEdges);
        return new GraphComponent(List.copyOf(componentNodes), List.copyOf(componentEdges));
    }

    public List<String> distinctRoadIdsAtNode(String nodeId) {
        return List.copyOf(network.getDistinctRoadIdsAtNode(nodeId));
    }

    public List<RoadEdge> edgesAtNode(String nodeId) {
        return network.getEdgesAtNode(nodeId);
    }

    public List<RoadNode> deadEndNodes() {
        List<RoadNode> deadEnds = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() == 1) {
                deadEnds.add(node);
            }
        }
        return Collections.unmodifiableList(deadEnds);
    }

    public List<RoadNode> junctionNodes() {
        List<RoadNode> junctions = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() >= 3) {
                junctions.add(node);
            }
        }
        return Collections.unmodifiableList(junctions);
    }

    public List<RoadNode> throughNodes() {
        List<RoadNode> through = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node.getDegree() == 2) {
                through.add(node);
            }
        }
        return Collections.unmodifiableList(through);
    }

    public List<List<String>> findCycles() {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visitedEdges = new HashSet<>();

        for (RoadEdge edge : network.getEdges().values()) {
            if (visitedEdges.contains(edge.getId())) {
                continue;
            }
            List<String> cycle = findCycleFromEdge(edge.getId(), visitedEdges);
            if (cycle != null && cycle.size() >= 3) {
                cycles.add(cycle);
            }
        }
        return Collections.unmodifiableList(cycles);
    }

    public Optional<GraphPath> shortestPath(String startNodeId, String endNodeId) {
        if (startNodeId == null || endNodeId == null) {
            return Optional.empty();
        }
        if (startNodeId.equals(endNodeId)) {
            return Optional.of(new GraphPath(List.of(startNodeId), List.of(), 0.0));
        }
        if (network.getNode(startNodeId) == null || network.getNode(endNodeId) == null) {
            return Optional.empty();
        }

        Map<String, Double> bestDistance = new HashMap<>();
        Map<String, String> previousNode = new HashMap<>();
        Map<String, String> previousEdge = new HashMap<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));

        bestDistance.put(startNodeId, 0.0);
        queue.add(new NodeDistance(startNodeId, 0.0));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            if (current.distance > bestDistance.getOrDefault(current.nodeId, Double.POSITIVE_INFINITY)) {
                continue;
            }
            if (current.nodeId.equals(endNodeId)) {
                break;
            }

            RoadNode node = network.getNode(current.nodeId);
            if (node == null) {
                continue;
            }
            for (String edgeId : node.getConnectedEdgeIds()) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                String neighborId = edge.getStartNodeId().equals(current.nodeId)
                    ? edge.getEndNodeId()
                    : edge.getStartNodeId();
                double candidate = current.distance + Math.max(0.0, edge.getLength());
                if (candidate >= bestDistance.getOrDefault(neighborId, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                bestDistance.put(neighborId, candidate);
                previousNode.put(neighborId, current.nodeId);
                previousEdge.put(neighborId, edgeId);
                queue.add(new NodeDistance(neighborId, candidate));
            }
        }

        if (!previousNode.containsKey(endNodeId) && !startNodeId.equals(endNodeId)) {
            return Optional.empty();
        }

        List<String> nodePath = new ArrayList<>();
        List<String> edgePath = new ArrayList<>();
        String cursor = endNodeId;
        nodePath.add(cursor);
        while (!cursor.equals(startNodeId)) {
            String edgeId = previousEdge.get(cursor);
            if (edgeId != null) {
                edgePath.addFirst(edgeId);
            }
            cursor = previousNode.get(cursor);
            if (cursor == null) {
                return Optional.empty();
            }
            nodePath.addFirst(cursor);
        }
        return Optional.of(new GraphPath(
            List.copyOf(nodePath),
            List.copyOf(edgePath),
            bestDistance.getOrDefault(endNodeId, 0.0)
        ));
    }

    private record NodeDistance(String nodeId, double distance) {
    }

    private void collectComponent(
            String startNodeId,
            Set<String> visitedNodes,
            Set<String> componentNodes,
            Set<String> componentEdges) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        visitedNodes.add(startNodeId);

        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            componentNodes.add(nodeId);
            RoadNode node = network.getNode(nodeId);
            if (node == null) {
                continue;
            }
            for (String edgeId : node.getConnectedEdgeIds()) {
                componentEdges.add(edgeId);
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                String neighborId = edge.getStartNodeId().equals(nodeId)
                    ? edge.getEndNodeId()
                    : edge.getStartNodeId();
                if (visitedNodes.add(neighborId)) {
                    queue.add(neighborId);
                }
            }
        }
    }

    private List<String> findCycleFromEdge(String startEdgeId, Set<String> visitedEdges) {
        RoadEdge startEdge = network.getEdge(startEdgeId);
        if (startEdge == null) {
            return null;
        }

        String anchorNodeId = startEdge.getStartNodeId();
        String currentNodeId = startEdge.getEndNodeId();
        String incomingEdgeId = startEdge.getId();
        visitedEdges.add(incomingEdgeId);

        List<String> pathNodes = new ArrayList<>();
        pathNodes.add(anchorNodeId);
        pathNodes.add(currentNodeId);

        int guard = network.getEdges().size() + 2;
        while (guard-- > 0) {
            RoadEdge nextEdge = pickNextEdge(currentNodeId, incomingEdgeId, visitedEdges);
            if (nextEdge == null) {
                return null;
            }
            visitedEdges.add(nextEdge.getId());
            incomingEdgeId = nextEdge.getId();
            currentNodeId = nextEdge.getStartNodeId().equals(pathNodes.getLast())
                ? nextEdge.getEndNodeId()
                : nextEdge.getStartNodeId();

            if (currentNodeId.equals(anchorNodeId)) {
                return List.copyOf(pathNodes);
            }
            if (pathNodes.contains(currentNodeId)) {
                return null;
            }
            pathNodes.add(currentNodeId);
        }
        return null;
    }

    private RoadEdge pickNextEdge(String nodeId, String excludeEdgeId, Set<String> visitedEdges) {
        RoadNode node = network.getNode(nodeId);
        if (node == null) {
            return null;
        }
        RoadEdge candidate = null;
        for (String edgeId : node.getConnectedEdgeIds()) {
            if (edgeId.equals(excludeEdgeId) || visitedEdges.contains(edgeId)) {
                continue;
            }
            if (candidate != null) {
                return null;
            }
            candidate = network.getEdge(edgeId);
        }
        return candidate;
    }

    public record GraphComponent(List<String> nodeIds, List<String> edgeIds) {
        public GraphComponent {
            nodeIds = List.copyOf(nodeIds);
            edgeIds = List.copyOf(edgeIds);
        }
    }

    public record GraphPath(List<String> nodeIds, List<String> edgeIds, double length) {
        public GraphPath {
            nodeIds = List.copyOf(nodeIds);
            edgeIds = List.copyOf(edgeIds);
        }
    }
}
