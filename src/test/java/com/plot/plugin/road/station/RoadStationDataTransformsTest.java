package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadStationDataTransformsTest {

    @Test
    void splitAtStationRemapsTailFacilities() {
        Road head = new Road("head");
        Road tail = new Road("tail");
        RoadStationFacilities source = new RoadStationFacilities(List.of(
            StationFacilityRun.of(30.0, 80.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT),
            StationFacilityRun.of(150.0, 250.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT)
        ));
        head.setStationFacilities(source);

        RoadStationDataTransforms.applyRoadSplit(head, tail, 120.0, 300.0);

        assertNotNull(head.getStationFacilities());
        assertEquals(1, head.getStationFacilities().runCount());
        assertEquals(30.0, head.getStationFacilities().sortedRuns().getFirst().getStartStation(), 1e-6);

        assertNotNull(tail.getStationFacilities());
        assertEquals(1, tail.getStationFacilities().runCount());
        StationFacilityRun tailRun = tail.getStationFacilities().sortedRuns().getFirst();
        assertEquals(30.0, tailRun.getStartStation(), 1e-6);
        assertEquals(130.0, tailRun.getEndStation(), 1e-6);
        assertEquals(RoadFacilitySide.RIGHT, tailRun.getSide());
    }

    @Test
    void splitAtStationRemapsVerticalAlignment() {
        Road head = new Road("head");
        Road tail = new Road("tail");
        head.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(120.0, 12.0),
            PointOfVerticalIntersection.of(300.0, 15.0)
        )));

        RoadStationDataTransforms.applyRoadSplit(head, tail, 120.0, 300.0);

        assertNotNull(head.getVerticalAlignment());
        assertEquals(2, head.getVerticalAlignment().sortedPvis().size());
        assertEquals(120.0, head.getVerticalAlignment().sortedPvis().getLast().getStation(), 1e-6);

        assertNotNull(tail.getVerticalAlignment());
        assertEquals(2, tail.getVerticalAlignment().sortedPvis().size());
        assertEquals(0.0, tail.getVerticalAlignment().sortedPvis().getFirst().getStation(), 1e-6);
        assertEquals(12.0, tail.getVerticalAlignment().sortedPvis().getFirst().getElevation(), 1e-6);
        assertEquals(180.0, tail.getVerticalAlignment().sortedPvis().getLast().getStation(), 1e-6);
    }

    @Test
    void splitAtStationRemapsVariableCrossSections() {
        RoadCrossSection narrow = sectionWithWidth(6);
        RoadCrossSection wide = sectionWithWidth(12);
        Road head = new Road("head");
        head.setCrossSection(narrow.copy());
        head.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(200.0, wide)
        )));
        Road tail = new Road("tail");
        tail.setCrossSection(narrow.copy());

        RoadStationDataTransforms.applyRoadSplit(head, tail, 120.0, 300.0);

        assertNull(head.getVariableCrossSections());
        assertNotNull(tail.getVariableCrossSections());
        assertEquals(80.0, tail.getVariableCrossSections().sortedStations().getFirst().getStation(), 1e-6);
        assertEquals(12, tail.getVariableCrossSections().sortedStations().getFirst().getCrossSection()
            .getCarriageway().getWidth());
    }

    @Test
    void splitRoadBeforeSegmentPreservesPhase2Data() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        road.setCrossSection(sectionWithWidth(6));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadNode n4 = network.createNode(new Vec2d(30, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(20, 0), new Vec2d(30, 0)), road.getId());

        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(30.0, 12.0)
        )));
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(15.0, 25.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));

        String splitSegmentId = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String newRoadId = network.splitRoadBeforeSegment(road.getId(), splitSegmentId);

        Road head = network.getRoad(road.getId());
        Road tail = network.getRoad(newRoadId);

        assertNotNull(tail);
        assertNotNull(head.getVerticalAlignment());
        assertEquals(1, head.getVerticalAlignment().sortedPvis().size());
        assertNotNull(tail.getVerticalAlignment());
        assertEquals(1, tail.getVerticalAlignment().sortedPvis().size());
        assertEquals(20.0, tail.getVerticalAlignment().sortedPvis().getFirst().getStation(), 1e-6);
        assertEquals(12.0, tail.getVerticalAlignment().sortedPvis().getFirst().getElevation(), 1e-6);

        assertNull(head.getStationFacilities());
        assertNotNull(tail.getStationFacilities());
        assertEquals(1, tail.getStationFacilities().runCount());
        StationFacilityRun tailRun = tail.getStationFacilities().sortedRuns().getFirst();
        assertEquals(5.0, tailRun.getStartStation(), 1e-6);
        assertEquals(15.0, tailRun.getEndStation(), 1e-6);
    }

    @Test
    void computeComponentStationRangeUsesOrientedSegments() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        String edge1 = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()).getId();
        String edge2 = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId()).getId();

        RoadStationDataTransforms.StationRange first = RoadStationDataTransforms.computeComponentStationRange(
            network, road, Set.of(edge1));
        RoadStationDataTransforms.StationRange second = RoadStationDataTransforms.computeComponentStationRange(
            network, road, Set.of(edge2));

        assertEquals(0.0, first.start(), 1e-6);
        assertEquals(10.0, first.end(), 1e-6);
        assertEquals(10.0, second.start(), 1e-6);
        assertEquals(20.0, second.end(), 1e-6);
    }

    @Test
    void mergeStitchesTailStationDataAfterHead() {
        Road head = new Road("head");
        Road tail = new Road("tail");
        head.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(100.0, 11.0)
        )));
        tail.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 11.0),
            PointOfVerticalIntersection.of(80.0, 13.0)
        )));
        head.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(10.0, 40.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT)
        )));
        tail.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(20.0, 60.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT)
        )));

        RoadStationDataTransforms.applyRoadMerge(head, head, tail, 100.0, 80.0);

        assertNotNull(head.getVerticalAlignment());
        assertEquals(3, head.getVerticalAlignment().sortedPvis().size());
        assertEquals(100.0, head.getVerticalAlignment().sortedPvis().get(1).getStation(), 1e-6);
        assertEquals(11.0, head.getVerticalAlignment().sortedPvis().get(1).getElevation(), 1e-6);
        assertEquals(180.0, head.getVerticalAlignment().sortedPvis().getLast().getStation(), 1e-6);

        assertNotNull(head.getStationFacilities());
        assertEquals(2, head.getStationFacilities().runCount());
        assertEquals(120.0, head.getStationFacilities().sortedRuns().get(1).getStartStation(), 1e-6);
        assertEquals(160.0, head.getStationFacilities().sortedRuns().get(1).getEndStation(), 1e-6);
    }

    @Test
    void splitThenMergeRestoresStationData() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road");
        road.setCrossSection(sectionWithWidth(6));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadNode n4 = network.createNode(new Vec2d(30, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(20, 0), new Vec2d(30, 0)), road.getId());

        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(15.0, 11.0),
            PointOfVerticalIntersection.of(30.0, 12.0)
        )));
        road.setStationFacilities(new RoadStationFacilities(List.of(
            StationFacilityRun.of(5.0, 8.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.LEFT),
            StationFacilityRun.of(22.0, 28.0, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.RIGHT)
        )));

        String splitSegmentId = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String tailRoadId = network.splitRoadBeforeSegment(road.getId(), splitSegmentId);
        assertNotNull(tailRoadId);

        String mergedId = network.mergeRoadTailIntoHead(road.getId(), tailRoadId);
        assertEquals(road.getId(), mergedId);
        assertNull(network.getRoad(tailRoadId));

        Road merged = network.getRoad(mergedId);
        assertNotNull(merged.getVerticalAlignment());
        assertEquals(3, merged.getVerticalAlignment().sortedPvis().size());
        assertEquals(30.0, merged.getVerticalAlignment().sortedPvis().getLast().getStation(), 1e-6);

        assertNotNull(merged.getStationFacilities());
        assertEquals(2, merged.getStationFacilities().runCount());
        assertEquals(22.0, merged.getStationFacilities().sortedRuns().get(1).getStartStation(), 1e-6);
        assertEquals(3, RoadSegmentOrdering.orderedSegmentIds(network, merged).size());
    }

    private static RoadCrossSection sectionWithWidth(int width) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        return section;
    }
}
