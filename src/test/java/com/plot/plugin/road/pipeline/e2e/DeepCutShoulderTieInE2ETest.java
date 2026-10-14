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
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepCutShoulderTieInE2ETest {

    @Test
    void deepMountainCutKeepsPartialBatterWithoutVerticalCliff() {
        RoadNetwork network = straightRoad("deep-tie-in", 64, 64);
        RoadGenerationResult edge = preview(network, slopeConfig(), new FlatTerrainSampler(120));

        assertEquals(0, edge.missingRoadSurfaceCount);
        assertTrue(edge.needsRetainingWallCount > 0, "deep cut should report width-capped batter");
        assertEquals(0, edge.unresolvedSlopeConstructionCount,
            "partial batter must emit instead of skipping entire ray");
        assertTrue(edge.cutVolume > 0);

        int batterPlacements = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            if (entry.getValue() == RoadSolidLayer.SHOULDER
                    && entry.getKey().getY() >= 64
                    && entry.getKey().getY() <= 100) {
                batterPlacements++;
            }
        }
        assertTrue(batterPlacements > 0, "cut batter shoulder placements expected");

        Map<Long, Integer> stackedByColumn = new HashMap<>();
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : edge.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue() != RoadSolidLayer.SHOULDER && entry.getValue() != RoadSolidLayer.SUBGRADE) {
                continue;
            }
            if (pos.getY() <= 70 || pos.getY() >= 115) {
                continue;
            }
            long columnKey = ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
            stackedByColumn.merge(columnKey, 1, Integer::sum);
        }
        int verticalFillColumns = 0;
        for (int stackHeight : stackedByColumn.values()) {
            if (stackHeight > 2) {
                verticalFillColumns++;
            }
        }
        assertTrue(verticalFillColumns <= 8,
            "贴路区域不得出现长垂直挡墙, columns=" + verticalFillColumns);
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
        road.setIncludeShoulder(true);
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
            com.plot.core.terrain.TerrainSampler terrain) {
        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        return RoadBenchmarkHarness.generatePreview(network, generator, terrain)
            .edgeResults().values().iterator().next();
    }
}
