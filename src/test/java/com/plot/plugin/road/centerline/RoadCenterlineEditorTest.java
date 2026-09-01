package com.plot.plugin.road.centerline;

import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCenterlineEditorTest {

    @Test
    void insertPiAddsVertexAlongSegment() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        CenterlineEditResult result = RoadCenterlineEditor.insertPiAtLocalDistance(network, edge.getId(), 40.0);

        assertTrue(result.isSuccess());
        assertEquals(3, network.getEdge(edge.getId()).getCenterlinePoints().size());
    }

    @Test
    void splitCreatesTwoEdgesAtDistance() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        CenterlineEditResult result = RoadCenterlineEditor.splitAtLocalDistance(network, edge.getId(), 40.0);

        assertTrue(result.isSuccess());
        assertNotNull(result.firstEdgeId());
        assertNotNull(result.secondEdgeId());
        assertEquals(2, road.getSegmentIds().size());
    }

    @Test
    void filletReplacesCornerWithArcPoints() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 50));
        Road road = network.createRoad("r1");
        RoadEdge edge = network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0), new Vec2d(50, 50)),
            road.getId());

        int before = edge.getCenterlinePoints().size();
        CenterlineEditResult result = RoadCenterlineEditor.filletVertex(network, edge.getId(), 1, 5.0);

        assertTrue(result.isSuccess());
        assertTrue(network.getEdge(edge.getId()).getCenterlinePoints().size() > before);
    }

    @Test
    void reverseRoadReordersSegmentsAndAlignments() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment horizontal = new RoadHorizontalAlignment();
        horizontal.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(horizontal);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 100.0),
            PointOfVerticalIntersection.of(100.0, 110.0)
        )));

        List<String> before = RoadSegmentOrdering.orderedSegmentIds(network, road);
        CenterlineEditResult result = RoadCenterlineEditor.reverseRoad(network, road);

        assertTrue(result.isSuccess());
        assertEquals(before.reversed(), road.getOrderedSegmentIds());
        assertNotNull(road.getHorizontalAlignment());
        assertEquals(100.0, road.getVerticalAlignment().endStation(), 1e-6);
    }

    @Test
    void reverseRoadMirrorsStationBasedData() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        RoadCrossSection base = new RoadCrossSection();
        base.getCarriageway().setWidth(6);
        RoadCrossSection wide = new RoadCrossSection();
        wide.getCarriageway().setWidth(12);
        road.setWidth(6);
        road.getCrossSection().getCarriageway().setWidth(6);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(80.0, wide)
        )));
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 30.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        CenterlineEditResult result = RoadCenterlineEditor.reverseRoad(network, road);

        assertTrue(result.isSuccess());
        List<StationCrossSection> stations = road.getVariableCrossSections().sortedStations();
        assertEquals(0.0, stations.getFirst().getStation(), 1e-6);
        assertEquals(12, widthOf(stations.getFirst().getCrossSection()));
        StationFacilityRun mirrored = road.getStationFacilities().sortedRuns().getFirst();
        assertEquals(70.0, mirrored.getStartStation(), 1e-6);
        assertEquals(RoadFacilitySide.RIGHT, mirrored.getSide());

        double total = RoadStationing.canonicalLength(network, road);
        assertEquals(12, widthOf(VariableCrossSectionResolver.resolveTemplate(road, 10.0)));
        assertEquals(6, widthOf(VariableCrossSectionResolver.resolveTemplate(road, total - 10.0)));
    }

    private static int widthOf(RoadCrossSection section) {
        Integer width = section.getCarriageway().getWidth();
        return width != null ? width : 0;
    }

    @Test
    void reverseEdgePreservesStationDataForSingleSegmentRoad() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        road.setWidth(6);
        road.getCrossSection().getCarriageway().setWidth(6);
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 30.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        CenterlineEditResult result = RoadCenterlineEditor.reverseEdge(network, edge.getId());

        assertTrue(result.isSuccess());
        StationFacilityRun preserved = road.getStationFacilities().sortedRuns().getFirst();
        assertEquals(10.0, preserved.getStartStation(), 1e-6);
        assertEquals(RoadFacilitySide.LEFT, preserved.getSide());
    }

    @Test
    void reverseEdgePreservesFacilitiesOutsideReversedSegment() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        road.setWidth(6);
        road.getCrossSection().getCarriageway().setWidth(6);
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge tail = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 30.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT),
            StationFacilityRun.of(60.0, 90.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        CenterlineEditResult result = RoadCenterlineEditor.reverseEdge(network, tail.getId());
        assertTrue(result.isSuccess());

        List<StationFacilityRun> runs = road.getStationFacilities().sortedRuns();
        assertEquals(10.0, runs.get(0).getStartStation(), 1e-6);
        assertEquals(60.0, runs.get(1).getStartStation(), 1e-6);
        assertEquals(RoadFacilitySide.LEFT, runs.get(0).getSide());
        assertEquals(RoadFacilitySide.LEFT, runs.get(1).getSide());
    }

    @Test
    void reverseEdgePreservesVerticalAlignmentOnSingleSegmentRoad() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 72.0)
        )));

        CenterlineEditResult result = RoadCenterlineEditor.reverseEdge(network, edge.getId());
        assertTrue(result.isSuccess());

        List<PointOfVerticalIntersection> pvis = road.getVerticalAlignment().getPvis();
        assertEquals(0.0, pvis.get(0).getStation(), 1e-6);
        assertEquals(100.0, pvis.get(1).getStation(), 1e-6);
        assertEquals(64.0, pvis.get(0).getElevation(), 1e-6);
        assertEquals(72.0, pvis.get(1).getElevation(), 1e-6);
    }

    @Test
    void reverseEdgeFlipsOrientedForwardWithoutChangingChainage() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        OrientedRoadSegment before = RoadStationing.orientedSegment(network, road, edge.getId()).orElseThrow();
        assertTrue(before.forward());
        assertEquals(0.0, before.startStation(), 1e-6);

        CenterlineEditResult result = RoadCenterlineEditor.reverseEdge(network, edge.getId());
        assertTrue(result.isSuccess());

        OrientedRoadSegment after = RoadStationing.orientedSegment(network, road, edge.getId()).orElseThrow();
        assertFalse(after.forward());
        assertEquals(before.startStation(), after.startStation(), 1e-6);
        assertEquals(before.length(), after.length(), 1e-6);
    }

    @Test
    void splitEdgeRefitsHorizontalAlignmentFromCenterline() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment();
        alignment.addElement(HorizontalAlignmentElement.tangent(50.0));
        road.setHorizontalAlignment(alignment);

        CenterlineEditResult result = RoadCenterlineEditor.splitAtLocalDistance(network, edge.getId(), 40.0);
        assertTrue(result.isSuccess());
        assertNotNull(road.getHorizontalAlignment());
        assertEquals(100.0, road.getHorizontalAlignment().totalLength(), 2.0);
    }

    @Test
    void filletRescalesStationDataOnEditedSegment() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(40, 0));
        RoadNode n3 = network.createNode(new Vec2d(40, 40));
        Road road = network.createRoad("r1");
        RoadEdge edge = network.createEdge(
            n1.getId(),
            n3.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(40, 40)),
            road.getId());
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(55.0, 65.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        double oldLength = RoadStationing.canonicalLength(network, road);
        CenterlineEditResult result = RoadCenterlineEditor.filletVertex(network, edge.getId(), 1, 5.0);
        assertTrue(result.isSuccess());

        double newLength = RoadStationing.canonicalLength(network, road);
        assertTrue(newLength > 0.0);
        assertNotNull(road.getStationFacilities());
        StationFacilityRun run = road.getStationFacilities().sortedRuns().getFirst();
        double expectedStart = 55.0 * (newLength / oldLength);
        assertEquals(expectedStart, run.getStartStation(), 1e-3);
        assertEquals(65.0 * (newLength / oldLength), run.getEndStation(), 1e-3);
    }

    @Test
    void mergeThroughDegreeTwoNode() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r1");
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        CenterlineEditResult result = RoadCenterlineEditor.mergeThroughNode(network, n2.getId());

        assertTrue(result.isSuccess());
        assertEquals(1, road.getSegmentIds().size());
        assertEquals(100.0, network.getEdge(road.getOrderedSegmentIds().getFirst()).getLength(), 1e-6);
    }
}
