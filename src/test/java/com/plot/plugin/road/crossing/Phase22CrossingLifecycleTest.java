package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.IntersectionProbeResult;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.RoadLoopSeamService;
import com.plot.plugin.road.station.RoadStationDataTransforms;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.2 crossing lifecycle regressions (L01–L25 priority coverage).
 */
class Phase22CrossingLifecycleTest {

    @Test
    void l01_reconcilePreservesCrossingIdAndDesign() {
        RoadNetwork network = crossRoads();
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing original = network.getCrossings().values().iterator().next();
        String id = original.id();
        network.setCrossingGradeSeparation(id, CrossingType.GRADE_SEPARATED, original.roadBId(), 7.0);

        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing kept = network.getCrossing(id);
        assertNotNull(kept);
        assertEquals(CrossingType.GRADE_SEPARATED, kept.type());
        assertEquals(7.0, kept.crossingClearance(), 1e-6);
        assertEquals(original.roadBId(), kept.elevatedRoadId());
    }

    @Test
    void l02_l03_exactMatchBeatsNeighborRegardlessOfOrder() {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadCrossing oldExact = RoadCrossing.gradeSeparated(
            a.getId(), 5.0, b.getId(), 5.0, new Vec2d(5, 5), b.getId(), 4.0);
        RoadCrossing oldNear = RoadCrossing.atGrade(
            a.getId(), 8.0, b.getId(), 2.0, new Vec2d(5.2, 5.1));
        network.registerCrossing(oldExact);
        network.registerCrossing(oldNear);

        RoadCrossing detectedExact = RoadCrossing.atGrade(
            a.getId(), 5.0, b.getId(), 5.0, new Vec2d(5.01, 5.01));
        RoadCrossing detectedNear = RoadCrossing.atGrade(
            a.getId(), 8.0, b.getId(), 2.0, new Vec2d(5.25, 5.15));

        List<CrossingMatchAssignment> forward = RoadCrossingMatcher.matchAll(
            List.of(detectedNear, detectedExact),
            List.of(oldExact, oldNear));
        List<CrossingMatchAssignment> reverse = RoadCrossingMatcher.matchAll(
            List.of(detectedExact, detectedNear),
            List.of(oldExact, oldNear));

        assertEquals(CrossingMatchOutcome.MATCHED, forward.get(1).outcome());
        assertEquals(oldExact.id(), forward.get(1).existingId());
        assertEquals(CrossingMatchOutcome.MATCHED, reverse.get(0).outcome());
        assertEquals(oldExact.id(), reverse.get(0).existingId());
        assertEquals(
            Set.of(forward.get(0).existingId(), forward.get(1).existingId()),
            Set.of(reverse.get(0).existingId(), reverse.get(1).existingId()));
    }

    @Test
    void d01_contestedAssistMatchIsAmbiguousRegardlessOfOrder() {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadCrossing old = RoadCrossing.gradeSeparated(
            a.getId(), 5.0, b.getId(), 5.0, new Vec2d(5, 5), b.getId(), 4.0);

        // Two detections both within assist tolerance of the same old record, no exact keys.
        RoadCrossing far = RoadCrossing.atGrade(
            a.getId(), 4.7, b.getId(), 5.3, new Vec2d(5.30, 5.0));
        RoadCrossing near = RoadCrossing.atGrade(
            a.getId(), 5.2, b.getId(), 4.8, new Vec2d(5.10, 5.0));

        List<CrossingMatchAssignment> forward = RoadCrossingMatcher.matchAll(
            List.of(far, near), List.of(old));
        List<CrossingMatchAssignment> reverse = RoadCrossingMatcher.matchAll(
            List.of(near, far), List.of(old));

        assertEquals(CrossingMatchOutcome.AMBIGUOUS, forward.get(0).outcome());
        assertEquals(CrossingMatchOutcome.AMBIGUOUS, forward.get(1).outcome());
        assertEquals(CrossingMatchOutcome.AMBIGUOUS, reverse.get(0).outcome());
        assertEquals(CrossingMatchOutcome.AMBIGUOUS, reverse.get(1).outcome());
    }

