package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTerrainStylePersistenceTest {

    @Test
    void jsonRoundTripPreservesTerrainStyle() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("terrain-style");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setTerrainStyle(RoadTerrainStyle.FOLLOW);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(40, 0));
        network.createEdge(
            start.getId(), end.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        String json = network.toJson();
        assertTrue(json.contains("\"terrainStyle\": \"FOLLOW\""));

        Road restored = RoadNetwork.parseSnapshot(json).getRoad("terrain-style");
        assertEquals(RoadTerrainStyle.FOLLOW, restored.getStoredTerrainStyle());
        assertEquals(RoadTerrainStyle.FOLLOW, restored.getEffectiveTerrainStyle(null));
    }
}
