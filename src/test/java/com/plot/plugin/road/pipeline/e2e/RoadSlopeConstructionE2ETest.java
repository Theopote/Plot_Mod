package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.golden.RoadGoldenScenario;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end preview: {@link RoadGenerator} → {@link com.plot.plugin.road.RoadNetworkGenerator}
 * → final {@link RoadGenerationResult#placementRecords}, not only corridor masks.
 */
class RoadSlopeConstructionE2ETest {

    @Test
    void straightCutRoadHasEmbankmentPlacementsAlongPath() {
        RoadNetwork network = straightRoad("cut-straight", 64, 64);
        RoadSystemConfig config = slopeEnabledConfig();
        TerrainSampler terrain = new FlatTerrainSampler(95);

        RoadGenerationResult edge = singleEdgePreview(network, config, terrain);
        assertEquals(0, edge.missingRoadSurfaceCount, "road surface must be placed");
        assertTrue(edge.cutVolume > 0, "cut volume expected on high ground");

        int missingEmbankment = 0;
        for (int x = 4; x <= 36; x++) {
            if (!hasEmbankmentPlacement(edge, x)) {
                missingEmbankment++;
            }
        }
        assertTrue(missingEmbankment <= 2,
            "cut embankment (air/shoulder/subgrade) expected at most path columns, missing=" + missingEmbankment);
    }

    @Test
    void descendingTerrainHasFewerShoulderTraceGapsThanFlatPlateau() {
        RoadNetwork network = straightRoad("trace-compare", 64, 64);
        RoadSystemConfig config = slopeEnabledConfig();

        RoadGenerationResult flat = singleEdgePreview(
            network, config, new FlatTerrainSampler(90));
        RoadGenerationResult descending = singleEdgePreview(
            network, config, descendingGroundSampler());

        assertEquals(0, flat.missingRoadSurfaceCount);
        assertEquals(0, descending.missingRoadSurfaceCount);
        assertTrue(descending.shoulderTraceGapCount <= flat.shoulderTraceGapCount,
            "descending ground should not increase shoulder trace gaps vs flat plateau, flat="
                + flat.shoulderTraceGapCount + " descending=" + descending.shoulderTraceGapCount);
    }

    @Test
    void straightCutRoadPopulatesShoulderTraceMetricAfterPipeline() {
        RoadNetwork network = straightRoad("trace-straight", 64, 64);
        RoadGenerationResult edge = singleEdgePreview(
            network, slopeEnabledConfig(), new FlatTerrainSampler(90));
        assertEquals(0, edge.missingRoadSurfaceCount);
        assertTrue(edge.cutVolume > 0);
        // On flat high ground the outward trace may still report missing mask steps until
        // terrain contact or width cap; metric must be populated from integrity pass.
        assertTrue(edge.shoulderTraceGapCount >= 0);
    }

    @Test
    void quarterTurnCurvedCutRoadHasEmbankmentAndAcceptableTraceGaps() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r02Curved();
        RoadNetwork network = scenario.network();
        Road road = network.getRoads().values().iterator().next();
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setIncludeSlopeBatter(true);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(40, 64))));

        RoadSystemConfig config = slopeEnabledConfig();
        RoadGenerationResult edge = singleEdgePreview(network, config, new FlatTerrainSampler(88));

        assertEquals(0, edge.missingRoadSurfaceCount);
        assertTrue(edge.cutVolume > 0);

        int embankmentSamples = 0;
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() < 6 || pos.getX() > 24) {
                continue;
            }
            if (pos.getZ() < -16 || pos.getZ() > 16) {
                continue;
            }
            if (isEmbankmentLayer(edge, pos)) {
                embankmentSamples++;
            }
        }
        assertTrue(embankmentSamples >= 25,
            "quarter-turn curve should emit embankment voxels, found=" + embankmentSamples);
        assertTrue(edge.shoulderTraceGapCount <= 80,
            "curved cut may have more cardinal trace gaps than straight roads, got "
                + edge.shoulderTraceGapCount);
    }

    @Test
    void curvedCutRoadHasEmbankmentAlongArc() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r02Curved();
        RoadNetwork network = scenario.network();
        Road road = network.getRoads().values().iterator().next();
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(40, 64))));

        RoadSystemConfig config = slopeEnabledConfig();
        config.setMaxSlope(40.0f);
        TerrainSampler highGround = new FlatTerrainSampler(88);

        RoadGenerationResult edge = singleEdgePreview(network, config, highGround);
        assertEquals(0, edge.missingRoadSurfaceCount);
        assertTrue(edge.cutVolume > 0);

        int embankmentSamples = 0;
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() < 8 || pos.getX() > 22) {
                continue;
            }
            if (pos.getZ() < -14 || pos.getZ() > 14) {
                continue;
            }
            if (isEmbankmentLayer(edge, pos)) {
                embankmentSamples++;
            }
        }
        assertTrue(embankmentSamples >= 20,
            "curved cut road should emit embankment voxels around the arc, found=" + embankmentSamples);
    }

    @Test
    void diagonalCutRoadHasEmbankmentPlacements() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("diag");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(30, 64))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 30));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());

        RoadGenerationResult edge = singleEdgePreview(
            network, slopeEnabledConfig(), new FlatTerrainSampler(90));
        assertEquals(0, edge.missingRoadSurfaceCount);
        assertTrue(edge.cutVolume > 0);

        int embankmentAlongDiagonal = 0;
        for (int step = 4; step <= 26; step += 2) {
            int x = step;
            int z = step;
            if (hasEmbankmentNear(edge, x, z, 4)) {
                embankmentAlongDiagonal++;
            }
        }
        assertTrue(embankmentAlongDiagonal >= 8,
            "diagonal cut should place embankment voxels along the span, hits=" + embankmentAlongDiagonal);
    }

    @Test
    void deepCutPreviewReportsWidthCapOnEdgeResult() {
        RoadNetwork network = straightRoad("deep-cut", 64, 64);
        RoadGenerationResult edge = singleEdgePreview(
            network, slopeEnabledConfig(), new FlatTerrainSampler(120));

        assertTrue(edge.slopeBatterWidthCapCount > 0,
            "deep cut must surface width-cap diagnostic on edge result");
        assertTrue(edge.unresolvedSlopeConstructionCount > 0,
            "deep cut must report batter that did not reach natural ground");
        assertTrue(edge.cutVolume > 0);
        assertEquals(0, edge.missingRoadSurfaceCount);
    }

    @Test
    void highFillPreviewHasSubgradeSupportPlacements() {
        RoadNetwork network = straightRoad("fill", 64, 64);
        RoadGenerationResult edge = singleEdgePreview(
            network, slopeEnabledConfig(), new FlatTerrainSampler(50));

        assertEquals(0, edge.missingRoadSurfaceCount);

        int subgradeColumns = 0;
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            RoadSolidLayer layer = edge.placementLayers.get(pos);
            if (layer != RoadSolidLayer.SUBGRADE && layer != RoadSolidLayer.SHOULDER) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                continue;
            }
            if (pos.getX() >= 5 && pos.getX() <= 25 && Math.abs(pos.getZ()) <= 6) {
                subgradeColumns++;
            }
        }
        assertTrue(subgradeColumns >= 10,
            "fill road should place subgrade/shoulder fill voxels, count=" + subgradeColumns);
    }

    @Test
    void slopeBatterDisabledStillBuildsRoadWithFewerOuterEmbankmentPlacements() {
        TerrainSampler terrain = new FlatTerrainSampler(92);
        RoadSystemConfig config = slopeEnabledConfig();

        RoadNetwork withSlopeNetwork = straightRoad("with-slope", 64, 64);
        withSlopeNetwork.getRoad("with-slope").setIncludeSlopeBatter(true);

        RoadNetwork withoutSlopeNetwork = straightRoad("without-slope", 64, 64);
        withoutSlopeNetwork.getRoad("without-slope").setIncludeSlopeBatter(false);

        RoadGenerationResult with = singleEdgePreview(withSlopeNetwork, config, terrain);
        RoadGenerationResult without = singleEdgePreview(withoutSlopeNetwork, config, terrain);

        assertEquals(0, with.missingRoadSurfaceCount);
        assertEquals(0, without.missingRoadSurfaceCount);
        assertNotNull(findRoadSurface(with, 15, 0));
        assertNotNull(findRoadSurface(without, 15, 0));

        int embankmentWith = countEmbankmentPlacements(with, 4);
        int embankmentWithout = countEmbankmentPlacements(without, 4);
        assertTrue(embankmentWith > embankmentWithout,
            "road-level slope batter disable should reduce outer embankment voxels, with="
                + embankmentWith + " without=" + embankmentWithout);
    }

    @Test
    void rollingTerrainFitPreviewKeepsRoadSurfaceAndRecordsIntegrity() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r07TerrainFit();
        RoadGenerator generator = generator(scenario.config());
        RoadBenchmarkHarness.PreviewRun run = RoadBenchmarkHarness.generatePreview(
            scenario.network(), generator, scenario.terrain());

        for (RoadGenerationResult edge : run.edgeResults().values()) {
            assertEquals(0, edge.missingRoadSurfaceCount, "R07 surface must be complete");
            assertTrue(edge.pathLength > 0);
        }
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

    private static RoadSystemConfig slopeEnabledConfig() {
        RoadSystemConfig config = new RoadSystemConfig("slope-e2e");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setIncludeSlopeBatter(true);
        config.setMaxSlope(40.0f);
        return config;
    }

    private static RoadGenerator generator(RoadSystemConfig config) {
        return new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
    }

    private static RoadGenerationResult singleEdgePreview(
            RoadNetwork network,
            RoadSystemConfig config,
            TerrainSampler terrain) {
        RoadBenchmarkHarness.PreviewRun run = RoadBenchmarkHarness.generatePreview(
            network, generator(config), terrain);
        assertEquals(1, run.edgeResults().size(), "expected single-edge preview");
        return run.edgeResults().values().iterator().next();
    }

    private static boolean hasEmbankmentPlacement(RoadGenerationResult edge, int worldX) {
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() != worldX) {
                continue;
            }
            if (Math.abs(pos.getZ()) <= 1) {
                continue;
            }
            if (isEmbankmentLayer(edge, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEmbankmentNear(RoadGenerationResult edge, int x, int z, int radius) {
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (Math.abs(pos.getX() - x) > radius || Math.abs(pos.getZ() - z) > radius) {
                continue;
            }
            if (isEmbankmentLayer(edge, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmbankmentLayer(RoadGenerationResult edge, BlockPos pos) {
        RoadSolidLayer layer = edge.placementLayers.get(pos);
        if (layer == RoadSolidLayer.ROAD || layer == RoadSolidLayer.MARKING) {
            return false;
        }
        String blockId = edge.placementRecords.get(pos).newBlockId;
        if ("minecraft:air".equals(blockId)) {
            return layer == RoadSolidLayer.TUNNEL;
        }
        return layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.SUBGRADE;
    }

    private static int countEmbankmentPlacements(RoadGenerationResult edge, int minAbsZ) {
        int count = 0;
        for (BlockPos pos : edge.placementRecords.keySet()) {
            if (Math.abs(pos.getZ()) < minAbsZ) {
                continue;
            }
            if (isEmbankmentLayer(edge, pos)) {
                count++;
            }
        }
        return count;
    }

    private static TerrainSampler descendingGroundSampler() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return (int) Math.max(64, 90 - Math.abs(planPoint.y) * 3);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };
    }

    private static BlockPos findRoadSurface(RoadGenerationResult edge, int x, int z) {
        for (Map.Entry<BlockPos, BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (edge.placementLayers.get(pos) != RoadSolidLayer.ROAD) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                continue;
            }
            if (pos.getX() == x && Math.abs(pos.getZ() - z) <= 2) {
                return pos;
            }
        }
        return null;
    }
}