    @Test
    void d01_duplicateStableKeyIsAmbiguous() {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadCrossing first = RoadCrossing.gradeSeparated(
            a.getId(), 5.0, b.getId(), 5.0, new Vec2d(5, 5), b.getId(), 4.0);
        RoadCrossing duplicateKey = new RoadCrossing(
            "dup-" + first.id(),
            a.getId(),
            5.0,
            b.getId(),
            5.0,
            new Vec2d(5.01, 5.01),
            CrossingType.GRADE_SEPARATED,
            b.getId(),
            9.0,
            null,
            null);
        assertEquals(first.stableKey(), duplicateKey.stableKey());

        RoadCrossing detected = RoadCrossing.atGrade(
            a.getId(), 5.0, b.getId(), 5.0, new Vec2d(5.0, 5.0));
        List<CrossingMatchAssignment> assignments = RoadCrossingMatcher.matchAll(
            List.of(detected), List.of(first, duplicateKey));
        assertEquals(CrossingMatchOutcome.AMBIGUOUS, assignments.getFirst().outcome());
    }

    @Test
    void d04_generatorBlocksWhenReconcileIsAmbiguous() {
        RoadNetwork network = crossRoads();
        List<Road> roads = List.copyOf(network.getRoads().values());
        Road a = roads.get(0);
        Road b = roads.get(1);
        network.registerCrossing(RoadCrossing.gradeSeparated(
            a.getId(), 4.0, b.getId(), 4.0, new Vec2d(5, 5), b.getId(), 6.0));
        network.registerCrossing(RoadCrossing.gradeSeparated(
            a.getId(), 6.0, b.getId(), 6.0, new Vec2d(5.05, 5.05), b.getId(), 8.0));

        RoadSystemConfig config = new RoadSystemConfig("phase22-d04");
        RoadNetworkGenerator generator = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetworkGenerator.NetworkGenerationResult result =
            generator.generateAll(network, new FlatTerrainSampler(64));
        assertTrue(result.hasCrossingReconcileIncomplete());
        assertTrue(result.hasPartialFailure());
    }

    @Test
    void l05_ambiguousMatchDoesNotDeleteDesign() {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadCrossing first = RoadCrossing.gradeSeparated(
            a.getId(), 4.0, b.getId(), 4.0, new Vec2d(5, 5), b.getId(), 6.0);
        RoadCrossing second = RoadCrossing.gradeSeparated(
            a.getId(), 6.0, b.getId(), 6.0, new Vec2d(5.05, 5.05), b.getId(), 8.0);
        network.registerCrossing(first);
        network.registerCrossing(second);

        // One detection near both — ambiguous; both designs must remain.
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

        List<RoadCrossing> detected = List.of(RoadCrossing.atGrade(
            a.getId(), 5.0, b.getId(), 5.0, new Vec2d(5.02, 5.02)));
        List<CrossingMatchAssignment> assignments = RoadCrossingMatcher.matchAll(
            detected, List.of(first, second));
        assertEquals(CrossingMatchOutcome.AMBIGUOUS, assignments.getFirst().outcome());

        CrossingReconcileResult result = RoadCrossingReconciler.reconcileCrossingsDetailed(network);
        assertTrue(result.hasAmbiguity() || network.getCrossing(first.id()) != null);
        assertNotNull(network.getCrossing(first.id()));
        assertNotNull(network.getCrossing(second.id()));
        assertEquals(6.0, network.getCrossing(first.id()).crossingClearance(), 1e-6);
        assertEquals(8.0, network.getCrossing(second.id()).crossingClearance(), 1e-6);
    }

