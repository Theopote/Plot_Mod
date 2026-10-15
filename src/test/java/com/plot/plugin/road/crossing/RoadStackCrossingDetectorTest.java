package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStackCrossingDetectorTest {

    @Test
    void detectsFigureEightSelfIntersection() {
        RoadNetwork network = figureEightNetwork();
        List<RoadStackCrossing> stacks = RoadStackCrossingDetector.detectAll(network);

        assertEquals(1, stacks.size());
        RoadStackCrossing stack = stacks.getFirst();
        assertTrue(stack.stationA() < stack.stationB());
        assertTrue(stack.position().distance(new Vec2d(5, 5)) < 0.5);
    }

    @Test
    void linearRoadHasNoStackCrossing() {
        RoadNetwork network = new RoadNetwork();
        var road = network.createRoad("linear");
        var n1 = network.createNode(new Vec2d(0, 0));
        var n2 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());

        assertTrue(RoadStackCrossingDetector.detectAll(network).isEmpty());
    }

    @Test
    void reconcilerRegistersDetectedStackCrossing() {
        RoadNetwork network = figureEightNetwork();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);

        assertFalse(network.getStackCrossings().isEmpty());
        assertEquals(1, network.getStackCrossings().size());
    }

    private static RoadNetwork figureEightNetwork() {
        RoadNetwork network = new RoadNetwork();
        var road = network.createRoad("figure-eight");
        var n1 = network.createNode(new Vec2d(0, 0));
        var n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10),
                new Vec2d(10, 0)),
            road.getId());
        return network;
    }
}
