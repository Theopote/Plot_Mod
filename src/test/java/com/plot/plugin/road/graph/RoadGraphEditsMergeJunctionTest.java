package com.plot.plugin.road.graph;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphEditsMergeJunctionTest {

    @Test
    void mergesNearbyJunctionNodes() {
        RoadNetwork network = new RoadNetwork();
        RoadNode survivor = network.createNode(new Vec2d(0, 0));
        RoadNode absorbed = network.createNode(new Vec2d(2, 0));
        String roadA = network.createRoad("road-a").getId();
        String roadB = network.createRoad("road-b").getId();
        network.createEdge(
            survivor.getId(), network.createNode(new Vec2d(-10, 0)).getId(),
            List.of(new Vec2d(0, 0), new Vec2d(-10, 0)), roadA);
        network.createEdge(
            absorbed.getId(), network.createNode(new Vec2d(10, 0)).getId(),
            List.of(new Vec2d(2, 0), new Vec2d(10, 0)), roadB);

        Optional<String> merged = RoadGraphEdits.of(network).mergeJunctionNode(
            survivor.getId(), absorbed.getId());

        assertTrue(merged.isPresent());
        assertEquals(survivor.getId(), merged.get());
        assertEquals(null, network.getNode(absorbed.getId()));
        assertEquals(2, survivor.getDegree());
    }
}
