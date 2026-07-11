package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadNetworkOverviewRendererTest {

    @Test
    void hitTestEdgeSelectsNearestSegment() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadEdge horizontal = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        String hit = RoadNetworkOverviewRenderer.hitTestEdge(network, 5, 0.5, 2.0);
        assertEquals(horizontal.getId(), hit);
    }

    @Test
    void hitTestEdgeReturnsNullWhenTooFar() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        assertNull(RoadNetworkOverviewRenderer.hitTestEdge(network, 5, 50, 1.0));
    }
}
