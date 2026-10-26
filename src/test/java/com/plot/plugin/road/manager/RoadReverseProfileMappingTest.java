package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadReverseProfileMappingTest {

    private RoadNetworkManager manager;
    private RoadNetwork network;
    private Road road;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        network = manager.getNetwork();
        road = network.createRoad("main");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(30.0, 74.0),
            PointOfVerticalIntersection.of(100.0, 80.0))));
    }

    @Test
    void reverseRoadMirrorsPviStationsAndElevations() {
        CenterlineEditResult result = manager.reverseRoad(road);

        assertTrue(result.isSuccess());
        List<PointOfVerticalIntersection> pvis = road.getVerticalAlignment().sortedPvis();
        assertEquals(3, pvis.size());
        assertEquals(0.0, pvis.get(0).getStation(), 1e-6);
        assertEquals(80.0, pvis.get(0).getElevation(), 1e-6);
        assertEquals(70.0, pvis.get(1).getStation(), 1e-6);
        assertEquals(74.0, pvis.get(1).getElevation(), 1e-6);
        assertEquals(100.0, pvis.get(2).getStation(), 1e-6);
        assertEquals(70.0, pvis.get(2).getElevation(), 1e-6);
    }

    @Test
    void reverseEdgeDoesNotMirrorWholeRoadVerticalAlignment() {
        RoadEdge tail = network.getEdge(road.getOrderedSegmentIds().get(1));
        List<PointOfVerticalIntersection> before = road.getVerticalAlignment().sortedPvis();

        CenterlineEditResult result = manager.reverseEdge(tail.getId());

        assertTrue(result.isSuccess());
        List<PointOfVerticalIntersection> after = road.getVerticalAlignment().sortedPvis();
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).getStation(), after.get(i).getStation(), 1e-6);
            assertEquals(before.get(i).getElevation(), after.get(i).getElevation(), 1e-6);
        }
    }
}
