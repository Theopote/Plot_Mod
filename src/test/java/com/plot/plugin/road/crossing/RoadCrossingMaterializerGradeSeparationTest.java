package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossingMaterializerGradeSeparationTest {

    @Test
    void gradeSeparatedRegistryAppliesWhenNodeIsNotASimpleCrossing() {
        RoadNetwork network = new RoadNetwork();
        Road through = network.createRoad("through");
        Road stub = network.createRoad("stub");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 20));
        RoadNode south = network.createNode(new Vec2d(0, -20));
        RoadNode east = network.createNode(new Vec2d(20, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 20)), through.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, -20)), through.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(20, 0)), stub.getId());

        assertFalse(network.setNodeGradeSeparation(junction.getId(), true, through.getId(), 4.0));
        assertFalse(junction.isGradeSeparated());

        RoadCrossing crossing = new RoadCrossing(
            "t-grade",
            through.getId(),
            20.0,
            stub.getId(),
            0.0,
            new Vec2d(0, 0),
            CrossingType.GRADE_SEPARATED,
            through.getId(),
            4.0,
            null);
        network.registerCrossing(crossing);

        RoadNetwork materialized = RoadCrossingMaterializer.materializeForSnapshot(network);
        RoadNode materializedJunction = materialized.getNodes().values().stream()
            .filter(node -> node.getPosition().distance(new Vec2d(0, 0)) < 0.5)
            .findFirst()
            .orElseThrow();
        assertTrue(materializedJunction.isGradeSeparated());
        assertEquals(through.getId(), materializedJunction.getElevatedRoadId());
        assertEquals(4.0, materializedJunction.getCrossingClearance());
    }
}
