package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-0 junction acceptance regressions: T/cross plaza plane, degree skip,
 * and elevated-deck protection through edge→junction merge.
 */
class RoadJunctionAcceptanceRegressionTest {

    @Test
    void tJunctionPlazaStaysOnConsensusElevation() {
        RoadSystemConfig config = new RoadSystemConfig("t-plaza");
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        RoadNetwork network = tNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        var blocks = junctionGenerator.generateJunction(
            junction, network, flatTerrain(64), Map.of(junction.getId(), 67));

        int minRoadY = Integer.MAX_VALUE;
        int maxRoadY = Integer.MIN_VALUE;
        int roadCount = 0;
        for (var primitive : blocks.getSolids().primitives()) {
            if (primitive.layer() != RoadSolidLayer.ROAD) {
                continue;
            }
            roadCount++;
            minRoadY = Math.min(minRoadY, primitive.elevation());
            maxRoadY = Math.max(maxRoadY, primitive.elevation());
        }
        assertTrue(roadCount > 0, "T junction must stamp plaza surface");
        assertEquals(67, minRoadY);
        assertEquals(67, maxRoadY);
    }

    @Test
    void degreeTwoNodeSkipsPlazaGeneration() {
        RoadSystemConfig config = new RoadSystemConfig("degree2");
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("through");
        RoadNode a = network.createNode(new Vec2d(-16, 0));
        RoadNode mid = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(16, 0));
        network.createEdge(a.getId(), mid.getId(),
            List.of(a.getPosition(), mid.getPosition()), road.getId());
        network.createEdge(mid.getId(), b.getId(),
            List.of(mid.getPosition(), b.getPosition()), road.getId());

        var blocks = junctionGenerator.generateJunction(
            mid, network, flatTerrain(64), Map.of(mid.getId(), 64));

        assertTrue(blocks.getSolids().isEmpty(),
            "degree <= 2 must skip plaza (self-loop unbroken or through-node)");
    }

    @Test
    void mergedFlushKeepsProtectedElevatedDeckFromJunctionAir() {
        RoadSystemConfig config = new RoadSystemConfig("deck-protect");
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        RoadNetwork network = twoRoadCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        assertTrue(network.setNodeGradeSeparation(junction.getId(), true, "roadA", 4.0));

        Map<String, RoadGenerationResult> edgeResults = leftoverNeighborhood(network, 68);
        var blocks = junctionGenerator.generateJunction(
            junction, network, hillTerrain(64, 80), Map.of(junction.getId(), 64), edgeResults);

        assertTrue(!blocks.validationManifest().expectedProtectedBlocks().isEmpty(),
            "grade-separated junction must list elevated deck columns as protected");

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        for (RoadGenerationResult edgeResult : edgeResults.values()) {
            aggregate.mergeFrom(edgeResult);
        }

        RoadGenerationResultAssembler.mergeJunction(
            aggregate,
            blocks,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance(),
            "minecraft:stone",
            "minecraft:stone",
            "minecraft:white_concrete");

        Integer deckY = RoadJunctionGenerator.resolveElevatedDeckY(junction, 64, config);
        assertEquals(68, deckY);
        boolean deckSurvived = aggregate.placementRecords.entrySet().stream().anyMatch(entry ->
            entry.getKey().getY() == 68
                && Math.abs(entry.getKey().getX()) <= 1
                && Math.abs(entry.getKey().getZ()) <= 1
                && "minecraft:stone".equals(entry.getValue().newBlockId));
        assertTrue(deckSurvived, "elevated deck ROAD must survive junction AIR flush");
        boolean deckPunched = aggregate.placementRecords.entrySet().stream().anyMatch(entry ->
            entry.getKey().getY() == 68
                && Math.abs(entry.getKey().getX()) <= 1
                && Math.abs(entry.getKey().getZ()) <= 1
                && "minecraft:air".equals(entry.getValue().newBlockId));
        assertTrue(!deckPunched, "elevated deck must not be replaced by junction clearance AIR");
    }

    @Test
    void crossJunctionPreviewHasSinglePlanePlaza() {
        RoadNetwork network = crossNetwork();
        RoadNetworkGenerator generator = new RoadNetworkGenerator(new RoadGenerator(
            new RoadSystemConfig("cross-preview"),
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        RoadNetworkGenerator.PreviewResult preview = generator.generatePreview(network, flatTerrain(64));

        assertTrue(!preview.networkResult().getJunctionResults().isEmpty());
        RoadNode plazaNode = preview.profileNetwork().getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        int plazaY = preview.nodeElevations().getOrDefault(plazaNode.getId(), 64);
        long surfaceAtPlaza = 0;
        long surfaceOffPlane = 0;
        for (var junction : preview.networkResult().getJunctionResults().values()) {
            for (var primitive : junction.getSolids().primitives()) {
                if (primitive.layer() != RoadSolidLayer.ROAD) {
                    continue;
                }
                if (primitive.elevation() == plazaY) {
                    surfaceAtPlaza++;
                } else {
                    surfaceOffPlane++;
                }
            }
        }
        assertTrue(surfaceAtPlaza > 0, "cross junction must stamp plaza");
        assertEquals(0, surfaceOffPlane, "cross plaza must share one elevation");
    }

    private static RoadNetwork tNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("stem");
        Road roadB = network.createRoad("crossbar");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 16));
        RoadNode east = network.createNode(new Vec2d(16, 0));
        RoadNode west = network.createNode(new Vec2d(-16, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), roadA.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), roadB.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(junction.getPosition(), west.getPosition()), roadB.getId());
        return network;
    }

    private static RoadNetwork crossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 16));
        RoadNode south = network.createNode(new Vec2d(0, -16));
        RoadNode east = network.createNode(new Vec2d(16, 0));
        RoadNode west = network.createNode(new Vec2d(-16, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), road.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), road.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), road.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(junction.getPosition(), west.getPosition()), road.getId());
        return network;
    }

    private static RoadNetwork twoRoadCrossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("roadA");
        Road roadB = network.createRoad("roadB");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 16));
        RoadNode south = network.createNode(new Vec2d(0, -16));
        RoadNode east = network.createNode(new Vec2d(16, 0));
        RoadNode west = network.createNode(new Vec2d(-16, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), roadA.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), roadA.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), roadB.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(junction.getPosition(), west.getPosition()), roadB.getId());
        return network;
    }

    private static Map<String, RoadGenerationResult> leftoverNeighborhood(RoadNetwork network, int y) {
        Map<String, RoadGenerationResult> results = new LinkedHashMap<>();
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    result.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
                    result.placementLayers.put(pos, RoadSolidLayer.ROAD);
                    result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.DEFAULT);
                }
            }
            results.put(edge.getId(), result);
        }
        return results;
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int x, int blockY, int z) {
                return blockY <= y;
            }
        };
    }

    private static TerrainSampler hillTerrain(int groundY, int hillY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                if (Math.abs(point.x) <= 2 && Math.abs(point.y) <= 2) {
                    return hillY;
                }
                return groundY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                if (Math.abs(x) <= 2 && Math.abs(z) <= 2) {
                    return y <= hillY;
                }
                return y <= groundY;
            }
        };
    }
}
