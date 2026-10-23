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
        assertEquals(CrossingType.AT_GRADE, network.getStackCrossings().values().iterator().next().type());
    }

    @Test
    void stackedFigureEightProfilesRegisterGradeSeparated() {
        RoadNetwork network = figureEightNetwork();
        var road = network.getRoads().values().iterator().next();
        road.setVerticalAlignment(new com.plot.plugin.road.vertical.RoadVerticalAlignment(java.util.List.of(
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(0.0, 64.0),
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(10.0, 64.0),
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(20.0, 72.0),
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(40.0, 72.0))));
        RoadStackCrossingReconciler.reconcileStackCrossings(network);

        assertEquals(1, network.getStackCrossings().size());
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        assertEquals(CrossingType.GRADE_SEPARATED, stack.type());
        assertTrue(stack.resolvedUpperStation() > stack.resolvedLowerStation());
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
