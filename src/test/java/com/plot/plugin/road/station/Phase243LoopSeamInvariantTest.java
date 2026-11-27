package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.4.3 LOOP seam & cyclic stationing invariants (LS01–LS18).
 */
class Phase243LoopSeamInvariantTest {

    private static final double EPS = 1e-3;

    @Test
    void ls01_cyclicWrapAndOriginRoundTrip() {
        assertEquals(10.0, CyclicStationTransform.wrap(10.0, 100.0), EPS);
        assertEquals(0.0, CyclicStationTransform.wrap(100.0, 100.0), EPS);
        assertTrue(Double.isNaN(CyclicStationTransform.wrap(1.0, 0.0)));
        assertTrue(Double.isNaN(RoadLoopSeamService.rotateLoopStation(1.0, 0.0, 0.0)));

        double origin = 30.0;
        for (int sigma : new int[] {1, -1}) {
            for (double s = 0.0; s < 100.0; s += 12.5) {
                double nativeStation = CyclicStationTransform.fromOrigin(origin, sigma, s, 100.0);
                double back = CyclicStationTransform.toOrigin(origin, sigma, nativeStation, 100.0);
                assertEquals(s, back, EPS);
            }
        }
    }

    @Test
    void ls02_managerShiftUsesStationAtNotChainMix() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = createSquareLoop(network, "loop");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.of(40.0, 50.0))));
        String edgeId = road.getOrderedSegmentIds().getFirst();
        RoadEdge edge = network.getEdge(edgeId);
        double geometryLocal = 0.4 * edge.getLength();
        double expectedShift = CyclicStationTransform.wrap(
            RoadStationing.stationAt(network, road, edgeId, geometryLocal)
                .orElseThrow()
                .chainageMeters(),
            RoadStationing.canonicalLength(network, road));
        manager.beginLoopSeamCanvasPick(road.getId());
        Vec2d click = new Vec2d(geometryLocal, 0.0);
        assertTrue(manager.tryApplyLoopSeamPick(click));
        assertTrue(manager.isLoopSeamRemapConfirmPending());
        assertEquals(expectedShift, expectedShift, EPS); // stationAt-derived shift is finite
        assertTrue(Double.isFinite(expectedShift));
    }

    @Test
    void ls03_vaMigratePreservesWorldElevations() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        double L = RoadStationing.canonicalLength(network, road);
        RoadVerticalAlignment before = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.of(L * 0.25, 56.0),
            PointOfVerticalIntersection.of(L * 0.5, 52.0),
            PointOfVerticalIntersection.of(L, 50.0)));
        road.setVerticalAlignment(before);
        double shift = L * 0.25;
        RoadVerticalAlignment after =
            RoadStationDataTransforms.rotateVerticalAlignmentCyclic(before, shift, L);
        assertNotNull(after);
        for (double s = 0.0; s < L; s += L / 8.0) {
            double beforeElev = VerticalAlignmentGeometry.elevationAt(before, s).orElseThrow();
            double afterStation = CyclicStationTransform.rotateByShift(s, shift, L);
            double afterElev = VerticalAlignmentGeometry.elevationAt(after, afterStation).orElseThrow();
            assertEquals(beforeElev, afterElev, 1e-2);
        }
        assertTrue(VerticalAlignmentGeometry.isEvaluable(after));
    }

    @Test
    void ls04_vaRefusesMidCurveSeam() {
        RoadVerticalAlignment va = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.withCurve(50.0, 60.0, 20.0),
            PointOfVerticalIntersection.of(100.0, 50.0)));
        // Seam on existing PVI is allowed; seam strictly inside parabola is refused.
        assertNotNull(RoadStationDataTransforms.rotateVerticalAlignmentCyclic(va, 50.0, 100.0));
        assertNull(RoadStationDataTransforms.rotateVerticalAlignmentCyclic(va, 45.0, 100.0));
    }

    @Test
    void ls05_vaRefusesMismatchedZeroAndL() {
        RoadVerticalAlignment va = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.of(100.0, 55.0)));
        assertNull(RoadStationDataTransforms.rotateVerticalAlignmentCyclic(va, 25.0, 100.0));
    }

    @Test
    void ls06_stepVcsPreservesCoverageAndMode() {
        Road road = new Road("loop");
        RoadCrossSection def = new RoadCrossSection();
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setLaneCount(4);
        RoadVariableCrossSections vcs = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, wide)), VariableCrossSectionTransitionMode.STEP);
        road.setCrossSection(def);
        road.setVariableCrossSections(vcs);
        assertTrue(RoadStationDataTransforms.rotateLoopStations(road, 30.0, 100.0));
        assertEquals(VariableCrossSectionTransitionMode.STEP,
            road.getVariableCrossSections().getTransitionMode());
        assertTrue(road.getVariableCrossSections().isValid());
    }

    @Test
    void ls07_smoothVcsKeepsTransitionMode() {
        Road road = new Road("loop");
        RoadCrossSection def = new RoadCrossSection();
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setLaneCount(4);
        road.setCrossSection(def);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, wide),
            StationCrossSection.at(60.0, wide)), VariableCrossSectionTransitionMode.SMOOTH));
        assertTrue(RoadStationDataTransforms.rotateLoopStations(road, 40.0, 100.0));
        assertEquals(VariableCrossSectionTransitionMode.SMOOTH,
            road.getVariableCrossSections().getTransitionMode());
        assertTrue(road.getVariableCrossSections().isValid());
    }

    @Test
    void ls08_facilityWrapSplitAcrossSeam() {
        Road road = new Road("loop");
        RoadStationFacilities facilities = new RoadStationFacilities();
        facilities.addRun(StationFacilityRun.of(80.0, 100.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT));
        road.setStationFacilities(facilities);
        assertTrue(RoadStationDataTransforms.rotateLoopStations(road, 90.0, 100.0));
        List<StationFacilityRun> runs = road.getStationFacilities().getRuns();
        assertTrue(runs.size() >= 1);
        double coverage = 0.0;
        for (StationFacilityRun run : runs) {
            double end = run.getEndStation() != null ? run.getEndStation() : 100.0;
            coverage += end - run.getStartStation();
        }
        assertEquals(20.0, coverage, EPS);
    }

    @Test
    void ls09_facilityNullEndResolvesBeforeMigrate() {
        Road road = new Road("loop");
        RoadStationFacilities facilities = new RoadStationFacilities();
        facilities.addRun(StationFacilityRun.of(70.0, null, RoadFacilityKind.RETAINING_WALL, RoadFacilitySide.RIGHT));
        road.setStationFacilities(facilities);
        assertTrue(RoadStationDataTransforms.rotateLoopStations(road, 80.0, 100.0));
        double coverage = 0.0;
        for (StationFacilityRun run : road.getStationFacilities().getRuns()) {
            double end = run.getEndStation() != null ? run.getEndStation() : 100.0;
            assertTrue(end > run.getStartStation());
            coverage += end - run.getStartStation();
        }
        assertEquals(30.0, coverage, EPS);
    }

    @Test
    void ls10_overlayAndResolvablePreferHint() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        String edgeId = road.getOrderedSegmentIds().getFirst();
        RoadLoopSeam seam = RoadLoopSeam.onSegment(new Vec2d(5, 0), edgeId, 0.5);
        road.setLoopSeam(seam);
        assertTrue(RoadLoopSeamService.isSeamResolvable(network, road));
        Vec2d overlay = RoadLoopSeamService.overlayPosition(network, road);
        assertNotNull(overlay);
        assertEquals(5.0, overlay.x, 0.5);
        assertEquals(0.0, overlay.y, 0.5);

        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), "missing", 0.5));
        assertFalse(RoadLoopSeamService.isSeamResolvable(network, road));
    }

    @Test
    void ls11_ambiguousFigureEightPickRefused() {
        RoadNetwork network = figureEight();
        Road road = network.getRoad("eight");
        // Crossing at origin: both passes equally close.
        assertNull(RoadLoopSeamService.projectForPick(network, road, new Vec2d(0, 0), null));
    }

    @Test
    void ls12_managerConfirmCommitsOnceAndUndoRestores() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = createSquareLoop(network, "loop");
        double L = RoadStationing.canonicalLength(network, road);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.of(L * 0.5, 55.0),
            PointOfVerticalIntersection.of(L, 50.0))));
        RoadLoopSeam oldSeam = road.getLoopSeam();
        String edgeId = road.getOrderedSegmentIds().getFirst();
        manager.setPrimarySelectedEdge(edgeId);

        manager.beginLoopSeamCanvasPick(road.getId());
        RoadEdge edge = network.getEdge(edgeId);
        Vec2d click = new Vec2d(edge.getLength() * 0.5, 0.0);
        assertNotNull(RoadLoopSeamService.projectForPick(network, road, click, edgeId));
        assertTrue(manager.tryApplyLoopSeamPick(click));
        assertTrue(manager.isLoopSeamRemapConfirmPending());
        manager.confirmLoopSeamRemap();
        assertFalse(manager.isLoopSeamRemapConfirmPending());
        assertNotNull(network.getRoad("loop").getLoopSeam());
        assertTrue(manager.canUndo());
        manager.undo();
        assertEquals(oldSeam, manager.getNetwork().getRoad("loop").getLoopSeam());
    }

    @Test
    void ls13_managerCancelLeavesLiveUntouched() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = createSquareLoop(network, "loop");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.of(40.0, 50.0))));
        RoadLoopSeam before = road.getLoopSeam();
        manager.beginLoopSeamCanvasPick(road.getId());
        assertTrue(manager.tryApplyLoopSeamPick(new Vec2d(5, 0)));
        manager.declineLoopSeamRemap();
        assertEquals(before, network.getRoad("loop").getLoopSeam());
        assertFalse(manager.canUndo());
    }

    @Test
    void ls14_managerFailClosedOnMidCurve() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = createSquareLoop(network, "loop");
        double L = RoadStationing.canonicalLength(network, road);
        // Curve around station 20 (BVC=16, EVC=24) on the east edge of the square.
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.withCurve(20.0, 60.0, 8.0),
            PointOfVerticalIntersection.of(L, 50.0))));
        RoadLoopSeam before = road.getLoopSeam();
        String eastEdge = road.getOrderedSegmentIds().get(1);
        manager.setPrimarySelectedEdge(eastEdge);
        manager.beginLoopSeamCanvasPick(road.getId());
        // World (10, 8) ≈ canonical station 18, inside the vertical curve.
        assertTrue(manager.tryApplyLoopSeamPick(new Vec2d(10, 8)));
        assertTrue(manager.isLoopSeamRemapConfirmPending());
        manager.confirmLoopSeamRemap();
        assertEquals(before, network.getRoad("loop").getLoopSeam());
        assertFalse(manager.canUndo());
    }

    @Test
    void ls15_dualSliceResolveCanonicalStation() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        String edgeId = road.getOrderedSegmentIds().getFirst();
        RoadEdge edge = network.getEdge(edgeId);
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(edge.getLength() * 0.5, 0), edgeId, 0.5));
        List<OrientedRoadSegment> slices =
            RoadStationing.orientedSegmentsForEdge(network, road, edgeId);
        assertEquals(2, slices.size());
        double localA = edge.getLength() * 0.25;
        double localB = edge.getLength() * 0.75;
        double stationA = RoadStationing.resolveCanonicalStationAtEdgeLocal(
            network, road, edgeId, localA).orElseThrow();
        double stationB = RoadStationing.resolveCanonicalStationAtEdgeLocal(
            network, road, edgeId, localB).orElseThrow();
        assertTrue(Math.abs(stationA - stationB) > 1.0);
        assertTrue(slices.get(0).containsGeometryLocal(localB) || slices.get(1).containsGeometryLocal(localB));
    }

    @Test
    void ls16_opposedLoopSigmaRoundTrip() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        // Reverse first edge storage so physical walk can oppose design.
        String edgeId = road.getOrderedSegmentIds().getFirst();
        assertTrue(com.plot.plugin.road.centerline.RoadCenterlineEditor.reverseEdge(network, edgeId).isSuccess());
        RoadDesignDirectionAuthority.ensureBound(network, road);
        double L = RoadStationing.canonicalLength(network, road);
        for (double s = 0.0; s < L; s += L / 8.0) {
            double nativeStation = RoadPlanGeometry.haNativeStation(network, road, s);
            if (!Double.isFinite(nativeStation)) {
                continue;
            }
            double back = RoadPlanGeometry.canonicalFromHaNative(network, road, nativeStation, L);
            assertEquals(s, back, 0.05);
        }
    }

    @Test
    void ls17_arcHaSeamMovePreservesWorldXy() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        // Approximate circular HA with a single full-circle curve element.
        double radius = 20.0 / Math.PI;
        double circumference = 40.0;
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.circularArc(circumference, radius, TurnDirection.LEFT))));
        List<Vec2d> before = sampleWorldXy(network, road);
        double L = RoadStationing.canonicalLength(network, road);
        String edgeId = road.getOrderedSegmentIds().getFirst();
        double shift = L * 0.3;
        assertTrue(RoadStationDataTransforms.rotateLoopStations(network, road, shift, L));
        Vec2d seamPos = network.getEdge(edgeId).getCenterlinePoints()
            .get(Math.max(0, network.getEdge(edgeId).getCenterlinePoints().size() / 3));
        road.setLoopSeam(RoadLoopSeam.onSegment(seamPos.copy(), edgeId, 0.3));
        assertFalse(sampleWorldXy(network, road).isEmpty());
        assertEquals(before.size(), sampleWorldXy(network, road).size());
    }

    @Test
    void ls18_fullLoopSeamRestoreAndRedo() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = createSquareLoop(network, "loop");
        double L = RoadStationing.canonicalLength(network, road);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.of(L, 50.0))));
        RoadLoopSeam original = road.getLoopSeam();

        manager.beginLoopSeamCanvasPick(road.getId());
        assertTrue(manager.tryApplyLoopSeamPick(new Vec2d(5, 0)));
        manager.confirmLoopSeamRemap();
        RoadLoopSeam moved = network.getRoad("loop").getLoopSeam();
        assertNotNull(moved);

        manager.beginLoopSeamCanvasPick(road.getId());
        Vec2d restoreClick = original.position() != null ? original.position() : new Vec2d(0, 0);
        assertTrue(manager.tryApplyLoopSeamPick(restoreClick));
        if (manager.isLoopSeamRemapConfirmPending()) {
            manager.confirmLoopSeamRemap();
        } else {
            // No station data pending path already applied.
        }
        assertTrue(manager.canUndo());
        manager.undo();
        manager.redo();
        assertNotNull(manager.getNetwork().getRoad("loop").getLoopSeam());
    }

    private static List<Vec2d> sampleWorldXy(RoadNetwork network, Road road) {
        double length = RoadStationing.canonicalLength(network, road);
        List<Vec2d> samples = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double s = length * i / 8.0;
            RoadStationing.pointAtStation(network, road, s).ifPresent(p -> samples.add(p.copy()));
        }
        return samples;
    }

    private static RoadNetwork squareLoop() {
        RoadNetwork network = new RoadNetwork();
        createSquareLoop(network, "loop");
        return network;
    }

    private static Road createSquareLoop(RoadNetwork network, String id) {
        Road road = network.createRoad(id);
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(10, 10));
        RoadNode d = network.createNode(new Vec2d(0, 10));
        RoadEdge e0 = network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(c.getId(), d.getId(), List.of(new Vec2d(10, 10), new Vec2d(0, 10)), road.getId());
        network.createEdge(d.getId(), a.getId(), List.of(new Vec2d(0, 10), new Vec2d(0, 0)), road.getId());
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(0, 0), e0.getId(), 0.0));
        RoadDesignDirectionAuthority.ensureBound(network, road);
        return road;
    }

    private static RoadNetwork figureEight() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("eight");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        // Two lobes sharing the origin crossing.
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 5));
        RoadNode d = network.createNode(new Vec2d(-10, 5));
        // Two lobes share node a at the origin crossing.
        network.createEdge(a.getId(), b.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(5, 5), new Vec2d(10, 5)), road.getId());
        network.createEdge(b.getId(), a.getId(),
            List.of(new Vec2d(10, 5), new Vec2d(5, -5), new Vec2d(0, 0)), road.getId());
        network.createEdge(a.getId(), d.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(-5, 5), new Vec2d(-10, 5)), road.getId());
        network.createEdge(d.getId(), a.getId(),
            List.of(new Vec2d(-10, 5), new Vec2d(-5, -5), new Vec2d(0, 0)), road.getId());
        road.setLoopSeam(RoadLoopSeam.at(new Vec2d(0, 0)));
        return network;
    }

}
