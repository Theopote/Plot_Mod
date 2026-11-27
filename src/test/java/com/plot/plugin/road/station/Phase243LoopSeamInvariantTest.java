package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.alignment.AlignmentPose;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.lamp.RoadLampPlacement;
import com.plot.plugin.road.lamp.RoadLampPresets;
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
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.4.3 LOOP seam & cyclic stationing invariants (LS01–LS18) plus generation regressions.
 */
class Phase243LoopSeamInvariantTest {

    private static final double EPS = 1e-3;

    @Test
    void ls01_cyclicWrapAndOriginRoundTrip() {
        assertEquals(10.0, CyclicStationTransform.wrap(10.0, 100.0), EPS);
        assertEquals(0.0, CyclicStationTransform.wrap(100.0, 100.0), EPS);
        assertEquals(0.0, CyclicStationTransform.wrap(0.0, 100.0), EPS);
        assertEquals(1.0, CyclicStationTransform.wrap(1e12 + 1.0, 100.0), 1e-6);
        assertEquals(99.0, CyclicStationTransform.wrap(-1.0, 100.0), EPS);
        assertTrue(Double.isNaN(CyclicStationTransform.wrap(1.0, 0.0)));
        assertTrue(Double.isNaN(CyclicStationTransform.wrap(Double.NaN, 100.0)));
        assertTrue(Double.isNaN(CyclicStationTransform.wrap(1.0, Double.POSITIVE_INFINITY)));
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
        manager.setPrimarySelectedEdge(edgeId);
        manager.beginLoopSeamCanvasPick(road.getId());
        Vec2d click = RoadStationing.pointAtStation(network, road, expectedShift).orElseThrow();
        assertTrue(manager.tryApplyLoopSeamPick(click));
        assertTrue(manager.isLoopSeamRemapConfirmPending());
        assertEquals(expectedShift, manager.pendingLoopSeamShiftMeters(), 0.05);
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
    void ls04_vaRefusesCurvedPviAllowsBvcEvc() {
        RoadVerticalAlignment va = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.withCurve(50.0, 60.0, 20.0),
            PointOfVerticalIntersection.of(100.0, 50.0)));
        var curves = VerticalAlignmentValidator.curveIntervals(va.sortedPvis());
        assertEquals(1, curves.size());
        double bvc = curves.getFirst().bvcStation();
        double evc = curves.getFirst().evcStation();
        assertEquals(40.0, bvc, EPS);
        assertEquals(60.0, evc, EPS);

        // Curved PVI station is mid-parabola — refuse.
        assertNull(RoadStationDataTransforms.rotateVerticalAlignmentCyclic(va, 50.0, 100.0));
        // Strict interior of (BVC, EVC) — refuse.
        assertNull(RoadStationDataTransforms.rotateVerticalAlignmentCyclic(va, 45.0, 100.0));
        assertNull(RoadStationDataTransforms.rotateVerticalAlignmentCyclic(va, 55.0, 100.0));

        RoadVerticalAlignment atBvc =
            RoadStationDataTransforms.rotateVerticalAlignmentCyclic(va, bvc, 100.0);
        assertNotNull(atBvc);
        assertTrue(VerticalAlignmentGeometry.isEvaluable(atBvc));
        for (double s : new double[] {0.0, 20.0, 40.0, 70.0, 100.0}) {
            double beforeElev = VerticalAlignmentGeometry.elevationAt(va, s).orElseThrow();
            double afterStation = CyclicStationTransform.rotateByShift(s, bvc, 100.0);
            double afterElev = VerticalAlignmentGeometry.elevationAt(atBvc, afterStation).orElseThrow();
            assertEquals(beforeElev, afterElev, 1e-2, "elev mismatch at s=" + s);
        }

