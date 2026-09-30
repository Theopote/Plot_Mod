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
    void forRoadSpansCanonicalRoadLength() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 100.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        VerticalAlignmentProfileOverlay overlay = VerticalAlignmentProfileOverlay
            .forRoad(network, road)
            .orElseThrow();
        assertTrue(!overlay.isEmpty());
        assertEquals(0.0, overlay.stations().getFirst(), 1e-6);
        assertEquals(100.0, overlay.stations().getLast(), 1e-6);
        assertEquals(80.0, overlay.elevations().getFirst(), 1e-6);
        assertEquals(100.0, overlay.elevations().getLast(), 1e-6);
    }

    @Test
    void forRoadSamplesElevationsAlongCanonicalStations() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 100.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        VerticalAlignmentProfileOverlay overlay = VerticalAlignmentProfileOverlay
            .forRoad(network, road)
            .orElseThrow();

        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < overlay.stations().size(); i++) {
            double delta = Math.abs(overlay.stations().get(i) - 50.0);
            if (delta < best) {
                best = delta;
                nearest = i;
            }
        }
        assertEquals(90.0, overlay.elevations().get(nearest), 1.0);
    }
}
