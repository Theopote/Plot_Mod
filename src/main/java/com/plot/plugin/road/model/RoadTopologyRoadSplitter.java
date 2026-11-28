package com.plot.plugin.road.model;

import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationDataTransforms;
import com.plot.plugin.road.station.RoadStationDataTransforms.StationDataSnapshot;
import com.plot.plugin.road.station.RoadStationDataTransforms.StationRange;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.VerticalIntentSnapshot;

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

    public record RepairResult(
            int sourceRoadsRepaired,
            int newRoadsCreated,
            int loopsPromoted,
            boolean migrationFailed) {
        public RepairResult(int sourceRoadsRepaired, int newRoadsCreated) {
            this(sourceRoadsRepaired, newRoadsCreated, 0, false);
        }

        public RepairResult(int sourceRoadsRepaired, int newRoadsCreated, int loopsPromoted) {
            this(sourceRoadsRepaired, newRoadsCreated, loopsPromoted, false);
        }

        public static RepairResult failed() {
            return new RepairResult(0, 0, 0, true);
        }
    }

    /**
     * 认领批次末尾调用：拆断开/分叉 Road，提升闭合环为 {@link RoadTopologyMode#LOOP}，并同步分段顺序。
     * Atomic: mutate working copy only; adopt on success (R04).
     */
    public static RepairResult repairAfterAdopt(RoadNetwork network) {
        if (network == null) {
            return new RepairResult(0, 0);
        }
        RoadNetwork working = network.snapshot();
        RepairResult result = repairAfterAdoptMutating(working);
        if (result.migrationFailed()) {
            return result;
        }
        network.replaceContentsFrom(working);
        return result;
    }

    private static RepairResult repairAfterAdoptMutating(RoadNetwork network) {
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
                if (disconnectedCreated < 0) {
                    return RepairResult.failed();
                }
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
                if (created < 0) {
                    return RepairResult.failed();
                }
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
     * Atomic: mutate working copy only; adopt on success (R04).
     */
    public static RepairResult repairRoad(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return new RepairResult(0, 0);
        }
        if (network.getRoad(road.getId()) == null) {
            return new RepairResult(0, 0);
        }
        RoadNetwork working = network.snapshot();
        Road workingRoad = working.getRoad(road.getId());
        if (workingRoad == null) {
            return new RepairResult(0, 0);
        }
        RepairResult result = repairRoadMutating(working, workingRoad);
        if (result.migrationFailed()) {
            return result;
        }
        network.replaceContentsFrom(working);
        return result;
    }

    private static RepairResult repairRoadMutating(RoadNetwork network, Road road) {
        Road current = network.getRoad(road.getId());
        if (current == null) {
            return new RepairResult(0, 0);
        }

        int loopsPromoted = promoteClosedLoopsToLoopMode(network, road.getId());

        int sourceRoadsRepaired = 0;
        int newRoadsCreated = 0;

        int disconnectedCreated = repairDisconnectedRoad(network, current);
        if (disconnectedCreated < 0) {
            return RepairResult.failed();
        }
        if (disconnectedCreated > 0) {
            sourceRoadsRepaired++;
            newRoadsCreated += disconnectedCreated;
        }

        current = network.getRoad(road.getId());
        if (current != null) {
            int branchingCreated = splitBranchingRoad(network, current);
            if (branchingCreated < 0) {
                return RepairResult.failed();
            }
            if (branchingCreated > 0) {
                sourceRoadsRepaired++;
                newRoadsCreated += branchingCreated;
            }
            RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(network, current);
        }

        return new RepairResult(sourceRoadsRepaired, newRoadsCreated, loopsPromoted);
    }

    private static void promoteToLoop(RoadNetwork network, Road road) {
        promoteToLoop(network, road, null);
    }

    private static void promoteToLoop(
            RoadNetwork network,
            Road road,
            java.util.List<com.plot.api.geometry.Vec2d> spliceOriginPoints) {
        road.setTopologyMode(RoadTopologyMode.LOOP);
        if (road.getLoopSeam() == null) {
            road.setLoopSeam(com.plot.plugin.road.station.RoadLoopSeamService.computeDefault(
                network, road, spliceOriginPoints));
        }
    }

    /**
     * Explicit edit/repair path: demote an open {@link RoadTopologyMode#LOOP} road to LINEAR
     * and clear the obsolete seam. Never auto-closes or stitches endpoints.
     *
     * @return true when the road was demoted
     */
    public static boolean demoteOpenLoopToLinear(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return false;
        }
        Road current = network.getRoad(road.getId());
        if (current == null || current.getTopologyMode() != RoadTopologyMode.LOOP) {
            return false;
        }
        Subgraph subgraph = Subgraph.build(network, current);
        if (subgraph.endpointCount <= 0) {
            return false;
        }
        current.setTopologyMode(RoadTopologyMode.LINEAR);
        current.setLoopSeam(null);
        return true;
    }

    /** Promote a real closed ring (or single self-loop edge) to LOOP with default seam. */
    public static boolean promoteClosedRoadToLoop(RoadNetwork network, Road road) {
        return promoteClosedRoadToLoop(network, road, null);
    }

    /**
     * Promote closed ring to LOOP. When {@code spliceOriginPoints} is non-empty, the default seam
     * is projected from that splice origin instead of min-XY.
     */
    public static boolean promoteClosedRoadToLoop(
            RoadNetwork network,
            Road road,
            java.util.List<com.plot.api.geometry.Vec2d> spliceOriginPoints) {
        if (network == null || road == null) {
            return false;
        }
        Road current = network.getRoad(road.getId());
        if (current == null || current.getTopologyMode() == RoadTopologyMode.LOOP) {
            return false;
        }
        Subgraph subgraph = Subgraph.build(network, current);
        if (subgraph.edgeIds.size() == 1) {
            return promoteSingleSelfLoop(network, current, subgraph, spliceOriginPoints);
        }
        if (subgraph.componentCount == 1
                && !subgraph.hasBranching
                && subgraph.endpointCount == 0
                && !subgraph.edgeIds.isEmpty()) {
            promoteToLoop(network, current, spliceOriginPoints);
            return true;
        }
        return false;
    }

    private static boolean promoteSingleSelfLoop(
            RoadNetwork network,
            Road road,
            Subgraph subgraph,
            java.util.List<com.plot.api.geometry.Vec2d> spliceOriginPoints) {
        if (subgraph.edgeIds.size() != 1 || subgraph.hasBranching || subgraph.componentCount != 1) {
            return false;
        }
        String edgeId = subgraph.edgeIds.iterator().next();
        RoadEdge edge = network.getEdge(edgeId);
        if (edge == null || !edge.getStartNodeId().equals(edge.getEndNodeId())) {
            return false;
        }
        promoteToLoop(network, road, spliceOriginPoints);
        return true;
    }

    private static boolean promoteSingleSelfLoop(RoadNetwork network, Road road, Subgraph subgraph) {
        return promoteSingleSelfLoop(network, road, subgraph, null);
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
            if (subgraph.edgeIds.size() == 1) {
                if (promoteSingleSelfLoop(network, road, subgraph)) {
                    promoted++;
                }
                continue;
            }
            if (subgraph.componentCount == 1
                    && !subgraph.hasBranching
                    && subgraph.endpointCount == 0) {
                promoteToLoop(network, road);
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
     * @return 新创建的 Road 数量；迁移失败返回 -1
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
     * @return 新创建的 Road 数量；任一分量迁移失败返回 -1（不得清空后继续）
     */
    private static int distributeComponents(
            RoadNetwork network,
            Road road,
            List<Set<String>> components,
            Set<String> keepComponent,
            int branchIndexStart) {
        StationDataSnapshot snapshot = StationDataSnapshot.capture(road);
        VerticalIntentSnapshot intentSnapshot = VerticalIntentSnapshot.capture(road);
        // Freeze base engineering before keep is range-cropped (R01).
        Road baseEngineering = new Road("base-engineering");
        baseEngineering.copyEngineeringFrom(road);
        boolean sourceEditedValid =
            com.plot.plugin.road.vertical.ResolvedProfileAuthority.hasValidEditedProfile(network, road);
        double totalLength = RoadStationing.canonicalLength(network, road);
        // Phase-2 data with invalid length must refuse — never silent drop (R04).
        // Branching/disconnected roads are not isStationable; component ranges still come from
        // orientedSegments walk, and empty/unextractable ranges refuse below.
        if (snapshot.hasPhase2Data() && !(totalLength > 1e-6)) {
            return -1;
        }
        boolean mapStationData = snapshot.hasPhase2Data() && totalLength > 1e-6;
        boolean stationable = RoadStationing.isStationable(network, road);
        // T01: protected VA/VCS/facilities on disconnected/branching topology cannot prove
        // physical equivalence here. Refuse direct repair; Manager Delete Edge clears along-track
        // before repairRoad and verifies externally after components are stationable.
        if (network.hasProtectedAlongTrackData(road) && !stationable) {
            return -1;
        }
        // Physical anchors need a stationable chain (S03).
        boolean physicalVerify = mapStationData && stationable;
        var vaCap = physicalVerify
            ? RoadStationDataTransforms.captureVaPhysicalAnchors(network, road)
            : RoadStationDataTransforms.VaAnchorCapture.none();
        if (vaCap.failed()) {
            return -1;
        }
        var alongCap = physicalVerify
            ? RoadStationDataTransforms.captureAlongTrackPhysicalAnchors(network, road)
            : RoadStationDataTransforms.AlongTrackAnchorCapture.none();
        if (alongCap.failed()) {
            return -1;
        }

        List<List<StationRange>> componentRanges = new ArrayList<>(components.size());
        for (Set<String> component : components) {
            componentRanges.add(mapStationData
                ? RoadStationDataTransforms.computeComponentStationRanges(network, road, component)
                : List.of());
        }

        int keepIndex = components.indexOf(keepComponent);
        if (keepIndex < 0) {
            keepIndex = 0;
        }
        if (mapStationData) {
            keepIndex = pickExtractableKeepIndex(
                components, componentRanges, snapshot, totalLength, keepIndex);
            // Dry-run every component against the original snapshot before mutating (Q04).
            for (int i = 0; i < components.size(); i++) {
                List<StationRange> runs = componentRanges.get(i);
                if (runs.size() > 1) {
                    return -1;
                }
                if (runs.size() == 1) {
                    Road scratch = new Road("distribute-dry-run-" + i);
                    if (snapshot.defaultCrossSection() != null) {
                        scratch.setCrossSection(snapshot.defaultCrossSection().copy());
                    }
                    if (!snapshot.applyRangeTo(scratch, totalLength, runs.getFirst()).isSuccess()) {
                        return -1;
                    }
                } else if (componentShouldHaveStationCoverage(
                        network, road, components.get(i), totalLength)) {
                    // hasPhase2Data + oriented walk should cover this component — empty is refuse.
                    return -1;
                }
            }
            List<StationRange> keepRuns = componentRanges.get(keepIndex);
            if (keepRuns.size() == 1) {
                var keepResult = snapshot.applyRangeTo(road, totalLength, keepRuns.getFirst());
                if (!keepResult.isSuccess()) {
                    return -1;
                }
            } else {
                // Truly uncovered orphan: scratch stays empty (no stale full-road inherit).
                clearStationEngineeringData(road);
            }
            if (snapshot.hadHorizontalAlignment()) {
                road.setHorizontalAlignment(null);
            }
        }
        reassignComponent(network, road, components.get(keepIndex));
        intentSnapshot.applyTo(network, road, components.get(keepIndex));

        int branchIndex = branchIndexStart;
        int created = 0;
        List<Road> splitRoads = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            if (i == keepIndex) {
                continue;
            }
            // Base road-level engineering always inherited; along-track data only from snapshot (R01).
            Road splitRoad = network.createRoad();
            splitRoad.copyEngineeringFrom(baseEngineering);
            splitRoad.setTopologyMode(RoadTopologyMode.LINEAR);
            applyBranchName(road, splitRoad, branchIndex++);
            if (mapStationData) {
                // Clear along-track inherited from keep after copyEngineeringFrom (none copied) —
                // then apply snapshot extract for this component only.
                List<StationRange> runs = componentRanges.get(i);
                if (runs.size() == 1) {
                    var branchResult = snapshot.applyRangeTo(splitRoad, totalLength, runs.getFirst());
                    if (!branchResult.isSuccess()) {
                        return -1;
                    }
                } else {
                    clearStationEngineeringData(splitRoad);
                }
                if (snapshot.hadHorizontalAlignment()) {
                    splitRoad.setHorizontalAlignment(null);
                }
            }
            reassignComponent(network, splitRoad, components.get(i));
            intentSnapshot.applyTo(network, splitRoad, components.get(i));
            splitRoads.add(splitRoad);
            created++;
        }

        if (mapStationData && snapshot.hadHorizontalAlignment()) {
            RoadStationDataTransforms.refitHorizontalAlignmentFromCenterline(network, road);
            if (road.getHorizontalAlignment() == null
                    || !stationDomainReliable(network, road)) {
                return -1;
            }
            for (Road splitRoad : splitRoads) {
                RoadStationDataTransforms.refitHorizontalAlignmentFromCenterline(network, splitRoad);
                if (splitRoad.getHorizontalAlignment() == null
                        || !stationDomainReliable(network, splitRoad)) {
                    return -1;
                }
            }
        }
        if (mapStationData) {
            if (physicalVerify) {
                for (int i = 0; i < components.size(); i++) {
                    Road part = i == keepIndex ? road : null;
                    if (part == null) {
                        for (Road splitRoad : splitRoads) {
                            if (componentOwnsAny(splitRoad, components.get(i))) {
                                part = splitRoad;
                                break;
                            }
                        }
                    }
                    if (part == null) {
                        return -1;
                    }
                    List<StationRange> runs = componentRanges.get(i);
                    if (!vaCap.anchors().isEmpty()
                            && !RoadStationDataTransforms.verifyVaPhysicalAnchors(
                                network, part, vaCap.anchors())) {
                        return -1;
                    }
                    if (!alongCap.isEmpty()) {
                        var sectionOnly = RoadStationDataTransforms.AlongTrackAnchorCapture.ok(
                            alongCap.sectionAnchors(), List.of());
                        if (!sectionOnly.isEmpty()
                                && !RoadStationDataTransforms.verifyAlongTrackPhysicalAnchors(
                                    network, part, sectionOnly)) {
                            return -1;
                        }
                        if (!alongCap.facilityIntervals().isEmpty() && runs.size() == 1) {
                            StationRange range = runs.getFirst();
                            double length = RoadStationing.canonicalLength(network, part);
                            var expected = RoadStationDataTransforms.clipAndShiftFacilityIntervals(
                                alongCap.facilityIntervals(), range.start(), range.end());
                            var actual = RoadStationDataTransforms.captureFacilityIntervals(
                                part, length);
                            if (!RoadStationDataTransforms.facilityIntervalsEqual(
                                    expected, actual)) {
                                return -1;
                            }
                        }
                    }
                }
            }
            if (!RoadStationDataTransforms.rebindEditedProfileAfterFinalGeometry(
                    network, road, sourceEditedValid)) {
                return -1;
            }
            for (Road splitRoad : splitRoads) {
                if (!RoadStationDataTransforms.rebindEditedProfileAfterFinalGeometry(
                        network, splitRoad, sourceEditedValid)) {
                    return -1;
                }
            }
        }
        return created;
    }

    private static boolean stationDomainReliable(RoadNetwork network, Road road) {
        if (road == null || road.getHorizontalAlignment() == null) {
            return true;
        }
        var map = RoadStationing.domainMap(network, road);
        return map != null && map.isReliable();
    }

    private static boolean componentOwnsAny(Road road, Set<String> component) {
        if (road == null || component == null) {
            return false;
        }
        for (String edgeId : road.getOrderedSegmentIds()) {
            if (component.contains(edgeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the component participates in the oriented walk and therefore should receive a
     * non-empty station extract when the source has Phase-2 data.
     */
    private static boolean componentShouldHaveStationCoverage(
            RoadNetwork network,
            Road road,
            Set<String> component,
            double totalLength) {
        if (network == null || road == null || component == null || component.isEmpty()
                || !(totalLength > 1e-6)) {
            return false;
        }
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            if (segment != null && component.contains(segment.edgeId())) {
                return true;
            }
        }
        return false;
    }

    private static void clearStationEngineeringData(Road road) {
        if (road == null) {
            return;
        }
        road.setVerticalAlignment(null);
        road.setVariableCrossSections(null);
        road.setStationFacilities(null);
    }

    /**
     * Prefer the caller-chosen keep when its station extract succeeds; otherwise the first
     * component that can extract losslessly. Empty coverage is allowed (clears data).
     */
    private static int pickExtractableKeepIndex(
            List<Set<String>> components,
            List<List<StationRange>> componentRanges,
            StationDataSnapshot snapshot,
            double totalLength,
            int preferredIndex) {
        if (componentExtractable(snapshot, totalLength, componentRanges.get(preferredIndex))) {
            return preferredIndex;
        }
        for (int i = 0; i < components.size(); i++) {
            if (i != preferredIndex
                    && componentExtractable(snapshot, totalLength, componentRanges.get(i))) {
                return i;
            }
        }
        return preferredIndex;
    }

    private static boolean componentExtractable(
            StationDataSnapshot snapshot,
            double totalLength,
            List<StationRange> runs) {
        if (runs.size() > 1) {
            return false;
        }
        if (runs.isEmpty()) {
            return true;
        }
        Road scratch = new Road("extract-probe");
        return snapshot.applyRangeTo(scratch, totalLength, runs.getFirst()).isSuccess();
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
