package com.plot.plugin.road.model;

import com.plot.plugin.road.station.RoadStationDataTransforms;
import com.plot.plugin.road.station.RoadStationDataTransforms.StationDataSnapshot;
import com.plot.plugin.road.station.RoadStationDataTransforms.StationRange;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 认领后修复 Road 拓扑：将断开分量与内部分叉拆成多条符合不变量的逻辑道路。
 *
 * @see docs/decisions/0005-road-adopt-fork-split.md
 */
public final class RoadTopologyRoadSplitter {

    private static final int MAX_REPAIR_PASSES = 32;

    private RoadTopologyRoadSplitter() {
    }

    public record RepairResult(int sourceRoadsRepaired, int newRoadsCreated, int loopsPromoted) {
        public RepairResult(int sourceRoadsRepaired, int newRoadsCreated) {
            this(sourceRoadsRepaired, newRoadsCreated, 0);
        }
    }

    /**
     * 认领批次末尾调用：拆断开/分叉 Road，提升闭合环为 {@link RoadTopologyMode#LOOP}，并同步分段顺序。
     */
    public static RepairResult repairAfterAdopt(RoadNetwork network) {
        if (network == null) {
            return new RepairResult(0, 0);
        }

        int loopsPromoted = promoteClosedLoopsToLoopMode(network);

        int sourceRoadsRepaired = 0;
        int newRoadsCreated = 0;
        boolean changed = true;
        int pass = 0;

        while (changed && pass++ < MAX_REPAIR_PASSES) {
            changed = false;
            List<String> roadIds = new ArrayList<>(network.getRoads().keySet());
            for (String roadId : roadIds) {
                Road road = network.getRoad(roadId);
                if (road == null) {
                    continue;
                }
                int disconnectedCreated = repairDisconnectedRoad(network, road);
                if (disconnectedCreated > 0) {
                    changed = true;
                    sourceRoadsRepaired++;
                    newRoadsCreated += disconnectedCreated;
                }
            }

            roadIds = new ArrayList<>(network.getRoads().keySet());
            for (String roadId : roadIds) {
                Road road = network.getRoad(roadId);
                if (road == null) {
                    continue;
                }
                int created = splitBranchingRoad(network, road);
                if (created > 0) {
                    changed = true;
                    sourceRoadsRepaired++;
                    newRoadsCreated += created;
                }
            }
        }

        RoadSegmentOrdering.applyTopologicalOrderToAllRoads(network);
        for (Road road : network.getRoads().values()) {
            RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(network, road);
        }

        return new RepairResult(sourceRoadsRepaired, newRoadsCreated, loopsPromoted);
    }

    /**
     * 修复单条道路的断开分量 / 内部分叉，并同步分段顺序。
     */
    public static RepairResult repairRoad(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return new RepairResult(0, 0);
        }
        Road current = network.getRoad(road.getId());
        if (current == null) {
            return new RepairResult(0, 0);
        }

        int loopsPromoted = promoteClosedLoopsToLoopMode(network, road.getId());

        int sourceRoadsRepaired = 0;
        int newRoadsCreated = 0;

        int disconnectedCreated = repairDisconnectedRoad(network, current);
        if (disconnectedCreated > 0) {
            sourceRoadsRepaired++;
            newRoadsCreated += disconnectedCreated;
        }

        current = network.getRoad(road.getId());
        if (current != null) {
            int branchingCreated = splitBranchingRoad(network, current);
            if (branchingCreated > 0) {
                sourceRoadsRepaired++;
                newRoadsCreated += branchingCreated;
            }
            RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(network, current);
        }

