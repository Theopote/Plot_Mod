package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNodeListHelperTest {

    @Test
    void sortJunctionBeforeEndpointThenByRoadCountAndPosition() {
        RoadNetwork network = new RoadNetwork();
        RoadNode endpoint = network.createNode(new Vec2d(100, 0));
        RoadNode through = network.createNode(new Vec2d(50, 0));
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode west = network.createNode(new Vec2d(-10, 0));

        Road roadA = network.createRoad();
        roadA.setName("Alpha");
        Road roadB = network.createRoad();
        roadB.setName("Bravo");

        network.createEdge(endpoint.getId(), through.getId(), List.of(new Vec2d(100, 0), new Vec2d(50, 0)), roadA.getId());
        network.createEdge(through.getId(), junction.getId(), List.of(new Vec2d(50, 0), new Vec2d(0, 0)), roadA.getId());
        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)), roadB.getId());
        network.createEdge(junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)), roadB.getId());

        List<RoadNode> sorted = RoadNodeListHelper.filterAndSort(
            network,
            network.getNodes().values(),
            "",
            new RoadNodeListHelper.NodeFilter(false, false, false, false, false));

        assertEquals(junction.getId(), sorted.getFirst().getId());
        assertTrue(sorted.indexOf(through) < sorted.indexOf(endpoint));
    }

    @Test
    void filterByEndpointAndManualElevation() {
        RoadNetwork network = new RoadNetwork();
        RoadNode endpointStart = network.createNode(new Vec2d(0, 0));
        RoadNode manual = network.createNode(new Vec2d(10, 0));
        RoadNode endpointEnd = network.createNode(new Vec2d(20, 0));
        manual.setManualElevation(72.0);

        Road road = network.createRoad();
        network.createEdge(endpointStart.getId(), manual.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(manual.getId(), endpointEnd.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        List<RoadNode> endpoints = RoadNodeListHelper.filterAndSort(
            network,
            network.getNodes().values(),
            "",
            new RoadNodeListHelper.NodeFilter(false, true, false, false, false));
        assertEquals(2, endpoints.size());
        assertTrue(endpoints.stream().anyMatch(node -> node.getId().equals(endpointStart.getId())));
        assertTrue(endpoints.stream().anyMatch(node -> node.getId().equals(endpointEnd.getId())));

        List<RoadNode> manualNodes = RoadNodeListHelper.filterAndSort(
            network,
            network.getNodes().values(),
            "",
            new RoadNodeListHelper.NodeFilter(false, false, true, false, false));
        assertEquals(1, manualNodes.size());
        assertEquals(manual.getId(), manualNodes.getFirst().getId());
    }

    @Test
    void searchMatchesRoadName() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        Road road = network.createRoad();
        road.setName("Main Street");
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        List<RoadNode> matches = RoadNodeListHelper.filterAndSort(
            network,
            network.getNodes().values(),
            "main street",
            new RoadNodeListHelper.NodeFilter(false, false, false, false, false));

        assertEquals(2, matches.size());
    }
}