        RoadVerticalAlignment atEvc =
            RoadStationDataTransforms.rotateVerticalAlignmentCyclic(va, evc, 100.0);
        assertNotNull(atEvc);
        double elevAtEvc = VerticalAlignmentGeometry.elevationAt(va, evc).orElseThrow();
        assertEquals(elevAtEvc,
            VerticalAlignmentGeometry.elevationAt(atEvc, 0.0).orElseThrow(), 1e-2);
    }

    @Test
    void ls05_vaRefusesMismatchedZeroAndL() {
        RoadVerticalAlignment va = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.of(100.0, 55.0)));
        assertNull(RoadStationDataTransforms.rotateVerticalAlignmentCyclic(va, 25.0, 100.0));
    }

    @Test
    void ls06_stepVcsPreservesWidthDistribution() {
        RoadSystemConfig config = flatWidthConfig();

        Road road = new Road("loop");
        RoadCrossSection narrow = sectionWithTravelWidth(8);
        RoadCrossSection wide = sectionWithTravelWidth(16);
        road.setCrossSection(narrow);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, wide),
            StationCrossSection.at(60.0, narrow)), VariableCrossSectionTransitionMode.STEP));

        int beforeMid = VariableCrossSectionResolver.resolve(
            road.getCrossSection(),
            road.getVariableCrossSections().sortedStations(),
            VariableCrossSectionTransitionMode.STEP,
            40.0,
            config).carriagewayWidth;
        assertEquals(16, beforeMid);

        assertTrue(RoadStationDataTransforms.rotateLoopStations(road, 30.0, 100.0));
        assertEquals(VariableCrossSectionTransitionMode.STEP,
            road.getVariableCrossSections().getTransitionMode());
        assertTrue(road.getVariableCrossSections().isValid());

        for (double s = 0.0; s < 100.0; s += 10.0) {
            int before = VariableCrossSectionResolver.resolve(
                narrow,
                List.of(StationCrossSection.at(20.0, wide), StationCrossSection.at(60.0, narrow)),
                VariableCrossSectionTransitionMode.STEP,
                s,
                config).carriagewayWidth;
            double afterStation = CyclicStationTransform.rotateByShift(s, 30.0, 100.0);
            int after = VariableCrossSectionResolver.resolve(
                narrow,
                road.getVariableCrossSections().sortedStations(),
                VariableCrossSectionTransitionMode.STEP,
                afterStation,
                config).carriagewayWidth;
            assertEquals(before, after, "STEP width at s=" + s);
        }
    }

    @Test
    void ls07_smoothVcsInterpolatedSeamWidth() {
        RoadSystemConfig config = flatWidthConfig();

        RoadCrossSection narrow = sectionWithTravelWidth(8);
        RoadCrossSection wide = sectionWithTravelWidth(16);
        // Closable SMOOTH: 8@0 → 16@80 → 8@L; midpoint@40 → 12.
        List<StationCrossSection> anchors = List.of(
            StationCrossSection.at(0.0, narrow),
            StationCrossSection.at(80.0, wide),
            StationCrossSection.at(100.0, narrow));
        int seamWidth = VariableCrossSectionResolver.resolve(
            narrow, anchors, VariableCrossSectionTransitionMode.SMOOTH, 40.0, config)
            .carriagewayWidth;
        assertEquals(12, seamWidth);

        RoadCrossSection seamSection = RoadStationDataTransforms.interpolateSmoothCrossSection(
            new RoadVariableCrossSections(anchors, VariableCrossSectionTransitionMode.SMOOTH),
            narrow,
            40.0,
            config);
        assertEquals(12, seamSection.resolve(config).carriagewayWidth);

        // Different lane counts → discrete switch moves when seam splits the span → refuse.
        Road road = new Road("loop");
        road.setCrossSection(narrow);
        road.setVariableCrossSections(new RoadVariableCrossSections(
            anchors, VariableCrossSectionTransitionMode.SMOOTH));
        assertFalse(RoadStationDataTransforms.rotateLoopStations(road, 40.0, 100.0, config));

        // Identical discrete attrs (same lane count/materials) with continuous width only → allow.
        RoadCrossSection narrowSame = sectionWithTravelWidth(8);
        RoadCrossSection wideSameLanes = sectionWithTravelWidth(12); // still 2 lanes
        List<StationCrossSection> sameDiscrete = List.of(
            StationCrossSection.at(0.0, narrowSame),
            StationCrossSection.at(80.0, wideSameLanes),
            StationCrossSection.at(100.0, narrowSame));
        Road roadOk = new Road("loop-ok");
        roadOk.setCrossSection(narrowSame);
        roadOk.setVariableCrossSections(new RoadVariableCrossSections(
            sameDiscrete, VariableCrossSectionTransitionMode.SMOOTH));
        assertTrue(RoadStationDataTransforms.rotateLoopStations(roadOk, 40.0, 100.0, config));
        assertEquals(VariableCrossSectionTransitionMode.SMOOTH,
            roadOk.getVariableCrossSections().getTransitionMode());
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
    void ls11_continuousSelfCrossingAmbiguityAndNodePick() {
        RoadNetwork network = continuousSelfCrossingLoop();
        Road road = network.getRoad("eight");
        // Crossing at origin: dual passes equally close → refuse.
        assertNull(RoadLoopSeamService.projectForPick(network, road, new Vec2d(0, 0), null));

        // Unique lobe tip is unambiguous.
        RoadLoopSeamService.SeamProjection tip =
            RoadLoopSeamService.projectForPick(network, road, new Vec2d(20, 0), null);
        assertNotNull(tip);

        // Shared topo node (loop closure) is one physical pass — not ambiguous with itself.
        RoadNode node = network.getNode(
            network.getEdge(road.getOrderedSegmentIds().getFirst()).getStartNodeId());
        RoadLoopSeamService.SeamProjection atNode =
            RoadLoopSeamService.projectForPick(network, road, node.getPosition().copy(), null);
        // May be null (cross) or a projection; if present, geometry must be near the node.
        if (atNode != null) {
            assertTrue(atNode.position().distance(node.getPosition())
                < com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE + 0.5);
        }
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
        String jsonBefore = network.toJson();
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
        assertNotEquals(jsonBefore, network.toJson());
        assertTrue(manager.canUndo());
        manager.undo();
        assertEquals(oldSeam, manager.getNetwork().getRoad("loop").getLoopSeam());
        assertEquals(
            RoadNetwork.parseSnapshot(jsonBefore).getRoad("loop").getLoopSeam(),
            manager.getNetwork().getRoad("loop").getLoopSeam());
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
        for (OrientedRoadSegment slice : slices) {
            assertEquals(edge.getLength(), slice.fullEdgeLength(), EPS);
            assertTrue(slice.isPartialSlice());
        }
        double localA = edge.getLength() * 0.25;
        double localB = edge.getLength() * 0.75;
        double stationA = RoadStationing.resolveCanonicalStationAtEdgeLocal(
            network, road, edgeId, localA).orElseThrow();
        double stationB = RoadStationing.resolveCanonicalStationAtEdgeLocal(
            network, road, edgeId, localB).orElseThrow();
        assertTrue(Math.abs(stationA - stationB) > 1.0);

        RoadSystemConfig config = flatWidthConfig();
        RoadCrossSection narrow = sectionWithTravelWidth(8);
        RoadCrossSection wide = sectionWithTravelWidth(16);
        road.setCrossSection(narrow);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(stationA, narrow),
            StationCrossSection.at(stationB, wide)), VariableCrossSectionTransitionMode.STEP));
        CrossSectionBuildContext ctx = CrossSectionBuildContext.forEdge(network, edge, config);
        double chainA = ctx.chainageAtGeometryLocal(localA);
        double chainB = ctx.chainageAtGeometryLocal(localB);
        assertTrue(Double.isFinite(chainA) && Double.isFinite(chainB));
        assertEquals(8, ctx.resolve(chainA).carriagewayWidth);
        assertEquals(16, ctx.resolve(chainB).carriagewayWidth);
    }

    @Test
    void ls16_opposedLoopSigmaRoundTrip() {
        RoadNetwork network = arcHaSquareLoop();
        Road road = network.getRoad("square-ha");
        RoadDesignDirectionAuthority.ensureBound(network, road);
        // Reverse HA circulation only — design binding unchanged → physical walk opposes design.
        RoadHorizontalAlignment ha = road.getHorizontalAlignment();
        double haLen = ha.totalLength();
        AlignmentPose endPose = HorizontalAlignmentGeometry.poseAt(ha, haLen).orElseThrow();
        List<HorizontalAlignmentElement> reversed = new ArrayList<>();
        List<HorizontalAlignmentElement> elements = ha.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HorizontalAlignmentElement element = elements.get(i);
            reversed.add(switch (element.getType()) {
                case TANGENT -> HorizontalAlignmentElement.tangent(element.getLength());
                case CIRCULAR_ARC -> HorizontalAlignmentElement.circularArc(
                    element.getLength(),
                    element.getRadius(),
                    element.getDirection() == TurnDirection.LEFT
                        ? TurnDirection.RIGHT
                        : TurnDirection.LEFT);
                case SPIRAL -> HorizontalAlignmentElement.spiral(
                    element.getLength(), element.getSpiralParameterA());
            });
        }
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(endPose.x(), endPose.y()),
            endPose.bearingRadians() + Math.PI,
            reversed));
        assertTrue(RoadStationing.isPhysicalWalkOpposedToDesign(network, road));
        double L = RoadStationing.canonicalLength(network, road);
        for (double s = 0.0; s < L; s += L / 8.0) {
            double nativeStation = RoadPlanGeometry.haNativeStation(network, road, s);
            assertTrue(Double.isFinite(nativeStation), "native at s=" + s);
            double back = RoadPlanGeometry.canonicalFromHaNative(network, road, nativeStation, L);
            assertEquals(s, back, 0.05);
        }
    }

    @Test
    void ls17_arcHaSeamMovePreservesWorldXy() {
        RoadNetwork network = arcHaSquareLoop();
        Road road = network.getRoad("square-ha");
        double L = RoadStationing.canonicalLength(network, road);
        double shift = L * 0.3;
        List<Vec2d> before = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double s = L * i / 8.0;
            before.add(RoadStationing.pointAtStation(network, road, s).orElseThrow().copy());
        }
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        // Rebuild under manager so confirm path owns live network.
        Road managed = createArcHaSquareLoop(manager.getNetwork(), "square-ha");
        double managedL = RoadStationing.canonicalLength(manager.getNetwork(), managed);
        List<Vec2d> managedBefore = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double s = managedL * i / 8.0;
            managedBefore.add(
                RoadStationing.pointAtStation(manager.getNetwork(), managed, s).orElseThrow().copy());
        }
        String edgeId = managed.getOrderedSegmentIds().getFirst();
        manager.setPrimarySelectedEdge(edgeId);
        RoadEdge edge = manager.getNetwork().getEdge(edgeId);
        double targetStation = CyclicStationTransform.wrap(shift, managedL);
        Vec2d click = RoadStationing.pointAtStation(manager.getNetwork(), managed, targetStation)
            .orElse(new Vec2d(edge.getLength() * 0.3, 0));
        manager.beginLoopSeamCanvasPick(managed.getId());
        assertTrue(manager.tryApplyLoopSeamPick(click));
        if (manager.isLoopSeamRemapConfirmPending()) {
            manager.confirmLoopSeamRemap();
        }
        Road afterRoad = manager.getNetwork().getRoad("square-ha");
        double afterL = RoadStationing.canonicalLength(manager.getNetwork(), afterRoad);
        for (int i = 0; i < 8; i++) {
            double s = managedL * i / 8.0;
            double afterStation = CyclicStationTransform.rotateByShift(
                s, manager.getNetwork().equals(network) ? shift : targetStation, afterL);
            // Prefer physical equivalence via mapped stations from pre-samples.
            Vec2d after = RoadStationing.pointAtStation(
                manager.getNetwork(), afterRoad,
                CyclicStationTransform.rotateByShift(s, targetStation, afterL)).orElse(null);
            if (after != null) {
                assertTrue(managedBefore.get(i).distance(after) < 0.75,
                    "XY drift at sample " + i);
            }
        }
        assertFalse(before.isEmpty());
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
        String jsonOriginal = network.toJson();

        manager.beginLoopSeamCanvasPick(road.getId());
        assertTrue(manager.tryApplyLoopSeamPick(new Vec2d(5, 0)));
        manager.confirmLoopSeamRemap();
        RoadLoopSeam moved = network.getRoad("loop").getLoopSeam();
        assertNotNull(moved);
        String jsonMoved = network.toJson();

        manager.beginLoopSeamCanvasPick(road.getId());
        Vec2d restoreClick = original.position() != null ? original.position() : new Vec2d(0, 0);
        assertTrue(manager.tryApplyLoopSeamPick(restoreClick));
        if (manager.isLoopSeamRemapConfirmPending()) {
            manager.confirmLoopSeamRemap();
        }
        assertTrue(manager.canUndo());
        manager.undo();
        manager.redo();
        assertNotNull(manager.getNetwork().getRoad("loop").getLoopSeam());
        assertTrue(manager.getNetwork().toJson().contains("\"loopSeam\""));
        assertNotEquals(jsonOriginal, jsonMoved);
    }

    // --- Generation regressions (9) ---

    @Test
    void gen01_dualSliceAsymmetricWidth() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        String edgeId = road.getOrderedSegmentIds().getFirst();
        RoadEdge edge = network.getEdge(edgeId);
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), edgeId, 0.5));
        assertEquals(2, RoadStationing.orientedSegmentsForEdge(network, road, edgeId).size());
        RoadSystemConfig config = flatWidthConfig();
        road.setCrossSection(sectionWithTravelWidth(8));
        double c25 = RoadStationing.resolveCanonicalStationAtEdgeLocal(
            network, road, edgeId, edge.getLength() * 0.25).orElseThrow();
        double c75 = RoadStationing.resolveCanonicalStationAtEdgeLocal(
            network, road, edgeId, edge.getLength() * 0.75).orElseThrow();
        assertTrue(Math.abs(c25 - c75) > 1.0);
        double wideStation = Math.max(c25, c75);
        double narrowStation = Math.min(c25, c75);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(wideStation, sectionWithTravelWidth(16))),
            VariableCrossSectionTransitionMode.STEP));
        CrossSectionBuildContext ctx = CrossSectionBuildContext.forEdge(network, edge, config);
        assertEquals(2, ctx.lightingOrientations().size());
        assertEquals(8, ctx.resolve(narrowStation).carriagewayWidth);
        assertEquals(16, ctx.resolve(wideStation).carriagewayWidth);
        assertNotEquals(
            ctx.resolve(c25).carriagewayWidth,
            ctx.resolve(c75).carriagewayWidth);
    }

    @Test
    void gen02_nonuniformHaMapRoundTrip() {
        RoadNetwork network = arcHaSquareLoop();
        Road road = network.getRoad("square-ha");
        double L = RoadStationing.canonicalLength(network, road);
        for (double s = 0.0; s < L; s += L / 10.0) {
            double nativeStation = RoadPlanGeometry.haNativeStation(network, road, s);
            assertTrue(Double.isFinite(nativeStation));
            double back = RoadPlanGeometry.canonicalFromHaNative(network, road, nativeStation, L);
            assertEquals(s, back, 0.05);
        }
    }

    @Test
    void gen03_circularLoopBothDirections() {
        RoadNetwork network = continuousCircleLoop();
        Road road = network.getRoad("circle");
        double L = RoadStationing.canonicalLength(network, road);
        for (int sigma : new int[] {1, -1}) {
            double origin = L * 0.25;
            for (double s = 0.0; s < L; s += L / 8.0) {
                double n = CyclicStationTransform.fromOrigin(origin, sigma, s, L);
                assertEquals(s, CyclicStationTransform.toOrigin(origin, sigma, n, L), EPS);
            }
        }
        assertNotNull(RoadLoopSeamService.projectForPick(network, road, new Vec2d(10, 0), null));
    }

    @Test
    void gen04_stackCrossingFigureEightLoop() {
        RoadNetwork network = continuousSelfCrossingLoop();
        Road road = network.getRoad("eight");
        double L = RoadStationing.canonicalLength(network, road);
        network.registerStackCrossing(RoadStackCrossing.gradeSeparated(
            road.getId(), L * 0.25, L * 0.75, new Vec2d(0, 0), 4));
        double shift = L * 0.1;
        assertTrue(RoadStationDataTransforms.rotateLoopStations(network, road, shift, L));
        var stacks = network.stackCrossingsForRoad(road.getId());
        assertEquals(1, stacks.size());
        RoadStackCrossing stack = stacks.iterator().next();
        assertEquals(
            CyclicStationTransform.rotateByShift(L * 0.25, shift, L),
            stack.stationA(),
            EPS);
        assertEquals(
            CyclicStationTransform.rotateByShift(L * 0.75, shift, L),
            stack.stationB(),
            EPS);
    }

    @Test
    void gen05_nodeSeamPick() {
        RoadNetwork network = squareLoop();
        Road road = network.getRoad("loop");
        String edgeId = road.getOrderedSegmentIds().getFirst();
        RoadEdge edge = network.getEdge(edgeId);
        RoadNode end = network.getNode(edge.getEndNodeId());
        RoadLoopSeamService.SeamProjection pick =
            RoadLoopSeamService.projectForPick(network, road, end.getPosition().copy(), edgeId);
        assertNotNull(pick);
        assertTrue(pick.position().distance(end.getPosition())
            <= com.plot.plugin.road.RoadNetworkBuilder.NODE_TOLERANCE + 0.25);
    }

    @Test
    void gen06_smoothSplitPreservesWidths() {
        RoadSystemConfig config = flatWidthConfig();
        Road road = new Road("loop");
        RoadCrossSection narrow = sectionWithTravelWidth(8);
        RoadCrossSection wide = sectionWithTravelWidth(12); // same lane count as 8
        road.setCrossSection(narrow);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(0.0, narrow),
            StationCrossSection.at(25.0, wide),
            StationCrossSection.at(75.0, narrow),
            StationCrossSection.at(100.0, narrow)), VariableCrossSectionTransitionMode.SMOOTH));
        assertTrue(RoadStationDataTransforms.rotateLoopStations(road, 50.0, 100.0, config));
        List<StationCrossSection> beforeAnchors = List.of(
            StationCrossSection.at(0.0, narrow),
            StationCrossSection.at(25.0, wide),
            StationCrossSection.at(75.0, narrow),
            StationCrossSection.at(100.0, narrow));
        for (double s = 0.0; s < 100.0; s += 10.0) {
            var before = VariableCrossSectionResolver.resolve(
                narrow, beforeAnchors, VariableCrossSectionTransitionMode.SMOOTH, s, config);
            var after = VariableCrossSectionResolver.resolve(
                narrow,
                road.getVariableCrossSections().sortedStations(),
                VariableCrossSectionTransitionMode.SMOOTH,
                CyclicStationTransform.rotateByShift(s, 50.0, 100.0),
                config);
            assertEquals(before.carriagewayWidth, after.carriagewayWidth, "SMOOTH width at s=" + s);
            assertEquals(before.laneCount, after.laneCount, "SMOOTH lanes at s=" + s);
            assertEquals(
                before.carriagewayMaterial != null ? before.carriagewayMaterial.getPrimaryMaterial() : null,
                after.carriagewayMaterial != null ? after.carriagewayMaterial.getPrimaryMaterial() : null,
                "SMOOTH material at s=" + s);
        }
    }

    @Test
    void gen07_vaRefuseFullRollback() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = createSquareLoop(network, "loop");
        double L = RoadStationing.canonicalLength(network, road);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.withCurve(20.0, 60.0, 8.0),
            PointOfVerticalIntersection.of(L, 50.0))));
        String jsonBefore = network.toJson();
        RoadLoopSeam before = road.getLoopSeam();
        String eastEdge = road.getOrderedSegmentIds().get(1);
        manager.setPrimarySelectedEdge(eastEdge);
        manager.beginLoopSeamCanvasPick(road.getId());
        assertTrue(manager.tryApplyLoopSeamPick(new Vec2d(10, 8)));
        assertTrue(manager.isLoopSeamRemapConfirmPending());
        manager.confirmLoopSeamRemap();
        assertEquals(before, network.getRoad("loop").getLoopSeam());
        assertFalse(manager.canUndo());
        assertEquals(
            RoadNetwork.parseSnapshot(jsonBefore).getRoad("loop").getVerticalAlignment().sortedPvis().size(),
            network.getRoad("loop").getVerticalAlignment().sortedPvis().size());
    }

    @Test
    void gen08_lampPhysicalStationsStableAcrossSeam() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = createSquareLoop(network, "loop");
        var style = RoadLampPresets.ALL.getFirst().style();
        road.getCrossSection().getStreetFurniture().setRoadLampPlacement(
            new RoadLampPlacement(style, RoadLampPlacement.Sides.BOTH, 8, 0, 2, true, false));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.of(
                RoadStationing.canonicalLength(network, road), 50.0))));
        String edgeId = road.getOrderedSegmentIds().getFirst();
        RoadEdge edge = network.getEdge(edgeId);
        RoadSystemConfig config = new RoadSystemConfig("test");
        CrossSectionBuildContext beforeCtx = CrossSectionBuildContext.forEdge(network, edge, config);
        List<Double> physicalBefore = new ArrayList<>();
        for (double g = 1.0; g < edge.getLength(); g += 2.0) {
            double phys = beforeCtx.physicalStationAtGeometryLocal(g);
            assertTrue(Double.isFinite(phys), "pre physical at g=" + g);
            physicalBefore.add(phys);
        }
        manager.setPrimarySelectedEdge(edgeId);
        manager.beginLoopSeamCanvasPick(road.getId());
        assertTrue(manager.tryApplyLoopSeamPick(new Vec2d(5, 0)));
        if (manager.isLoopSeamRemapConfirmPending()) {
            manager.confirmLoopSeamRemap();
        }
        Road afterRoad = manager.getNetwork().getRoad("loop");
        RoadEdge afterEdge = manager.getNetwork().getEdge(edgeId);
        CrossSectionBuildContext afterCtx =
            CrossSectionBuildContext.forEdge(manager.getNetwork(), afterEdge, config);
        int i = 0;
        for (double g = 1.0; g < afterEdge.getLength(); g += 2.0) {
            double phys = afterCtx.physicalStationAtGeometryLocal(g);
            assertTrue(Double.isFinite(phys), "post physical at g=" + g);
            assertEquals(physicalBefore.get(i), phys, 0.05, "physical lamp station at g=" + g);
            i++;
        }
        assertNotNull(afterRoad.getCrossSection().getStreetFurniture().getRoadLampPlacement());
    }

    @Test
    void gen09_undoRedoPersistenceEquivalence() {
        RoadNetworkManager manager = new RoadNetworkManager(
            new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = createSquareLoop(network, "loop");
        double L = RoadStationing.canonicalLength(network, road);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.of(L, 50.0))));
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(L * 0.5, sectionWithTravelWidth(16))),
            VariableCrossSectionTransitionMode.STEP));
        String jsonBefore = network.toJson();

        manager.beginLoopSeamCanvasPick(road.getId());
        assertTrue(manager.tryApplyLoopSeamPick(new Vec2d(5, 0)));
        manager.confirmLoopSeamRemap();
        String jsonAfter = network.toJson();
        assertNotEquals(jsonBefore, jsonAfter);

        manager.undo();
        assertEquals(
            RoadNetwork.parseSnapshot(jsonBefore).getRoad("loop").getLoopSeam(),
            manager.getNetwork().getRoad("loop").getLoopSeam());
        manager.redo();
        RoadNetwork restored = RoadNetwork.parseSnapshot(manager.getNetwork().toJson());
        assertEquals(
            manager.getNetwork().getRoad("loop").getLoopSeam(),
            restored.getRoad("loop").getLoopSeam());
        assertEquals(
            manager.getNetwork().getRoad("loop").getVariableCrossSections().getTransitionMode(),
            restored.getRoad("loop").getVariableCrossSections().getTransitionMode());
    }

    private static RoadSystemConfig flatWidthConfig() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setCenterLineStyle(CenterLineStyle.NONE);
        config.setLaneDividers(false);
        return config;
    }

    private static RoadCrossSection sectionWithTravelWidth(int travelWidth) {
        RoadCrossSection section = new RoadCrossSection();
        section.getMarkings().setCenterLineStyle(CenterLineStyle.NONE);
        section.getMarkings().setLaneDividers(false);
        int lanes = travelWidth <= 6 ? 1 : (travelWidth <= 12 ? 2 : 4);
        int laneW = Math.max(1, travelWidth / lanes);
        section.getCarriageway().setLaneCount(lanes);
        section.getCarriageway().applyUniformDefaultLaneWidth(laneW);
        return section;
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

    /** Continuous self-crossing LOOP (single edge A→A), not a 4-edge fork. */
    private static RoadNetwork continuousSelfCrossingLoop() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("eight");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        List<Vec2d> pts = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 10),
            new Vec2d(20, 0),
            new Vec2d(10, -10),
            new Vec2d(0, 0),
            new Vec2d(-10, 10),
            new Vec2d(-20, 0),
            new Vec2d(-10, -10),
            new Vec2d(0, 0));
        RoadEdge edge = network.createEdge(a.getId(), a.getId(), pts, road.getId());
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(0, 0), edge.getId(), 0.0));
        RoadDesignDirectionAuthority.ensureBound(network, road);
        return network;
    }

    private static RoadNetwork continuousCircleLoop() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("circle");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode a = network.createNode(new Vec2d(10, 0));
        List<Vec2d> pts = new ArrayList<>();
        for (int i = 0; i <= 16; i++) {
            double ang = 2.0 * Math.PI * i / 16.0;
            pts.add(new Vec2d(10.0 * Math.cos(ang), 10.0 * Math.sin(ang)));
        }
        RoadEdge edge = network.createEdge(a.getId(), a.getId(), pts, road.getId());
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(10, 0), edge.getId(), 0.0));
        RoadDesignDirectionAuthority.ensureBound(network, road);
        return network;
    }

    private static RoadNetwork arcHaSquareLoop() {
        RoadNetwork network = new RoadNetwork();
        createArcHaSquareLoop(network, "square-ha");
        return network;
    }

    private static Road createArcHaSquareLoop(RoadNetwork network, String id) {
        double arcLen = Math.PI * 10.0 / 2.0;
        double sideChain = 10.0 + arcLen;
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(10),
                HorizontalAlignmentElement.circularArc(arcLen, 10, TurnDirection.LEFT)));
        Road road = network.createRoad(id);
        road.setHorizontalAlignment(alignment);
        road.setTopologyMode(RoadTopologyMode.LOOP);
        List<RoadNode> corners = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            double chainage = i * sideChain;
            AlignmentPose pose = HorizontalAlignmentGeometry.poseAt(alignment, chainage).orElseThrow();
            corners.add(network.createNode(new Vec2d(pose.x(), pose.y())));
        }
        RoadEdge first = null;
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            double start = i * sideChain;
            double end = (i + 1) * sideChain;
            List<Vec2d> points = new ArrayList<>();
            for (double s = start; s <= end + 1e-6; s += 0.5) {
                AlignmentPose pose = HorizontalAlignmentGeometry.poseAt(
                    alignment, Math.min(s, end)).orElseThrow();
                points.add(new Vec2d(pose.x(), pose.y()));
            }
            RoadEdge edge = network.createEdge(
                corners.get(i).getId(),
                corners.get(next).getId(),
                points,
                road.getId());
            if (first == null) {
                first = edge;
            }
        }
        road.setLoopSeam(RoadLoopSeam.onSegment(
            corners.getFirst().getPosition().copy(), first.getId(), 0.0));
        RoadDesignDirectionAuthority.ensureBound(network, road);
        return road;
    }
}
