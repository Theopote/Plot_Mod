package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossingCornerRadiusPersistenceTest {

    @Test
    void materializeWritesAtGradeCornerRadiusOntoJunctionNode() {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadNode a0 = network.createNode(new Vec2d(-40, 0));
        RoadNode a1 = network.createNode(new Vec2d(40, 0));
        RoadNode b0 = network.createNode(new Vec2d(0, -40));
        RoadNode b1 = network.createNode(new Vec2d(0, 40));
        network.createEdge(a0.getId(), a1.getId(),
            List.of(a0.getPosition(), a1.getPosition()), a.getId());
        network.createEdge(b0.getId(), b1.getId(),
            List.of(b0.getPosition(), b1.getPosition()), b.getId());

        RoadCrossing crossing = RoadCrossing.atGrade(
            a.getId(), 40, b.getId(), 40, new Vec2d(0, 0)).withCornerRadius(3.5);
        network.registerCrossing(crossing);

        RoadNetwork snapshot = RoadCrossingMaterializer.materializeForSnapshot(network);
        RoadNode junction = snapshot.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .filter(node -> Math.hypot(node.getPosition().x, node.getPosition().y) < 0.5)
            .findFirst()
            .orElse(null);
        assertNotNull(junction);
        assertEquals(3.5, junction.getCornerRadius(), 1e-6);
    }

    @Test
    void gradeSeparatedCrossingForcesZeroCornerRadiusOnJunction() {
        RoadNetwork network = new RoadNetwork();
        Road lower = network.createRoad("lower");
        Road upper = network.createRoad("upper");
        RoadNode a0 = network.createNode(new Vec2d(-40, 0));
        RoadNode a1 = network.createNode(new Vec2d(40, 0));
        RoadNode b0 = network.createNode(new Vec2d(0, -40));
        RoadNode b1 = network.createNode(new Vec2d(0, 40));
        network.createEdge(a0.getId(), a1.getId(),
            List.of(a0.getPosition(), a1.getPosition()), lower.getId());
        network.createEdge(b0.getId(), b1.getId(),
            List.of(b0.getPosition(), b1.getPosition()), upper.getId());

        RoadCrossing crossing = RoadCrossing.gradeSeparated(
            lower.getId(), 40, upper.getId(), 40, new Vec2d(0, 0), upper.getId(), 6.0)
            .withCornerRadius(4.0);
        network.registerCrossing(crossing);

        RoadNetwork snapshot = RoadCrossingMaterializer.materializeForSnapshot(network);
        RoadNode junction = snapshot.getNodes().values().stream()
            .filter(node -> node.isGradeSeparated())
            .findFirst()
            .orElse(null);
        assertNotNull(junction);
        assertEquals(0.0, junction.getCornerRadius(), 1e-6);
        assertEquals(0.0, crossing.effectiveCornerRadius(3.0), 1e-6);
    }

    @Test
    void roundTripJsonPreservesCrossingCornerRadius() throws Exception {
        RoadNetwork network = new RoadNetwork();
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        RoadNode a0 = network.createNode(new Vec2d(-20, 0));
        RoadNode a1 = network.createNode(new Vec2d(20, 0));
        RoadNode b0 = network.createNode(new Vec2d(0, -20));
        RoadNode b1 = network.createNode(new Vec2d(0, 20));
        network.createEdge(a0.getId(), a1.getId(),
            List.of(a0.getPosition(), a1.getPosition()), a.getId());
        network.createEdge(b0.getId(), b1.getId(),
            List.of(b0.getPosition(), b1.getPosition()), b.getId());
        network.registerCrossing(
            RoadCrossing.atGrade(a.getId(), 20, b.getId(), 20, new Vec2d(0, 0)).withCornerRadius(2.25));

        String json = network.toJson();
        RoadNetwork loaded = RoadNetwork.fromJson(json);
        assertTrue(loaded.getCrossings().values().stream()
            .anyMatch(c -> c.cornerRadius() != null && Math.abs(c.cornerRadius() - 2.25) < 1e-6));
    }
}
