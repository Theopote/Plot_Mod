package com.plot.plugin.road.graph;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadExplicitConnectorTest {

    @Test
    void connectEndpoints_mergesColocatedNodes() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(10, 0));
        RoadEdge e1 = network.createEdge(n1.getId(), n2.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)), roadA.getId());
        RoadEdge e2 = network.createEdge(n3.getId(), network.createNode(new Vec2d(20, 0)).getId(), List.of(
            new Vec2d(10, 0), new Vec2d(20, 0)), roadB.getId());

        assertTrue(RoadExplicitConnector.connectEndpoints(
            network, e1.getId(), false, e2.getId(), true));
        assertEquals(3, network.getNodes().size());
        assertEquals(n3.getId(), e1.getEndNodeId());
        assertEquals(n3.getId(), e2.getStartNodeId());
    }
}
