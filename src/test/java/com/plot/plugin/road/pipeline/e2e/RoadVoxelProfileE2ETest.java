package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.ProfilePlacementTestSupport;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.pipeline.profile.RoadVoxelProfileCompiler;
import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
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
 * Voxel compile preview must match generated ROAD surface Y through full preview pipeline.
 */
class RoadVoxelProfileE2ETest {

    @Test
    void voxelCompileMatchesGeneratedRoadOnLongFlatManualProfile() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("voxel-e2e");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(40.0f);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 78.0),
            new PointOfVerticalIntersection(120.0, 78.0)));
        road.setVerticalAlignment(alignment);

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(120, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);

        RoadSystemConfig config = new RoadSystemConfig("voxel-e2e");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        VoxelProfileCompileResult compiled = RoadVoxelProfileCompiler.compile(
            network,
            road,
            config,
            generator,
            terrain,
            Map.of(),
            ProfileCompileOptions.defaults(),
            null,
            alignment);
        assertTrue(compiled.feasible(), compiled.failureReason());

        RoadBenchmarkHarness.PreviewRun generated = RoadBenchmarkHarness.generatePreview(
            network, generator, terrain);
        RoadGenerationResult edge = generated.edgeResults().values().iterator().next();
        assertEquals(0, edge.missingRoadSurfaceCount);

        int compared = 0;
        for (BuildHeightSample sample : compiled.samples()) {
            if (sample.station() < 2 || sample.station() > 118) {
                continue;
            }
            Integer pavementY = pavementYNear(edge, (int) Math.round(sample.station()));
            if (pavementY == null) {
                continue;
            }
            assertEquals(sample.buildY(), pavementY.intValue(),
                "voxel compile must match ROAD placement at station " + sample.station());
            compared++;
        }
        assertTrue(compared >= 20, "expected many station comparisons along 120-block road");
    }

    @Test
    void designBlockPlacementChainOnFlatManualProfile() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design-chain");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(40.0f);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 77.0),
            new PointOfVerticalIntersection(100.0, 77.0)));
        road.setVerticalAlignment(alignment);

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);

        RoadSystemConfig config = new RoadSystemConfig("design-chain");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        VoxelProfileCompileResult compiled = RoadVoxelProfileCompiler.compile(
            network, road, config, generator, terrain,
            Map.of(), ProfileCompileOptions.defaults(), null, alignment);
        RoadBenchmarkHarness.PreviewRun preview =
            RoadBenchmarkHarness.generatePreview(network, generator, terrain);

        ProfilePlacementTestSupport.assertDesignBlockPlacementChain(
            alignment, compiled, preview, 1e-3, 1);
    }

    @Test
    void voxelCompileMatchesGeneratedRoadOnSlopedManualProfile() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("voxel-slope-e2e");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(40.0f);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(40.0, 72.0),
            new PointOfVerticalIntersection(80.0, 64.0)));
        road.setVerticalAlignment(alignment);

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(80, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);

        RoadSystemConfig config = new RoadSystemConfig("voxel-slope-e2e");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());

        VoxelProfileCompileResult compiled = RoadVoxelProfileCompiler.compile(
            network, road, config, generator, new FlatTerrainSampler(64),
            Map.of(), ProfileCompileOptions.defaults(), null, alignment);
        assertTrue(compiled.feasible());

        RoadGenerationResult edge = RoadBenchmarkHarness.generatePreview(
            network, generator, new FlatTerrainSampler(64))
            .edgeResults().values().iterator().next();

        for (int x : new int[] {10, 25, 40, 55, 70}) {
            int previewY = compiled.buildProfile().elevationAtWorldStation(x);
            Integer pavementY = pavementYNear(edge, x);
            assertNotNull(pavementY, "ROAD surface expected near x=" + x);
            assertEquals(previewY, pavementY.intValue(), "mismatch at x=" + x);
        }
    }

    private static Integer pavementYNear(RoadGenerationResult edge, int x) {
        for (Map.Entry<BlockPos, com.plot.core.command.BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (edge.placementLayers.get(pos) != RoadSolidLayer.ROAD) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                continue;
            }
            if (pos.getX() >= x - 1 && pos.getX() <= x + 1 && Math.abs(pos.getZ()) <= 2) {
                return pos.getY();
            }
        }
        return null;
    }
}
