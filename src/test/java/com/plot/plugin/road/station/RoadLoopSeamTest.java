package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadLoopSeam;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadLoopSeamTest {

    @Test
    void loopSeam_defaultDeterministic() {
        RoadNetwork network = buildTriangleLoopNetwork();
        Road road = network.getRoads().values().iterator().next();
        road.setTopologyMode(RoadTopologyMode.LOOP);
        road.setLoopSeam(RoadLoopSeamService.computeDefault(network, road, List.of(new Vec2d(0, 0))));

        RoadNetwork network2 = buildTriangleLoopNetwork();
        Road road2 = network2.getRoads().values().iterator().next();
        road2.setTopologyMode(RoadTopologyMode.LOOP);
        road2.setLoopSeam(RoadLoopSeamService.computeDefault(network2, road2, List.of(new Vec2d(0, 0))));

        assertEquals(road.getLoopSeam().position().x, road2.getLoopSeam().position().x, 1e-6);
        assertEquals(road.getLoopSeam().position().y, road2.getLoopSeam().position().y, 1e-6);
    }

    private static RoadNetwork buildTriangleLoopNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ring");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(5, 10));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(5, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(new Vec2d(5, 10), new Vec2d(0, 0)), road.getId());
        return network;
    }

    @Test
    void loopStationing_startsAtSeam() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("ring");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 10));
        RoadEdge e1 = network.createEdge(n1.getId(), n2.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(
            new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        network.createEdge(n3.getId(), n1.getId(), List.of(
            new Vec2d(10, 10), new Vec2d(0, 0)), road.getId());

        road.setLoopSeam(RoadLoopSeam.at(new Vec2d(10, 0)));

        List<OrientedRoadSegment> segments = RoadStationing.orientedSegments(network, road);
        assertEquals(e1.getId(), segments.getFirst().edgeId());
        assertEquals(n2.getId(), segments.getFirst().entryNodeId());
    }

    @Test
    void loopProfile_endpointsSyncElevation() {
        Road road = new Road("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 10.0),
            PointOfVerticalIntersection.of(100.0, 12.0)
        )));

        RoadVerticalAlignment updated = VerticalProfileControlPoints.withElevation(
            road.getVerticalAlignment(), 0, 15.0, road);

        assertEquals(15.0, updated.getPvis().getFirst().getElevation(), 1e-6);
        assertEquals(15.0, updated.getPvis().getLast().getElevation(), 1e-6);
    }

    @Test
    void rotateLoopStation_wrapsCorrectly() {
        assertEquals(90.0, RoadLoopSeamService.rotateLoopStation(10.0, 20.0, 100.0), 1e-6);
        assertEquals(0.0, RoadLoopSeamService.rotateLoopStation(25.0, 25.0, 100.0), 1e-6);
    }

    @Test
    void loopSeam_survivesJsonRoundTrip() throws Exception {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("loop");
        road.setTopologyMode(RoadTopologyMode.LOOP);
        road.setLoopSeam(RoadLoopSeam.onSegment(new Vec2d(5, 0), "edge", 0.5));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        Road restoredRoad = restored.getRoad(road.getId());
        assertNotNull(restoredRoad.getLoopSeam());
        assertEquals(5.0, restoredRoad.getLoopSeam().position().x, 1e-6);
        assertTrue(network.toJson().contains("\"loopSeam\""));
    }
}
