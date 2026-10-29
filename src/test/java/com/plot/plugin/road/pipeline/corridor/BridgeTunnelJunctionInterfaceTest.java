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

/**
 * P2: junction interfaces with bridge decks and tunnel clearance must keep layer priority
 * and protected volumes coherent after plaza flush.
 */
class BridgeTunnelJunctionInterfaceTest {

    @Test
    void bridgeDeckNearGradeSeparatedJunctionSurvivesPlazaClearanceMerge() {
        RoadSystemConfig config = new RoadSystemConfig("bridge-junction");
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        RoadNetwork network = twoRoadCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        assertTrue(network.setNodeGradeSeparation(junction.getId(), true, "roadA", 5.0));

        int underpassY = 64;
        int deckY = underpassY + 5;
        Map<String, RoadGenerationResult> edgeResults = leftoverNeighborhood(network, deckY, RoadSolidLayer.BRIDGE);

        var blocks = junctionGenerator.generateJunction(
            junction, network, hillTerrain(underpassY, 78), Map.of(junction.getId(), underpassY), edgeResults);

        assertTrue(!blocks.validationManifest().expectedProtectedBlocks().isEmpty(),
            "bridge deck columns must be listed as protected");

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

        boolean deckSurvived = aggregate.placementRecords.entrySet().stream().anyMatch(entry ->
            entry.getKey().getY() == deckY
                && Math.abs(entry.getKey().getX()) <= 1
                && Math.abs(entry.getKey().getZ()) <= 1
                && !"minecraft:air".equals(entry.getValue().newBlockId)
                && aggregate.placementLayers.get(entry.getKey()) == RoadSolidLayer.BRIDGE);
        assertTrue(deckSurvived, "BRIDGE deck must survive underpass plaza AIR flush");

        boolean deckPunched = aggregate.placementRecords.entrySet().stream().anyMatch(entry ->
            entry.getKey().getY() == deckY
                && Math.abs(entry.getKey().getX()) <= 1
                && Math.abs(entry.getKey().getZ()) <= 1
                && "minecraft:air".equals(entry.getValue().newBlockId));
        assertTrue(!deckPunched, "plaza clearance must not replace BRIDGE deck cells with AIR");
    }

    @Test
    void tunnelTJunctionKeepsPlazaSurfaceAboveClearanceAir() {
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

        RoadSystemConfig config = new RoadSystemConfig("tunnel-plaza");
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.SMOOTH);
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()));
        RoadNetworkGenerator.PreviewResult preview = networkGenerator.generatePreview(network, mountain);

        int plazaY = preview.nodeElevations().get(junction.getId());
        var junctionBlocks = preview.networkResult().getJunctionResults().get(junction.getId());
        assertTrue(junctionBlocks != null && !junctionBlocks.isEmpty(), "tunnel T must still stamp plaza");

        long plazaSurface = 0;
        long plazaAirAtSurface = 0;
        for (var primitive : junctionBlocks.getSolids().primitives()) {
            if (Math.abs(primitive.planPoint().x) > 2.5 || Math.abs(primitive.planPoint().y) > 2.5) {
                continue;
            }
            if (primitive.elevation() != plazaY) {
                continue;
            }
            if (primitive.layer() == RoadSolidLayer.ROAD
                    || primitive.layer() == RoadSolidLayer.SIDEWALK
                    || primitive.layer() == RoadSolidLayer.SHOULDER) {
                plazaSurface++;
                if ("minecraft:air".equals(primitive.materialId())) {
                    plazaAirAtSurface++;
                }
            }
        }
        assertTrue(plazaSurface > 0, "plaza surface must exist at junctionY");
        assertEquals(0, plazaAirAtSurface, "plaza surface layer must not be carved to air");

        JunctionConstructionReport report =
            preview.networkResult().getJunctionReports().get(junction.getId());
        if (report != null) {
            assertEquals(0, report.crossLayerLeaks(),
                "tunnel/plaza merge must not report cross-layer leaks");
        }
    }

    @Test
    void valleyBridgeApproachIntoAtGradeTJunctionHasNoPostMergeDeckLeak() {
        RoadNetwork network = tJunctionNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        junction.setManualElevation(64.0);

        TerrainSampler valley = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                // Deep valley on the north approach encourages bridge classification.
                if (point.y > 8 && point.y < 36 && Math.abs(point.x) <= 6) {
                    return 40;
                }
                return 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };

        RoadSystemConfig config = new RoadSystemConfig("bridge-approach");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setMaxSlope(15.0f);
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()));
        RoadNetworkGenerator.PreviewResult preview = networkGenerator.generatePreview(network, valley);

        assertTrue(preview.networkResult().getJunctionResults().containsKey(junction.getId()));
        JunctionConstructionReport report =
            preview.networkResult().getJunctionReports().get(junction.getId());
        assertTrue(report != null);
        assertEquals(0, report.crossLayerLeaks(),
            "bridge approach into plaza must not leave protected-volume leaks");
        assertEquals(0, report.postMergeProtectedLosses(),
            "protected bridge/road cells must survive plaza merge");
    }

    private static Map<String, RoadGenerationResult> leftoverNeighborhood(
            RoadNetwork network, int y, RoadSolidLayer layer) {
        Map<String, RoadGenerationResult> results = new LinkedHashMap<>();
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    result.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
                    result.placementLayers.put(pos, layer);
                    result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.STRUCTURAL);
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

    private static RoadNetwork tJunctionNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road stem = network.createRoad("stem");
        Road bar = network.createRoad("bar");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 48));
        RoadNode east = network.createNode(new Vec2d(40, 0));
        RoadNode west = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), stem.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), bar.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(junction.getPosition(), west.getPosition()), bar.getId());
        return network;
    }

    private static RoadNetwork tunnelTJunctionNetwork() {
        RoadNetwork network = tJunctionNetwork();
        for (Road road : network.getRoads().values()) {
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
        return network;
    }
}