        return new RepairResult(sourceRoadsRepaired, newRoadsCreated, loopsPromoted);
    }

    private static int promoteClosedLoopsToLoopMode(RoadNetwork network) {
        return promoteClosedLoopsToLoopMode(network, null);
    }

    private static int promoteClosedLoopsToLoopMode(RoadNetwork network, String roadIdFilter) {
        int promoted = 0;
        for (Road road : network.getRoads().values()) {
            if (roadIdFilter != null && !roadIdFilter.equals(road.getId())) {
                continue;
            }
            if (road.getTopologyMode() == RoadTopologyMode.LOOP) {
                continue;
            }
            Subgraph subgraph = Subgraph.build(network, road);
            if (subgraph.edgeIds.size() <= 1) {
                continue;
            }
            if (subgraph.componentCount == 1
                    && !subgraph.hasBranching
                    && subgraph.endpointCount == 0) {
                road.setTopologyMode(RoadTopologyMode.LOOP);
                promoted++;
            }
        }
        return promoted;
    }

    /**
     * @return 新创建的 Road 数量
     */
    static int repairDisconnectedRoad(RoadNetwork network, Road road) {
        Subgraph subgraph = Subgraph.build(network, road);
        if (subgraph.edgeIds.size() <= 1 || subgraph.componentCount <= 1) {
            return 0;
        }

        List<Set<String>> components = subgraph.connectedComponents(network);
        components.sort(Comparator
            .comparingInt(Set<String>::size)
            .thenComparing(component -> -chainLength(network, component))
            .reversed());

        Set<String> keepComponent = components.getFirst();
        int created = distributeComponents(network, road, components, keepComponent, 1);
        return created;
    }

    /**
     * @return 新创建的 Road 数量
     */
    static int splitBranchingRoad(RoadNetwork network, Road road) {
        Subgraph subgraph = Subgraph.build(network, road);
        if (!subgraph.hasBranching || subgraph.edgeIds.size() <= 1) {
            return 0;
        }

        String forkNodeId = subgraph.forkNodeIds().stream()
            .min(Comparator.comparing(nodeId -> nodeId != null ? nodeId : ""))
            .orElse(null);
        if (forkNodeId == null) {
            return 0;
        }

        List<Set<String>> chains = collectChainsAtFork(network, subgraph, forkNodeId);
        if (chains.size() <= 1) {
            return 0;
        }

        chains.sort(Comparator
            .comparingInt(Set<String>::size)
            .thenComparing(chain -> -chainLength(network, chain))
            .reversed());

        return distributeComponents(network, road, chains, chains.getFirst(), 1);
    }

    /**
     * 将 {@code components} 拆到多条 Road：首项保留在 {@code road}，其余新建。
     *
     * @return 新创建的 Road 数量
     */
    private static int distributeComponents(
            RoadNetwork network,
            Road road,
            List<Set<String>> components,
            Set<String> keepComponent,
            int branchIndexStart) {
        StationDataSnapshot snapshot = StationDataSnapshot.capture(road);
        double totalLength = RoadStationing.canonicalLength(network, road);
        boolean mapStationData = snapshot.hasPhase2Data() && totalLength > 1e-6;

        List<StationRange> ranges = new ArrayList<>(components.size());
        for (Set<String> component : components) {
            ranges.add(mapStationData
                ? RoadStationDataTransforms.computeComponentStationRange(network, road, component)
                : StationRange.invalid());
        }

        int keepIndex = components.indexOf(keepComponent);
        if (keepIndex < 0) {
            keepIndex = 0;
        }
        if (mapStationData) {
            StationRange keepRange = ranges.get(keepIndex);
            if (keepRange.isValid()) {
                snapshot.applyRangeTo(road, totalLength, keepRange);
            } else {
                clearStationEngineeringData(road);
            }
            if (snapshot.hadHorizontalAlignment()) {
                road.setHorizontalAlignment(null);
            }
        }
        reassignComponent(network, road, components.get(keepIndex));

        int branchIndex = branchIndexStart;
        int created = 0;
        List<Road> splitRoads = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            if (i == keepIndex) {
                continue;
            }
            Road splitRoad = network.createRoad();
            splitRoad.copyEngineeringFrom(road);
            splitRoad.setTopologyMode(RoadTopologyMode.LINEAR);
            applyBranchName(road, splitRoad, branchIndex++);
            if (mapStationData) {
                StationRange range = ranges.get(i);
                if (range.isValid()) {
                    snapshot.applyRangeTo(splitRoad, totalLength, range);
                }
                if (snapshot.hadHorizontalAlignment()) {
                    splitRoad.setHorizontalAlignment(null);
                }
            }
            reassignComponent(network, splitRoad, components.get(i));
            splitRoads.add(splitRoad);
            created++;
        }

        if (mapStationData && snapshot.hadHorizontalAlignment()) {
            RoadStationDataTransforms.refitHorizontalAlignmentFromCenterline(network, road);
            for (Road splitRoad : splitRoads) {
                RoadStationDataTransforms.refitHorizontalAlignmentFromCenterline(network, splitRoad);
            }
        }
        return created;
    }

    private static void clearStationEngineeringData(Road road) {
        if (road == null) {
            return;
        }
        road.setVerticalAlignment(null);
        road.setVariableCrossSections(null);
        road.setStationFacilities(null);
    }

    private static void applyBranchName(Road parent, Road splitRoad, int branchIndex) {
        String parentName = parent.getName();
        if (parentName == null || parentName.isBlank()) {
            return;
        }
        splitRoad.setName(parentName + " · " + branchIndex);
    }

    private static void reassignComponent(RoadNetwork network, Road road, Set<String> edgeIds) {
        List<String> ordered = new ArrayList<>();
        for (String edgeId : edgeIds) {
            network.assignEdgeToRoad(edgeId, road.getId());
            ordered.add(edgeId);
        }
        road.reorderSegments(ordered);
    }

    private static double chainLength(RoadNetwork network, Set<String> edgeIds) {
        double total = 0.0;
        for (String edgeId : edgeIds) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null) {
                total += edge.getLength();
            }
        }
        return total;
    }

    private static List<Set<String>> collectChainsAtFork(
            RoadNetwork network,
            Subgraph subgraph,
            String forkNodeId) {
        List<String> forkEdges = incidentRoadEdges(subgraph, forkNodeId);
        List<Set<String>> chains = new ArrayList<>();
        for (String startEdgeId : forkEdges) {
            chains.add(walkChain(network, subgraph, forkNodeId, startEdgeId));
        }
        return chains;
    }

    private static Set<String> walkChain(
            RoadNetwork network,
            Subgraph subgraph,
            String startNodeId,
            String startEdgeId) {
        LinkedHashSet<String> chain = new LinkedHashSet<>();
        String currentNodeId = startNodeId;
        String currentEdgeId = startEdgeId;

        while (currentEdgeId != null && chain.add(currentEdgeId)) {
            RoadEdge edge = network.getEdge(currentEdgeId);
            if (edge == null) {
                break;
            }
            String nextNodeId = edge.getStartNodeId().equals(currentNodeId)
                ? edge.getEndNodeId()
                : edge.getStartNodeId();
            currentNodeId = nextNodeId;

            List<String> incident = incidentRoadEdges(subgraph, nextNodeId);
            if (incident.size() == 2) {
                currentEdgeId = incident.get(0).equals(currentEdgeId)
                    ? incident.get(1)
                    : incident.get(0);
            } else {
                currentEdgeId = null;
            }
        }
        return chain;
    }

    private static List<String> incidentRoadEdges(Subgraph subgraph, String nodeId) {
        List<String> incident = subgraph.nodeToEdgeIds.get(nodeId);
        if (incident == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String edgeId : incident) {
            if (subgraph.edgeIds.contains(edgeId)) {
                result.add(edgeId);
            }
        }
        return result;
    }

    private static final class Subgraph {
        final Set<String> edgeIds = new HashSet<>();
        final Map<String, List<String>> nodeToEdgeIds = new HashMap<>();
        int componentCount;
        int endpointCount;
        boolean hasBranching;

        static Subgraph build(RoadNetwork network, Road road) {
            Subgraph subgraph = new Subgraph();
            for (String segmentId : road.getOrderedSegmentIds()) {
                RoadEdge edge = network.getEdge(segmentId);
                if (edge == null) {
                    continue;
                }
                subgraph.edgeIds.add(segmentId);
                subgraph.nodeToEdgeIds
                    .computeIfAbsent(edge.getStartNodeId(), ignored -> new ArrayList<>())
                    .add(segmentId);
                subgraph.nodeToEdgeIds
                    .computeIfAbsent(edge.getEndNodeId(), ignored -> new ArrayList<>())
                    .add(segmentId);
            }

            subgraph.componentCount = subgraph.connectedComponents(network).size();
            for (List<String> incident : subgraph.nodeToEdgeIds.values()) {
                int degree = 0;
                for (String edgeId : incident) {
                    if (subgraph.edgeIds.contains(edgeId)) {
                        degree++;
                    }
                }
                if (degree > 2) {
                    subgraph.hasBranching = true;
                } else if (degree == 1) {
                    subgraph.endpointCount++;
                }
            }
            return subgraph;
        }

        List<Set<String>> connectedComponents(RoadNetwork network) {
            List<Set<String>> components = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            for (String seedEdgeId : edgeIds) {
                if (visited.contains(seedEdgeId)) {
                    continue;
                }
                Set<String> component = new LinkedHashSet<>();
                bfsComponent(seedEdgeId, network, visited, component);
                components.add(component);
            }
            return components;
        }

        List<String> forkNodeIds() {
            List<String> forks = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : nodeToEdgeIds.entrySet()) {
                int degree = 0;
                for (String edgeId : entry.getValue()) {
                    if (edgeIds.contains(edgeId)) {
                        degree++;
                    }
                }
                if (degree > 2) {
                    forks.add(entry.getKey());
                }
            }
            return forks;
        }

        private void bfsComponent(
                String seedEdgeId,
                RoadNetwork network,
                Set<String> visited,
                Set<String> component) {
            Queue<String> queue = new ArrayDeque<>();
            queue.add(seedEdgeId);
            visited.add(seedEdgeId);
            component.add(seedEdgeId);

            while (!queue.isEmpty()) {
                String edgeId = queue.poll();
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                for (String nodeId : List.of(edge.getStartNodeId(), edge.getEndNodeId())) {
                    for (String nextEdgeId : incidentRoadEdges(this, nodeId)) {
                        if (visited.contains(nextEdgeId)) {
                            continue;
                        }
                        visited.add(nextEdgeId);
                        component.add(nextEdgeId);
                        queue.add(nextEdgeId);
                    }
                }
            }
        }
    }
}
