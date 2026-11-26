package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persisted design-direction binding for a {@link Road}.
 * <p>
 * Coordinate heuristics only initialize missing/invalid bindings; subsequent reads use the
 * stored entry node (and LOOP entry edge) and do not re-elect direction.
 */
public final class RoadDesignDirectionAuthority {

    private RoadDesignDirectionAuthority() {
    }

    /**
     * Ensures the road has a valid design-direction binding, initializing deterministically if needed.
     *
     * @return entry node id used for chain walk, or {@code null} if the road cannot be chained
     */
    public static String ensureBound(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return null;
        }
        Map<String, List<String>> nodeToEdgeIds = nodeToEdgeIds(network, road);
        if (nodeToEdgeIds.isEmpty()) {
            return null;
        }
        if (isBindingValid(network, road, nodeToEdgeIds)) {
            return road.getDesignEntryNodeId();
        }
        String entryNodeId = deterministicEntryNodeId(network, nodeToEdgeIds);
        if (entryNodeId == null) {
            return null;
        }
        String entryEdgeId = firstEdgeAtNode(nodeToEdgeIds.get(entryNodeId));
        road.setDesignEntryNodeId(entryNodeId);
        road.setDesignEntryEdgeId(
            road.getTopologyMode() == RoadTopologyMode.LOOP ? entryEdgeId : entryEdgeId);
        return entryNodeId;
    }

    public static boolean isBindingValid(
            RoadNetwork network,
            Road road,
            Map<String, List<String>> nodeToEdgeIds) {
        if (road == null || network == null) {
            return false;
        }
        String entryNodeId = road.getDesignEntryNodeId();
        if (entryNodeId == null || entryNodeId.isBlank() || !nodeToEdgeIds.containsKey(entryNodeId)) {
            return false;
        }
        if (network.getNode(entryNodeId) == null) {
            return false;
        }
        String entryEdgeId = road.getDesignEntryEdgeId();
        if (entryEdgeId == null || entryEdgeId.isBlank()) {
            return false;
        }
        RoadEdge edge = network.getEdge(entryEdgeId);
        if (edge == null || !road.getSegmentIds().contains(entryEdgeId)) {
            return false;
        }
        if (!entryNodeId.equals(edge.getStartNodeId()) && !entryNodeId.equals(edge.getEndNodeId())) {
            return false;
        }
        // LINEAR design entry must remain a chain endpoint. Binding created on the first edge
        // would otherwise stick after later edges make that node interior (broken chain walks).
        if (road.getTopologyMode() != RoadTopologyMode.LOOP) {
            List<String> atEntry = nodeToEdgeIds.get(entryNodeId);
            if (atEntry == null || atEntry.size() != 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves the chain-start for traversal without mutating the model.
     * Prefer persisted binding when valid; otherwise return a deterministic start for this read
     * (call {@link #ensureBound} from create/edit/repair/preflight to persist).
     */
    public static ResolvedStart resolveStart(RoadNetwork network, Road road) {
        Map<String, List<String>> nodeToEdgeIds = nodeToEdgeIds(network, road);
        if (nodeToEdgeIds.isEmpty()) {
            return new ResolvedStart(null, null, nodeToEdgeIds);
        }
        if (isBindingValid(network, road, nodeToEdgeIds)) {
            String preferredEdge = road.getDesignEntryEdgeId();
            if (preferredEdge != null && network.getEdge(preferredEdge) == null) {
                preferredEdge = null;
            }
            return new ResolvedStart(road.getDesignEntryNodeId(), preferredEdge, nodeToEdgeIds);
        }
        String entryNodeId = deterministicEntryNodeId(network, nodeToEdgeIds);
        if (entryNodeId == null) {
            return new ResolvedStart(null, null, nodeToEdgeIds);
        }
        return new ResolvedStart(entryNodeId, firstEdgeAtNode(nodeToEdgeIds.get(entryNodeId)), nodeToEdgeIds);
    }

    /**
     * Flips design-direction binding for Reverse Road after geometry/order have been reversed.
     * Call with the network state <em>after</em> edge storage reverse + segment reorder.
     */
    public static void flipAfterReverseRoad(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return;
        }
        Map<String, List<String>> nodeToEdgeIds = nodeToEdgeIds(network, road);
        if (nodeToEdgeIds.isEmpty()) {
            return;
        }
        String oldEntry = road.getDesignEntryNodeId();
        String oldEdge = road.getDesignEntryEdgeId();
        if (oldEntry == null || !nodeToEdgeIds.containsKey(oldEntry)) {
            ensureBound(network, road);
            return;
        }
        if (road.getTopologyMode() == RoadTopologyMode.LOOP) {
            // LOOP has no unique exit; reverse by leaving the same entry via the other edge.
            List<String> atEntry = nodeToEdgeIds.get(oldEntry);
            String other = null;
            if (atEntry != null) {
                for (String edgeId : atEntry) {
                    if (oldEdge == null || !edgeId.equals(oldEdge)) {
                        other = edgeId;
                        break;
                    }
                }
            }
            if (other == null) {
                ensureBound(network, road);
                return;
            }
            road.setDesignEntryNodeId(oldEntry);
            road.setDesignEntryEdgeId(other);
            return;
        }
        List<String> walk = walkEdgeIds(network, nodeToEdgeIds, oldEntry, oldEdge);
        if (walk.isEmpty()) {
            ensureBound(network, road);
            return;
        }
        String lastEdgeId = walk.getLast();
        RoadEdge lastEdge = network.getEdge(lastEdgeId);
        if (lastEdge == null) {
            ensureBound(network, road);
            return;
        }
        // LINEAR: previous exit becomes the new design entry; last walk edge leaves it.
        String newEntryNodeId = chainExitNodeId(network, nodeToEdgeIds, oldEntry, oldEdge);
        if (newEntryNodeId == null) {
            ensureBound(network, road);
            return;
        }
        String newEntryEdgeId = lastEdgeId;
        if (!newEntryNodeId.equals(lastEdge.getStartNodeId())
                && !newEntryNodeId.equals(lastEdge.getEndNodeId())) {
            newEntryEdgeId = firstEdgeAtNode(nodeToEdgeIds.get(newEntryNodeId));
        }
        road.setDesignEntryNodeId(newEntryNodeId);
        road.setDesignEntryEdgeId(newEntryEdgeId);
    }

    public static String firstEdgeAtNode(
            RoadNetwork network,
            Road road,
            String nodeId,
            String preferredEdgeId,
            Set<String> visited) {
        return firstEdgeAtNode(nodeToEdgeIds(network, road), nodeId, preferredEdgeId, visited);
    }

    /** Prefer this overload in chain walks to reuse {@link ResolvedStart#nodeToEdgeIds()}. */
    public static String firstEdgeAtNode(
            Map<String, List<String>> nodeToEdgeIds,
            String nodeId,
            String preferredEdgeId,
            Set<String> visited) {
        if (nodeId == null || nodeToEdgeIds == null) {
            return null;
        }
        List<String> edges = nodeToEdgeIds.get(nodeId);
        if (edges == null || edges.isEmpty()) {
            return null;
        }
        if (preferredEdgeId != null
                && !visited.contains(preferredEdgeId)
                && edges.contains(preferredEdgeId)) {
            return preferredEdgeId;
        }
        for (String edgeId : edges) {
            if (!visited.contains(edgeId)) {
                return edgeId;
            }
        }
        return null;
    }

    public record ResolvedStart(
            String entryNodeId,
            String preferredEntryEdgeId,
            Map<String, List<String>> nodeToEdgeIds) {
    }

    static Map<String, List<String>> nodeToEdgeIds(RoadNetwork network, Road road) {
        Map<String, List<String>> nodeToEdgeIds = new HashMap<>();
        if (network == null || road == null) {
            return nodeToEdgeIds;
        }
        for (String segmentId : road.getOrderedSegmentIds()) {
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                continue;
            }
            nodeToEdgeIds.computeIfAbsent(edge.getStartNodeId(), ignored -> new ArrayList<>()).add(segmentId);
            nodeToEdgeIds.computeIfAbsent(edge.getEndNodeId(), ignored -> new ArrayList<>()).add(segmentId);
        }
        return nodeToEdgeIds;
    }

    private static String deterministicEntryNodeId(
            RoadNetwork network,
            Map<String, List<String>> nodeToEdgeIds) {
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

    private static String firstEdgeAtNode(List<String> edgeIds) {
        if (edgeIds == null || edgeIds.isEmpty()) {
            return null;
        }
        return edgeIds.getFirst();
    }

    private static List<String> walkEdgeIds(
            RoadNetwork network,
            Map<String, List<String>> nodeToEdgeIds,
            String startNodeId,
            String preferredEdgeId) {
        List<String> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentNodeId = startNodeId;
        String currentEdgeId = preferredFirst(nodeToEdgeIds.get(startNodeId), preferredEdgeId, visited);
        while (currentEdgeId != null) {
            visited.add(currentEdgeId);
            ordered.add(currentEdgeId);
            RoadEdge edge = network.getEdge(currentEdgeId);
            if (edge == null) {
                break;
            }
            String nextNodeId = edge.getStartNodeId().equals(currentNodeId)
                ? edge.getEndNodeId()
                : edge.getStartNodeId();
            currentNodeId = nextNodeId;
            currentEdgeId = preferredFirst(nodeToEdgeIds.get(nextNodeId), null, visited);
        }
        return ordered;
    }

    private static String chainExitNodeId(
            RoadNetwork network,
            Map<String, List<String>> nodeToEdgeIds,
            String startNodeId,
            String preferredEdgeId) {
        String currentNodeId = startNodeId;
        String currentEdgeId = preferredFirst(nodeToEdgeIds.get(startNodeId), preferredEdgeId, new HashSet<>());
        Set<String> visited = new HashSet<>();
        while (currentEdgeId != null) {
            visited.add(currentEdgeId);
            RoadEdge edge = network.getEdge(currentEdgeId);
            if (edge == null) {
                break;
            }
            String nextNodeId = edge.getStartNodeId().equals(currentNodeId)
                ? edge.getEndNodeId()
                : edge.getStartNodeId();
            currentNodeId = nextNodeId;
            currentEdgeId = preferredFirst(nodeToEdgeIds.get(nextNodeId), null, visited);
        }
        return currentNodeId;
    }

    private static String preferredFirst(List<String> edgeIds, String preferred, Set<String> visited) {
        if (edgeIds == null) {
            return null;
        }
        if (preferred != null && !visited.contains(preferred) && edgeIds.contains(preferred)) {
            return preferred;
        }
        for (String edgeId : edgeIds) {
            if (!visited.contains(edgeId)) {
                return edgeId;
            }
        }
        return null;
    }

    private static int comparePosition(Vec2d left, Vec2d right) {
        int byX = Double.compare(left.x, right.x);
        return byX != 0 ? byX : Double.compare(left.y, right.y);
    }
}
