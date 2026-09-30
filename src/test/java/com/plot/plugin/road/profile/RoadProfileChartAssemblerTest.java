package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileChartAssemblerTest {

    @Test
    void assemblesMultiSegmentRoadProfile() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(15.0, 68.0),
            PointOfVerticalIntersection.of(30.0, 70.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadNode n4 = network.createNode(new Vec2d(30, 0));
        String edge1 = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()).getId();
        String edge2 = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId()).getId();
        String edge3 = network.createEdge(
            n3.getId(), n4.getId(), List.of(new Vec2d(20, 0), new Vec2d(30, 0)), road.getId()).getId();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(edge1, profileResult(10.0, 64, 66));
        edgeResults.put(edge2, profileResult(10.0, 66, 68));
        edgeResults.put(edge3, profileResult(10.0, 68, 70));

        RoadProfileChartData chart = RoadProfileChartAssembler.assemble(
            network,
            road,
            new RoadSystemConfig("test"),
            edgeResults).orElseThrow();

        double canonical = RoadStationing.canonicalLength(network, road);
        assertEquals(canonical, chart.totalStation(), 1e-3);
        assertTrue(chart.hasCompleteRoadProfile());
        assertEquals(0.0, chart.stations().getFirst(), 1e-3);
        assertEquals(canonical, chart.stations().getLast(), 1e-3);
        for (int i = 1; i < chart.stations().size(); i++) {
            assertTrue(chart.stations().get(i) >= chart.stations().get(i - 1) - 1e-6);
        }
    }

    @Test
    void fourSegmentRoadHasNoDuplicateSeamStations() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(40.0, 70.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadNode n4 = network.createNode(new Vec2d(30, 0));
        RoadNode n5 = network.createNode(new Vec2d(40, 0));
        String edge1 = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()).getId();
        String edge2 = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId()).getId();
        String edge3 = network.createEdge(
            n3.getId(), n4.getId(), List.of(new Vec2d(20, 0), new Vec2d(30, 0)), road.getId()).getId();
        String edge4 = network.createEdge(
            n4.getId(), n5.getId(), List.of(new Vec2d(30, 0), new Vec2d(40, 0)), road.getId()).getId();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(edge1, profileResult(10.0, 64, 65));
        edgeResults.put(edge2, profileResult(10.0, 65, 66));
        edgeResults.put(edge3, profileResult(10.0, 66, 68));
        edgeResults.put(edge4, profileResult(10.0, 68, 70));

        RoadProfileChartData chart = RoadProfileChartAssembler.assemble(
            network,
            road,
            new RoadSystemConfig("test"),
            edgeResults).orElseThrow();

        assertEquals(5, chart.stations().size());
        assertEquals(0.0, chart.stations().getFirst(), 1e-3);
        assertEquals(10.0, chart.stations().get(1), 1e-3);
        assertEquals(20.0, chart.stations().get(2), 1e-3);
        assertEquals(30.0, chart.stations().get(3), 1e-3);
        assertEquals(40.0, chart.stations().getLast(), 1e-3);
        for (int i = 1; i < chart.stations().size(); i++) {
            assertTrue(chart.stations().get(i) > chart.stations().get(i - 1));
        }
    }

    @Test
    void controlPointsIncludeRoadEndpoints() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(20.0, 68.0),
            PointOfVerticalIntersection.of(30.0, 70.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        String edge1 = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()).getId();
        String edge2 = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId()).getId();

        Map<String, RoadGenerationResult> edgeResults = Map.of(
            edge1, profileResult(10.0, 64, 66),
            edge2, profileResult(10.0, 66, 70));

        RoadProfileChartData chart = RoadProfileChartAssembler.assemble(
            network,
            road,
            new RoadSystemConfig("test"),
            edgeResults).orElseThrow();

        assertFalse(chart.controlPoints().isEmpty());
        assertEquals(ProfilePointRole.START_ENDPOINT, chart.controlPoints().getFirst().role());
        assertEquals(ProfilePointRole.END_ENDPOINT, chart.controlPoints().getLast().role());
        assertEquals(0.0, chart.controlPoints().getFirst().roadStation(), 1e-6);
        assertEquals(30.0, chart.controlPoints().getLast().roadStation(), 1e-3);
    }

    @Test
    void assemblesProfileUsingCanonicalStationsWhenHaLengthDiffers() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ha-main");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        double instanceLength = 100.0;
        double designLength = 120.0;
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(instanceLength, 0));
        String edgeId = network.createEdge(
            n1.getId(), n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(instanceLength, 0)),
            road.getId()).getId();
        road.setHorizontalAlignment(new RoadHorizontalAlignment(
            new Vec2d(0, 0),
            0.0,
            List.of(HorizontalAlignmentElement.tangent(designLength))));

        Map<String, RoadGenerationResult> edgeResults = Map.of(
            edgeId, profileResult(instanceLength, 64, 70));

        RoadProfileChartData chart = RoadProfileChartAssembler.assemble(
            network,
            road,
            new RoadSystemConfig("test"),
            edgeResults).orElseThrow();

        assertEquals(designLength, chart.totalStation(), 1e-3);
        assertTrue(chart.hasCompleteRoadProfile());
        assertEquals(0.0, chart.stations().getFirst(), 1e-3);
        assertEquals(designLength, chart.stations().getLast(), 1e-3);
    }

    @Test
    void reversedEdgeProfileIsOrientedToRoadChain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("reversed");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        RoadNode chainStart = network.createNode(new Vec2d(0, 0));
        RoadNode chainEnd = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            chainEnd.getId(),
            chainStart.getId(),
            List.of(new Vec2d(100, 0), new Vec2d(0, 0)),
            road.getId());

        RoadGenerationResult profile = profileResult(100.0, 64, 70);
        Map<String, RoadGenerationResult> edgeResults = Map.of(edge.getId(), profile);

        RoadProfileChartData chart = RoadProfileChartAssembler.assemble(
            network,
            road,
            new RoadSystemConfig("test"),
            edgeResults).orElseThrow();

        assertEquals(64.0, chart.groundElevations().getLast(), 1e-6);
        assertEquals(70.0, chart.groundElevations().getFirst(), 1e-6);
    }

    @Test
    void missingMiddleEdgeProfileReturnsEmpty() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("gap");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadNode n4 = network.createNode(new Vec2d(30, 0));
        String edge1 = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId()).getId();
        network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        String edge3 = network.createEdge(
            n3.getId(), n4.getId(), List.of(new Vec2d(20, 0), new Vec2d(30, 0)), road.getId()).getId();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(edge1, profileResult(10.0, 64, 66));
        edgeResults.put(edge3, profileResult(10.0, 68, 70));

        assertTrue(RoadProfileChartAssembler.assemble(
            network,
            road,
            new RoadSystemConfig("test"),
            edgeResults).isEmpty());
    }

    private static RoadGenerationResult profileResult(double span, int startHeight, int endHeight) {
        RoadGenerationResult result = new RoadGenerationResult(span);
        result.profileDistances = List.of(0.0, span);
        result.profileGroundHeights = List.of(startHeight, endHeight);
        result.profileGuideLine = List.of(startHeight, endHeight);
        result.profileTargetHeights = List.of(startHeight, endHeight);
        return result;
    }
}
