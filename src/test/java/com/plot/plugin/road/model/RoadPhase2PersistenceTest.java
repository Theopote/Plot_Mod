package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.HorizontalAlignmentElementType;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.TurnDirection;
import com.plot.plugin.road.model.facility.RoadFacilityKind;
import com.plot.plugin.road.model.facility.RoadFacilitySide;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.facility.StationFacilityRun;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSectionEngineeringEquality;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPhase2PersistenceTest {

    @Test
    void jsonRoundTripPreservesAllPhase2EngineeringSemantics() {
        RoadNetwork network = buildNetworkWithPhase2Road();
        Road original = network.getRoad("phase2-road");
        List<String> expectedSegmentIds = List.copyOf(original.getOrderedSegmentIds());

        String json = network.toJson();
        assertTrue(json.contains("\"topologyMode\": \"LOOP\""));
        assertTrue(json.contains("\"horizontalAlignment\""));
        assertTrue(json.contains("\"verticalAlignment\""));
        assertTrue(json.contains("\"verticalMode\": \"MANUAL_PROFILE\""));
        assertTrue(json.contains("\"variableCrossSections\""));
        assertTrue(json.contains("\"stationFacilities\""));
        assertTrue(json.contains("\"segmentIds\""));

        RoadNetwork restored = RoadNetwork.parseSnapshot(json);
        Road roundTripped = restored.getRoad("phase2-road");

        assertNotNull(roundTripped);
        assertPhase2RoadEngineeringEquals(original, roundTripped);
        assertEquals(expectedSegmentIds, roundTripped.getOrderedSegmentIds());
        assertEquals(expectedSegmentIds.size(), roundTripped.getOrderedSegmentIds().size());
    }

    @Test
    void snapshotRoundTripPreservesAllPhase2EngineeringSemantics() {
        RoadNetwork network = buildNetworkWithPhase2Road();
        Road original = network.getRoad("phase2-road");

        RoadNetwork snapshot = network.snapshot();
        Road roundTripped = snapshot.getRoad("phase2-road");

        assertPhase2RoadEngineeringEquals(original, roundTripped);
    }

    @Test
    void jsonRoundTripOmitsEmptyPhase2FieldsForLinearRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("linear");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(40, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        String json = network.toJson();
        assertTrue(!json.contains("horizontalAlignment"));
        assertTrue(!json.contains("verticalAlignment"));
        assertTrue(!json.contains("variableCrossSections"));
        assertTrue(!json.contains("stationFacilities"));
        assertTrue(!json.contains("topologyMode"));

        Road restored = RoadNetwork.parseSnapshot(json).getRoad("linear");
        assertEquals(RoadTopologyMode.LINEAR, restored.getTopologyMode());
        assertNull(restored.getHorizontalAlignment());
        assertNull(restored.getVerticalAlignment());
        assertNull(restored.getVariableCrossSections());
        assertNull(restored.getStationFacilities());
    }

    private static RoadNetwork buildNetworkWithPhase2Road() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("phase2-road");
        road.setName("Phase 2 Persistence");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        road.setWidth(6);
        road.setHorizontalAlignment(sampleHorizontalAlignment());
        road.setVerticalAlignment(sampleVerticalAlignment());
        road.setVariableCrossSections(sampleVariableCrossSections());
        road.setStationFacilities(sampleStationFacilities());

        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(80, 0));
        RoadNode n3 = network.createNode(new Vec2d(160, 0));
        RoadEdge first = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(80, 0)), road.getId());
        RoadEdge second = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(80, 0), new Vec2d(160, 0)), road.getId());

        assertEquals(List.of(first.getId(), second.getId()), road.getOrderedSegmentIds());
        return network;
    }

    private static void assertPhase2RoadEngineeringEquals(Road expected, Road actual) {
        assertEquals(expected.getTopologyMode(), actual.getTopologyMode());
        assertEquals(expected.getVerticalMode(), actual.getVerticalMode());
        assertEquals(expected.getOrderedSegmentIds(), actual.getOrderedSegmentIds());

        assertHorizontalAlignmentEquals(expected.getHorizontalAlignment(), actual.getHorizontalAlignment());
        assertVerticalAlignmentEquals(expected.getVerticalAlignment(), actual.getVerticalAlignment());
        assertVariableCrossSectionsEquals(expected.getVariableCrossSections(), actual.getVariableCrossSections());
        assertStationFacilitiesEquals(expected.getStationFacilities(), actual.getStationFacilities());
    }

    private static void assertHorizontalAlignmentEquals(
            RoadHorizontalAlignment expected,
            RoadHorizontalAlignment actual) {
        assertNotNull(actual);
        assertEquals(expected.getOrigin().x, actual.getOrigin().x, 1e-6);
        assertEquals(expected.getOrigin().y, actual.getOrigin().y, 1e-6);
        assertEquals(expected.getStartBearingRadians(), actual.getStartBearingRadians(), 1e-9);
        assertEquals(expected.totalLength(), actual.totalLength(), 1e-6);

        List<HorizontalAlignmentElement> expectedElements = expected.getElements();
        List<HorizontalAlignmentElement> actualElements = actual.getElements();
        assertEquals(expectedElements.size(), actualElements.size());
        for (int i = 0; i < expectedElements.size(); i++) {
            assertHorizontalAlignmentElementEquals(expectedElements.get(i), actualElements.get(i));
        }
    }

    private static void assertHorizontalAlignmentElementEquals(
            HorizontalAlignmentElement expected,
            HorizontalAlignmentElement actual) {
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getLength(), actual.getLength(), 1e-6);
        if (expected.getType() == HorizontalAlignmentElementType.CIRCULAR_ARC) {
            assertEquals(expected.getRadius(), actual.getRadius(), 1e-6);
            assertEquals(expected.getDirection(), actual.getDirection());
        }
        if (expected.getType() == HorizontalAlignmentElementType.SPIRAL) {
            assertEquals(expected.getSpiralParameterA(), actual.getSpiralParameterA(), 1e-6);
        }
    }

    private static void assertVerticalAlignmentEquals(
            RoadVerticalAlignment expected,
            RoadVerticalAlignment actual) {
        assertNotNull(actual);
        List<PointOfVerticalIntersection> expectedPvis = expected.getPvis();
        List<PointOfVerticalIntersection> actualPvis = actual.getPvis();
        assertEquals(expectedPvis.size(), actualPvis.size());
        for (int i = 0; i < expectedPvis.size(); i++) {
            PointOfVerticalIntersection exp = expectedPvis.get(i);
            PointOfVerticalIntersection act = actualPvis.get(i);
            assertEquals(exp.getStation(), act.getStation(), 1e-6);
            assertEquals(exp.getElevation(), act.getElevation(), 1e-6);
            if (exp.hasCurve()) {
                assertTrue(act.hasCurve());
                assertEquals(exp.getCurveLength(), act.getCurveLength(), 1e-6);
            } else {
                assertTrue(!act.hasCurve());
            }
        }
    }

    private static void assertVariableCrossSectionsEquals(
            RoadVariableCrossSections expected,
            RoadVariableCrossSections actual) {
        assertNotNull(actual);
        assertEquals(expected.stationCount(), actual.stationCount());

        List<StationCrossSection> expectedStations = expected.sortedStations();
        List<StationCrossSection> actualStations = actual.sortedStations();
        for (int i = 0; i < expectedStations.size(); i++) {
            assertEquals(expectedStations.get(i).getStation(), actualStations.get(i).getStation(), 1e-6);
            assertTrue(RoadCrossSectionEngineeringEquality.equals(
                expectedStations.get(i).getCrossSection(),
                actualStations.get(i).getCrossSection()));
        }
    }

    private static void assertStationFacilitiesEquals(
            RoadStationFacilities expected,
            RoadStationFacilities actual) {
        assertNotNull(actual);
        assertEquals(expected.runCount(), actual.runCount());

        List<StationFacilityRun> expectedRuns = expected.sortedRuns();
        List<StationFacilityRun> actualRuns = actual.sortedRuns();
        for (int i = 0; i < expectedRuns.size(); i++) {
            StationFacilityRun exp = expectedRuns.get(i);
            StationFacilityRun act = actualRuns.get(i);
            assertEquals(exp.getStartStation(), act.getStartStation(), 1e-6);
            assertEquals(exp.getEndStation(), act.getEndStation());
            assertEquals(exp.getKind(), act.getKind());
            assertEquals(exp.getSide(), act.getSide());
            assertEquals(exp.getMaterial(), act.getMaterial());
            assertEquals(exp.getHeight(), act.getHeight());
        }
    }

    private static RoadHorizontalAlignment sampleHorizontalAlignment() {
        return new RoadHorizontalAlignment(new Vec2d(12.5, -3.0), Math.PI / 4, List.of(
            HorizontalAlignmentElement.tangent(80.0),
            HorizontalAlignmentElement.circularArc(31.4, 50.0, TurnDirection.LEFT),
            HorizontalAlignmentElement.spiral(20.0, 12.0)
        ));
    }

    private static RoadVerticalAlignment sampleVerticalAlignment() {
        return new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.withCurve(60.0, 70.0, 24.0),
            PointOfVerticalIntersection.of(120.0, 68.0),
            PointOfVerticalIntersection.of(160.0, 72.0)
        ));
    }

    private static RoadVariableCrossSections sampleVariableCrossSections() {
        return new RoadVariableCrossSections(List.of(
            StationCrossSection.at(30.0, engineeringCrossSection(8, 2)),
            StationCrossSection.at(90.0, engineeringCrossSection(12, 4))
        ));
    }

    private static RoadStationFacilities sampleStationFacilities() {
        return new RoadStationFacilities(List.of(
            new StationFacilityRun(
                25.0, 80.0, RoadFacilityKind.RETAINING_WALL, RoadFacilitySide.RIGHT, "stone_bricks", 3.0),
            StationFacilityRun.of(90.0, null, RoadFacilityKind.GUARDRAIL, RoadFacilitySide.BOTH)
        ));
    }

    private static RoadCrossSection engineeringCrossSection(int width, int laneCount) {
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(width);
        section.getCarriageway().setLaneCount(laneCount);
        section.getSidewalk().setEnabled(true);
        section.getBikeLane().setEnabled(laneCount >= 4);
        section.getMedian().setEnabled(laneCount >= 4);
        section.getMedian().setWidth(2);
        return section;
    }
}
