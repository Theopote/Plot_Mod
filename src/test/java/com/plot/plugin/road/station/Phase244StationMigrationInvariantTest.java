package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.vertical.EditedProfileGeometry;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalControlPointConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2.4.4 station migration invariants MS01–MS16.
 */
class Phase244StationMigrationInvariantTest {

    private static final double EPS = 1e-3;

    private RoadNetworkManager manager;
    private RoadNetwork network;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("phase244"), new RoadProjectStatus());
        network = manager.getNetwork();
    }

    @Test
    void ms01_splitEdgePreservesHaAndAlongTrack() {
        Road road = linearRoad(3, 30.0);
        RoadHorizontalAlignment ha = new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0, List.of(HorizontalAlignmentElement.tangent(90.0)));
        road.setHorizontalAlignment(ha);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(45.0, 12.0),
            PointOfVerticalIntersection.of(90.0, 11.0))));
        String edgeId = road.getOrderedSegmentIds().get(1);
        assertTrue(RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, 15.0).isSuccess());
        Road after = network.getRoad(road.getId());
        assertNotNull(after.getHorizontalAlignment());
        assertEquals(ha.getElements().size(), after.getHorizontalAlignment().getElements().size());
        assertEquals(3, after.getVerticalAlignment().pviCount());
        assertEquals(90.0, RoadStationing.canonicalLength(network, after), EPS);
    }

    @Test
    void ms02_mergeEdgePreservesDesignEntry() {
        Road road = linearRoad(2, 40.0);
        String first = road.getOrderedSegmentIds().getFirst();
        road.setDesignEntryEdgeId(first);
        road.setDesignEntryNodeId(network.getEdge(first).getStartNodeId());
        String mid = network.getEdge(first).getEndNodeId();
        assertTrue(RoadCenterlineEditor.mergeThroughNode(network, mid).isSuccess());
        Road after = network.getRoad(road.getId());
        assertEquals(1, after.getOrderedSegmentIds().size());
        assertEquals(after.getOrderedSegmentIds().getFirst(), after.getDesignEntryEdgeId());
    }

    @Test
    void ms03_seamMigratesOnInteriorEdgeSplit() {
        Road road = squareLoop(20.0);
        String edgeId = road.getOrderedSegmentIds().getFirst();
        // localFraction 0.75 on 20m edge → physical (15, 0)
        Vec2d seamPos = new Vec2d(15, 0);
        road.setLoopSeam(RoadLoopSeam.onSegment(seamPos, edgeId, 0.75));
        Vec2d beforePos = RoadLoopSeamService.overlayPosition(network, road);
        assertNotNull(beforePos);
        assertEquals(15.0, beforePos.x, 0.5);
        assertTrue(RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, 10.0).isSuccess());
        Road after = network.getRoad(road.getId());
        RoadLoopSeam seam = after.getLoopSeam();
        assertNotNull(seam);
        assertNotNull(seam.segmentHintId());
        assertFalse(edgeId.equals(seam.segmentHintId()));
        Vec2d afterPos = RoadLoopSeamService.overlayPosition(network, after);
        assertNotNull(afterPos);
        assertEquals(beforePos.x, afterPos.x, 0.5);
        assertEquals(beforePos.y, afterPos.y, 0.5);
    }

    @Test
    void ms04_vaGradeSplitUsesRealBoundaryElevation() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(100.0, 20.0)));
        double expected = VerticalAlignmentGeometry.elevationAt(source, 40.0).orElseThrow();
        RoadVerticalAlignment head = RoadStationDataTransforms.extractVerticalAlignmentRange(
            source, 0.0, 40.0);
        RoadVerticalAlignment tail = RoadStationDataTransforms.extractVerticalAlignmentRange(
            source, 40.0, 100.0);
        assertNotNull(head);
        assertNotNull(tail);
        assertEquals(expected, head.sortedPvis().getLast().getElevation(), EPS);
        assertEquals(expected, tail.sortedPvis().getFirst().getElevation(), EPS);
        assertEquals(40.0, head.sortedPvis().getLast().getStation(), EPS);
        assertEquals(0.0, tail.sortedPvis().getFirst().getStation(), EPS);
    }

    @Test
    void ms05_vaCurveCutRefusesOrKeepsWhole() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.withCurve(50.0, 15.0, 20.0),
            PointOfVerticalIntersection.of(100.0, 12.0)));
        assertNull(RoadStationDataTransforms.extractVerticalAlignmentRange(source, 0.0, 50.0),
            "cut through vertical curve must refuse");
        RoadVerticalAlignment whole = RoadStationDataTransforms.extractVerticalAlignmentRange(
            source, 0.0, 100.0);
        assertNotNull(whole);
        assertEquals(3, whole.pviCount());
    }

    @Test
    void ms06_vaMergeKeepsContinuityAndConstraints() {
        RoadVerticalAlignment head = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            new PointOfVerticalIntersection(
                50.0, 12.0, null, VerticalControlPointConstraint.FREE)));
        RoadVerticalAlignment tail = new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(
                0.0, 12.0, null, VerticalControlPointConstraint.USER_LOCKED),
            PointOfVerticalIntersection.of(40.0, 11.0)));
        RoadVerticalAlignment merged = RoadStationDataTransforms.mergeVerticalAlignments(
            head, tail, 50.0);
        assertNotNull(merged);
        assertEquals(3, merged.pviCount());
        assertEquals(VerticalControlPointConstraint.USER_LOCKED,
            merged.sortedPvis().get(1).getConstraint());

        RoadVerticalAlignment conflictHead = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            new PointOfVerticalIntersection(
                50.0, 12.0, null, VerticalControlPointConstraint.JUNCTION_FIXED)));
        RoadVerticalAlignment conflictTail = new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(
                0.0, 12.0, null, VerticalControlPointConstraint.USER_LOCKED),
            PointOfVerticalIntersection.of(40.0, 11.0)));
        assertNull(RoadStationDataTransforms.mergeVerticalAlignments(
            conflictHead, conflictTail, 50.0));
    }

    @Test
    void ms07_vaJunctionElevConflictRefusesNetworkUnchanged() {
        Road head = linearRoad(2, 50.0);
        Road tail = network.createRoad("tail");
        RoadNode n = network.getNode(
            network.getEdge(head.getOrderedSegmentIds().getLast()).getEndNodeId());
        RoadNode end = network.createNode(new Vec2d(n.getPosition().x + 40, 0));
        network.createEdge(n.getId(), end.getId(),
            List.of(n.getPosition().copy(), end.getPosition().copy()), tail.getId());
        head.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(100.0, 12.0))));
        tail.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 20.0),
            PointOfVerticalIntersection.of(40.0, 21.0))));
        String before = network.toJson();
        assertNull(network.mergeRoadTailIntoHead(head.getId(), tail.getId()));
        assertEquals(before, network.toJson());
        assertNotNull(network.getRoad(tail.getId()));
    }

    @Test
    void ms08_smoothVcsSplitPreservesOrRefuses() {
        RoadCrossSection def = section(8);
        RoadCrossSection wide = section(16);
        RoadVariableCrossSections smooth = new RoadVariableCrossSections(List.of(
            StationCrossSection.at(0.0, def.copy()),
            StationCrossSection.at(100.0, wide.copy())),
            VariableCrossSectionTransitionMode.SMOOTH);
        Road head = new Road("h");
        Road t = new Road("t");
        head.setCrossSection(def.copy());
        head.setVariableCrossSections(smooth);
        String beforeSnapshot = head.getVariableCrossSections().toString()
            + "|" + head.getVerticalAlignment()
            + "|" + head.getCrossSection();
        RoadSystemConfig cfg = new RoadSystemConfig("phase244");
        var expectedMid = VariableCrossSectionResolver.resolve(
            def, smooth.sortedStations(), VariableCrossSectionTransitionMode.SMOOTH, 40.0, cfg);
        StationMigrationResult result = RoadStationDataTransforms.applyRoadSplit(
            head, t, 40.0, 100.0, cfg);
        if (result.isSuccess()) {
            assertEquals(VariableCrossSectionTransitionMode.SMOOTH,
                head.getVariableCrossSections().getTransitionMode());
            assertNotNull(t.getVariableCrossSections());
            assertEquals(VariableCrossSectionTransitionMode.SMOOTH,
                t.getVariableCrossSections().getTransitionMode());
            var atHeadEnd = VariableCrossSectionResolver.resolve(
                head.getCrossSection(), head.getVariableCrossSections().sortedStations(),
                VariableCrossSectionTransitionMode.SMOOTH, 40.0, cfg);
            var atTailStart = VariableCrossSectionResolver.resolve(
                t.getCrossSection() != null ? t.getCrossSection() : head.getCrossSection(),
                t.getVariableCrossSections().sortedStations(),
                VariableCrossSectionTransitionMode.SMOOTH, 0.0, cfg);
            assertTrue(atHeadEnd.displayEquals(expectedMid),
                "success must preserve resolved section at split (displayEquals)");
            assertTrue(atTailStart.displayEquals(expectedMid),
                "tail start must match pre-split boundary section");
        } else {
            assertEquals(beforeSnapshot,
                head.getVariableCrossSections().toString()
                    + "|" + head.getVerticalAlignment()
                    + "|" + head.getCrossSection(),
                "failure must leave full engineering snapshot unchanged");
            assertNull(t.getVariableCrossSections());
        }
    }

    @Test
    void ms09_differentDefaultSectionsKeepPhysicalIntervals() {
        RoadCrossSection narrow = section(6);
        RoadCrossSection wide = section(12);
        Road head = new Road("h");
        Road tail = new Road("t");
        head.setCrossSection(narrow.copy());
        tail.setCrossSection(wide.copy());
        RoadSystemConfig cfg = new RoadSystemConfig("phase244");
        StationMigrationResult result = RoadStationDataTransforms.applyRoadMerge(
            head, head, tail, 50.0, 50.0, cfg);
        assertTrue(result.isSuccess());
        assertNotNull(head.getVariableCrossSections());
        assertEquals(6, head.getCrossSection().getCarriageway().getWidth());
        var resolvedHead = VariableCrossSectionResolver.resolve(
            head.getCrossSection(),
            head.getVariableCrossSections().sortedStations(),
            head.getVariableCrossSections().getTransitionMode(),
            10.0,
            cfg);
        var resolvedTail = VariableCrossSectionResolver.resolve(
            head.getCrossSection(),
            head.getVariableCrossSections().sortedStations(),
            head.getVariableCrossSections().getTransitionMode(),
            75.0,
            cfg);
        assertEquals(6, resolvedHead.carriagewayWidth);
        assertEquals(12, resolvedTail.carriagewayWidth);
    }

    @Test
    void ms10_nullEndGuardrailDoesNotExtendOnMerge() {
        Road head = new Road("h");
        Road tail = new Road("t");
        head.setStationFacilities(new RoadStationFacilities(List.of(
            new StationFacilityRun(0.0, null, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT,
                null, null))));
        tail.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 20.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT))));
        StationMigrationResult result = RoadStationDataTransforms.applyRoadMerge(
            head, head, tail, 100.0, 80.0);
        assertTrue(result.isSuccess());
        List<StationFacilityRun> runs = head.getStationFacilities().sortedRuns();
        StationFacilityRun left = runs.stream()
            .filter(r -> r.getSide() == RoadFacilitySide.LEFT).findFirst().orElseThrow();
        assertEquals(100.0, left.getEndStation(), EPS);
        assertTrue(left.getEndStation() <= 100.0 + EPS);
    }

    @Test
    void ms11_deleteHeadEdgeClipsNotCompresses() {
        Road road = linearRoad(3, 30.0);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(30.0, 11.0),
            PointOfVerticalIntersection.of(60.0, 12.0),
            PointOfVerticalIntersection.of(90.0, 13.0))));
        String headEdge = road.getOrderedSegmentIds().getFirst();
        manager.deleteEdge(headEdge);
        network = manager.getNetwork();
        Road after = network.getRoad(road.getId());
        assertNotNull(after);
        assertNotNull(after.getVerticalAlignment());
        assertEquals(0.0, after.getVerticalAlignment().sortedPvis().getFirst().getStation(), EPS);
        assertEquals(60.0, after.getVerticalAlignment().sortedPvis().getLast().getStation(), EPS);
        assertEquals(11.0, after.getVerticalAlignment().sortedPvis().getFirst().getElevation(), EPS);
    }

    @Test
    void ms12_deleteMiddleEdgeExtractsBothComponents() {
        Road road = linearRoad(3, 40.0);
        List<String> segs = List.copyOf(road.getOrderedSegmentIds());
        String headEdge = segs.get(0);
        String mid = segs.get(1);
        String tailEdge = segs.get(2);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(40.0, 11.0),
            PointOfVerticalIntersection.of(80.0, 12.0),
            PointOfVerticalIntersection.of(120.0, 13.0))));
        manager.deleteEdge(mid);
        network = manager.getNetwork();
        Road headComp = null;
        Road tailComp = null;
        for (Road r : network.getRoads().values()) {
            if (r.getOrderedSegmentIds().contains(headEdge)) {
                headComp = r;
            }
            if (r.getOrderedSegmentIds().contains(tailEdge)) {
                tailComp = r;
            }
        }
        assertNotNull(headComp);
        assertNotNull(tailComp);
        assertNotNull(headComp.getDesignEntryEdgeId());
        assertNotNull(tailComp.getDesignEntryEdgeId());
        assertEquals(10.0, headComp.getVerticalAlignment().sortedPvis().getFirst().getElevation(), EPS);
        assertEquals(11.0, headComp.getVerticalAlignment().sortedPvis().getLast().getElevation(), EPS);
        assertEquals(12.0, tailComp.getVerticalAlignment().sortedPvis().getFirst().getElevation(), EPS);
        assertEquals(13.0, tailComp.getVerticalAlignment().sortedPvis().getLast().getElevation(), EPS);
    }

    @Test
    void ms13_loopOpenWithoutAlongTrackDemotes() {
        Road loop = squareLoop(10.0);
        String edge = loop.getOrderedSegmentIds().getFirst();
        manager.deleteEdge(edge);
        network = manager.getNetwork();
        Road after = network.getRoad(loop.getId());
        assertNotNull(after);
        assertEquals(RoadTopologyMode.LINEAR, after.getTopologyMode());
    }

    @Test
    void ms14_haPresentSplitRoadRefitsOrRefusesCleanly() {
        // Forced success: non-uniform tangents that match the instance polyline.
        Road road = linearRoad(3, 30.0);
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(
                HorizontalAlignmentElement.tangent(20.0),
                HorizontalAlignmentElement.tangent(40.0),
                HorizontalAlignmentElement.tangent(30.0))));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(45.0, 12.0),
            PointOfVerticalIntersection.of(90.0, 11.0))));
        assertTrue(RoadStationing.domainMap(network, road).isReliable());
        String splitSeg = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String newId = manager.splitRoadBeforeSegment(road.getId(), splitSeg);
        network = manager.getNetwork();
        assertNotNull(newId, "compatible non-uniform HA + VA split must succeed");
        Road head = network.getRoad(road.getId());
        Road tail = network.getRoad(newId);
        assertNotNull(head.getVerticalAlignment());
        assertNotNull(tail.getVerticalAlignment());
        assertNotNull(head.getHorizontalAlignment());
        assertNotNull(tail.getHorizontalAlignment());
        double headLen = RoadStationing.canonicalLength(network, head);
        assertEquals(headLen, head.getVerticalAlignment().sortedPvis().getLast().getStation(), 0.5);

        // Forced refuse: circular-arc HA vs straight instance is unreliable, and a vertical
        // curve straddling the split station refuses migration (live unchanged).
        Road refuseRoad = linearRoad(3, 30.0);
        refuseRoad.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(
                HorizontalAlignmentElement.tangent(30.0),
                HorizontalAlignmentElement.circularArc(30.0, 40.0, TurnDirection.LEFT),
                HorizontalAlignmentElement.tangent(30.0))));
        refuseRoad.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.withCurve(30.0, 12.0, 20.0),
            PointOfVerticalIntersection.of(90.0, 11.0))));
        assertFalse(RoadStationing.domainMap(network, refuseRoad).isReliable());
        String refuseSeg = RoadSegmentOrdering.orderedSegmentIds(network, refuseRoad).get(1);
        String beforeJson = network.toJson();
        assertNull(manager.splitRoadBeforeSegment(refuseRoad.getId(), refuseSeg),
            "arc HA + vertical-curve cut must refuse split");
        network = manager.getNetwork(); // abort replaces live network reference
        assertEquals(beforeJson, network.toJson());

        // Deterministic refuse: curve cut on extract.
        RoadVerticalAlignment curved = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.withCurve(15.0, 12.0, 20.0),
            PointOfVerticalIntersection.of(30.0, 11.0)));
        assertNull(RoadStationDataTransforms.extractVerticalAlignmentRange(curved, 0.0, 15.0));

        // Forced success: reliable circular-arc HA + VA + VCS + facilities → extract + physical check.
        double radius = 40.0;
        double arcLen = Math.PI * radius / 2.0;
        RoadHorizontalAlignment arcHa = new RoadHorizontalAlignment(
            new Vec2d(radius, 0),
            Math.PI / 2.0,
            List.of(HorizontalAlignmentElement.circularArc(arcLen, radius, TurnDirection.LEFT)));
        List<Vec2d> onHa = HorizontalAlignmentGeometry.sample(arcHa, 4.0);
        assertTrue(onHa.size() >= 6);
        int mid = onHa.size() / 2;
        Road arcRoad = network.createRoad("arc-ok");
        RoadNode a0 = network.createNode(onHa.getFirst().copy());
        RoadNode aMid = network.createNode(onHa.get(mid).copy());
        RoadNode a1 = network.createNode(onHa.getLast().copy());
        String arcHeadEdge = network.createEdge(a0.getId(), aMid.getId(),
            onHa.subList(0, mid + 1).stream().map(Vec2d::copy).toList(), arcRoad.getId()).getId();
        String arcSplit = network.createEdge(aMid.getId(), a1.getId(),
            onHa.subList(mid, onHa.size()).stream().map(Vec2d::copy).toList(), arcRoad.getId()).getId();
        arcRoad.setHorizontalAlignment(arcHa);
        arcRoad.setDesignEntryEdgeId(arcHeadEdge);
        arcRoad.setDesignEntryNodeId(a0.getId());
        double arcTotal = RoadStationing.canonicalLength(network, arcRoad);
        double splitSta = RoadStationing.segmentStartStation(network, arcRoad, arcSplit);
        arcRoad.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(splitSta * 0.5, 11.0),
            PointOfVerticalIntersection.of(arcTotal, 12.0))));
        arcRoad.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(0.0, section(8)),
            StationCrossSection.at(splitSta * 0.4, section(10))),
            VariableCrossSectionTransitionMode.STEP));
        arcRoad.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(1.0, splitSta * 0.6, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT))));
        double elevBefore = VerticalAlignmentGeometry.elevationAt(
            arcRoad.getVerticalAlignment(), splitSta * 0.5).orElseThrow();
        var vaCap = RoadStationDataTransforms.captureVaPhysicalAnchors(
            network, arcRoad, List.of(splitSta));
        var alongCap = RoadStationDataTransforms.captureAlongTrackPhysicalAnchors(
            network, arcRoad, List.of(splitSta));
        assertFalse(vaCap.failed());
        assertFalse(alongCap.failed());
        assertTrue(RoadStationing.domainMap(network, arcRoad).isReliable(),
            "arc HA + matching samples must be reliable");
        assertTrue(splitSta > 1.0);
        String arcNew = manager.splitRoadBeforeSegment(arcRoad.getId(), arcSplit);
        network = manager.getNetwork();
        assertNotNull(arcNew, "reliable arc HA + matching polyline must split via extract");
        Road arcHead = network.getRoad(arcRoad.getId());
        Road arcTail = network.getRoad(arcNew);
        assertNotNull(arcHead.getHorizontalAlignment());
        assertNotNull(arcTail.getHorizontalAlignment());
        assertTrue(RoadStationing.domainMap(network, arcHead).isReliable());
        assertTrue(RoadStationing.domainMap(network, arcTail).isReliable());
        assertTrue(RoadStationDataTransforms.verifyVaPhysicalAnchors(network, arcHead, vaCap.anchors()));
        assertTrue(RoadStationDataTransforms.verifyVaPhysicalAnchors(network, arcTail, vaCap.anchors()));
        assertTrue(RoadStationDataTransforms.verifyAlongTrackPhysicalAnchors(
            network, arcHead, alongCap.anchors()));
        assertTrue(RoadStationDataTransforms.verifyAlongTrackPhysicalAnchors(
            network, arcTail, alongCap.anchors()));
        assertEquals(elevBefore,
            VerticalAlignmentGeometry.elevationAt(arcHead.getVerticalAlignment(), splitSta * 0.5)
                .orElseThrow(),
            1e-2);
    }

    @Test
    void ms15_prependMergeBindsDesignEntryFromTail() {
        Road head = network.createRoad("head");
        RoadNode h0 = network.createNode(new Vec2d(40, 0));
        RoadNode h1 = network.createNode(new Vec2d(80, 0));
        network.createEdge(h0.getId(), h1.getId(),
            List.of(new Vec2d(40, 0), new Vec2d(80, 0)), head.getId());
        Road tail = network.createRoad("tail");
        RoadNode t0 = network.createNode(new Vec2d(0, 0));
        network.createEdge(t0.getId(), h0.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)), tail.getId());
        tail.setDesignEntryEdgeId(tail.getOrderedSegmentIds().getFirst());
        tail.setDesignEntryNodeId(t0.getId());
        String merged = network.mergeRoadTailIntoHead(head.getId(), tail.getId());
        assertEquals(head.getId(), merged);
        Road after = network.getRoad(head.getId());
        assertEquals(t0.getId(), after.getDesignEntryNodeId());
        assertNotNull(after.getDesignEntryEdgeId());
        assertEquals(t0.getId(),
            network.getEdge(after.getDesignEntryEdgeId()).getStartNodeId());
    }

    @Test
    void ms16_directModelMigrationFailureLeavesJsonUnchanged() {
        Road head = linearRoad(2, 50.0);
        Road tail = network.createRoad("tail");
        RoadNode join = network.getNode(
            network.getEdge(head.getOrderedSegmentIds().getLast()).getEndNodeId());
        RoadNode end = network.createNode(new Vec2d(join.getPosition().x + 50, 0));
        network.createEdge(join.getId(), end.getId(),
            List.of(join.getPosition().copy(), end.getPosition().copy()), tail.getId());
        head.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 5.0),
            PointOfVerticalIntersection.of(100.0, 6.0))));
        tail.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 99.0),
            PointOfVerticalIntersection.of(50.0, 100.0))));
        String before = network.toJson();
        assertNull(network.mergeRoadTailIntoHead(head.getId(), tail.getId()));
        assertEquals(before, network.toJson());
    }

    @Test
    void reverseEdgeMergePreservesSeamPhysicalPosition() {
        // Real LOOP + asymmetric fraction 0.2 (not 0.5) so reverse L−d mirroring is detectable.
        Road road = squareLoop(40.0);
        String first = road.getOrderedSegmentIds().getFirst();
        String second = road.getOrderedSegmentIds().get(1);
        road.setDesignEntryEdgeId(first);
        road.setDesignEntryNodeId(network.getEdge(first).getStartNodeId());
        // 0.2 along second edge from storage start (40,0)→(40,40) → (40, 8).
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(40, 8), second, 0.2));
        assertTrue(RoadLoopSeamService.isSeamResolvable(network, road));
        Vec2d beforePos = RoadLoopSeamService.overlayPosition(network, road);
        assertNotNull(beforePos);
        assertEquals(40.0, beforePos.x, 0.5);
        assertEquals(8.0, beforePos.y, 0.5);

        // Reverse-storage second edge: merge must apply L−d (0.2 → 0.8 on that span).
        RoadEdge e2 = network.getEdge(second);
        network.updateEdgeEndpoints(second, e2.getEndNodeId(), e2.getStartNodeId());
        List<Vec2d> pts = new java.util.ArrayList<>(e2.getCenterlinePoints());
        java.util.Collections.reverse(pts);
        e2.setCenterlinePoints(pts);
        // After reverse, same world point is at localFraction 0.8 in storage.
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(40, 8), second, 0.8));
        assertTrue(RoadLoopSeamService.isSeamResolvable(network, road));

        String through = network.getEdge(first).getEndNodeId();
        // Through may now be end of reversed second — use shared node of first & second.
        RoadEdge e1 = network.getEdge(first);
        String nA = e1.getEndNodeId();
        String nB = e1.getStartNodeId();
        RoadEdge e2b = network.getEdge(second);
        if (e2b.getStartNodeId().equals(nA) || e2b.getEndNodeId().equals(nA)) {
            through = nA;
        } else {
            through = nB;
        }
        assertTrue(RoadCenterlineEditor.mergeThroughNode(network, through).isSuccess());
        Road after = network.getRoad(road.getId());
        assertTrue(RoadLoopSeamService.isSeamResolvable(network, after));
        Vec2d afterPos = RoadLoopSeamService.overlayPosition(network, after);
        assertNotNull(afterPos);
        assertEquals(beforePos.x, afterPos.x, 0.5);
        assertEquals(beforePos.y, afterPos.y, 0.5);
        assertNotNull(after.getLoopSeam().localFraction());
        assertTrue(Math.abs(after.getLoopSeam().localFraction() - 0.5) > 1e-3,
            "asymmetric seam must not collapse to midpoint");
    }

    @Test
    void reverseDesignWalkSplitBindsTailEntryFromWalk() {
        Road road = network.createRoad("rev-walk");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        RoadNode c = network.createNode(new Vec2d(80, 0));
        // Edges stored B←A and C←B (both reverse of design A→B→C).
        String e1 = network.createEdge(b.getId(), a.getId(),
            List.of(new Vec2d(40, 0), new Vec2d(0, 0)), road.getId()).getId();
        String e2 = network.createEdge(c.getId(), b.getId(),
            List.of(new Vec2d(80, 0), new Vec2d(40, 0)), road.getId()).getId();
        road.setDesignEntryEdgeId(e1);
        road.setDesignEntryNodeId(a.getId());
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(40.0, 11.0),
            PointOfVerticalIntersection.of(80.0, 12.0))));
        String newId = manager.splitRoadBeforeSegment(road.getId(), e2);
        network = manager.getNetwork();
        assertNotNull(newId);
        Road head = network.getRoad(road.getId());
        Road tail = network.getRoad(newId);
        assertEquals(a.getId(), head.getDesignEntryNodeId());
        assertEquals(b.getId(), tail.getDesignEntryNodeId());
    }

    @Test
    void deleteEdgeCuttingVerticalCurveRefusesWholeNetwork() {
        Road road = linearRoad(2, 40.0);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.withCurve(40.0, 15.0, 20.0),
            PointOfVerticalIntersection.of(80.0, 12.0))));
        String before = network.toJson();
        String mid = road.getOrderedSegmentIds().getFirst();
        // Deleting first edge clips at station 40 which cuts the vertical curve → refuse.
        manager.deleteEdge(mid);
        network = manager.getNetwork();
        assertEquals(before, network.toJson());
    }

    @Test
    void editedProfileSurvivesSuccessfulSplitMerge() {
        Road road = linearRoad(2, 40.0);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(40.0, 11.0),
            PointOfVerticalIntersection.of(80.0, 12.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
        String splitSeg = road.getOrderedSegmentIds().get(1);
        String tailId = manager.splitRoadBeforeSegment(road.getId(), splitSeg);
        network = manager.getNetwork();
        assertNotNull(tailId);
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, network.getRoad(road.getId())));
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, network.getRoad(tailId)));
        String merged = manager.mergeRoadTailIntoHead(road.getId(), tailId);
        network = manager.getNetwork();
        assertEquals(road.getId(), merged);
        Road after = network.getRoad(road.getId());
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, after));
        assertTrue(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, after));
    }

    @Test
    void staleEditedProfileIsNotReactivatedOnSplit() {
        Road road = linearRoad(2, 40.0);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(80.0, 12.0))));
        road.setProfileSource(ProfileSource.EDITED);
        road.setEditedGeometrySignature(999_001); // deliberately wrong
        assertFalse(ResolvedProfileAuthority.hasValidEditedProfile(network, road));
        String splitSeg = road.getOrderedSegmentIds().get(1);
        String tailId = manager.splitRoadBeforeSegment(road.getId(), splitSeg);
        network = manager.getNetwork();
        assertNotNull(tailId);
        Road head = network.getRoad(road.getId());
        Road tail = network.getRoad(tailId);
        assertFalse(ResolvedProfileAuthority.hasValidEditedProfile(network, head));
        assertFalse(ResolvedProfileAuthority.hasValidEditedProfile(network, tail));
        assertFalse(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, head));
    }

    @Test
    void topologyRepairMigrationFailureRollsBack() {
        Road road = linearRoad(3, 40.0);
        List<String> segs = List.copyOf(road.getOrderedSegmentIds());
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.withCurve(40.0, 14.0, 20.0),
            PointOfVerticalIntersection.of(120.0, 11.0))));
        // Disconnect without migration so repair must extract across the vertical curve.
        network.removeEdge(segs.get(1));
        network = manager.getNetwork();
        String before = network.toJson();
        long revision = manager.getNetworkRevision();
        var result = manager.repairTopology(network.getRoad(road.getId()));
        network = manager.getNetwork();
        assertTrue(result.migrationFailed());
        assertEquals(before, network.toJson());
        assertEquals(revision, manager.getNetworkRevision());
        assertFalse(manager.canUndo());
    }

    @Test
    void openMergeToLoopPreservesVaVcsFacilitiesAndSpliceOrigin() {
        // Non-min-XY design origin at (40,0): min-XY would be (0,0) — seam must stay at splice.
        Road head = network.createRoad("loop-head");
        RoadNode n0 = network.createNode(new Vec2d(40, 0));
        RoadNode n1 = network.createNode(new Vec2d(40, 40));
        RoadNode n2 = network.createNode(new Vec2d(0, 40));
        network.createEdge(n0.getId(), n1.getId(),
            List.of(new Vec2d(40, 0), new Vec2d(40, 40)), head.getId());
        network.createEdge(n1.getId(), n2.getId(),
            List.of(new Vec2d(40, 40), new Vec2d(0, 40)), head.getId());
        head.setDesignEntryEdgeId(head.getOrderedSegmentIds().getFirst());
        head.setDesignEntryNodeId(n0.getId());
        head.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(40.0, 11.0),
            PointOfVerticalIntersection.of(80.0, 12.0))));
        head.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(20.0, section(10))),
            VariableCrossSectionTransitionMode.STEP));
        head.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(5.0, 60.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT))));

        Road tail = network.createRoad("loop-tail");
        RoadNode n3 = network.createNode(new Vec2d(0, 0));
        String tailFirst = network.createEdge(n2.getId(), n3.getId(),
            List.of(new Vec2d(0, 40), new Vec2d(0, 0)), tail.getId()).getId();
        network.createEdge(n3.getId(), n0.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)), tail.getId());
        // Bind tail walk n2→n0 so append is forward (no reverse + protected refuse).
        tail.setDesignEntryEdgeId(tailFirst);
        tail.setDesignEntryNodeId(n2.getId());
        tail.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 12.0),
            PointOfVerticalIntersection.of(40.0, 11.5),
            PointOfVerticalIntersection.of(80.0, 10.0))));
        tail.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(30.0, section(12))),
            VariableCrossSectionTransitionMode.STEP));
        tail.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 50.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT))));

        String merged = manager.mergeRoadTailIntoHead(head.getId(), tail.getId());
        network = manager.getNetwork();
        assertEquals(head.getId(), merged);
        Road after = network.getRoad(head.getId());
        assertEquals(RoadTopologyMode.LOOP, after.getTopologyMode());
        assertNotNull(after.getLoopSeam());
        assertTrue(RoadLoopSeamService.isSeamResolvable(network, after));
        Vec2d seamPos = RoadLoopSeamService.overlayPosition(network, after);
        assertNotNull(seamPos);
        assertEquals(40.0, seamPos.x, 0.5, "seam must stay at non-min-XY splice origin");
        assertEquals(0.0, seamPos.y, 0.5);
        Optional<Vec2d> atZero = RoadStationing.pointAtStation(network, after, 0.0);
        assertTrue(atZero.isPresent());
        assertEquals(40.0, atZero.get().x, 0.5);
        assertEquals(0.0, atZero.get().y, 0.5);
        // Design direction: station grows away from (40,0) toward (40,40), not toward min-XY (0,0).
        Optional<Vec2d> atSmall = RoadStationing.pointAtStation(network, after, 10.0);
        assertTrue(atSmall.isPresent());
        assertEquals(40.0, atSmall.get().x, 1.0);
        assertTrue(atSmall.get().y > 5.0, "design direction must climb +Y from splice, not toward (0,0)");
        assertNotNull(after.getVerticalAlignment());
        assertTrue(after.getVerticalAlignment().pviCount() >= 3);
        assertNotNull(after.getVariableCrossSections());
        assertFalse(after.getVariableCrossSections().isEmpty());
        assertNotNull(after.getStationFacilities());
        assertFalse(after.getStationFacilities().isEmpty());
    }

    @Test
    void deleteEdgeMigrationFailureKeepsSelectionUndoAndRevision() {
        Road road = linearRoad(2, 40.0);
        String mid = road.getOrderedSegmentIds().getFirst();
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.withCurve(40.0, 15.0, 20.0),
            PointOfVerticalIntersection.of(80.0, 12.0))));
        manager.handleEdgeSelect(mid, false);
        assertTrue(manager.getSelectedEdgeIds().contains(mid));
        String before = network.toJson();
        long revision = manager.getNetworkRevision();
        boolean couldUndo = manager.canUndo();
        manager.deleteEdge(mid);
        network = manager.getNetwork();
        assertEquals(before, network.toJson());
        assertEquals(revision, manager.getNetworkRevision());
        assertEquals(couldUndo, manager.canUndo());
        assertTrue(manager.getSelectedEdgeIds().contains(mid),
            "failed delete must not clear selection");
    }

    private Road linearRoad(int segments, double segLen) {
        Road road = network.createRoad("linear-" + System.nanoTime());
        RoadNode prev = network.createNode(new Vec2d(0, 0));
        for (int i = 1; i <= segments; i++) {
            RoadNode next = network.createNode(new Vec2d(segLen * i, 0));
            network.createEdge(prev.getId(), next.getId(),
                List.of(prev.getPosition().copy(), next.getPosition().copy()), road.getId());
            prev = next;
        }
        return road;
    }

    private Road squareLoop(double edge) {
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode n1 = network.createNode(new Vec2d(edge, 0));
        RoadNode n2 = network.createNode(new Vec2d(edge, edge));
        RoadNode n3 = network.createNode(new Vec2d(0, edge));
        network.createEdge(n0.getId(), n1.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(edge, 0)), road.getId());
        network.createEdge(n1.getId(), n2.getId(),
            List.of(new Vec2d(edge, 0), new Vec2d(edge, edge)), road.getId());
        network.createEdge(n2.getId(), n3.getId(),
            List.of(new Vec2d(edge, edge), new Vec2d(0, edge)), road.getId());
        network.createEdge(n3.getId(), n0.getId(),
            List.of(new Vec2d(0, edge), new Vec2d(0, 0)), road.getId());
        return road;
    }

    @Test
    void intactVerticalCurveSurvivesSplitWithElevationAtEquivalence() {
        Road road = linearRoad(3, 40.0);
        // Curve fully inside first 40m; split at 40 leaves curve intact on head.
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.withCurve(20.0, 14.0, 16.0),
            PointOfVerticalIntersection.of(40.0, 12.0),
            PointOfVerticalIntersection.of(120.0, 11.0))));
        double elev10 = VerticalAlignmentGeometry.elevationAt(
            road.getVerticalAlignment(), 10.0).orElseThrow();
        double elev20 = VerticalAlignmentGeometry.elevationAt(
            road.getVerticalAlignment(), 20.0).orElseThrow();
        double elev30 = VerticalAlignmentGeometry.elevationAt(
            road.getVerticalAlignment(), 30.0).orElseThrow();
        String splitSeg = road.getOrderedSegmentIds().get(1);
        String tailId = manager.splitRoadBeforeSegment(road.getId(), splitSeg);
        network = manager.getNetwork();
        assertNotNull(tailId);
        Road head = network.getRoad(road.getId());
        assertEquals(elev10,
            VerticalAlignmentGeometry.elevationAt(head.getVerticalAlignment(), 10.0).orElseThrow(),
            1e-2);
        assertEquals(elev20,
            VerticalAlignmentGeometry.elevationAt(head.getVerticalAlignment(), 20.0).orElseThrow(),
            1e-2);
        assertEquals(elev30,
            VerticalAlignmentGeometry.elevationAt(head.getVerticalAlignment(), 30.0).orElseThrow(),
            1e-2);
    }

    @Test
    void validEditedPlusStaleEditedMergeRefuses() {
        Road head = linearRoad(1, 40.0);
        head.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(40.0, 11.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, head);
        assertTrue(ResolvedProfileAuthority.hasValidEditedProfile(network, head));

        Road tail = network.createRoad("stale-tail");
        // Share head exit node so merge can append.
        String headExit = RoadStationing.chainExitNodeId(network, head).orElseThrow();
        RoadNode shared = network.getNode(headExit);
        RoadNode t1 = network.createNode(new Vec2d(80, 0));
        network.createEdge(shared.getId(), t1.getId(),
            List.of(shared.getPosition().copy(), t1.getPosition().copy()), tail.getId());
        tail.setDesignEntryEdgeId(tail.getOrderedSegmentIds().getFirst());
        tail.setDesignEntryNodeId(shared.getId());
        tail.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 11.0),
            PointOfVerticalIntersection.of(40.0, 12.0))));
        tail.setProfileSource(ProfileSource.EDITED);
        tail.setEditedGeometrySignature(999_002);
        assertFalse(ResolvedProfileAuthority.hasValidEditedProfile(network, tail));

        String before = network.toJson();
        assertNull(manager.mergeRoadTailIntoHead(head.getId(), tail.getId()));
        network = manager.getNetwork();
        assertEquals(before, network.toJson());
    }

    @Test
    void opposedHaSplitUsesNativeExtractOrRefusesCleanly() {
        Road road = network.createRoad("opposed-ha");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        RoadNode c = network.createNode(new Vec2d(80, 0));
        // Edges stored reverse of design A→B→C.
        String e1 = network.createEdge(b.getId(), a.getId(),
            List.of(new Vec2d(40, 0), new Vec2d(0, 0)), road.getId()).getId();
        String e2 = network.createEdge(c.getId(), b.getId(),
            List.of(new Vec2d(80, 0), new Vec2d(40, 0)), road.getId()).getId();
        road.setDesignEntryEdgeId(e1);
        road.setDesignEntryNodeId(a.getId());
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0, List.of(HorizontalAlignmentElement.tangent(80.0))));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(40.0, 11.0),
            PointOfVerticalIntersection.of(80.0, 12.0))));
        assertTrue(RoadStationing.isPhysicalWalkOpposedToDesign(network, road)
            || RoadStationing.domainMap(network, road).isReliable());
        String before = network.toJson();
        String newId = manager.splitRoadBeforeSegment(road.getId(), e2);
        network = manager.getNetwork();
        if (newId == null) {
            assertEquals(before, network.toJson(), "opposed HA refuse must leave live unchanged");
        } else {
            Road head = network.getRoad(road.getId());
            Road tail = network.getRoad(newId);
            assertNotNull(head.getHorizontalAlignment());
            assertNotNull(tail.getHorizontalAlignment());
            assertTrue(RoadStationing.domainMap(network, head).isReliable());
            assertTrue(RoadStationing.domainMap(network, tail).isReliable());
            assertEquals(10.0,
                VerticalAlignmentGeometry.elevationAt(head.getVerticalAlignment(), 0.0).orElseThrow(),
                1e-2);
            assertEquals(11.0,
                VerticalAlignmentGeometry.elevationAt(tail.getVerticalAlignment(), 0.0).orElseThrow(),
                1e-2);
        }
    }

    @Test
    void splitEdgeCrossingUnresolvedLeavesJsonUnchanged() {
        Road road = linearRoad(1, 40.0);
        Road other = network.createRoad("other");
        RoadNode o0 = network.createNode(new Vec2d(20, -20));
        RoadNode o1 = network.createNode(new Vec2d(20, 20));
        network.createEdge(o0.getId(), o1.getId(),
            List.of(new Vec2d(20, -20), new Vec2d(20, 20)), other.getId());
        network.registerCrossing(com.plot.plugin.road.crossing.RoadCrossing.atGrade(
            road.getId(), 9_999.0, other.getId(), 20.0, new Vec2d(20, 0)));
        String edgeId = road.getOrderedSegmentIds().getFirst();
        String before = network.toJson();
        assertFalse(RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, 20.0).isSuccess());
        assertEquals(before, network.toJson());
    }

    @Test
    void illegalHaMappingGeometryEditLeavesNetworkUnchanged() {
        Road road = linearRoad(1, 40.0);
        // Arc HA vs straight polyline → unreliable domain; geometry edit must fail closed.
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.circularArc(40.0, 30.0, TurnDirection.LEFT))));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(40.0, 11.0))));
        assertFalse(RoadStationing.domainMap(network, road).isReliable());
        String edgeId = road.getOrderedSegmentIds().getFirst();
        String before = network.toJson();
        assertFalse(RoadCenterlineEditor.insertPiAtLocalDistance(network, edgeId, 20.0).isSuccess());
        assertEquals(before, network.toJson());
    }

    private static RoadCrossSection section(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }
}
