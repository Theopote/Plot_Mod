package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.EdgeGenerationOutcome;
import com.plot.plugin.road.pipeline.EdgeGenerationResult;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkGeneratorTest {

    @Test
    void resolveJunctionMaterialPrefersWidestConnectedEdge() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setSelectedMaterial("minecraft:white_concrete");
        config.setSelectedSidewalkMaterial("minecraft:gravel");

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));

        Road narrowRoad = network.createRoad();
        narrowRoad.setWidth(5);
        narrowRoad.setMaterial("minecraft:stone");
        network.createEdge(
            junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)), narrowRoad.getId());

        Road wideRoad = network.createRoad();
        wideRoad.setWidth(9);
        wideRoad.setMaterial("minecraft:oak_planks");
        wideRoad.setIncludeSidewalk(true);
        wideRoad.setSidewalkMaterial("minecraft:oak_planks");
        network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), wideRoad.getId());

        network.createEdge(
            junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)));

        assertEquals(
            "minecraft:oak_planks",
            RoadGenerationResultAssembler.resolveJunctionMaterial(junction, network, config, false));
        assertEquals(
            "minecraft:oak_planks",
            RoadGenerationResultAssembler.resolveJunctionMaterial(junction, network, config, true));
    }

    @Test
    void shouldGenerateJunctionReturnsFalseWhenConnectedEdgeFailed() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));

        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)));
        RoadEdge eastEdge = network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        network.createEdge(junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)));

        assertTrue(RoadNetworkGenerator.shouldGenerateJunction(junction, Set.of()));
        assertFalse(RoadNetworkGenerator.shouldGenerateJunction(junction, Set.of(eastEdge.getId())));
    }

    @Test
    void networkResultSeparatesFailedEdgesFromSuccessfulGeometry() {
        var networkResult = new RoadNetworkGenerator.NetworkGenerationResult();
        networkResult.recordEdgeOutcome("ok", EdgeGenerationResult.success(new com.plot.plugin.road.solid.RoadGenerationResult(10)));
        networkResult.recordEdgeOutcome("bad", EdgeGenerationResult.failed("boom"));

        assertEquals(1, networkResult.getEdgeResults().size());
        assertTrue(networkResult.getFailedEdgeIds().contains("bad"));
        assertEquals(1, networkResult.successEdgeCount());
        assertEquals(2, networkResult.totalEdgeCount());
        assertTrue(networkResult.hasPartialFailure());
        assertEquals(1, networkResult.getErrors().size());
    }

    @Test
    void generateAllDoesNotMutateLiveDerivedCenterline() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeDrainage(false);
        config.setPathSampleDistance(4.0);

        RoadGenerator generator = new RoadGenerator(config, null, BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(60, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(60, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 12), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(60.0));
        road.setHorizontalAlignment(alignment);

        double liveYBefore = edge.getCenterlinePoints().getFirst().y;
        assertEquals(0.0, liveYBefore, 1e-6);

        RoadNetworkGenerator.NetworkGenerationResult result =
            networkGenerator.generateAll(network, terrain);

        assertEquals(0.0, edge.getCenterlinePoints().getFirst().y, 1e-6,
            "preview/generate must not write derived centerline back to live network");
        assertEquals(1, result.successEdgeCount());
    }
}