    @Test
    void l06_trulyGoneCrossingIsRemoved() {
        RoadNetwork network = crossRoads();
        RoadCrossingReconciler.reconcileCrossings(network);
        assertEquals(1, network.getCrossings().size());
        String id = network.getCrossings().values().iterator().next().id();

        // Move vertical road away so the crossing disappears.
        Road vertical = network.getRoad(
            network.getCrossings().values().iterator().next().roadBId());
        RoadEdge edge = network.getEdge(vertical.getOrderedSegmentIds().getFirst());
        edge.setCenterlinePoints(List.of(new Vec2d(50, 0), new Vec2d(50, 10)));
        network.getNode(edge.getStartNodeId()).setPosition(new Vec2d(50, 0));
        network.getNode(edge.getEndNodeId()).setPosition(new Vec2d(50, 10));

        RoadCrossingReconciler.reconcileCrossings(network);
        assertTrue(network.getCrossing(id) == null);
        assertTrue(network.getCrossings().isEmpty());
    }

    @Test
    void l07_seamShiftRotatesStationInCorrectDirection() {
        // oldOffset=0, newOffset=30, station=50 → newStation=20
        double rotated = RoadLoopSeamService.rotateLoopStation(50.0, 30.0, 100.0);
        assertEquals(20.0, rotated, 1e-6);
        assertNotEquals(80.0, rotated, 1e-6);
    }

