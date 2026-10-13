package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadVoxelProfileCompilerTest {

    @Test
    void flatRoadProducesConstantBlockY() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("flat-voxel");
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setFlatVerticalIntent(new FlatVerticalIntent(64.0));
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(40.0, 64.0))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), b.getId(), java.util.List.of(a.getPosition(), b.getPosition()), road.getId());

        VoxelProfileCompileResult result = compile(network, road);
        assertTrue(result.feasible());
        assertTrue(result.hasSamples());
        int first = result.samples().getFirst().buildY();
        for (BuildHeightSample sample : result.samples()) {
            assertEquals(first, sample.buildY(), "FLAT voxel profile must stay level");
        }
    }

    @Test
    void multiEdgeRoadUsesGlobalChainage() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("multi-edge");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(20.0, 64.0),
            new PointOfVerticalIntersection(40.0, 64.0))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode mid = network.createNode(new Vec2d(20, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), mid.getId(), java.util.List.of(a.getPosition(), mid.getPosition()), road.getId());
        network.createEdge(mid.getId(), b.getId(), java.util.List.of(mid.getPosition(), b.getPosition()), road.getId());

        VoxelProfileCompileResult result = compile(network, road);
        assertTrue(result.feasible());
        assertTrue(result.hasSamples());
        assertTrue(result.samples().getLast().station() > 30.0,
            "assembled samples must span both edges");
        assertFalse(result.samples().getFirst().station() > result.samples().getLast().station());
    }

    @Test
    void shortCrestOnNearFlatDesignIsRemovedFromCompiledSamples() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("crest");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setMaxSlope(40.0f);
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            new PointOfVerticalIntersection(0.0, 64.0),
            new PointOfVerticalIntersection(30.0, 64.0))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(30, 0));
        network.createEdge(a.getId(), b.getId(), java.util.List.of(a.getPosition(), b.getPosition()), road.getId());

        VoxelProfileCompileResult result = compile(network, road);
        assertTrue(result.hasSamples());
        int[] build = result.samples().stream().mapToInt(BuildHeightSample::buildY).toArray();
        assertEquals(0, BuildHeightSequenceSmoother.countShortReversals(build),
            "compiled flat design must not contain short reversals");
    }

    private static VoxelProfileCompileResult compile(RoadNetwork network, Road road) {
        RoadSystemConfig config = new RoadSystemConfig("voxel-compiler");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);
        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        return RoadVoxelProfileCompiler.compile(
            network,
            road,
            config,
            generator,
            new FlatTerrainSampler(64),
            Map.of(),
            ProfileCompileOptions.defaults());
    }
}
