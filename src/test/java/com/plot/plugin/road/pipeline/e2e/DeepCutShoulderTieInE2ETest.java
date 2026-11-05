package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepCutShoulderTieInE2ETest {

    @Test
    void deepMountainCutKeepsPartialBatterWithoutVerticalCliff() {
        // 深覆土在生产阈值下禁开敞削坡（隧道/清障）；露天宽度截断放坡由走廊单测覆盖。
        RoadNetwork network = straightRoad("deep-tie-in", 64, 64);
        RoadGenerationResult edge = preview(network, slopeConfig(), new FlatTerrainSampler(120));

        assertEquals(0, edge.missingRoadSurfaceCount);
        assertTrue(edge.cutVolume > 0);
        assertTrue(
            edge.tunnelCount > 0 || !edge.tunnelBlocks.isEmpty() || edge.cutVolume > 100,
            "deep mountain cover should tunnel or excavate rather than leave a vertical open cliff");
        assertEquals(0, edge.slopeBatterWidthCapCount,
            "default tunnel threshold suppresses open width-cap batter on deep cover");
        assertEquals(0, edge.unresolvedSlopeConstructionCount);
    }

    private static RoadSystemConfig slopeConfig() {
        RoadSystemConfig config = new RoadSystemConfig("deep-tie-in");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.0f);
        config.setCutSlopeRatio(1.0f);
        return config;
    }

    private static RoadNetwork straightRoad(String id, int startY, int endY) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(id);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, startY),
            PointOfVerticalIntersection.of(40, endY))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        return network;
    }

    private static RoadGenerationResult preview(
            RoadNetwork network,
            RoadSystemConfig config,
            FlatTerrainSampler terrain) {
        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        return RoadBenchmarkHarness.generatePreview(network, generator, terrain)
            .edgeResults().values().iterator().next();
    }
}
