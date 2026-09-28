package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatRoadJunctionConflictResolverTest {

    @Test
    void detectsFlatFlatAtGradeMismatch() {
        RoadNetwork network = flatCrossNetwork(70.0, 76.0);
        assertEquals(1, FlatRoadJunctionConflictResolver.findFlatFlatConflicts(network).size());
        assertTrue(RoadNetworkEngineeringValidator.analyzePreGeneration(network).blocksBuild());
    }

    @Test
    void unifyToRoadBaseAppliesSharedJunctionElevation() {
        RoadNetwork network = flatCrossNetwork(70.0, 76.0);
        String nodeId = FlatRoadJunctionConflictResolver.findFlatFlatConflicts(network)
            .getFirst().nodeId();
        Road roadA = network.getRoad("flat-a");
        Road roadB = network.getRoad("flat-b");

        assertEquals(2, FlatRoadJunctionConflictResolver.unifyToRoadBaseAtJunction(
            network, nodeId, roadA.getId()));

        FlatVerticalIntent intentB = FlatVerticalIntentSupport.resolveIntent(network, roadB);
        assertEquals(76.0, intentB.getBaseElevation(), 1e-6);
        assertEquals(70.0, intentB.getIntersectionOverride(nodeId), 1e-6);
        assertFalse(RoadNetworkEngineeringValidator.analyzePreGeneration(network).blocksBuild());
    }

    @Test
    void sharedElevationOverrideResolvesFlatJunctionConflict() {
        RoadNetwork network = new RoadNetwork();
        var junction = network.createNode(new Vec2d(0, 0));
        addBareSpur(network, junction, new Vec2d(0, 20));
        addBareSpur(network, junction, new Vec2d(0, -20));

        Road flat = roadFromJunction(network, junction, "flat", new Vec2d(0, -100));
        flat.setVerticalMode(RoadVerticalMode.FLAT);
        flat.setFlatVerticalIntent(new FlatVerticalIntent(70.0));
        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, flat);
        intent.setBaseElevation(70.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, flat, 8.0);

        junction.setManualElevation(76.0);
        assertEquals(1, FlatRoadJunctionConflictResolver.find(network).size());

        assertEquals(1, FlatRoadJunctionConflictResolver.allowConflictingRoadsToSlope(network));
        assertEquals(RoadVerticalMode.FLAT, flat.getVerticalMode());
        assertEquals(76.0, intent.getIntersectionOverride(junction.getId()), 1e-6);
        assertTrue(FlatRoadJunctionConflictResolver.find(network).isEmpty());
    }

    private static RoadNetwork flatCrossNetwork(double baseA, double baseB) {
        RoadNetwork network = new RoadNetwork();
        var junction = network.createNode(new Vec2d(0, 0));
        addBareSpur(network, junction, new Vec2d(0, 20));
        addBareSpur(network, junction, new Vec2d(0, -20));

        Road roadA = roadFromJunction(network, junction, "flat-a", new Vec2d(100, 0));
        Road roadB = roadFromJunction(network, junction, "flat-b", new Vec2d(-100, 0));
        roadA.setVerticalMode(RoadVerticalMode.FLAT);
        roadB.setVerticalMode(RoadVerticalMode.FLAT);
        roadA.setFlatVerticalIntent(new FlatVerticalIntent(baseA));
        roadB.setFlatVerticalIntent(new FlatVerticalIntent(baseB));
        FlatVerticalIntentSupport.syncCompiledAlignment(network, roadA, 8.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, roadB, 8.0);
        return network;
    }

    private static void addBareSpur(
            RoadNetwork network,
            com.plot.plugin.road.model.RoadNode junction,
            Vec2d endPosition) {
        var end = network.createNode(endPosition);
        network.createEdge(junction.getId(), end.getId(),
            List.of(junction.getPosition(), endPosition));
    }

    private static Road roadFromJunction(
            RoadNetwork network,
            com.plot.plugin.road.model.RoadNode junction,
            String id,
            Vec2d endPosition) {
        Road road = network.createRoad(id);
        var end = network.createNode(endPosition);
        network.createEdge(junction.getId(), end.getId(),
            List.of(new Vec2d(0, 0), endPosition), road.getId());
        return road;
    }
}
