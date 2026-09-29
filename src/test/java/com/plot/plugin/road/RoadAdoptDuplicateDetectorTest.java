package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadAdoptDuplicateDetectorTest {

    @Test
    void detectsPathOverlappingExistingEdge() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());

        List<Vec2d> duplicatePath = List.of(new Vec2d(0, 0), new Vec2d(20, 0));
        assertTrue(RoadAdoptDuplicateDetector.overlapsExistingPath(network, duplicatePath));
    }

    @Test
    void ignoresDistinctPathGeometry() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());

        List<Vec2d> otherPath = List.of(new Vec2d(0, 5), new Vec2d(20, 5));
        assertFalse(RoadAdoptDuplicateDetector.overlapsExistingPath(network, otherPath));
    }
}
