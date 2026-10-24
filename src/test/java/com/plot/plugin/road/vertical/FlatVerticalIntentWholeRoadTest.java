package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatVerticalIntentWholeRoadTest {

    @Test
    void setWholeRoadElevationClearsOverridesAndKeepsFlatAlignment() {
        RoadNetwork network = new RoadNetwork();
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(40, 0));
        var c = network.createNode(new Vec2d(80, 0));
        Road road = network.createRoad("flat");
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setFlatVerticalIntent(new FlatVerticalIntent(64.0, Map.of(
            b.getId(), 68.0,
            c.getId(), 72.0)));
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, 8.0);

        FlatVerticalIntentSupport.setWholeRoadElevation(network, road, 70.0, 8.0);

        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
        assertEquals(70.0, intent.getBaseElevation(), 1e-6);
        assertFalse(intent.hasIntersectionOverrides());
        assertFalse(FlatVerticalIntentSupport.hasLocalJunctionBends(road));
        assertTrue(VerticalProfileDesignRules.isFlat(road.getVerticalAlignment()));
    }

    @Test
    void sharedJunctionElevationMovesAllConnectedFlatRoadsTogether() {
        RoadNetwork network = new RoadNetwork();
        var junction = network.createNode(new Vec2d(0, 0));
        var endA = network.createNode(new Vec2d(40, 0));
        var endB = network.createNode(new Vec2d(-40, 0));
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        network.createEdge(junction.getId(), endA.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)), roadA.getId());
        network.createEdge(junction.getId(), endB.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(-40, 0)), roadB.getId());
        roadA.setVerticalMode(RoadVerticalMode.FLAT);
        roadB.setVerticalMode(RoadVerticalMode.FLAT);
        roadA.setFlatVerticalIntent(new FlatVerticalIntent(64.0));
        roadB.setFlatVerticalIntent(new FlatVerticalIntent(72.0));
        FlatVerticalIntentSupport.syncCompiledAlignment(network, roadA, 8.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, roadB, 8.0);

        RoadVerticalJunctionService.setAtGradeSharedElevation(network, junction.getId(), 68.0, null);

        assertEquals(68.0, FlatVerticalIntentSupport.resolveIntent(network, roadA).getBaseElevation(), 1e-6);
        assertEquals(68.0, FlatVerticalIntentSupport.resolveIntent(network, roadB).getBaseElevation(), 1e-6);
    }
}
