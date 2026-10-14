package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelJunctionConnectivityPassTest {

    @Test
    void replacesCrossingTunnelLiningWithAirAtAtGradeJunction() {
        RoadNetwork network = tunnelTJunctionNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }

        BlockPos blockingLining = new BlockPos(0, 66, 0);
        for (RoadGenerationResult result : edgeResults.values()) {
            result.recordPlacementOverride(blockingLining, "minecraft:stone", "minecraft:stone_bricks");
            result.placementLayers.put(blockingLining, RoadSolidLayer.TUNNEL);
            result.placementMaterialPriorities.put(blockingLining, RoadSolidMaterialPriority.STRUCTURAL);
            result.tunnelBlocks.add(blockingLining);
        }

        RoadSystemConfig config = new RoadSystemConfig("tunnel-junction");
        TunnelJunctionConnectivityPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), 64),
            config,
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0);

        for (RoadGenerationResult result : edgeResults.values()) {
            BlockRecord record = result.placementRecords.get(blockingLining);
            assertTrue(record != null && "minecraft:air".equals(record.newBlockId),
                "crossing tunnel lining must become air");
        }
    }

    @Test
    void generatedTunnelCrossingKeepsCenterlinePassable() {
        RoadNetwork network = tunnelTJunctionNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        TerrainSampler mountain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 72;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 72;
            }
        };

        RoadSystemConfig config = new RoadSystemConfig("tunnel-junction-e2e");
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.SMOOTH);
        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);

        RoadNetworkGenerator.PreviewResult preview = networkGenerator.generatePreview(network, mountain);

        int junctionX = (int) Math.round(junction.getPosition().x);
        int junctionZ = (int) Math.round(junction.getPosition().y);
        int junctionY = preview.nodeElevations().get(junction.getId());

        long tunnelLiningAtCrossing = preview.aggregate().placementRecords.entrySet().stream()
            .filter(entry -> entry.getKey().getX() == junctionX
                && entry.getKey().getZ() == junctionZ
                && entry.getKey().getY() > junctionY)
            .filter(entry -> preview.aggregate().placementLayers.get(entry.getKey()) == RoadSolidLayer.TUNNEL)
            .filter(entry -> entry.getValue().newBlockId != null
                && !"minecraft:air".equals(entry.getValue().newBlockId))
            .count();
        assertEquals(0, tunnelLiningAtCrossing, "junction center column must not retain tunnel lining");

        long airAtCrossing = preview.aggregate().placementRecords.entrySet().stream()
            .filter(entry -> entry.getKey().getX() == junctionX
                && entry.getKey().getZ() == junctionZ
                && entry.getKey().getY() > junctionY)
            .filter(entry -> "minecraft:air".equals(entry.getValue().newBlockId))
            .count();
        assertTrue(airAtCrossing > 0, "junction center must contain carved tunnel air");
    }

    @Test
    void skipsGradeSeparatedJunction() {
        RoadNetwork network = tunnelCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        assertTrue(network.setNodeGradeSeparation(junction.getId(), true, "roadA", 3.0));

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        BlockPos lining = new BlockPos(0, 66, 0);
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            result.recordPlacementOverride(lining, "minecraft:stone", "minecraft:stone_bricks");
            result.placementLayers.put(lining, RoadSolidLayer.TUNNEL);
            result.placementMaterialPriorities.put(lining, RoadSolidMaterialPriority.STRUCTURAL);
            edgeResults.put(edge.getId(), result);
        }

        TunnelJunctionConnectivityPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), 64),
            new RoadSystemConfig("tunnel-junction"),
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0);

        assertTrue(edgeResults.values().stream()
            .allMatch(result -> "minecraft:stone_bricks".equals(
                result.placementRecords.get(lining).newBlockId)),
            "grade-separated junction must not open tunnel connectivity");
    }

    private static RoadNetwork tunnelTJunctionNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("roadA");
        Road roadB = network.createRoad("roadB");
        Road roadC = network.createRoad("roadC");
        applyTunnelStyle(roadA);
        applyTunnelStyle(roadB);
        applyTunnelStyle(roadC);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        RoadNode north = network.createNode(new Vec2d(0, 40));
        north.setManualElevation(64.0);
        RoadNode east = network.createNode(new Vec2d(80, 0));
        east.setManualElevation(64.0);
        RoadNode south = network.createNode(new Vec2d(0, -40));
        south.setManualElevation(64.0);

        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), roadA.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), roadB.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), roadC.getId());
        return network;
    }

    private static RoadNetwork tunnelCrossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("roadA");
        Road roadB = network.createRoad("roadB");
        applyTunnelStyle(roadA);
        applyTunnelStyle(roadB);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        RoadNode north = network.createNode(new Vec2d(0, 40));
        north.setManualElevation(64.0);
        RoadNode south = network.createNode(new Vec2d(0, -40));
        south.setManualElevation(64.0);
        RoadNode east = network.createNode(new Vec2d(80, 0));
        east.setManualElevation(64.0);
        RoadNode west = network.createNode(new Vec2d(-80, 0));
        west.setManualElevation(64.0);

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

    private static void applyTunnelStyle(Road road) {
        TunnelStyle style = new TunnelStyle();
        style.setShape(TunnelShape.RECTANGULAR);
        style.setClearHeight(5);
        style.setSideClearance(1);
        style.setLiningThickness(1);
        style.setLiningMaterial("minecraft:stone_bricks");
        style.setLightingMode(TunnelLightingMode.NONE);
        style.ensureConfigDefaults();
        road.setTunnelStyle(style);
    }
}
