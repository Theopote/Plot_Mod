package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadPlacementSeamAssertions;
import com.plot.plugin.road.benchmark.RoadNetworkBenchmarkFactory;
import com.plot.plugin.road.golden.RoadGoldenScenario;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.model.NetworkElevationMode;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.NetworkElevationAuthority;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全网平路复杂地形建造验收：设计路面统一 Y；路基/填方/清障可延伸其它高度。
 */
class NetworkUniformFlatTerrainE2ETest {

    private static final int UNIFORM_Y = 72;

    @Test
    void flatTerrainKeepsCompletePavementAtUniformY() {
        RoadNetwork network = straightCorridor("flat-ground");
        enableUniformFlat(network, UNIFORM_Y);

        RoadNetworkGenerator.PreviewResult preview = generate(network, new FlatTerrainSampler(UNIFORM_Y), false);
        RoadGenerationResult aggregate = preview.aggregate();

        assertAllNodeElevations(preview, UNIFORM_Y);
        assertDesignPavementYs(aggregate, UNIFORM_Y);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertEquals(0, aggregate.unsupportedPavementCount);
    }

    @Test
    void hillCutClearsAbovePavementAndKeepsSupportedRoadSurface() {
        RoadNetwork network = straightCorridor("hill-cut");
        enableUniformFlat(network, UNIFORM_Y);

        RoadNetworkGenerator.PreviewResult preview = generate(
            network, new FlatTerrainSampler(95), true);
        RoadGenerationResult aggregate = preview.aggregate();

        assertAllNodeElevations(preview, UNIFORM_Y);
        assertDesignPavementYs(aggregate, UNIFORM_Y);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertEquals(0, aggregate.unsupportedPavementCount,
            "cut pavement must rest on terrain/subgrade after clearance");
        assertTrue(aggregate.cutVolume > 0, "hill above uniform Y must produce cut/clearance");

        // 清障结果写入 placement：路面上方走廊应有空气清除记录（而非仅有土方计量）
        int clearanceAir = countClearanceAirAbovePavement(aggregate, UNIFORM_Y);
        assertTrue(clearanceAir > 20,
            "hill cut should place air clearance above design pavement, air=" + clearanceAir);
    }

    @Test
    void gullyFillOrStructureSupportsUniformPavement() {
        RoadNetwork network = straightCorridor("gully-fill");
        enableUniformFlat(network, UNIFORM_Y);

        RoadNetworkGenerator.PreviewResult preview = generate(
            network, new FlatTerrainSampler(50), true);
        RoadGenerationResult aggregate = preview.aggregate();

        assertAllNodeElevations(preview, UNIFORM_Y);
        assertDesignPavementYs(aggregate, UNIFORM_Y);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertEquals(0, aggregate.unsupportedPavementCount,
            "gully pavement must not float unsupported");
        assertTrue(
            aggregate.fillVolume > 0 || hasSupportBelowPavement(aggregate, UNIFORM_Y),
            "deep gully should fill or place structure under design pavement");
    }

    @Test
    void slopingHillStillKeepsUniformDesignPavementY() {
        RoadNetwork network = straightCorridor("sloped-hill");
        enableUniformFlat(network, UNIFORM_Y);

        TerrainSampler sloping = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                // 沿道路 X 升高：西端 80 → 东端约 100
                return (int) Math.round(80 + Math.max(0, planPoint.x) * 0.4);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };

        RoadNetworkGenerator.PreviewResult preview = generate(network, sloping, true);
        RoadGenerationResult aggregate = preview.aggregate();

