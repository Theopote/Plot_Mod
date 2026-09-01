package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/** Bounded junction queue that propagates vertical constraints through the road graph. */
public final class VerticalProfileNetworkPropagator {
    public static final int MAX_PROPAGATION_PASSES = 16;

    public record RoadResult(
            String roadId,
            String nodeId,
            RoadVerticalMode mode,
            boolean changed,
            boolean regenerationRequired,
            boolean fullyResolved) { }

    public record Result(List<RoadResult> roads, int passes, boolean limitReached) {
        public Result(List<RoadResult> roads) {
            this(roads, 0, false);
        }

        public Result {
            roads = List.copyOf(roads);
        }

        public long adjustedRoadCount() {
            return roads.stream().filter(RoadResult::changed).count();
        }

        public long unresolvedRoadCount() {
            return roads.stream().filter(result -> !result.fullyResolved()).count();
        }
    }

    private VerticalProfileNetworkPropagator() { }

    public static Result propagate(
            RoadNetwork network,
            Road sourceRoad,
            ToDoubleFunction<Road> maxGradeResolver) {
        if (network == null || sourceRoad == null || maxGradeResolver == null) {
            return new Result(List.of());
        }
        LinkedHashSet<String> seeds = new LinkedHashSet<>(
            VerticalAlignmentJunctionSynchronizer.publishJunctionElevations(network, sourceRoad));
        for (String nodeId : VerticalAlignmentJunctionSynchronizer
                .junctionStations(network, sourceRoad).keySet()) {
            RoadNode node = network.getNode(nodeId);
            if (node != null && node.getManualElevation() != null) seeds.add(nodeId);
        }
        return propagateQueue(network, seeds, Set.of(), maxGradeResolver);
    }

    /** Applies an already-changed shared junction elevation to the complete reachable constraint graph. */
    public static Result propagateNode(
            RoadNetwork network,
            RoadNode node,
            ToDoubleFunction<Road> maxGradeResolver) {
        if (network == null || node == null || node.getManualElevation() == null
                || node.isGradeSeparated() || maxGradeResolver == null) {
            return new Result(List.of());
        }
        return propagateQueue(network, List.of(node.getId()), Set.of(), maxGradeResolver);
    }

    private static Result propagateQueue(
            RoadNetwork network,
            Iterable<String> seedNodeIds,
            Set<String> initiallyVisitedRoadIds,
            ToDoubleFunction<Road> maxGradeResolver) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> queuedNodeIds = new HashSet<>();
        for (String nodeId : seedNodeIds) enqueue(queue, queuedNodeIds, nodeId);

        Set<String> visitedRoadIds = new HashSet<>(initiallyVisitedRoadIds);
        List<RoadResult> results = new ArrayList<>();
        int passes = 0;
        while (!queue.isEmpty() && passes < MAX_PROPAGATION_PASSES) {
            String nodeId = queue.removeFirst();
            queuedNodeIds.remove(nodeId);
            RoadNode node = network.getNode(nodeId);
            passes++;
            if (node == null || node.getManualElevation() == null || node.isGradeSeparated()) continue;

            List<FlatRoadJunctionConflictResolver.Conflict> flatConflicts =
                FlatRoadJunctionConflictResolver.find(network);
            for (String roadId : network.getDistinctRoadIdsAtNode(nodeId)) {
                if (!visitedRoadIds.add(roadId)) continue;
                Road road = network.getRoad(roadId);
                RoadResult result = resolveRoad(
                    network, road, node, maxGradeResolver, flatConflicts);
                if (result == null) continue;
                results.add(result);
                if (road.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE) {
                    for (String changedNodeId : VerticalAlignmentJunctionSynchronizer
                            .publishJunctionElevations(network, road)) {
                        if (!changedNodeId.equals(nodeId)) {
                            enqueue(queue, queuedNodeIds, changedNodeId);
                        }
                    }
                }
            }
        }
        return new Result(results, passes, !queue.isEmpty());
    }

    private static RoadResult resolveRoad(
            RoadNetwork network,
            Road road,
            RoadNode changedNode,
            ToDoubleFunction<Road> maxGradeResolver,
            List<FlatRoadJunctionConflictResolver.Conflict> flatConflicts) {
        if (road == null) return null;
        RoadVerticalMode mode = road.getVerticalMode();
        if (mode == RoadVerticalMode.AUTO_SMOOTH || mode == RoadVerticalMode.FIT_TERRAIN) {
            return new RoadResult(road.getId(), changedNode.getId(), mode, false, true, true);
        }
        if (mode == RoadVerticalMode.FLAT) {
            boolean conflict = flatConflicts.stream().anyMatch(item ->
                item.roadId().equals(road.getId()) && item.nodeId().equals(changedNode.getId()));
            return new RoadResult(
                road.getId(), changedNode.getId(), mode, false, false, !conflict);
        }
        if (mode != RoadVerticalMode.MANUAL_PROFILE || road.getVerticalAlignment() == null) return null;

        int synchronizedPvis = VerticalAlignmentJunctionSynchronizer
            .applySharedJunctionConstraints(network, road);
        boolean changed = synchronizedPvis > 0;
        boolean fullyResolved = true;
        for (var entry : VerticalAlignmentJunctionSynchronizer.junctionStations(network, road).entrySet()) {
            RoadNode junction = network.getNode(entry.getKey());
            if (junction == null || junction.getManualElevation() == null) continue;
            int anchor = VerticalAlignmentJunctionSynchronizer.matchingPviIndex(
                road.getVerticalAlignment().getPvis(), entry.getValue());
            if (anchor < 0) continue;
            VerticalProfileAutoFixer.Result fixed = VerticalProfileAutoFixer.extendAdjacentRuns(
                road.getVerticalAlignment(), anchor, RoadStationing.canonicalLength(network, road),
                maxGradeResolver.applyAsDouble(road),
                index -> index > 0 && index < road.getVerticalAlignment().pviCount() - 1
                    && !VerticalAlignmentJunctionSynchronizer.isSharedJunctionAtStation(
                        network, road,
                        road.getVerticalAlignment().getPvis().get(index).getStation()));
            if (fixed.changed()) road.setVerticalAlignment(fixed.alignment());
            changed |= fixed.changed();
            fullyResolved &= fixed.fullyResolved();
        }
        return new RoadResult(
            road.getId(), changedNode.getId(), mode, changed, false, fullyResolved);
    }

    private static void enqueue(ArrayDeque<String> queue, Set<String> queuedNodeIds, String nodeId) {
        if (nodeId != null && queuedNodeIds.add(nodeId)) queue.addLast(nodeId);
    }
}
