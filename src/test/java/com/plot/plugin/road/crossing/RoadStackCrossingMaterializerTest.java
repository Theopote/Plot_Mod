package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStackCrossingMaterializerTest {

    @Test
    void materializesFourArmJunctionAtSelfIntersection() {
        RoadNetwork network = figureEightNetwork();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();

        RoadNetwork materialized = RoadCrossingMaterializer.materializeForSnapshot(network);

        RoadNode junction = materialized.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        assertTrue(junction.getPosition().distance(new Vec2d(5, 5)) < 0.5);
        assertEquals(4, junction.getDegree());
        assertEquals(4, materialized.getEdges().size());
        assertTrue(materialized.getEdges().values().stream()
            .allMatch(edge -> stack.roadId().equals(edge.getRoadId())));
    }

    @Test
    void gradeSeparatedStackCrossingAssignsUpperAndLowerArms() {
        RoadNetwork network = figureEightNetwork();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        network.setStackCrossingGradeSeparation(
            stack.id(),
            CrossingType.GRADE_SEPARATED,
            stack.stationA(),
            stack.stationB(),
            3.0);

        RoadNetwork materialized = RoadCrossingMaterializer.materializeForSnapshot(network);
        RoadNode junction = materialized.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        assertTrue(junction.isStackGradeSeparated());
        assertEquals(2, junction.getStackLowerEdgeIds().size());
        assertEquals(2, junction.getStackUpperEdgeIds().size());
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
