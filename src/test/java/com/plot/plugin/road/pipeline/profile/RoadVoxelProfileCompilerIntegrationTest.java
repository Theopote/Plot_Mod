package com.plot.plugin.road.pipeline.profile;

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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Editor voxel preview must match actual ROAD surface Y after generation.
 */
class RoadVoxelProfileCompilerIntegrationTest {

    @Test
    void manualFlatDesignCompileTracksDesignWithinOneBlock() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("manual-flat");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(40.0f);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(java.util.List.of(
            new PointOfVerticalIntersection(0.0, 78.0),
            new PointOfVerticalIntersection(120.0, 78.0)));
        road.setVerticalAlignment(alignment);

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(120, 0));
        network.createEdge(a.getId(), b.getId(), java.util.List.of(a.getPosition(), b.getPosition()), road.getId());

        RoadSystemConfig config = new RoadSystemConfig("manual-flat");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        VoxelProfileCompileResult preview = RoadVoxelProfileCompiler.compile(
            network,
            road,
            config,
            generator,
            terrain,
            Map.of(),
            ProfileCompileOptions.defaults(),
            null,
            alignment);
        assertTrue(preview.feasible(), preview.failureReason());
        assertTrue(preview.hasSamples());
        for (BuildHeightSample sample : preview.samples()) {
            assertTrue(Math.abs(sample.buildY() - 78) <= 1,
                "manual flat design at Y=78 must compile near design, station=" + sample.station()
                    + " buildY=" + sample.buildY());
        }
    }

    @Test
    void editorVoxelPreviewMatchesActualGeneratedRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("voxel-match");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(40.0f);
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(15.0, 66.0),
            new PointOfVerticalIntersection(30.0, 64.0))));

        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 0));
        network.createEdge(a.getId(), b.getId(), java.util.List.of(a.getPosition(), b.getPosition()), road.getId());

        RoadSystemConfig config = new RoadSystemConfig("voxel-match");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        VoxelProfileCompileResult preview = RoadVoxelProfileCompiler.compile(
            network,
            road,
            config,
            generator,
            terrain,
            Map.of(),
            ProfileCompileOptions.defaults());
        assertTrue(preview.feasible(), "preview must be feasible");
        assertTrue(preview.hasSamples());
        assertTrue(preview.buildProfile().isActive());

        RoadBenchmarkHarness.PreviewRun generated = RoadBenchmarkHarness.generatePreview(
            network, generator, terrain);
        assertFalse(generated.edgeResults().isEmpty());
        RoadGenerationResult edge = generated.edgeResults().values().iterator().next();
        assertNotNull(edge.buildProfile);
        assertTrue(edge.buildProfile.isActive());

        // Same compile path: edge build profile must agree with compiler at sampled stations.
        for (BuildHeightSample sample : preview.samples()) {
            int previewY = sample.buildY();
            int buildY = edge.buildProfile.elevationAtWorldStation(sample.station());
            assertEquals(previewY, buildY,
                "preview blockY must match generation BuildHeightProfile at station "
                    + sample.station());
        }

        // Mid-span pavement Y matches preview height at station ~15.
        int previewMid = preview.buildProfile().elevationAtWorldStation(15.0);
        Integer pavementY = null;
        for (Map.Entry<BlockPos, com.plot.core.command.BlockRecord> entry : edge.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (edge.placementLayers.get(pos) != RoadSolidLayer.ROAD) {
                continue;
            }
            if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                continue;
            }
            if (pos.getX() >= 14 && pos.getX() <= 16 && Math.abs(pos.getZ()) <= 2) {
                pavementY = pos.getY();
                break;
            }
        }
        assertNotNull(pavementY, "expected ROAD surface near X=15");
        assertEquals(previewMid, pavementY.intValue(),
            "Minecraft ROAD surface Y must equal voxel preview at midspan");
    }
}
