package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.test.world.OrthographicProjectionTestFixtures;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionFinalVoxelIntegrityTest {

    @Test
    void orthographicDistantTunnelBlocksDoNotFalselyQualifyNodeApproach() {
        RoadNetwork network = crossNetworkWithLongArm();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        RoadEdge longEdge = network.getEdges().values().stream()
            .filter(edge -> edge.getLength() > 100)
            .findFirst()
            .orElseThrow();

        List<Vec2d> centerline = List.of(
            junction.getPosition(),
            new Vec2d(50, 0),
            new Vec2d(100, 0),
            new Vec2d(150, 0));
        List<PathSegment> approach = List.of(new PathSegment(centerline.get(0), centerline.get(1)));

        RoadGenerationResult result = new RoadGenerationResult(longEdge.getLength());
        result.constructionTypes.add(RoadConstructionType.ROAD);
        result.constructionTypes.add(RoadConstructionType.ROAD);
        result.constructionTypes.add(RoadConstructionType.TUNNEL);
        // World X/Z equal canvas node coords — old canvas/world mix would falsely match.
        result.tunnelBlocks.add(new BlockPos(0, 66, 0));
        result.tunnelBlocks.add(new BlockPos(1, 66, 0));

        assertFalse(TunnelJunctionConnectivityPass.tunnelAtNode(
            network,
            junction,
            longEdge,
            result,
            new RoadSystemConfig("ortho-distant"),
            centerline,
            approach,
            1.0));
    }

    @Test
    void orthographicProjectionDoesNotTriggerConnectivityFromDistantTunnelBlocks() {
        RoadNetwork network = crossNetworkWithLongArm();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            if (edge.getLength() > 100) {
                result.constructionTypes.add(RoadConstructionType.ROAD);
                result.constructionTypes.add(RoadConstructionType.ROAD);
                result.constructionTypes.add(RoadConstructionType.TUNNEL);
                result.tunnelBlocks.add(new BlockPos(0, 66, 0));
            } else {
                result.constructionTypes.add(RoadConstructionType.ROAD);
            }
            edgeResults.put(edge.getId(), result);
        }

        Map<String, JunctionConstructionReport> reports = new LinkedHashMap<>();
        TunnelJunctionConnectivityPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), 64),
            new RoadSystemConfig("ortho-distant-cross"),
            OrthographicProjectionTestFixtures.liveOrthographic(),
            (path, segments) -> 1.0,
            null,
            reports);

        long airPlacements = edgeResults.values().stream()
            .flatMap(r -> r.placementRecords.values().stream())
            .filter(r -> "minecraft:air".equals(r.newBlockId))
            .count();
        assertEquals(0, airPlacements);
        JunctionConstructionReport report = reports.get(junction.getId());
        assertTrue(report == null || !report.tunnelConnectivityApplied());
    }

    @Test
    void atGradeMixedWidthSidewalkShoulderCrossHasContinuousSurface() {
        RoadSystemConfig config = new RoadSystemConfig("mixed-surface");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(1);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);

        RoadNetwork network = mixedWidthCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        var blocks = junctionGenerator.generateJunction(
            junction, network, flatTerrain(64), Map.of(junction.getId(), 68));
        JunctionConstructionReport report = blocks.report();
        assertNotNull(report);
        assertEquals(0, report.missingSurfaceColumns());
        assertTrue(report.expectedSurfaceColumns() > 0);
        assertTrue(blocks.getSolids().primitives().stream()
            .anyMatch(p -> p.layer() == RoadSolidLayer.ROAD));
        assertTrue(blocks.getSolids().primitives().stream()
            .anyMatch(p -> p.layer() == RoadSolidLayer.SIDEWALK || p.layer() == RoadSolidLayer.SHOULDER));
    }

    @Test
    void skewCrossJunctionHasNoSurfaceGaps() {
        RoadSystemConfig config = new RoadSystemConfig("skew-surface");
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);

        RoadNetwork network = skewCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        var blocks = junctionGenerator.generateJunction(
            junction, network, flatTerrain(64), Map.of(junction.getId(), 68));
        assertEquals(0, blocks.report().missingSurfaceColumns());
    }

    @Test
    void gradeSeparatedBridgeOverTunnelKeepsDeckAndOpensUnderpass() {
        RoadNetwork network = tunnelCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        assertTrue(network.setNodeGradeSeparation(junction.getId(), true, "roadA", 8.0));

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        BlockPos underpassLining = new BlockPos(0, 66, 0);
        BlockPos elevatedDeck = new BlockPos(0, 72, 0);
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            if ("roadA".equals(edge.getRoadId())) {
                result.constructionTypes.add(RoadConstructionType.BRIDGE);
                result.recordPlacementOverride(elevatedDeck, "minecraft:air", "minecraft:stone");
                result.placementLayers.put(elevatedDeck, RoadSolidLayer.BRIDGE);
            } else {
                result.constructionTypes.add(RoadConstructionType.TUNNEL);
                result.tunnelCount = 1;
                result.recordPlacementOverride(underpassLining, "minecraft:stone", "minecraft:stone_bricks");
                result.placementLayers.put(underpassLining, RoadSolidLayer.TUNNEL);
                result.placementMaterialPriorities.put(underpassLining, RoadSolidMaterialPriority.STRUCTURAL);
                result.tunnelBlocks.add(underpassLining);
            }
            edgeResults.put(edge.getId(), result);
        }

        Map<String, JunctionConstructionReport> reports = new LinkedHashMap<>();
        TunnelJunctionConnectivityPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), 64),
            new RoadSystemConfig("bridge-over-tunnel"),
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0,
            null,
            reports);

        for (RoadGenerationResult result : edgeResults.values()) {
            BlockRecord deck = result.placementRecords.get(elevatedDeck);
            if (deck != null) {
                assertEquals("minecraft:stone", deck.newBlockId, "elevated deck must remain");
            }
        }
        long underpassAir = edgeResults.entrySet().stream()
            .filter(entry -> {
                var edge = network.getEdge(entry.getKey());
                return edge != null && "roadB".equals(edge.getRoadId());
            })
            .map(Map.Entry::getValue)
            .filter(result -> {
                BlockRecord record = result.placementRecords.get(underpassLining);
                return record != null && "minecraft:air".equals(record.newBlockId);
            })
            .count();
        assertTrue(underpassAir > 0, "underpass lining must open");
        JunctionConstructionReport report = reports.get(junction.getId());
        assertTrue(report != null && report.tunnelConnectivityApplied());
    }

    @Test
    void previewSessionMergesJunctionReportsIntoNetworkResult() {
        RoadSystemConfig config = new RoadSystemConfig("session-reports");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);

        RoadNetwork network = tunnelCrossNetwork();
        RoadNetworkGenerator.PreviewResult preview =
            networkGenerator.generatePreview(network, flatTerrain(72));

        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        Map<String, JunctionConstructionReport> reports =
            preview.networkResult().getJunctionReports();
        assertFalse(reports.isEmpty(), "session must retain junction reports");
        JunctionConstructionReport report = reports.get(junction.getId());
        assertNotNull(report, "junction node must have a merged report");
    }

    @Test
    void tunnelAirFloodFillDetectsDisconnectedSeeds() {
        java.util.Set<BlockPos> open = java.util.Set.of(
            new BlockPos(0, 64, 0),
            new BlockPos(1, 64, 0),
            new BlockPos(10, 64, 0),
            new BlockPos(11, 64, 0));

        TunnelJunctionAirConnectivityValidator.Result result =
            TunnelJunctionAirConnectivityValidator.validate(
                open,
                List.of(new BlockPos(0, 64, 0), new BlockPos(10, 64, 0)));
        assertEquals(1, result.disconnectedComponentCount());
        assertFalse(result.allSeedsConnected());
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int x, int yCoord, int z) {
                return yCoord <= y;
            }
        };
    }

    private static RoadNetwork crossNetworkWithLongArm() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("roadA");
        Road roadB = network.createRoad("roadB");
        Road roadC = network.createRoad("roadC");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode east = network.createNode(new Vec2d(150, 0));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), roadA.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), new Vec2d(50, 0), new Vec2d(100, 0), east.getPosition()),
            roadB.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), roadC.getId());
        return network;
    }

    private static RoadNetwork tunnelCrossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("roadA");
        Road roadB = network.createRoad("roadB");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        RoadNode west = network.createNode(new Vec2d(-80, 0));
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

    private static RoadNetwork mixedWidthCrossNetwork() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 16));
        RoadNode east = network.createNode(new Vec2d(16, 0));
        RoadNode west = network.createNode(new Vec2d(-16, 0));
        Road wide = network.createRoad("wide");
        Road narrow = network.createRoad("narrow");
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), wide.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), narrow.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(junction.getPosition(), west.getPosition()), narrow.getId());
        return network;
    }

    private static RoadNetwork skewCrossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("skew");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(-6, 14));
        RoadNode east = network.createNode(new Vec2d(14, 4));
        RoadNode west = network.createNode(new Vec2d(-14, -4));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), road.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), road.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(junction.getPosition(), west.getPosition()), road.getId());
        return network;
    }
}
