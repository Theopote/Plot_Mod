package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSlopeCurvedContinuityE2ETest {

    @Test
    void curvedCutRoadHasNoBrokenSlopeIntegrity() {
        RoadNetwork network = RoadGoldenScenarioFactory.r02Curved().network();
        Road road = network.getRoads().values().iterator().next();
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setIncludeSlopeBatter(true);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(40, 64))));

        RoadGenerationResult edge = preview(network, slopeEnabledConfig(), new FlatTerrainSampler(88));
        assertEquals(0, edge.missingRoadSurfaceCount);
        assertTrue(edge.cutVolume > 0);
        assertTrue(edge.brokenSlopeSurfaceCount <= 2,
            "curved cut should keep broken slope columns minimal, count=" + edge.brokenSlopeSurfaceCount);
        assertEquals(0, edge.orphanSlopePillarCount);
    }

    @Test
    void deepCutDoesNotLeaveTruncatedVerticalShoulderWall() {
        RoadNetwork network = straightRoad("deep-wall", 64, 64);
        RoadGenerationResult edge = preview(network, slopeEnabledConfig(), new FlatTerrainSampler(120));

        assertTrue(edge.needsRetainingWallCount > 0);
        assertEquals(0, edge.unresolvedSlopeConstructionCount);
        Map<Long, Integer> stackedByColumn = new java.util.HashMap<>();
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
            "width-capped deep cut must not emit long vertical fill walls, columns=" + verticalFillColumns);
    }

    private static RoadSystemConfig slopeEnabledConfig() {
        RoadSystemConfig config = new RoadSystemConfig("curved-slope-e2e");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.0f);
        config.setCutSlopeRatio(1.0f);
        config.setMaxSlope(40.0f);
        return config;
    }

    private static RoadNetwork straightRoad(String id, int startY, int endY) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(id);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(40.0f);
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
        RoadBenchmarkHarness.PreviewRun preview = RoadBenchmarkHarness.generatePreview(
            network, generator, terrain);
        return preview.edgeResults().values().iterator().next();
    }
}
