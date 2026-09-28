package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatVerticalIntentPersistenceTest {

    @Test
    void flatIntentRoundTripsWithIntersectionOverrides() {
        FlatVerticalIntent original = new FlatVerticalIntent(70.0, Map.of(
            "node-a", 74.0,
            "node-b", 68.0));

        FlatVerticalIntentPersistence.FlatVerticalIntentData data =
            FlatVerticalIntentPersistence.toData(original);
        FlatVerticalIntent restored = FlatVerticalIntentPersistence.fromData(data);

        assertNotNull(restored);
        assertEquals(70.0, restored.getBaseElevation(), 1e-6);
        assertEquals(74.0, restored.getIntersectionOverride("node-a"), 1e-6);
        assertEquals(68.0, restored.getIntersectionOverride("node-b"), 1e-6);
    }

    @Test
    void flatIntentRoundTripsThroughRoadNetworkJson() {
        RoadNetwork network = junctionRoadNetwork();
        Road road = network.getRoad("flat-main");
        String junctionId = network.getNodes().values().stream()
            .filter(node -> Math.abs(node.getPosition().x - 50) < 1e-6
                && Math.abs(node.getPosition().y) < 1e-6)
            .findFirst()
            .orElseThrow()
            .getId();
        FlatVerticalIntent intent = new FlatVerticalIntent(70.0);
        intent.setIntersectionOverride(junctionId, 74.0);
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setFlatVerticalIntent(intent);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, 8.0);

        String json = network.toJson();
        assertTrue(json.contains("flatVerticalIntent"));

        RoadNetwork restored = RoadNetwork.parseSnapshot(json);
        Road restoredRoad = restored.getRoad(road.getId());
        assertEquals(RoadVerticalMode.FLAT, restoredRoad.getVerticalMode());
        FlatVerticalIntent restoredIntent = restoredRoad.getFlatVerticalIntent();
        assertNotNull(restoredIntent);
        assertEquals(70.0, restoredIntent.getBaseElevation(), 1e-6);
        assertEquals(74.0, restoredIntent.getIntersectionOverride(junctionId), 1e-6);
    }

    @Test
    void nullIntentRoundTripsAsNull() {
        assertNull(FlatVerticalIntentPersistence.toData(null));
        assertNull(FlatVerticalIntentPersistence.fromData(null));
    }

    private static RoadNetwork junctionRoadNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("flat-main");
        Road side = network.createRoad("side");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode junction = network.createNode(new Vec2d(50, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        RoadNode north = network.createNode(new Vec2d(50, 30));
        network.createEdge(start.getId(), junction.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(junction.getId(), end.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(50, 0), new Vec2d(50, 30)), side.getId());
        return network;
    }
}
