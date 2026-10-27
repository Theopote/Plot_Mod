package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.ProfilePlacementTestSupport;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.pipeline.profile.RoadVoxelProfileCompiler;
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentProfileSupport;
import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设计线 / 采样方块线 / 体素编译 / 最终 ROAD 放置应遵循同一建造方案。
 */
class RoadProfileGenerationParityTest {

    @Test
    void editedSlopedProfileKeepsDesignBlockPlacementAligned() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("edited-slope");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(40.0f);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 66.0),
            PointOfVerticalIntersection.of(50.0, 74.0),
            PointOfVerticalIntersection.of(100.0, 66.0)));
        road.setVerticalAlignment(alignment);
        wireStraight(network, road, 100.0);
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);

        RunBundle run = compileAndGenerate(network, road, "edited-slope-parity");
        RoadGenerationResult placement = run.preview().edgeResults().values().iterator().next();
        // 采样与体素编译必须同方案；放置允许坡段 ±1 格量化差。
        for (double station : new double[] {20, 40, 50, 60, 80}) {
            int compileY = run.compile().buildProfile().elevationAtWorldStation(station);
            int samplingY = ProfilePlacementTestSupport.nearestBuildSampleY(run.samplingEdge(), station);
            assertEquals(compileY, samplingY, "sampling vs compile at station " + station);
            Integer pavementY = ProfilePlacementTestSupport.pavementYNear(
                placement, (int) Math.round(station));
            if (pavementY != null) {
                assertEquals(compileY, pavementY.intValue(), 1,
                    "placement vs compile within 1 at station " + station);
            }
            double designY = com.plot.plugin.road.vertical.VerticalAlignmentGeometry
                .elevationAt(alignment, station)
                .orElseThrow();
            assertEquals(designY, compileY, 1.0,
                "block Y within 1 of design at station " + station);
        }
    }

    @Test
    void flatIntentKeepsDesignBlockPlacementAligned() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("flat-parity");
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setFlatVerticalIntent(new FlatVerticalIntent(71.0));
        road.setMaxSlope(40.0f);
        wireStraight(network, road, 80.0);

        RoadSystemConfig config = baseConfig("flat-parity");
        RoadGenerator generator = generator(config);
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        VoxelProfileCompileResult compile = RoadVoxelProfileCompiler.compile(
            network, road, config, generator, terrain,
            Map.of(), ProfileCompileOptions.defaults(), null, null);
        assertTrue(compile.feasible(), compile.failureReason());

        RoadBenchmarkHarness.PreviewRun preview =
            RoadBenchmarkHarness.generatePreview(network, generator, terrain);
        RoadNetworkGenerator.ProfileSamplingResult sampling =
            new RoadNetworkGenerator(generator).calculateProfileSampling(network, terrain);

        ProfilePlacementTestSupport.assertBuildSchemeParityAtStations(
            compile,
            sampling.edgeResults().values().iterator().next(),
            preview.edgeResults().values().iterator().next(),
            new double[] {10, 30, 50, 70},
            1);

        for (double station : new double[] {10, 40, 70}) {
            assertEquals(71, compile.buildProfile().elevationAtWorldStation(station),
                "flat intent should drive build Y at station " + station);
        }
    }

    @Test
    void autoWithDormantEditedCurveDoesNotUseStoredPviForGenerationAuthority() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("auto-dormant");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setMaxSlope(40.0f);
        // Dormant stored curve far above flat terrain — must not become generation authority.
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 90.0),
            PointOfVerticalIntersection.of(80.0, 90.0))));
        road.setProfileSource(ProfileSource.AUTO);
        wireStraight(network, road, 80.0);

        assertTrue(ResolvedProfileAuthority.hasStoredEditedCurve(road));
        assertFalse(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road));
        assertFalse(VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road));

        RunBundle run = compileAndGenerate(network, road, "auto-dormant-parity");
        RoadGenerationResult placement = run.preview().edgeResults().values().iterator().next();

        // On flat terrain=64, AUTO should not place ROAD near dormant Y=90.
        for (double station : new double[] {20, 40, 60}) {
            int compileY = run.compile().buildProfile().elevationAtWorldStation(station);
            assertTrue(Math.abs(compileY - 90) > 5,
                "AUTO must not follow dormant PVI=90 at station " + station + ", got " + compileY);
            Integer pavementY = ProfilePlacementTestSupport.pavementYNear(
                placement, (int) Math.round(station));
            if (pavementY != null) {
                assertTrue(Math.abs(pavementY - 90) > 5,
                    "placement must not follow dormant PVI at station " + station);
                assertEquals(compileY, pavementY.intValue());
            }
        }
    }

    private static RunBundle compileAndGenerate(RoadNetwork network, Road road, String configId) {
        RoadSystemConfig config = baseConfig(configId);
        RoadGenerator generator = generator(config);
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);
        VoxelProfileCompileResult compile = RoadVoxelProfileCompiler.compile(
            network,
            road,
            config,
            generator,
            terrain,
            Map.of(),
            ProfileCompileOptions.defaults(),
            null,
            road.getVerticalAlignment());
        assertTrue(compile.feasible(), compile.failureReason());
        RoadBenchmarkHarness.PreviewRun preview =
            RoadBenchmarkHarness.generatePreview(network, generator, terrain);
        RoadGenerationResult samplingEdge = new RoadNetworkGenerator(generator)
            .calculateProfileSampling(network, terrain)
            .edgeResults()
            .values()
            .iterator()
            .next();
        return new RunBundle(compile, preview, samplingEdge);
    }

    private static void wireStraight(RoadNetwork network, Road road, double length) {
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(length, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
    }

    private static RoadSystemConfig baseConfig(String id) {
        RoadSystemConfig config = new RoadSystemConfig(id);
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);
        return config;
    }

    private static RoadGenerator generator(RoadSystemConfig config) {
        return new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
    }

    private record RunBundle(
            VoxelProfileCompileResult compile,
            RoadBenchmarkHarness.PreviewRun preview,
            RoadGenerationResult samplingEdge) {
    }
}
