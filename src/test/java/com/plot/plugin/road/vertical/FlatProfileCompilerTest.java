package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatProfileCompilerTest {

    @Test
    void flatRoadWithoutOverridesStaysConstant() {
        RoadNetwork network = straightRoad(100.0);
        Road road = network.getRoads().values().iterator().next();
        FlatVerticalIntent intent = new FlatVerticalIntent(70.0);

        RoadVerticalAlignment alignment = FlatProfileCompiler.compile(network, road, intent, 8.0);
        assertEquals(2, alignment.pviCount());
        assertEquals(70.0, alignment.getPvis().getFirst().getElevation(), 1e-6);
        assertEquals(70.0, alignment.getPvis().getLast().getElevation(), 1e-6);
        assertEquals(0.0, alignment.getPvis().getFirst().getStation(), 1e-6);
        assertEquals(100.0, alignment.getPvis().getLast().getStation(), 1e-6);
    }

    @Test
    void junctionOverrideCreatesBumpAndReturnsToBase() {
        RoadNetwork network = roadWithJunction(100.0, 50.0);
        Road road = network.getRoads().values().iterator().next();
        RoadNode center = network.getNodes().values().stream()
            .filter(RoadNode::isJunction)
            .findFirst()
            .orElseThrow();
        FlatVerticalIntent intent = new FlatVerticalIntent(70.0);
        intent.setIntersectionOverride(center.getId(), 73.0);

        RoadVerticalAlignment alignment = FlatProfileCompiler.compile(network, road, intent, 8.0);
        assertTrue(alignment.pviCount() >= 3);
        double junctionElevation = VerticalAlignmentGeometry.elevationAt(alignment, 50.0).orElse(Double.NaN);
        assertEquals(73.0, junctionElevation, 1e-3);
        assertEquals(70.0, VerticalAlignmentGeometry.elevationAt(alignment, 0.0).orElse(Double.NaN), 1e-3);
        assertEquals(70.0, VerticalAlignmentGeometry.elevationAt(alignment, 100.0).orElse(Double.NaN), 1e-3);
    }

    @Test
    void changingBaseRecompilesWithoutLegacyPviResidue() {
        RoadNetwork network = straightRoad(80.0);
        Road road = network.getRoads().values().iterator().next();
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setVerticalAlignment(VerticalProfileDesignRules.flatAlignment(80.0, 68.0));

        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
        assertEquals(68.0, intent.getBaseElevation(), 1e-6);

        FlatVerticalIntentSupport.setBaseElevation(network, road, 72.0, 8.0);
        assertEquals(72.0, road.getVerticalAlignment().getPvis().getFirst().getElevation(), 1e-6);
        assertEquals(72.0, road.getVerticalAlignment().getPvis().getLast().getElevation(), 1e-6);
    }

    private static RoadNetwork straightRoad(double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(length, 0)), road.getId());
        return network;
    }

    private static RoadNetwork roadWithJunction(double length, double junctionStation) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        Road sideRoad = network.createRoad("side");
        RoadNode west = network.createNode(new Vec2d(0, 0));
        RoadNode center = network.createNode(new Vec2d(junctionStation, 0));
        RoadNode east = network.createNode(new Vec2d(length, 0));
        RoadNode north = network.createNode(new Vec2d(junctionStation, 20));
        network.createEdge(west.getId(), center.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(junctionStation, 0)), road.getId());
        network.createEdge(center.getId(), east.getId(),
            List.of(new Vec2d(junctionStation, 0), new Vec2d(length, 0)), road.getId());
        network.createEdge(center.getId(), north.getId(),
            List.of(new Vec2d(junctionStation, 0), new Vec2d(junctionStation, 20)), sideRoad.getId());
        return network;
    }
}
