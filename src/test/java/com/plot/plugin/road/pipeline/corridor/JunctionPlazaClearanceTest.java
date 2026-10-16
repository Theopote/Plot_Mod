package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.RoadGenerationResultAssembler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionPlazaClearanceTest {

    @Test
    void atGradePlazaSurfaceStaysOnConsensusElevation() {
        RoadSystemConfig config = new RoadSystemConfig("plaza-flat");
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        RoadNetwork network = crossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        var blocks = junctionGenerator.generateJunction(
            junction, network, hillTerrain(64, 80), Map.of(junction.getId(), 68));

        int minRoadY = Integer.MAX_VALUE;
        int maxRoadY = Integer.MIN_VALUE;
        for (RoadSolidPrimitive primitive : blocks.getSolids().primitives()) {
            if (primitive.layer() == RoadSolidLayer.ROAD) {
                minRoadY = Math.min(minRoadY, primitive.elevation());
                maxRoadY = Math.max(maxRoadY, primitive.elevation());
            }
        }
        assertEquals(68, minRoadY);
        assertEquals(68, maxRoadY);
    }

    @Test
    void atGradePlazaPunchesLeftoverEdgeRoadAndHill() {
        RoadSystemConfig config = new RoadSystemConfig("plaza-punch");
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        RoadNetwork network = crossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = leftoverNeighborhood(network, 72);
        var blocks = junctionGenerator.generateJunction(
            junction, network, hillTerrain(64, 70), Map.of(junction.getId(), 64), edgeResults);

        assertTrue(hasAirNearOrigin(blocks, 70), "hill above plaza must be punched");
        assertTrue(hasAirNearOrigin(blocks, 72), "leftover edge road above plaza must be punched");
        assertTrue(hasAirNearOrigin(blocks, 66), "clearance shaft above plaza surface");
    }

    @Test
    void gradeSeparatedPlazaGradesSubgradeBelowDeckClearance() {
        RoadSystemConfig config = new RoadSystemConfig("plaza-subgrade");
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        RoadNetwork network = twoRoadCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        assertTrue(network.setNodeGradeSeparation(junction.getId(), true, "roadA", 4.0));

        var blocks = junctionGenerator.generateJunction(
            junction, network, hillTerrain(61, 61), Map.of(junction.getId(), 64));

        assertTrue(
            blocks.getSolids().primitives().stream()
                .anyMatch(p -> p.layer() == com.plot.plugin.road.solid.RoadSolidLayer.SUBGRADE
                    && p.elevation() > 61 && p.elevation() <= 64
                    && Math.abs(p.planPoint().x) <= 2.5
                    && Math.abs(p.planPoint().y) <= 2.5),
            "underpass plaza must still receive subgrade fill below deck clearance");
    }

    @Test
    void gradeSeparatedPlazaStopsPunchBelowElevatedDeck() {
        RoadSystemConfig config = new RoadSystemConfig("plaza-deck");
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

        Integer deckY = RoadJunctionGenerator.resolveElevatedDeckY(junction, 64, config);
        assertEquals(68, deckY);
        for (int y = 65; y < deckY; y++) {
            assertTrue(hasAirNearOrigin(blocks, y),
                "underpass shaft missing air at y=" + y + " airYs=" + airElevationsNearOrigin(blocks));
        }
        assertTrue(
            blocks.getSolids().primitives().stream()
                .noneMatch(p -> p.elevation() >= deckY
                    && "minecraft:air".equals(p.materialId())
                    && Math.abs(p.planPoint().x) <= 2.5
                    && Math.abs(p.planPoint().y) <= 2.5),
            "elevated deck Y must not be punched by underpass plaza");
    }

    @Test
    void mergedJunctionAirOverridesLeftoverEdgeRoad() {
        RoadSystemConfig config = new RoadSystemConfig("plaza-merge");
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        RoadNetwork network = crossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = leftoverNeighborhood(network, 70);
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        for (RoadGenerationResult edgeResult : edgeResults.values()) {
            aggregate.mergeFrom(edgeResult);
        }

        var blocks = junctionGenerator.generateJunction(
            junction, network, hillTerrain(64, 64), Map.of(junction.getId(), 64), edgeResults);
        RoadGenerationResultAssembler.mergeJunction(
            aggregate,
            blocks,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance(),
            "minecraft:stone",
            "minecraft:stone",
            "minecraft:white_concrete");

        boolean airOverride = aggregate.placementRecords.entrySet().stream().anyMatch(entry ->
            entry.getKey().getY() == 70
                && Math.abs(entry.getKey().getX()) <= 2
                && Math.abs(entry.getKey().getZ()) <= 2
                && "minecraft:air".equals(entry.getValue().newBlockId));
        assertTrue(airOverride, "junction TUNNEL air must override leftover edge ROAD");
    }

    private static boolean hasAirNearOrigin(RoadJunctionGenerator.JunctionBlocks blocks, int y) {
        return blocks.getSolids().primitives().stream().anyMatch(primitive ->
            "minecraft:air".equals(primitive.materialId())
                && primitive.elevation() == y
                && Math.abs(primitive.planPoint().x) <= 2.5
                && Math.abs(primitive.planPoint().y) <= 2.5);
    }

    private static String airElevationsNearOrigin(RoadJunctionGenerator.JunctionBlocks blocks) {
        return blocks.getSolids().primitives().stream()
            .filter(primitive -> "minecraft:air".equals(primitive.materialId())
                && Math.abs(primitive.planPoint().x) <= 2.5
                && Math.abs(primitive.planPoint().y) <= 2.5)
            .map(primitive -> String.valueOf(primitive.elevation()))
            .distinct()
            .sorted()
            .reduce((a, b) -> a + "," + b)
            .orElse("none");
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
}