    @Test
    void l08_seamRotatePreservesStackLowerUpperIdentity() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(100, 0));
        network.createEdge(n0.getId(), n1.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        network.createEdge(n1.getId(), n0.getId(), List.of(new Vec2d(100, 0), new Vec2d(0, 0)), road.getId());

        RoadStackCrossing stack = RoadStackCrossing.gradeSeparated(
            road.getId(), 20.0, 70.0, new Vec2d(0, 0), 5.0);
        network.registerStackCrossing(stack);
        assertEquals(20.0, stack.resolvedLowerStation(), 1e-6);
        assertEquals(70.0, stack.resolvedUpperStation(), 1e-6);

        RoadStationDataTransforms.rotateLoopStations(network, road, 30.0, 100.0);
        RoadStackCrossing rotated = network.getStackCrossing(stack.id());
        assertNotNull(rotated);
        // 20→90, 70→40; physical lower remains the pass that was 20.
        assertEquals(90.0, rotated.resolvedLowerStation(), 1e-6);
        assertEquals(40.0, rotated.resolvedUpperStation(), 1e-6);
        assertEquals(CrossingType.GRADE_SEPARATED, rotated.type());
        assertEquals(5.0, rotated.crossingClearance(), 1e-6);
    }

    @Test
    void l09_crossingOnlyLoopStillMigratesStations() {
        RoadNetwork network = new RoadNetwork();
        Road loop = network.createRoad("loop");
        loop.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(40, 0));
        network.createEdge(n0.getId(), n1.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), loop.getId());
        network.createEdge(n1.getId(), n0.getId(), List.of(new Vec2d(40, 0), new Vec2d(0, 0)), loop.getId());
        loop.setLoopSeam(RoadLoopSeam.at(new Vec2d(0, 0)));

        Road spur = network.createRoad("spur");
        network.createEdge(
            network.createNode(new Vec2d(20, -5)).getId(),
            network.createNode(new Vec2d(20, 5)).getId(),
            List.of(new Vec2d(20, -5), new Vec2d(20, 5)),
            spur.getId());
        RoadCrossingReconciler.reconcileCrossings(network);
        assertFalse(network.getCrossings().isEmpty());
        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        double before = crossing.stationOn(loop.getId());
        network.setCrossingGradeSeparation(crossing.id(), CrossingType.GRADE_SEPARATED, spur.getId(), 4.0);

        RoadStationDataTransforms.rotateLoopStations(network, loop, 10.0, 80.0);
        RoadCrossing after = network.getCrossing(crossing.id());
        assertEquals(
            RoadLoopSeamService.rotateLoopStation(before, 10.0, 80.0),
            after.stationOn(loop.getId()),
            1e-4);
        assertEquals(CrossingType.GRADE_SEPARATED, after.type());
    }

    @Test
    void l12_l13_missingRoadOrStationReportsIncomplete() {
        RoadNetwork network = new RoadNetwork();
        RoadStackCrossing orphan = RoadStackCrossing.atGrade(
            "missing-road", 1.0, 10.0, new Vec2d(0, 0));
        network.registerStackCrossing(orphan);

        CrossingMaterializationReport report = RoadStackCrossingMaterializer.materializeAll(
            network, network.getStackCrossings().values());
        assertFalse(report.isComplete());
        assertEquals(IntersectionResult.INCOMPLETE, report.toIntersectionResult());
        assertFalse(report.failures().isEmpty());
    }

    @Test
    void l14_gradeSwapExchangesConcreteArmSets() {
        RoadNetwork network = figureEightNetwork();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        network.setStackCrossingGradeSeparation(
            stack.id(),
            CrossingType.GRADE_SEPARATED,
            stack.stationA(),
            stack.stationB(),
            3.0);

        SnapshotMaterialization first = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertTrue(first.report().isComplete());
        RoadNode junction = first.network().getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        Set<String> lowerBefore = armDirectionKeys(junction, junction.getStackLowerEdgeIds(), first.network());
        Set<String> upperBefore = armDirectionKeys(junction, junction.getStackUpperEdgeIds(), first.network());
        assertEquals(2, lowerBefore.size());
        assertEquals(2, upperBefore.size());
        assertNotEquals(lowerBefore, upperBefore);

        network.setStackCrossingGradeSeparation(
            stack.id(),
            CrossingType.GRADE_SEPARATED,
            stack.stationB(),
            stack.stationA(),
            3.0);
        SnapshotMaterialization swapped = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        RoadNode swappedJunction = swapped.network().getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        // Snapshot splits mint new edge UUIDs; compare geometric arm identity.
        assertEquals(upperBefore, armDirectionKeys(
            swappedJunction, swappedJunction.getStackLowerEdgeIds(), swapped.network()));
        assertEquals(lowerBefore, armDirectionKeys(
            swappedJunction, swappedJunction.getStackUpperEdgeIds(), swapped.network()));
    }

    @Test
    void f01_reversedFigureEightArmDirectionsMatchForward() {
        SnapshotMaterialization forward = gradeSeparatedFigureEight(figureEightNetwork());
        SnapshotMaterialization reversed = gradeSeparatedFigureEight(figureEightNetworkReversed());
        assertTrue(forward.report().isComplete(), forward.report().failures().toString());
        assertTrue(reversed.report().isComplete(), reversed.report().failures().toString());

        RoadNode fwdJunction = forward.network().getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        RoadNode revJunction = reversed.network().getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Set<String> fwdLower = armDirectionKeys(
            fwdJunction, fwdJunction.getStackLowerEdgeIds(), forward.network());
        Set<String> fwdUpper = armDirectionKeys(
            fwdJunction, fwdJunction.getStackUpperEdgeIds(), forward.network());
        Set<String> revLower = armDirectionKeys(
            revJunction, revJunction.getStackLowerEdgeIds(), reversed.network());
        Set<String> revUpper = armDirectionKeys(
            revJunction, revJunction.getStackUpperEdgeIds(), reversed.network());
        // Pass A/B labeling may flip under reversed storage; compare as unordered pair.
        assertTrue(
            (fwdLower.equals(revLower) && fwdUpper.equals(revUpper))
                || (fwdLower.equals(revUpper) && fwdUpper.equals(revLower)),
            () -> "forward lower=" + fwdLower + " upper=" + fwdUpper
                + " reversed lower=" + revLower + " upper=" + revUpper);
    }

    private static SnapshotMaterialization gradeSeparatedFigureEight(RoadNetwork network) {
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        network.setStackCrossingGradeSeparation(
            stack.id(),
            CrossingType.GRADE_SEPARATED,
            stack.stationA(),
            stack.stationB(),
            3.0);
        return RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
    }

    /** Fingerprint arms by unit direction leaving the junction (stable across snapshot UUIDs). */
    private static Set<String> armDirectionKeys(RoadNode junction, Set<String> edgeIds, RoadNetwork network) {
        Set<String> keys = new HashSet<>();
        for (String edgeId : edgeIds) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            List<Vec2d> pts = edge.getCenterlinePoints();
            if (pts.size() < 2) {
                continue;
            }
            boolean atStart = edge.getStartNodeId().equals(junction.getId());
            Vec2d from = atStart ? pts.getFirst() : pts.getLast();
            Vec2d toward = atStart ? pts.get(1) : pts.get(pts.size() - 2);
            double dx = toward.x - from.x;
            double dy = toward.y - from.y;
            double len = Math.hypot(dx, dy);
            if (len <= 1e-9) {
                continue;
            }
            keys.add(String.format(java.util.Locale.ROOT, "%.3f,%.3f", dx / len, dy / len));
        }
        return keys;
    }

    @Test
    void l18_atGradeClearsNodeGradeSeparation() {
        RoadNetwork network = crossRoads();
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, crossing.roadBId(), 5.0);

        SnapshotMaterialization graded = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        RoadNode gradedNode = graded.network().getNodes().values().stream()
            .filter(RoadNode::isGradeSeparated)
            .findFirst()
            .orElseThrow();
        assertTrue(gradedNode.isGradeSeparated());

        network.setCrossingGradeSeparation(crossing.id(), CrossingType.AT_GRADE, null, null);
        SnapshotMaterialization flat = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        RoadNode flatNode = flat.network().getNodes().values().stream()
            .filter(node -> node.getPosition().distance(gradedNode.getPosition()) < 0.5)
            .findFirst()
            .orElseThrow();
        assertFalse(flatNode.isGradeSeparated());
    }

    @Test
    void l19_authoritativeClearanceAllowsDecrease() {
        RoadNetwork network = new RoadNetwork();
        RoadNode node = network.createNode(new Vec2d(0, 0));
        network.applyCrossingGradeSeparation(node.getId(), null, 8.0, false);
        assertEquals(8.0, network.getNode(node.getId()).getCrossingClearance(), 1e-6);

        network.applyCrossingGradeSeparation(node.getId(), null, 5.0, true);
        assertEquals(5.0, network.getNode(node.getId()).getCrossingClearance(), 1e-6);
    }

    @Test
    void l21_l22_probeCoversStackRegistry() {
        RoadNetwork network = figureEightNetwork();
        // Detected but not registered → incomplete probe
        IntersectionProbeResult before = RoadCrossingReconciler.probeRegistryCompleteness(network);
        assertTrue(before.isIncomplete() || before.hasPendingWork());

        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadCrossingReconciler.reconcileCrossings(network);
        IntersectionProbeResult after = RoadCrossingReconciler.probeRegistryCompleteness(network);
        assertFalse(after.isIncomplete());
        assertFalse(network.getStackCrossings().isEmpty());
    }

    @Test
    void l24_largeNetworkReconcileIsStable() {
        RoadNetwork network = new RoadNetwork();
        for (int i = 0; i < 40; i++) {
            Road h = network.createRoad("h" + i);
            double y = i * 5.0;
            network.createEdge(
                network.createNode(new Vec2d(0, y)).getId(),
                network.createNode(new Vec2d(200, y)).getId(),
                List.of(new Vec2d(0, y), new Vec2d(200, y)),
                h.getId());
        }
        for (int i = 0; i < 40; i++) {
            Road v = network.createRoad("v" + i);
            double x = i * 5.0 + 2.5;
            network.createEdge(
                network.createNode(new Vec2d(x, -5)).getId(),
                network.createNode(new Vec2d(x, 205)).getId(),
                List.of(new Vec2d(x, -5), new Vec2d(x, 205)),
                v.getId());
        }
        CrossingReconcileResult first = RoadCrossingReconciler.reconcileCrossingsDetailed(network);
        int count = network.getCrossings().size();
        assertTrue(count > 100);
        CrossingReconcileResult second = RoadCrossingReconciler.reconcileCrossingsDetailed(network);
        assertEquals(0, second.added());
        assertEquals(0, second.removed());
        assertEquals(count, network.getCrossings().size());
        assertTrue(first.added() > 0);
    }

    @Test
    void snapshotReportMergesStackFailures() {
        RoadNetwork network = new RoadNetwork();
        network.registerStackCrossing(RoadStackCrossing.atGrade(
            "ghost", 1.0, 9.0, new Vec2d(1, 1)));
        SnapshotMaterialization materialization =
            RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertFalse(materialization.report().isComplete());
        assertFalse(materialization.report().failures().isEmpty());
    }

    @Test
    void d07_stackGradeSeparationRejectsInvalidStationsAndClearance() {
        RoadNetwork network = figureEightNetwork();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();

        assertFalse(network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED, stack.stationA(), stack.stationA(), 3.0));
        // Both inputs near the same pass (stationA ± eps) must be rejected.
        assertFalse(network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED,
            stack.stationA() + 0.1, stack.stationA() - 0.1, 3.0));
        assertFalse(network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED, -999.0, stack.stationB(), 3.0));
        assertFalse(network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED, stack.stationA(), stack.stationB(), Double.NaN));
        assertTrue(network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED, stack.stationA() + 0.2, stack.stationB() - 0.2, 3.0));
        RoadStackCrossing normalized = network.getStackCrossings().get(stack.id());
        assertEquals(stack.stationA(), normalized.resolvedLowerStation(), 1e-9);
        assertEquals(stack.stationB(), normalized.resolvedUpperStation(), 1e-9);
        assertTrue(network.setStackCrossingGradeSeparation(
            stack.id(), CrossingType.GRADE_SEPARATED, stack.stationB(), stack.stationA(), 5.0));
        RoadStackCrossing swapped = network.getStackCrossings().get(stack.id());
        assertEquals(stack.stationB(), swapped.resolvedLowerStation(), 1e-9);
        assertEquals(stack.stationA(), swapped.resolvedUpperStation(), 1e-9);
        assertEquals(5.0, swapped.crossingClearance(), 1e-6);
    }

    @Test
    void d07_authoritativeClearanceDecreaseSurvivesMaterialize() {
        RoadNetwork network = crossRoads();
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, crossing.roadBId(), 8.0);

        SnapshotMaterialization first = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        RoadNode graded = first.network().getNodes().values().stream()
            .filter(RoadNode::isGradeSeparated)
            .findFirst()
            .orElseThrow();
        assertEquals(8.0, graded.getCrossingClearance(), 1e-6);

        network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, crossing.roadBId(), 5.0);
        SnapshotMaterialization second = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        RoadNode lowered = second.network().getNodes().values().stream()
            .filter(RoadNode::isGradeSeparated)
            .findFirst()
            .orElseThrow();
        assertEquals(5.0, lowered.getCrossingClearance(), 1e-6);
    }

    @Test
    void d06_incompatibleMultiLayerGradeFailsMaterialize() {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        Road c = network.createRoad("c");
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
        network.createEdge(
            network.createNode(new Vec2d(0, 0)).getId(),
            network.createNode(new Vec2d(10, 10)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 10)),
            c.getId());
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing ab = network.getCrossings().values().stream()
            .filter(x -> x.involvesRoad(a.getId()) && x.involvesRoad(b.getId()))
            .findFirst()
            .orElseThrow();
        RoadCrossing ac = network.getCrossings().values().stream()
            .filter(x -> x.involvesRoad(a.getId()) && x.involvesRoad(c.getId()))
            .findFirst()
            .orElseThrow();
        // Incompatible: A elevated over B, then C elevated over A at the same node.
        assertTrue(network.setCrossingGradeSeparation(
            ab.id(), CrossingType.GRADE_SEPARATED, a.getId(), 4.0));
        assertTrue(network.setCrossingGradeSeparation(
            ac.id(), CrossingType.GRADE_SEPARATED, c.getId(), 4.0));
        assertNotEquals(
            network.getCrossing(ab.id()).elevatedRoadId(),
            network.getCrossing(ac.id()).elevatedRoadId());

        SnapshotMaterialization materialization =
            RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertFalse(materialization.report().isComplete());
        assertTrue(materialization.report().failures().stream()
            .anyMatch(f -> f.detail() != null && f.detail().contains("incompatible")));
    }

    @Test
    void e03_sameCrossingElevatedSwapIsNotForeignConflict() {
        RoadNetwork network = crossRoads();
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, crossing.roadAId(), 4.0);
        SnapshotMaterialization first = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertTrue(first.report().isComplete());

        network.setCrossingGradeSeparation(
            crossing.id(), CrossingType.GRADE_SEPARATED, crossing.roadBId(), 4.0);
        SnapshotMaterialization swapped = RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertTrue(swapped.report().isComplete(), swapped.report().failures().toString());
        RoadNode junction = swapped.network().getNodes().values().stream()
            .filter(RoadNode::isGradeSeparated)
            .findFirst()
            .orElseThrow();
        assertEquals(crossing.roadBId(), junction.getElevatedRoadId());
    }

    @Test
    void e02_stationSplitFarFromCrossingPositionFails() {
        RoadNetwork network = crossRoads();
        RoadCrossingReconciler.reconcileCrossings(network);
        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        // Keep valid stations but move registry position far from the true intersection.
        network.registerCrossing(crossing.withRefreshedGeometry(
            crossing.stationA(), crossing.stationB(), new Vec2d(50, 50)));

        SnapshotMaterialization materialization =
            RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertFalse(materialization.report().isComplete());
        assertFalse(materialization.report().failures().isEmpty());
    }

    @Test
    void f02_junctionIncidentEdgeWithFarStationFails() {
        // Edge A already touches junction at its start; registry claims that junction position
        // but station A is far along the edge — must not false-ALREADY_CONNECTED.
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("road-a");
        Road b = network.createRoad("road-b");
        RoadNode junction = network.createNode(new Vec2d(0, 5));
        network.createEdge(
            junction.getId(),
            network.createNode(new Vec2d(20, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(20, 5)),
            a.getId());
        network.createEdge(
            network.createNode(new Vec2d(10, 0)).getId(),
            network.createNode(new Vec2d(10, 10)).getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)),
            b.getId());
        network.registerCrossing(RoadCrossing.atGrade(
            a.getId(), 10.0, b.getId(), 5.0, new Vec2d(0, 5)));

        SnapshotMaterialization materialization =
            RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertFalse(materialization.report().isComplete());
        assertTrue(materialization.report().failures().stream()
            .anyMatch(f -> f.armA() == CrossingMaterializeStatus.SPLIT_FAILED
                || f.armB() == CrossingMaterializeStatus.SPLIT_FAILED));
    }

    @Test
    void f02_positionNearEndpointWithInteriorStationDoesNotAlreadyConnect() {
        // Registry position sits on A's start endpoint, but station A is mid-edge.
        // Spatial endpoint proximity must not yield false ALREADY_CONNECTED / endpoint connect.
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("road-a");
        Road b = network.createRoad("road-b");
        network.createEdge(
            network.createNode(new Vec2d(0, 5)).getId(),
            network.createNode(new Vec2d(20, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(20, 5)),
            a.getId());
        network.createEdge(
            network.createNode(new Vec2d(10, 0)).getId(),
            network.createNode(new Vec2d(10, 10)).getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)),
            b.getId());
        network.registerCrossing(RoadCrossing.atGrade(
            a.getId(), 10.0, b.getId(), 5.0, new Vec2d(0.2, 5)));

        SnapshotMaterialization materialization =
            RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertFalse(materialization.report().isComplete());
        assertTrue(materialization.report().failures().stream()
            .anyMatch(f -> f.armA() == CrossingMaterializeStatus.SPLIT_FAILED));
        assertTrue(materialization.report().failures().stream()
            .noneMatch(f -> f.armA() == CrossingMaterializeStatus.ALREADY_CONNECTED
                || f.armA() == CrossingMaterializeStatus.SUCCESS));
    }

    @Test
    void f02_interiorStationSplitsIntoTwoHalfEdges() {
        RoadNetwork network = crossRoads();
        RoadCrossingReconciler.reconcileCrossings(network);
        SnapshotMaterialization materialization =
            RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertTrue(materialization.report().isComplete(), materialization.report().failures().toString());
        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        RoadNode junction = materialization.network().getNodes().values().stream()
            .filter(node -> node.getPosition().distance(crossing.position()) < 0.5)
            .findFirst()
            .orElseThrow();
        long roadAArms = junction.getConnectedEdgeIds().stream()
            .map(materialization.network()::getEdge)
            .filter(edge -> edge != null && crossing.roadAId().equals(edge.getRoadId()))
            .count();
        long roadBArms = junction.getConnectedEdgeIds().stream()
            .map(materialization.network()::getEdge)
            .filter(edge -> edge != null && crossing.roadBId().equals(edge.getRoadId()))
            .count();
        assertEquals(2, roadAArms);
        assertEquals(2, roadBArms);
    }

    @Test
    void f02_crossRoadsAndFigureEightHappyPathsStayComplete() {
        RoadNetwork cross = crossRoads();
        RoadCrossingReconciler.reconcileCrossings(cross);
        SnapshotMaterialization crossMat =
            RoadCrossingMaterializer.materializeForSnapshotWithReport(cross);
        assertTrue(crossMat.report().isComplete(), crossMat.report().failures().toString());

        SnapshotMaterialization eight = gradeSeparatedFigureEight(figureEightNetwork());
        assertTrue(eight.report().isComplete(), eight.report().failures().toString());
    }

    @Test
    void d02_stationLocalSplitUsesResolvedLocalDistance() {
        RoadNetwork network = figureEightNetwork();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        network.setStackCrossingGradeSeparation(
            stack.id(),
            CrossingType.GRADE_SEPARATED,
            stack.stationA(),
            stack.stationB(),
            3.0);
        SnapshotMaterialization materialization =
            RoadCrossingMaterializer.materializeForSnapshotWithReport(network);
        assertTrue(materialization.report().isComplete(), materialization.report().failures().toString());
        RoadNode junction = materialization.network().getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        assertEquals(2, junction.getStackLowerEdgeIds().size());
        assertEquals(2, junction.getStackUpperEdgeIds().size());
    }

    @Test
    void d05_seamCanonicalOffsetPrefersResolveFromSeam() {
        RoadNetwork network = new RoadNetwork();
        Road loop = network.createRoad("loop");
        loop.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        RoadNode n4 = network.createNode(new Vec2d(0, 10));
        var e1 = network.createEdge(n1.getId(), n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), loop.getId());
        network.createEdge(n2.getId(), n3.getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)), loop.getId());
        network.createEdge(n3.getId(), n4.getId(),
            List.of(new Vec2d(10, 10), new Vec2d(0, 10)), loop.getId());
        network.createEdge(n4.getId(), n1.getId(),
            List.of(new Vec2d(0, 10), new Vec2d(0, 0)), loop.getId());
        loop.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), e1.getId(), 0.5));

        RoadLoopSeamService.SeamProjection resolved =
            RoadLoopSeamService.resolveFromSeam(network, loop, loop.getLoopSeam());
        assertNotNull(resolved);
        assertEquals(e1.getId(), resolved.segmentId());
        double canonical = com.plot.plugin.road.station.RoadStationing.toCanonicalChainage(
            network, loop, resolved.chainStation());
        assertTrue(canonical >= 0.0);
        assertTrue(canonical <= com.plot.plugin.road.station.RoadStationing.canonicalLength(network, loop) + 1e-6);
    }

    private static RoadNetwork crossRoads() {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("road-a");
        Road b = network.createRoad("road-b");
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
        return network;
    }

    private static RoadNetwork figureEightNetwork() {
        RoadNetwork network = new RoadNetwork();
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
        return network;
    }

    /** Same figure-eight geometry with centerline points and start/end nodes flipped. */
    private static RoadNetwork figureEightNetworkReversed() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("figure-eight");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n2.getId(),
            n1.getId(),
            List.of(
                new Vec2d(10, 0),
                new Vec2d(0, 10),
                new Vec2d(10, 10),
                new Vec2d(0, 0)),
            road.getId());
        return network;
    }
}
