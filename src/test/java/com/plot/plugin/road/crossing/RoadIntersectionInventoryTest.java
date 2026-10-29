package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadIntersectionInventoryTest {

    @Test
    void countMatchesRegistryCrossingsWhenTopologyStaysIndependent() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("inventory");

        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);

        assertEquals(0, network.getJunctionCount());
        assertEquals(1, network.getCrossings().size());
        assertEquals(1, RoadIntersectionInventory.count(network));
    }

    @Test
    void countIncludesLegacyTopologicalJunctionWithoutDoubleCountingRegistry() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode south = network.createNode(new Vec2d(0, -10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 10)), roadA.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, -10)), roadA.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadB.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(-10, 0)), roadB.getId());

        assertEquals(1, network.getJunctionCount());
        assertEquals(1, RoadIntersectionInventory.count(network));

        network.registerCrossing(RoadCrossing.atGrade(
            roadA.getId(), 10.0, roadB.getId(), 10.0, new Vec2d(0, 0)));
        assertEquals(1, RoadIntersectionInventory.count(network));
        assertEquals(0, RoadIntersectionInventory.listLegacyJunctionNodes(network).size());
    }
}
