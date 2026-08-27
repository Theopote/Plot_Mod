package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadNodeElevationUtilsTest {

    @Test
    void prefersPreviewResolvedElevation() {
        RoadNode node = new RoadNode(new Vec2d(0, 0));
        Map<String, Integer> preview = Map.of(node.getId(), 112);

        int resolved = RoadNodeElevationUtils.resolveForManualLock(
            node,
            new RoadNetwork(),
            preview,
            new FlatTerrainSampler(64),
            null);

        assertEquals(112, resolved);
    }

    @Test
    void fallsBackToJunctionSolverWithoutPreview() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadGenerator generator = new RoadGenerator(
            config, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        network.createEdge(start.getId(), end.getId(), java.util.List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)));
        TerrainSampler terrain = new FlatTerrainSampler(70);

        int resolved = RoadNodeElevationUtils.resolveForManualLock(
            start,
            network,
            null,
            terrain,
            generator);

        assertEquals(generator.computeJunctionTargetHeight(start, network, terrain), resolved);
    }

    @Test
    void fallsBackToTerrainWhenNoNetworkSolver() {
        RoadNode node = new RoadNode(new Vec2d(5, 5));
        TerrainSampler terrain = new FlatTerrainSampler(88);

        int resolved = RoadNodeElevationUtils.resolveForManualLock(
            node, null, null, terrain, null);

        assertEquals(88, resolved);
    }

    @Test
    void fallsBackToSeaLevelWhenNothingAvailable() {
        RoadNode node = new RoadNode(new Vec2d(0, 0));

        int resolved = RoadNodeElevationUtils.resolveForManualLock(
            node, null, null, null, null);

        assertEquals(TerrainSampler.DEFAULT_SEA_LEVEL, resolved);
    }
}