        assertAllNodeElevations(preview, UNIFORM_Y);
        assertDesignPavementYs(aggregate, UNIFORM_Y);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertEquals(0, aggregate.unsupportedPavementCount);
        assertTrue(aggregate.cutVolume > 0);
    }

    @Test
    void crossJunctionSeamsMeetAtUniformY() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r04CrossJunction();
        RoadNetwork network = scenario.network();
        // 与 R04 golden 同配置/地形，仅叠加 UNIFORM_FLAT，隔离模式权威对路口接缝的影响。
        int y = 64;
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(y);

        RoadNetworkGenerator.PreviewResult preview = new RoadNetworkGenerator(new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()))
            .generatePreview(network, scenario.terrain());
        RoadGenerationResult aggregate = preview.aggregate();

        assertAllNodeElevations(preview, y);
        assertDesignPavementYs(aggregate, y);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertEquals(0, aggregate.unsupportedPavementCount);

        RoadNode plaza = network.getNodes().values().stream()
            .filter(n -> Math.hypot(n.getPosition().x, n.getPosition().y) < 1e-6)
            .findFirst()
            .orElseThrow();
        Integer plazaY = preview.nodeElevations().get(plaza.getId());
        assertNotNull(plazaY);
        assertEquals(y, plazaY.intValue());

        RoadPlacementSeamAssertions.assertJunctionApproachSeams(
            network,
            aggregate,
            plaza.getPosition(),
            plazaY,
            2.0,
            10.0,
            1);
    }

    @Test
    void valleyBridgeKeepsUniformDeckAndStructure() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r08Bridge();
        RoadNetwork network = scenario.network();
        int y = 64;
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(y);

        RoadNetworkGenerator.PreviewResult preview = generateWithScenario(scenario, network);
        RoadGenerationResult aggregate = preview.aggregate();

        assertAllNodeElevations(preview, y);
        assertDesignPavementYs(aggregate, y);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertEquals(0, aggregate.unsupportedPavementCount);
        assertTrue(
            aggregate.constructionTypes.contains(RoadConstructionType.BRIDGE) || aggregate.bridgeLength > 0,
            "valley under uniform flat should still form a bridge span");
        assertTrue(countLayerInXBand(aggregate, RoadSolidLayer.ROAD, 10, 20) >= 5);
        assertTrue(countLayerInXBand(aggregate, RoadSolidLayer.BRIDGE, 10, 20) >= 5,
            "bridge members expected under valley deck");
        assertTrue(maxCenterlineRoadStep(aggregate, 2, 28) <= 1,
            "uniform deck approach/valley must stay continuous");
        assertEquals(0, aggregate.structureInteriorSlopeCellCount);
    }

    @Test
    void waterCrossingKeepsUniformDeckWithSupport() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r10WaterCrossing();
        RoadNetwork network = scenario.network();
        int y = 64;
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(y);

        RoadNetworkGenerator.PreviewResult preview = generateWithScenario(scenario, network);
        RoadGenerationResult aggregate = preview.aggregate();

        assertAllNodeElevations(preview, y);
        assertDesignPavementYs(aggregate, y);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertEquals(0, aggregate.unsupportedPavementCount);
        assertTrue(
            aggregate.fillVolume > 0
                || aggregate.bridgeLength > 0
                || aggregate.constructionTypes.contains(RoadConstructionType.BRIDGE)
                || hasSupportBelowPavement(aggregate, y),
            "water crossing must fill or bridge under uniform deck");
    }

    @Test
    void gradeSeparatedNetworkBlockedUnderUniformFlat() {
        RoadGoldenScenario scenario = RoadGoldenScenarioFactory.r11GradeSeparated();
        RoadNetwork network = scenario.network();
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(64);

        assertTrue(NetworkElevationAuthority.hasGradeSeparationConflict(network));
        assertTrue(RoadNetworkEngineeringValidator.analyzePreGeneration(network).blocksBuild());
    }

    @Test
    void mediumGridNetworkStaysUniformAndComplete() {
        RoadNetwork network = RoadNetworkBenchmarkFactory.gridNetwork(3, 4);
        enableUniformFlat(network, UNIFORM_Y);

        long started = System.nanoTime();
        RoadNetworkGenerator.PreviewResult preview = generate(
            network, new FlatTerrainSampler(UNIFORM_Y), false);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        RoadGenerationResult aggregate = preview.aggregate();
        assertAllNodeElevations(preview, UNIFORM_Y);
        assertDesignPavementYs(aggregate, UNIFORM_Y);
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertEquals(0, aggregate.unsupportedPavementCount);
        assertTrue(aggregate.placementRecords.size() > 100, "grid must produce merged placements");
        assertTrue(elapsedMs < 20_000L, "medium grid preview should finish <20s, took=" + elapsedMs);
    }

    private static RoadNetworkGenerator.PreviewResult generateWithScenario(
            RoadGoldenScenario scenario,
            RoadNetwork network) {
        return new RoadNetworkGenerator(new RoadGenerator(
            scenario.config(),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()))
            .generatePreview(network, scenario.terrain());
    }

    private static int countLayerInXBand(
            RoadGenerationResult aggregate,
            RoadSolidLayer layer,
            int minX,
            int maxX) {
        int count = 0;
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : aggregate.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() >= minX && pos.getX() <= maxX && entry.getValue() == layer) {
                count++;
            }
        }
        return count;
    }

    private static int maxCenterlineRoadStep(RoadGenerationResult aggregate, int minX, int maxX) {
        TreeMap<Integer, Integer> yByX = new TreeMap<>();
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : aggregate.placementLayers.entrySet()) {
            if (entry.getValue() != RoadSolidLayer.ROAD) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (pos.getX() < minX || pos.getX() > maxX || Math.abs(pos.getZ()) > 1) {
                continue;
            }
            yByX.merge(pos.getX(), pos.getY(), Math::max);
        }
        int maxStep = 0;
        Integer prevY = null;
        for (Integer y : yByX.values()) {
            if (prevY != null) {
                maxStep = Math.max(maxStep, Math.abs(y - prevY));
            }
            prevY = y;
        }
        return maxStep;
    }

    private static void enableUniformFlat(RoadNetwork network, int y) {
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(y);
        for (Road road : network.getRoads().values()) {
            if (road == null) {
                continue;
            }
            // live 逐路模式可保留；生成侧由 UNIFORM_FLAT 覆盖
            road.setIncludeShoulder(true);
            road.setIncludeSlopeBatter(true);
        }
    }

    private static RoadNetwork straightCorridor(String id) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(id);
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setIncludeShoulder(true);
        road.setIncludeSlopeBatter(true);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(48, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        return network;
    }

    private static RoadNetworkGenerator.PreviewResult generate(
            RoadNetwork network,
            TerrainSampler terrain,
            boolean slopeBatter) {
        RoadSystemConfig config = new RoadSystemConfig("uniform-flat-terrain");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setIncludeSlopeBatter(slopeBatter);
        config.setFillSlopeRatio(1.0f);
        config.setCutSlopeRatio(1.0f);
        config.setMaxSlope(40.0f);
        config.setGenerateBridgePillars(true);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        return new RoadNetworkGenerator(generator).generatePreview(network, terrain);
    }

    private static void assertAllNodeElevations(RoadNetworkGenerator.PreviewResult preview, int y) {
        assertFalse(preview.nodeElevations().isEmpty());
        for (Integer elev : preview.nodeElevations().values()) {
            assertEquals(y, elev.intValue());
        }
    }

    /** 仅断言设计路面 ROAD 层高度；允许路基/填方/桥墩在其它 Y。 */
    private static void assertDesignPavementYs(RoadGenerationResult aggregate, int expectedY) {
        Set<Integer> roadYs = new HashSet<>();
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : aggregate.placementLayers.entrySet()) {
            if (entry.getValue() != RoadSolidLayer.ROAD) {
                continue;
            }
            BlockPos pos = entry.getKey();
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            roadYs.add(pos.getY());
        }
        assertFalse(roadYs.isEmpty(), "expected ROAD pavement placements");
        assertEquals(Set.of(expectedY), roadYs,
            "design pavement Y must be uniform; support layers may differ");
    }

    private static int countClearanceAirAbovePavement(RoadGenerationResult aggregate, int pavementY) {
        int air = 0;
        for (Map.Entry<BlockPos, BlockRecord> entry : aggregate.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() <= pavementY || pos.getY() > pavementY + 24) {
                continue;
            }
            if (!"minecraft:air".equals(entry.getValue().newBlockId)) {
                continue;
            }
            air++;
        }
        return air;
    }

    private static boolean hasSupportBelowPavement(RoadGenerationResult aggregate, int pavementY) {
        for (Map.Entry<BlockPos, RoadSolidLayer> entry : aggregate.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() >= pavementY) {
                continue;
            }
            RoadSolidLayer layer = entry.getValue();
            if (layer == RoadSolidLayer.SUBGRADE
                    || layer == RoadSolidLayer.BRIDGE
                    || layer == RoadSolidLayer.RETAINING_WALL) {
                return true;
            }
        }
        return false;
    }

}
