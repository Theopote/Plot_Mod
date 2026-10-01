package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTerrainFollowPresetPersistenceTest {

    @Test
    void jsonRoundTripPreservesTerrainFollowPreset() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("terrain-follow");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setTerrainFollowPreset(TerrainFollowPreset.TIGHT);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(40, 0));
        network.createEdge(
            start.getId(), end.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        String json = network.toJson();
        assertTrue(json.contains("\"terrainFollowPreset\": \"TIGHT\""));

        Road restored = RoadNetwork.parseSnapshot(json).getRoad("terrain-follow");
        assertEquals(TerrainFollowPreset.TIGHT, restored.getStoredTerrainFollowPreset());
        assertEquals(TerrainFollowPreset.TIGHT, restored.getEffectiveTerrainFollowPreset());
    }
}
