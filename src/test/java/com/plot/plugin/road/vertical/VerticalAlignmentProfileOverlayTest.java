package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAlignmentProfileOverlayTest {

    @Test
    void clipsDesignProfileToEdgeRange() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 100.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        var first = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        var second = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        VerticalAlignmentProfileOverlay tail = VerticalAlignmentProfileOverlay
            .forEdge(network, second)
            .orElseThrow();
        assertTrue(!tail.isEmpty());
        assertEquals(0.0, tail.distances().getFirst(), 1e-6);
        assertEquals(50.0, tail.distances().getLast(), 1e-6);
        assertEquals(90, tail.heights().getFirst());
        assertEquals(100, tail.heights().getLast());

        VerticalAlignmentProfileOverlay head = VerticalAlignmentProfileOverlay
            .forEdge(network, first)
            .orElseThrow();
        assertEquals(80, head.heights().getFirst());
        assertEquals(90, head.heights().getLast());
    }
}
