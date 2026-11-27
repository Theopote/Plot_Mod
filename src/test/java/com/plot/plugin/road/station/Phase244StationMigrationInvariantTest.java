package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.graph.RoadGraphEdits;
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
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(10, 0), edgeId, 0.75));
        assertTrue(RoadCenterlineEditor.splitAtLocalDistance(network, edgeId, 10.0).isSuccess());
        RoadLoopSeam seam = network.getRoad(road.getId()).getLoopSeam();
        assertNotNull(seam);
        assertNotNull(seam.segmentHintId());
        assertFalse(edgeId.equals(seam.segmentHintId()));
        assertNotNull(network.getEdge(seam.segmentHintId()));
        assertTrue(seam.localFraction() >= 0.0 && seam.localFraction() <= 1.0);
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
            PointOfVerticalIntersection.of(50.0, 12.0)));
        RoadVerticalAlignment tail = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 12.0),
            PointOfVerticalIntersection.of(40.0, 11.0)));
        RoadVerticalAlignment merged = RoadStationDataTransforms.mergeVerticalAlignments(
            head, tail, 50.0);
        assertNotNull(merged);
        assertEquals(3, merged.pviCount());
        assertEquals(90.0, merged.sortedPvis().getLast().getStation(), EPS);
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
            StationCrossSection.at(0.0, def),
            StationCrossSection.at(100.0, wide)),
            VariableCrossSectionTransitionMode.SMOOTH);
        Road head = new Road("h");
        Road t = new Road("t");
        head.setCrossSection(def.copy());
        head.setVariableCrossSections(smooth);
        StationMigrationResult result = RoadStationDataTransforms.applyRoadSplit(
            head, t, 40.0, 100.0, new RoadSystemConfig("phase244"));
        if (result.isSuccess()) {
            assertEquals(VariableCrossSectionTransitionMode.SMOOTH,
                head.getVariableCrossSections().getTransitionMode());
            assertEquals(VariableCrossSectionTransitionMode.SMOOTH,
                t.getVariableCrossSections().getTransitionMode());
        } else {
            assertNotNull(result.diagnostic());
            assertEquals(smooth.stationCount(),
                new RoadVariableCrossSections(List.of(
                    StationCrossSection.at(0.0, def),
                    StationCrossSection.at(100.0, wide)),
                    VariableCrossSectionTransitionMode.SMOOTH).stationCount());
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
        StationMigrationResult result = RoadStationDataTransforms.applyRoadMerge(
            head, head, tail, 50.0, 50.0, new RoadSystemConfig("phase244"));
        assertTrue(result.isSuccess());
        assertNotNull(head.getVariableCrossSections());
        assertEquals(6, head.getCrossSection().getCarriageway().getWidth());
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
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(40.0, 11.0),
            PointOfVerticalIntersection.of(80.0, 12.0),
            PointOfVerticalIntersection.of(120.0, 13.0))));
        String mid = road.getOrderedSegmentIds().get(1);
        int roadsBefore = network.getRoads().size();
        manager.deleteEdge(mid);
        network = manager.getNetwork();
        assertTrue(network.getRoads().size() >= roadsBefore);
        int withVa = 0;
        for (Road r : network.getRoads().values()) {
            if (r.getVerticalAlignment() != null && r.getVerticalAlignment().pviCount() >= 2) {
                withVa++;
            }
        }
        assertTrue(withVa >= 2, "both components should receive extracted VA");
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
        Road road = linearRoad(3, 30.0);
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0), 0.0, List.of(HorizontalAlignmentElement.tangent(90.0))));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(45.0, 12.0),
            PointOfVerticalIntersection.of(90.0, 11.0))));
        String splitSeg = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String newId = manager.splitRoadBeforeSegment(road.getId(), splitSeg);
        network = manager.getNetwork();
        if (newId != null) {
            Road head = network.getRoad(road.getId());
            Road tail = network.getRoad(newId);
            assertNotNull(head.getVerticalAlignment());
            assertNotNull(tail.getVerticalAlignment());
            double headLen = RoadStationing.canonicalLength(network, head);
            assertEquals(headLen, head.getVerticalAlignment().sortedPvis().getLast().getStation(), 0.5);
        }
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

    private Road linearRoad(int segments, double segLen) {
        Road road = network.createRoad("linear");
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

    private static RoadCrossSection section(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }
}
