package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadEdgeBuildOrchestratorTest {

    private static final class FailingTerrainSampler implements TerrainSampler {
        @Override
        public int sampleSurfaceY(com.plot.api.geometry.Vec2d planPoint) {
            throw new IllegalStateException("terrain unavailable");
        }

        @Override
        public boolean isSolidBlock(int worldX, int y, int worldZ) {
            return false;
        }
    }

    @Test
    void nullEdgeIsSkippedNotFailed() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadGenerator generator = new RoadGenerator(config, null, BlockProjectionHandler.getInstance());
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));

        EdgeGenerationResult outcome = generator.generateEdgeOutcome(
            network, null, start, end, new FlatTerrainSampler(64), null);

        assertEquals(EdgeGenerationOutcome.SKIPPED, outcome.outcome());
    }

    @Test
    void generationExceptionIsFailedNotSilentSuccess() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadGenerator generator = new RoadGenerator(config, null, BlockProjectionHandler.getInstance());
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        var edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        EdgeGenerationResult outcome = generator.generateEdgeOutcome(
            network, edge, start, end, new FailingTerrainSampler(), null);

        assertEquals(EdgeGenerationOutcome.FAILED, outcome.outcome());
        assertTrue(outcome.message().contains("terrain unavailable"));
    }
}
