package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingOwnershipMigrator;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossingReconciler;
import com.plot.plugin.road.graph.RoadExplicitConnector;
import com.plot.plugin.road.graph.RoadGraphEdits;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.3 topology editing regressions T-R01–T-R16 and V-R01–V-R06.
 */
class Phase23TopologyEditingTest {
    private RoadNetworkManager manager;
    private RoadNetwork network;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("phase23"), new RoadProjectStatus());
        network = manager.getNetwork();
    }

    @Test
    void tr01_quadLoopDeleteOneEdgeBecomesLinear() {
        Road road = squareLoop();
        String topEdgeId = road.getOrderedSegmentIds().get(2);
        manager.deleteEdge(topEdgeId);

        Road after = network.getRoad(road.getId());
        assertNotNull(after);
        assertEquals(RoadTopologyMode.LINEAR, after.getTopologyMode());
        assertNull(after.getLoopSeam());
        assertTrue(RoadStationing.isStationable(network, after));
        assertTrue(RoadTopologyInvariantValidator.validateRoad(network, after).stream()
            .noneMatch(v -> v.kind() == RoadTopologyViolationKind.ROAD_LOOP_OPEN));
    }

    @Test
    void tr02_loopMultiEdgeDeleteSplitsComponents() {
        Road road = squareLoop();
        // Delete two opposite sides → remaining two edges are disconnected.
        String e0 = road.getOrderedSegmentIds().get(0);
        String e2 = road.getOrderedSegmentIds().get(2);
        manager.deleteEdge(e0);
        manager.deleteEdge(e2);
        assertTrue(!network.getRoads().isEmpty());
        for (Road surviving : network.getRoads().values()) {
            assertNotEquals(RoadTopologyMode.LOOP, surviving.getTopologyMode());
            assertTrue(RoadTopologyInvariantValidator.validateRoad(network, surviving).stream()
                .noneMatch(v -> v.kind() == RoadTopologyViolationKind.ROAD_LOOP_OPEN));
        }
    }

    @Test
    void tr03_crossingSurvivesLoopDemoteDelete() {
        Road loop = squareLoop();
        Road cross = network.createRoad("cross");
        // Vertical crosses the bottom edge at (5,0) — keep that edge when demoting.
        network.createEdge(
            network.createNode(new Vec2d(5, -5)).getId(),
            network.createNode(new Vec2d(5, 5)).getId(),
            List.of(new Vec2d(5, -5), new Vec2d(5, 5)),
            cross.getId());
        RoadCrossing design = RoadCrossing.gradeSeparated(
            loop.getId(), 5.0, cross.getId(), 5.0, new Vec2d(5, 0), cross.getId(), 4.0);
        network.registerCrossing(design);

        // Delete the top edge (index 2) — opens LOOP but leaves the bottom crossing intact.
        manager.deleteEdge(loop.getOrderedSegmentIds().get(2));
        network = manager.getNetwork();

        RoadCrossing kept = network.getCrossing(design.id());
        assertNotNull(kept, "design identity must survive demotion delete");
        assertEquals(CrossingType.GRADE_SEPARATED, kept.type());
        assertEquals(4.0, kept.crossingClearance(), 1e-6);
    }

    @Test
    void tr04_logicalSplitPreservesGradeCrossingDesign() {
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadNode a0 = network.createNode(new Vec2d(0, 5));
        RoadNode a1 = network.createNode(new Vec2d(10, 5));
        RoadNode a2 = network.createNode(new Vec2d(20, 5));
        String e0 = network.createEdge(a0.getId(), a1.getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), a.getId()).getId();
        String e1 = network.createEdge(a1.getId(), a2.getId(),
            List.of(new Vec2d(10, 5), new Vec2d(20, 5)), a.getId()).getId();
        a.reorderSegments(List.of(e0, e1));
        network.createEdge(
            network.createNode(new Vec2d(15, 0)).getId(),
            network.createNode(new Vec2d(15, 10)).getId(),
            List.of(new Vec2d(15, 0), new Vec2d(15, 10)),
            b.getId());

        RoadCrossing design = RoadCrossing.gradeSeparated(
            a.getId(), 15.0, b.getId(), 5.0, new Vec2d(15, 5), b.getId(), 3.5);
        network.registerCrossing(design);

        String newRoadId = manager.splitRoadBeforeSegment(a.getId(), e1);
        assertNotNull(newRoadId);

        RoadCrossing kept = network.getCrossing(design.id());
        assertNotNull(kept);
        assertEquals(CrossingType.GRADE_SEPARATED, kept.type());
        assertEquals(3.5, kept.crossingClearance(), 1e-6);
        // Station 15 was on the tail segment → must belong to the new road.
        assertTrue(kept.involvesRoad(newRoadId), "crossing at station 15 must move to newRoad");
        assertFalse(kept.involvesRoad(a.getId()) && kept.roadAId().equals(a.getId())
                && kept.roadBId().equals(a.getId()));
        assertEquals(newRoadId, kept.involvesRoad(newRoadId)
            ? (kept.roadAId().equals(newRoadId) ? kept.roadAId() : kept.roadBId())
            : null);
        assertTrue(kept.involvesRoad(b.getId()));
        assertEquals(b.getId(), kept.elevatedRoadId());
        assertTrue(kept.position().distance(new Vec2d(15, 5)) < 0.5);
    }

    @Test
    void tr05_figureEightStackSplitConvertsToTwoRoadCrossing() {
        Road road = figureEight();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        assertFalse(network.getStackCrossings().isEmpty());
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED, stack.stationA(), stack.stationB(), 3.0);
        stack = network.getStackCrossing(stack.id());
        String stackId = stack.id();
        Double clearance = stack.crossingClearance();

        // Force two edges by local-arc split, then logical-split roads so passes can diverge.
        String edgeId = road.getOrderedSegmentIds().getFirst();
        CenterlineEditResult geoSplit = RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, 8.0);
        assertTrue(geoSplit.isSuccess(), geoSplit.status().toString());
        road = network.getRoad(road.getId());
        List<String> segs = RoadSegmentOrdering.orderedSegmentIds(network, road);
        assertTrue(segs.size() >= 2);
        String newRoadId = manager.splitRoadBeforeSegment(road.getId(), segs.get(1));
        assertNotNull(newRoadId);

        RoadCrossing converted = network.getCrossing(stackId);
        assertNull(network.getStackCrossing(stackId), "passes on different roads must leave stack registry");
        assertNotNull(converted, "stack must convert to two-road crossing");
        assertEquals(CrossingType.GRADE_SEPARATED, converted.type());
        assertNotEquals(converted.roadAId(), converted.roadBId());
        if (clearance != null) {
            assertEquals(clearance, converted.crossingClearance(), 1e-6);
        }
        assertNotNull(converted.elevatedRoadId());
    }

    @Test
    void tr06_mergeRoadsRetainsCrossingDesign() {
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        Road c = network.createRoad("c");
        RoadNode n0 = network.createNode(new Vec2d(0, 5));
        RoadNode n1 = network.createNode(new Vec2d(10, 5));
        RoadNode n2 = network.createNode(new Vec2d(20, 5));
        network.createEdge(n0.getId(), n1.getId(), List.of(new Vec2d(0, 5), new Vec2d(10, 5)), a.getId());
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(10, 5), new Vec2d(20, 5)), b.getId());
        network.createEdge(
            network.createNode(new Vec2d(15, 0)).getId(),
            network.createNode(new Vec2d(15, 10)).getId(),
            List.of(new Vec2d(15, 0), new Vec2d(15, 10)),
            c.getId());
        RoadCrossing design = RoadCrossing.gradeSeparated(
            b.getId(), 5.0, c.getId(), 5.0, new Vec2d(15, 5), c.getId(), 2.5);
        network.registerCrossing(design);

        String merged = manager.mergeRoadTailIntoHead(a.getId(), b.getId());
        assertEquals(a.getId(), merged);
        RoadCrossing kept = network.getCrossing(design.id());
        assertNotNull(kept);
        assertEquals(CrossingType.GRADE_SEPARATED, kept.type());
        assertEquals(2.5, kept.crossingClearance(), 1e-6);
        assertTrue(kept.involvesRoad(a.getId()));
        assertEquals(c.getId(), kept.elevatedRoadId());
    }

    @Test
    void tr07_figureEightSecondPassLocalArcSplitDoesNotCutFirst() {
        Road road = figureEight();
        String edgeId = road.getOrderedSegmentIds().getFirst();
        RoadEdge edge = network.getEdge(edgeId);
        List<Vec2d> pts = edge.getCenterlinePoints();
        double seg0 = pts.get(0).distance(pts.get(1));
        double seg1 = pts.get(1).distance(pts.get(2));
        double secondPassLocal = seg0 + seg1 + pts.get(2).distance(pts.get(3)) * 0.5;
        Vec2d expected = com.plot.plugin.road.RoadGeometryUtils.pointAtDistance(pts, secondPassLocal);
        double total = edge.getLength();
        CenterlineEditResult split = RoadCenterlineEditor.splitAtLocalDistance(
            network, edgeId, secondPassLocal);
        assertTrue(split.isSuccess(), split.status().toString());
        RoadNode node = network.getNode(split.nodeId());
        assertNotNull(node);
        assertTrue(node.getPosition().distance(expected) < 1e-3);
        RoadEdge first = network.getEdge(split.firstEdgeId());
        RoadEdge second = network.getEdge(split.secondEdgeId());
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(secondPassLocal, first.getLength(), 0.05);
        assertEquals(total - secondPassLocal, second.getLength(), 0.05);
        assertTrue(first.getLength() > seg0 + seg1 * 0.5);
    }

    @Test
    void u01_geometricSplitNearForeignNodeDoesNotJoinRoads() {
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadEdge edgeA = network.createEdge(
            network.createNode(new Vec2d(0, 0)).getId(),
            network.createNode(new Vec2d(20, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)),
            a.getId());
        RoadNode foreign = network.createNode(new Vec2d(10.2, 0.3));
        network.createEdge(
            foreign.getId(),
            network.createNode(new Vec2d(10.2, 10)).getId(),
            List.of(new Vec2d(10.2, 0.3), new Vec2d(10.2, 10)),
            b.getId());
        CenterlineEditResult split = RoadCenterlineEditor.splitAtLocalDistance(
            network, edgeA.getId(), 10.0);
        assertTrue(split.isSuccess());
        assertNotEquals(foreign.getId(), split.nodeId(), "must not reuse foreign-road node");
        RoadNode splitNode = network.getNode(split.nodeId());
        assertTrue(splitNode.getPosition().distance(new Vec2d(10, 0)) < 1e-3);
        assertTrue(foreign.getConnectedEdgeIds().stream()
            .allMatch(id -> {
                RoadEdge e = network.getEdge(id);
                return e != null && b.getId().equals(e.getRoadId());
            }));
    }

    @Test
    void tr08_reversedEdgeLocalArcSplitAtCorrectPoint() {
        Road road = network.createRoad("rev");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(20, 0));
        RoadEdge edge = network.createEdge(
            n2.getId(),
            n1.getId(),
            List.of(new Vec2d(20, 0), new Vec2d(10, 0), new Vec2d(0, 0)),
            road.getId());
        double local = 5.0;
        Vec2d expected = com.plot.plugin.road.RoadGeometryUtils.pointAtDistance(
            edge.getCenterlinePoints(), local);
        CenterlineEditResult split = RoadCenterlineEditor.splitAtLocalDistance(
            network, edge.getId(), local);
        assertTrue(split.isSuccess());
        assertTrue(network.getNode(split.nodeId()).getPosition().distance(expected) < 1e-3);
    }

    @Test
    void tr09_explicitConnectSnapsCenterlineWithinGeometryTolerance() {
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadNode a0 = network.createNode(new Vec2d(0, 0));
        RoadNode a1 = network.createNode(new Vec2d(10, 0));
        RoadNode b0 = network.createNode(new Vec2d(10.3, 0));
        RoadNode b1 = network.createNode(new Vec2d(20, 0));
        RoadEdge edgeA = network.createEdge(a0.getId(), a1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadA.getId());
        RoadEdge edgeB = network.createEdge(b0.getId(), b1.getId(),
            List.of(new Vec2d(10.3, 0), new Vec2d(20, 0)), roadB.getId());

        assertTrue(RoadExplicitConnector.connectEndpoints(
            network, edgeA.getId(), false, edgeB.getId(), true));

        RoadEdge updatedA = network.getEdge(edgeA.getId());
        RoadNode shared = network.getNode(updatedA.getEndNodeId());
        assertNotNull(shared);
        assertEquals(b0.getId(), shared.getId());
        Vec2d endPt = updatedA.getCenterlinePoints().getLast();
        assertTrue(endPt.distance(shared.getPosition()) <= 1e-3);
    }

    @Test
    void tr10_beyondSnapToleranceRejectsUnchanged() {
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadEdge edgeA = network.createEdge(
            network.createNode(new Vec2d(0, 0)).getId(),
            network.createNode(new Vec2d(10, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)),
            roadA.getId());
        RoadEdge edgeB = network.createEdge(
            network.createNode(new Vec2d(12, 0)).getId(),
            network.createNode(new Vec2d(22, 0)).getId(),
            List.of(new Vec2d(12, 0), new Vec2d(22, 0)),
            roadB.getId());
        String endA = edgeA.getEndNodeId();
        String startB = edgeB.getStartNodeId();
        assertFalse(RoadExplicitConnector.connectEndpoints(
            network, edgeA.getId(), false, edgeB.getId(), true));
        assertEquals(endA, network.getEdge(edgeA.getId()).getEndNodeId());
        assertEquals(startB, network.getEdge(edgeB.getId()).getStartNodeId());
    }

    @Test
    void tr11_failedSplitLeavesNoOrphanNode() {
        int nodesBefore = network.getNodes().size();
        Optional<RoadGraphEdits.SplitResult> split =
            RoadGraphEdits.of(network).splitEdgeAtPoint("missing", new Vec2d(1, 1));
        assertTrue(split.isEmpty());
        assertEquals(nodesBefore, network.getNodes().size());

        Road road = network.createRoad("r");
        RoadEdge edge = network.createEdge(
            network.createNode(new Vec2d(0, 0)).getId(),
            network.createNode(new Vec2d(10, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)),
            road.getId());
        nodesBefore = network.getNodes().size();
        // Endpoint local distance cannot interior-split — must not leave orphan.
        assertTrue(RoadGraphEdits.of(network).splitEdgeAtLocalDistance(edge.getId(), 0.0).isEmpty());
        assertEquals(nodesBefore, network.getNodes().size());
    }

    @Test
    void tr12_failedMergeThroughNodeRestoresTopology() {
        Road road = network.createRoad("r");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode mid = network.createNode(new Vec2d(5, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadEdge e1 = network.createEdge(a.getId(), mid.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(5, 0)), road.getId());
        RoadEdge e2 = network.createEdge(mid.getId(), b.getId(),
            List.of(new Vec2d(5, 0), new Vec2d(10, 0)), road.getId());
        // Degree != 2 after removing one connection artificially — merge should no-op cleanly.
        mid.removeEdge(e2.getId());
        Optional<RoadGraphEdits.MergeResult> merged = RoadGraphEdits.of(network).mergeThroughNode(mid.getId());
        assertTrue(merged.isEmpty());
        assertNotNull(network.getNode(mid.getId()));
        assertNotNull(network.getEdge(e1.getId()));
        // Restore adjacency for a successful merge check path: re-add then force failure via self-loop.
        mid.addEdge(e2.getId());
        // Real success path still works.
        Optional<RoadGraphEdits.MergeResult> ok = RoadGraphEdits.of(network).mergeThroughNode(mid.getId());
        assertTrue(ok.isPresent());
        assertNull(network.getNode(mid.getId()));
    }

    @Test
    void tr13_mergeFormingRealRingPromotesLoop() {
        Road head = network.createRoad("head");
        Road tail = network.createRoad("tail");
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(10, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 10));
        RoadNode n3 = network.createNode(new Vec2d(0, 10));
        String h0 = network.createEdge(n0.getId(), n1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), head.getId()).getId();
        String h1 = network.createEdge(n1.getId(), n2.getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)), head.getId()).getId();
        String t0 = network.createEdge(n2.getId(), n3.getId(),
            List.of(new Vec2d(10, 10), new Vec2d(0, 10)), tail.getId()).getId();
        String t1 = network.createEdge(n3.getId(), n0.getId(),
            List.of(new Vec2d(0, 10), new Vec2d(0, 0)), tail.getId()).getId();
        head.reorderSegments(List.of(h0, h1));
        tail.reorderSegments(List.of(t0, t1));

        String merged = manager.mergeRoadTailIntoHead(head.getId(), tail.getId());
        assertEquals(head.getId(), merged);
        network = manager.getNetwork();
        Road after = network.getRoad(head.getId());
        assertEquals(RoadTopologyMode.LOOP, after.getTopologyMode());
        assertNotNull(after.getLoopSeam());
    }

    @Test
    void tr14_nullRoadEdgeScrubbedFromForeignSegmentLists() {
        Road road = network.createRoad("r");
        RoadEdge edge = network.createEdge(
            network.createNode(new Vec2d(0, 0)).getId(),
            network.createNode(new Vec2d(5, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(5, 0)),
            road.getId());
        // Stale membership: null roadId but still listed on the road.
        edge.setRoadId(null);
        assertTrue(road.getOrderedSegmentIds().contains(edge.getId()));
        network.reconcileRoadSegmentLinks();
        assertFalse(road.getOrderedSegmentIds().contains(edge.getId()));
        assertNull(edge.getRoadId());
    }

    @Test
    void tr15_ambiguousReconcilePropagatesPending() {
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(10, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)),
            a.getId());
        network.createEdge(
            network.createNode(new Vec2d(5, 0)).getId(),
            network.createNode(new Vec2d(5, 10)).getId(),
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)),
            b.getId());
        // Two design records nearly equidistant from the true (5,5) detection → contested assist.
        // Distances to (5,5) are ~0.07 and ~0.07; |Δ| ≤ AMBIGUITY_TOLERANCE (0.1).
        network.registerCrossing(RoadCrossing.gradeSeparated(
            a.getId(), 4.0, b.getId(), 4.0, new Vec2d(5.05, 5.05), b.getId(), 4.0));
        network.registerCrossing(RoadCrossing.gradeSeparated(
            a.getId(), 6.0, b.getId(), 6.0, new Vec2d(4.95, 4.95), b.getId(), 8.0));

        var result = manager.reconcileCrossings();
        assertEquals(com.plot.plugin.road.IntersectionResult.INCOMPLETE, result);
        assertTrue(manager.isAdoptIntersectionRepairPending());

        manager.undo();
        network = manager.getNetwork();
        manager.refreshCrossingIntegrityState();
        assertTrue(manager.isAdoptIntersectionRepairPending(),
            "contested registry after undo must still probe as pending");

        // Inline observe path must also set the flag (floating-editor contract).
        manager.observeReconcileAfterExternalEdit();
        assertTrue(manager.isAdoptIntersectionRepairPending());
    }

    @Test
    void tr16_undoRedoRestoresNetworkAndCrossingDesign() {
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadNode a0 = network.createNode(new Vec2d(0, 5));
        RoadNode a1 = network.createNode(new Vec2d(10, 5));
        RoadNode a2 = network.createNode(new Vec2d(20, 5));
        String e0 = network.createEdge(a0.getId(), a1.getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), a.getId()).getId();
        String e1 = network.createEdge(a1.getId(), a2.getId(),
            List.of(new Vec2d(10, 5), new Vec2d(20, 5)), a.getId()).getId();
        a.reorderSegments(List.of(e0, e1));
        network.createEdge(
            network.createNode(new Vec2d(15, 0)).getId(),
            network.createNode(new Vec2d(15, 10)).getId(),
            List.of(new Vec2d(15, 0), new Vec2d(15, 10)),
            b.getId());
        RoadCrossing design = RoadCrossing.gradeSeparated(
            a.getId(), 15.0, b.getId(), 5.0, new Vec2d(15, 5), b.getId(), 3.0);
        network.registerCrossing(design);
        String crossingId = design.id();

        manager.splitRoadBeforeSegment(a.getId(), e1);
        network = manager.getNetwork();
        assertNotNull(network.getCrossing(crossingId));

        manager.undo();
        network = manager.getNetwork();
        assertNotNull(network.getCrossing(crossingId));
        assertEquals(a.getId(), network.getCrossing(crossingId).roadAId());
        assertEquals(2, network.getRoad(a.getId()).getOrderedSegmentIds().size());
        assertFalse(manager.isAdoptIntersectionRepairPending());

        manager.redo();
        network = manager.getNetwork();
        assertNotNull(network.getCrossing(crossingId));
        assertEquals(CrossingType.GRADE_SEPARATED, network.getCrossing(crossingId).type());
        assertEquals(3.0, network.getCrossing(crossingId).crossingClearance(), 1e-6);
    }

    @Test
    void vr01_figureEightDualPassStationsSurviveReapply() {
        Road road = figureEight();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing before = network.getStackCrossings().values().iterator().next();
        double sA = before.stationA();
        double sB = before.stationB();
        assertTrue(Math.abs(sA - sB) > 1.0, "two physical passes must differ");
        assertEquals(7.07, sA, 0.2);
        assertEquals(31.21, sB, 0.2);

        var capture = RoadCrossingOwnershipMigrator.captureForRoads(network, Set.of(road.getId()));
        var apply = RoadCrossingOwnershipMigrator.reapply(network, capture);
        assertTrue(apply.complete());

        RoadStackCrossing after = network.getStackCrossing(before.id());
        assertNotNull(after);
        assertEquals(sA, after.stationA(), 1e-6);
        assertEquals(sB, after.stationB(), 1e-6);
        assertTrue(after.position().distance(new Vec2d(5, 5)) < 0.5);
    }

    @Test
    void vr02_loopInteriorSeamSelectsCorrectTraversalSlice() {
        Road road = squareLoop();
        String bottom = road.getOrderedSegmentIds().getFirst();
        RoadEdge edge = network.getEdge(bottom);
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), bottom, 0.5));

        double midLocal = edge.getLength() * 0.5;
        double earlyLocal = midLocal * 0.25;
        double lateLocal = midLocal + (edge.getLength() - midLocal) * 0.75;

        var early = RoadStationing.stationAt(network, road, bottom, earlyLocal);
        var late = RoadStationing.stationAt(network, road, bottom, lateLocal);
        assertTrue(early.isPresent());
        assertTrue(late.isPresent());
        assertNotEquals(early.get().chainageMeters(), late.get().chainageMeters(), 1e-3);

        // Local distance exactly at seam belongs to one slice; outside the edge fails.
        assertTrue(RoadStationing.stationAt(network, road, bottom, midLocal).isPresent());
        assertTrue(RoadStationing.stationAt(network, road, bottom, edge.getLength() + 10.0).isEmpty());
    }

    @Test
    void vr03_deleteCrossingEdgeDoesNotBindNearbyRoad() {
        Road a = network.createRoad("a");
        Road nearby = network.createRoad("nearby");
        RoadNode a0 = network.createNode(new Vec2d(0, 5));
        RoadNode a1 = network.createNode(new Vec2d(10, 5));
        RoadEdge edgeA = network.createEdge(
            a0.getId(), a1.getId(), List.of(new Vec2d(0, 5), new Vec2d(10, 5)), a.getId());
        network.createEdge(
            network.createNode(new Vec2d(5, 0)).getId(),
            network.createNode(new Vec2d(5, 10)).getId(),
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)),
            nearby.getId());
        RoadCrossing design = RoadCrossing.gradeSeparated(
            a.getId(), 5.0, nearby.getId(), 5.0, new Vec2d(5, 5), nearby.getId(), 4.0);
        network.registerCrossing(design);
        String crossingId = design.id();

        manager.deleteEdge(edgeA.getId());
        network = manager.getNetwork();

        assertNull(network.getCrossing(crossingId), "deleted edge must drop its crossing");
        assertTrue(network.crossingsForRoad(nearby.getId()).isEmpty(),
            "must not rebind the deleted pass onto the nearby road");
        assertNull(network.getEdge(edgeA.getId()));
    }

    @Test
    void vr04_migrationFailureRollsBackLogicalMerge() {
        Road road = figureEight();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED, stack.stationA(), stack.stationB(), 3.0);
        String stackId = stack.id();

        String edgeId = road.getOrderedSegmentIds().getFirst();
        CenterlineEditResult geoSplit = RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, 8.0);
        assertTrue(geoSplit.isSuccess());
        road = network.getRoad(road.getId());
        List<String> segs = RoadSegmentOrdering.orderedSegmentIds(network, road);
        String newRoadId = manager.splitRoadBeforeSegment(road.getId(), segs.get(1));
        assertNotNull(newRoadId);
        network = manager.getNetwork();

        RoadCrossing converted = network.getCrossing(stackId);
        assertNotNull(converted);
        // Corner radius cannot survive stack conversion — merge must refuse and roll back.
        network.registerCrossing(new RoadCrossing(
            converted.id(),
            converted.roadAId(),
            converted.stationA(),
            converted.roadBId(),
            converted.stationB(),
            converted.position(),
            converted.type(),
            converted.elevatedRoadId(),
            converted.crossingClearance(),
            converted.sharedElevation(),
            2.5));

        String headId = converted.roadAId();
        String tailId = converted.roadBId();
        String merged = manager.mergeRoadTailIntoHead(headId, tailId);
        assertNull(merged, "migration failure must abort the logical merge");
        network = manager.getNetwork();

        RoadCrossing kept = network.getCrossing(stackId);
        assertNotNull(kept, "original two-road design must be restored");
        assertEquals(2.5, kept.cornerRadius(), 1e-6);
        assertEquals(CrossingType.GRADE_SEPARATED, kept.type());
        assertNotNull(network.getRoad(headId));
        assertNotNull(network.getRoad(tailId));
    }

    @Test
    void vr05_farSplitPointRejectedWithoutNetworkChange() {
        Road road = network.createRoad("r");
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(20, 0));
        RoadEdge edge = network.createEdge(
            n0.getId(), n1.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());
        int edgesBefore = network.getEdges().size();
        String edgeId = edge.getId();
        double lengthBefore = edge.getLength();

        RoadNode farNode = network.createNode(new Vec2d(10, 5));
        Optional<RoadGraphEdits.SplitResult> far = RoadGraphEdits.of(network).splitEdgeAtInteriorSegment(
            edgeId, farNode.getId(), 0, new Vec2d(10, 5), 0.5);
        assertTrue(far.isEmpty());
        assertEquals(edgesBefore, network.getEdges().size());
        assertNotNull(network.getEdge(edgeId));
        assertEquals(lengthBefore, network.getEdge(edgeId).getLength(), 1e-9);

        // Wrong segment index: point lies on segment 0, but ask for segment 1.
        RoadEdge bent = network.createEdge(
            network.createNode(new Vec2d(0, 10)).getId(),
            network.createNode(new Vec2d(20, 10)).getId(),
            List.of(new Vec2d(0, 10), new Vec2d(10, 15), new Vec2d(20, 10)),
            road.getId());
        int edgesAfterBent = network.getEdges().size();
        RoadNode onFirst = network.createNode(new Vec2d(5, 12.5));
        Optional<RoadGraphEdits.SplitResult> wrongSeg = RoadGraphEdits.of(network).splitEdgeAtInteriorSegment(
            bent.getId(), onFirst.getId(), 1, new Vec2d(5, 12.5), 0.5);
        assertTrue(wrongSeg.isEmpty());
        assertEquals(edgesAfterBent, network.getEdges().size());
        assertNotNull(network.getEdge(bent.getId()));
    }

    @Test
    void vr06_reverseMergePreservesRolesOrRejectsAlongData() {
        // Ring-style join that reorients tail edges (enter via geometric end).
        Road head = network.createRoad("head");
        Road tail = network.createRoad("tail");
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(10, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 10));
        RoadNode n3 = network.createNode(new Vec2d(0, 10));
        String h0 = network.createEdge(n0.getId(), n1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), head.getId()).getId();
        String h1 = network.createEdge(n1.getId(), n2.getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)), head.getId()).getId();
        // Stored forward n0→n3→n2; ring merge walks from n2 and reverses traversal.
        String t0 = network.createEdge(n0.getId(), n3.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 10)), tail.getId()).getId();
        String t1 = network.createEdge(n3.getId(), n2.getId(),
            List.of(new Vec2d(0, 10), new Vec2d(10, 10)), tail.getId()).getId();
        head.reorderSegments(List.of(h0, h1));
        tail.reorderSegments(List.of(t0, t1));

        Road cross = network.createRoad("cross");
        network.createEdge(
            network.createNode(new Vec2d(5, -2)).getId(),
            network.createNode(new Vec2d(5, 2)).getId(),
            List.of(new Vec2d(5, -2), new Vec2d(5, 2)),
            cross.getId());
        RoadCrossing design = RoadCrossing.gradeSeparated(
            head.getId(), 5.0, cross.getId(), 2.0, new Vec2d(5, 0), cross.getId(), 4.0);
        network.registerCrossing(design);
        String crossingId = design.id();
        String elevated = design.elevatedRoadId();
        double clearance = design.crossingClearance();

        String merged = manager.mergeRoadTailIntoHead(head.getId(), tail.getId());
        assertEquals(head.getId(), merged);
        network = manager.getNetwork();
        RoadCrossing kept = network.getCrossing(crossingId);
        assertNotNull(kept);
        assertEquals(elevated, kept.elevatedRoadId());
        assertEquals(clearance, kept.crossingClearance(), 1e-6);
        assertEquals(CrossingType.GRADE_SEPARATED, kept.type());
        assertTrue(kept.involvesRoad(head.getId()));
        assertTrue(kept.involvesRoad(cross.getId()));

        // Rebuild with VA on the reverse-oriented tail → must refuse.
        manager = new RoadNetworkManager(new RoadSystemConfig("phase23-vr06b"), new RoadProjectStatus());
        network = manager.getNetwork();
        Road head2 = network.createRoad("head2");
        Road tail2 = network.createRoad("tail2");
        RoadNode m0 = network.createNode(new Vec2d(0, 0));
        RoadNode m1 = network.createNode(new Vec2d(10, 0));
        RoadNode m2 = network.createNode(new Vec2d(10, 10));
        RoadNode m3 = network.createNode(new Vec2d(0, 10));
        String a0 = network.createEdge(m0.getId(), m1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), head2.getId()).getId();
        String a1 = network.createEdge(m1.getId(), m2.getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)), head2.getId()).getId();
        String b0 = network.createEdge(m0.getId(), m3.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 10)), tail2.getId()).getId();
        String b1 = network.createEdge(m3.getId(), m2.getId(),
            List.of(new Vec2d(0, 10), new Vec2d(10, 10)), tail2.getId()).getId();
        head2.reorderSegments(List.of(a0, a1));
        tail2.reorderSegments(List.of(b0, b1));
        tail2.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(20.0, 70.0))));

        assertNull(manager.mergeRoadTailIntoHead(head2.getId(), tail2.getId()));
        assertNotNull(network.getRoad(head2.getId()));
        assertNotNull(network.getRoad(tail2.getId()));
        assertEquals(2, network.getRoad(tail2.getId()).getOrderedSegmentIds().size());
    }

    @Test
    void wr01_deleteNonCrossingEdgeKeepsGradeSeparationDesign() {
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadNode a0 = network.createNode(new Vec2d(0, 5));
        RoadNode a1 = network.createNode(new Vec2d(10, 5));
        RoadNode a2 = network.createNode(new Vec2d(20, 5));
        String e0 = network.createEdge(a0.getId(), a1.getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), a.getId()).getId();
        String e1 = network.createEdge(a1.getId(), a2.getId(),
            List.of(new Vec2d(10, 5), new Vec2d(20, 5)), a.getId()).getId();
        a.reorderSegments(List.of(e0, e1));
        network.createEdge(
            network.createNode(new Vec2d(15, 0)).getId(),
            network.createNode(new Vec2d(15, 10)).getId(),
            List.of(new Vec2d(15, 0), new Vec2d(15, 10)),
            b.getId());
        RoadCrossing design = RoadCrossing.gradeSeparated(
            a.getId(), 15.0, b.getId(), 5.0, new Vec2d(15, 5), b.getId(), 3.5);
        network.registerCrossing(design);
        String crossingId = design.id();

        manager.deleteEdge(e0);
        network = manager.getNetwork();

        RoadCrossing kept = network.getCrossing(crossingId);
        assertNotNull(kept, "crossing on surviving edge must remain");
        assertEquals(CrossingType.GRADE_SEPARATED, kept.type());
        assertEquals(3.5, kept.crossingClearance(), 1e-6);
        assertEquals(b.getId(), kept.elevatedRoadId());
        assertTrue(kept.involvesRoad(a.getId()) || kept.involvesRoad(b.getId()));
        assertNull(network.getEdge(e0));
        assertNotNull(network.getEdge(e1));
    }

    @Test
    void wr02_survivingPassResolveFailureRollsBackDelete() {
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadNode a0 = network.createNode(new Vec2d(0, 5));
        RoadNode a1 = network.createNode(new Vec2d(10, 5));
        RoadNode a2 = network.createNode(new Vec2d(20, 5));
        String e0 = network.createEdge(a0.getId(), a1.getId(),
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), a.getId()).getId();
        String e1 = network.createEdge(a1.getId(), a2.getId(),
            List.of(new Vec2d(10, 5), new Vec2d(20, 5)), a.getId()).getId();
        a.reorderSegments(List.of(e0, e1));
        network.createEdge(
            network.createNode(new Vec2d(15, 0)).getId(),
            network.createNode(new Vec2d(15, 10)).getId(),
            List.of(new Vec2d(15, 0), new Vec2d(15, 10)),
            b.getId());
        RoadCrossing good = RoadCrossing.gradeSeparated(
            a.getId(), 15.0, b.getId(), 5.0, new Vec2d(15, 5), b.getId(), 3.0);
        network.registerCrossing(good);
        // Uncapturable design on the same road → capture.hadUnresolved → delete must roll back.
        network.registerCrossing(RoadCrossing.gradeSeparated(
            a.getId(), 9999.0, b.getId(), 5.0, new Vec2d(15, 5), b.getId(), 1.0));

        int edgesBefore = network.getEdges().size();
        manager.deleteEdge(e0);
        network = manager.getNetwork();

        assertNotNull(network.getEdge(e0), "delete must roll back when migration is incomplete");
        assertEquals(edgesBefore, network.getEdges().size());
        assertNotNull(network.getCrossing(good.id()));
        assertEquals(3.0, network.getCrossing(good.id()).crossingClearance(), 1e-6);
        assertNotNull(network.getEdge(e1));
    }

    @Test
    void wr03_networkLogicalEditReturnsNullOnMigrationFailure() {
        Road road = figureEight();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED, stack.stationA(), stack.stationB(), 3.0);
        String stackId = stack.id();

        String edgeId = road.getOrderedSegmentIds().getFirst();
        assertTrue(RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, 8.0).isSuccess());
        road = network.getRoad(road.getId());
        List<String> segs = RoadSegmentOrdering.orderedSegmentIds(network, road);
        String newRoadId = network.splitRoadBeforeSegment(road.getId(), segs.get(1));
        assertNotNull(newRoadId);
        assertFalse(network.consumeCrossingMigrationFailure());

        RoadCrossing converted = network.getCrossing(stackId);
        assertNotNull(converted);
        network.registerCrossing(new RoadCrossing(
            converted.id(),
            converted.roadAId(),
            converted.stationA(),
            converted.roadBId(),
            converted.stationB(),
            converted.position(),
            converted.type(),
            converted.elevatedRoadId(),
            converted.crossingClearance(),
            converted.sharedElevation(),
            2.5));

        String beforeJson = network.toJson();
        Map<String, String> edgeOwnershipBefore = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            edgeOwnershipBefore.put(edge.getId(), edge.getRoadId());
        }
        Map<String, Integer> vaCountBefore = new LinkedHashMap<>();
        Map<String, Integer> vcsCountBefore = new LinkedHashMap<>();
        Map<String, Integer> facCountBefore = new LinkedHashMap<>();
        for (Road r : network.getRoads().values()) {
            vaCountBefore.put(r.getId(),
                r.getVerticalAlignment() == null ? 0 : r.getVerticalAlignment().pviCount());
            vcsCountBefore.put(r.getId(),
                r.getVariableCrossSections() == null ? 0 : r.getVariableCrossSections().stationCount());
            facCountBefore.put(r.getId(),
                r.getStationFacilities() == null ? 0 : r.getStationFacilities().runCount());
        }

        String merged = network.mergeRoadTailIntoHead(converted.roadAId(), converted.roadBId());
        assertNull(merged, "model merge must not report success when migration fails");
        assertTrue(network.consumeCrossingMigrationFailure());

        assertEquals(beforeJson, network.toJson(), "failed merge must leave live network JSON unchanged");
        assertEquals(edgeOwnershipBefore.size(), network.getEdges().size());
        for (RoadEdge edge : network.getEdges().values()) {
            assertEquals(edgeOwnershipBefore.get(edge.getId()), edge.getRoadId());
        }
        for (Road r : network.getRoads().values()) {
            assertEquals(vaCountBefore.get(r.getId()),
                r.getVerticalAlignment() == null ? 0 : r.getVerticalAlignment().pviCount());
            assertEquals(vcsCountBefore.get(r.getId()),
                r.getVariableCrossSections() == null ? 0 : r.getVariableCrossSections().stationCount());
            assertEquals(facCountBefore.get(r.getId()),
                r.getStationFacilities() == null ? 0 : r.getStationFacilities().runCount());
        }
    }

    @Test
    void wr04_offCenterlineSplitEdgeAtPointDoesNotWarpGeometry() {
        Road road = network.createRoad("r");
        RoadEdge edge = network.createEdge(
            network.createNode(new Vec2d(0, 0)).getId(),
            network.createNode(new Vec2d(20, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)),
            road.getId());
        double lengthBefore = edge.getLength();
        Optional<RoadGraphEdits.SplitResult> split = RoadGraphEdits.of(network).splitEdgeAtPoint(
            edge.getId(), new Vec2d(10, 0.3), 0.5);
        assertTrue(split.isPresent());
        RoadNode node = network.getNode(split.get().nodeId());
        assertNotNull(node);
        assertEquals(0.0, node.getPosition().y, 1e-6, "node must sit on the projected centerline");
        assertEquals(10.0, node.getPosition().x, 1e-3);
        RoadEdge first = network.getEdge(split.get().firstEdgeId());
        RoadEdge second = network.getEdge(split.get().secondEdgeId());
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(lengthBefore, first.getLength() + second.getLength(), 1e-6);
        assertEquals(0.0, first.getCenterlinePoints().getLast().y, 1e-6);
        assertEquals(0.0, second.getCenterlinePoints().getFirst().y, 1e-6);
    }

    @Test
    void wr05_loopDeleteWithAlongTrackDataIsProtected() {
        Road loop = squareLoop();
        String edgeId = loop.getOrderedSegmentIds().getFirst();
        loop.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(40.0, 70.0))));
        int edgesBefore = network.getEdges().size();
        int roadsBefore = network.getRoads().size();

        manager.deleteEdge(edgeId);
        network = manager.getNetwork();

        assertNotNull(network.getEdge(edgeId));
        assertEquals(edgesBefore, network.getEdges().size());
        assertEquals(roadsBefore, network.getRoads().size());
        assertEquals(RoadTopologyMode.LOOP, network.getRoad(loop.getId()).getTopologyMode());
        assertNotNull(network.getRoad(loop.getId()).getVerticalAlignment());
    }

    private Road squareLoop() {
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(10, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 10));
        RoadNode n3 = network.createNode(new Vec2d(0, 10));
        String e0 = network.createEdge(n0.getId(), n1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()).getId();
        String e1 = network.createEdge(n1.getId(), n2.getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId()).getId();
        String e2 = network.createEdge(n2.getId(), n3.getId(),
            List.of(new Vec2d(10, 10), new Vec2d(0, 10)), road.getId()).getId();
        String e3 = network.createEdge(n3.getId(), n0.getId(),
            List.of(new Vec2d(0, 10), new Vec2d(0, 0)), road.getId()).getId();
        road.reorderSegments(List.of(e0, e1, e2, e3));
        road.setLoopSeam(com.plot.plugin.road.station.RoadLoopSeamService.computeDefault(network, road));
        return road;
    }

    private Road figureEight() {
        Road road = network.createRoad("figure-eight");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10),
                new Vec2d(10, 0)),
            road.getId());
        return road;
    }
}
