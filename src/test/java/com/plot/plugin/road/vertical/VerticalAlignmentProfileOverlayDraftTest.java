package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerticalAlignmentProfileOverlayDraftTest {

    @Test
    void forAlignmentShowsDraftUnderFitTerrain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("draft");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(80, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(80, 0)), road.getId());

        RoadVerticalAlignment draft = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 60.0),
            PointOfVerticalIntersection.of(80.0, 68.0)));

        VerticalAlignmentProfileOverlay overlay = VerticalAlignmentProfileOverlay
            .forAlignment(network, road, draft)
            .orElseThrow();

        assertFalse(overlay.isEmpty());
        assertEquals(0.0, overlay.stations().getFirst(), 1e-6);
        assertEquals(80.0, overlay.stations().getLast(), 1e-6);
        assertEquals(60.0, overlay.elevations().getFirst(), 1e-6);
        assertEquals(68.0, overlay.elevations().getLast(), 1e-6);
    }
}
