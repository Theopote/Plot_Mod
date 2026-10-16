package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionConstructionIntegrityTest {

    @Test
    void tunnelAtNodeIgnoresDistantTunnelOnSameEdge() {
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
        List<PathSegment> segments = List.of(
            new PathSegment(centerline.get(0), centerline.get(1)),
            new PathSegment(centerline.get(1), centerline.get(2)),
            new PathSegment(centerline.get(2), centerline.get(3)));

        RoadGenerationResult result = new RoadGenerationResult(longEdge.getLength());
        result.constructionTypes.add(RoadConstructionType.ROAD);
        result.constructionTypes.add(RoadConstructionType.ROAD);
        result.constructionTypes.add(RoadConstructionType.TUNNEL);
        result.tunnelCount = 1;
        result.tunnelLength = 50;

        RoadSystemConfig config = new RoadSystemConfig("distant-tunnel");
        assertFalse(TunnelJunctionConnectivityPass.tunnelAtNode(
            network,
            junction,
            longEdge,
            result,
            config,
            centerline,
            List.of(segments.get(0)),
            1.0));
    }

    @Test
    void distantTunnelOnEdgeDoesNotTriggerConnectivityAtPlainCrossing() {
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
                result.tunnelCount = 1;
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
            new RoadSystemConfig("distant-tunnel-cross"),
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0,
            null,
            reports);

        long airPlacements = edgeResults.values().stream()
            .flatMap(r -> r.placementRecords.values().stream())
            .filter(r -> "minecraft:air".equals(r.newBlockId))
            .count();
        assertEquals(0, airPlacements,
            "plain crossing must not carve air when only a distant arm is tunnel");
        JunctionConstructionReport report = reports.get(junction.getId());
        assertTrue(report == null || !report.tunnelConnectivityApplied());
    }

    @Test
    void openVolumeDoesNotCarveProtectedRoadSurface() {
        RoadNetwork network = tunnelTJunctionNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        BlockPos protectedInCavity = new BlockPos(0, 66, 0);
        BlockPos lining = new BlockPos(0, 67, 0);
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            result.tunnelCount = 1;
            result.tunnelLength = edge.getLength();
            result.recordPlacementOverride(protectedInCavity, "minecraft:grass_block", "minecraft:black_concrete");
            result.placementLayers.put(protectedInCavity, RoadSolidLayer.ROAD);
            result.recordPlacementOverride(lining, "minecraft:stone", "minecraft:stone_bricks");
            result.placementLayers.put(lining, RoadSolidLayer.TUNNEL);
            result.placementMaterialPriorities.put(lining, RoadSolidMaterialPriority.STRUCTURAL);
            result.tunnelBlocks.add(lining);
            edgeResults.put(edge.getId(), result);
        }

        Map<String, JunctionConstructionReport> reports = new LinkedHashMap<>();
        TunnelJunctionConnectivityPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), 64),
            new RoadSystemConfig("protect-road"),
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0,
            null,
            reports);

        for (RoadGenerationResult result : edgeResults.values()) {
            BlockRecord road = result.placementRecords.get(protectedInCavity);
            assertTrue(road != null && "minecraft:black_concrete".equals(road.newBlockId),
                "protected road surface inside cavity must not become air");
        }
        JunctionConstructionReport report = reports.get(junction.getId());
        assertTrue(report != null && report.protectedStructureConflicts() > 0,
            "protected placements inside open volume must be counted");
    }

    @Test
    void junctionFillCoversEveryPolygonCellWithoutSurfaceGaps() {
        RoadSystemConfig config = new RoadSystemConfig("junction-integrity");
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeSlopeBatter(true);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        TerrainSampler terrain = flatTerrain(64);

        RoadNetwork network = skewCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        var blocks = junctionGenerator.generateJunction(junction, network, terrain, Map.of(junction.getId(), 68));
        JunctionConstructionReport report = blocks.report();
        assertEquals(0, report.missingSurfaceColumns(),
            "every polygon cell must receive a surface stamp");
        assertTrue(report.expectedSurfaceColumns() > 0);
        assertTrue(blocks.getSolids().primitives().stream()
            .anyMatch(p -> p.layer() == RoadSolidLayer.ROAD || p.layer() == RoadSolidLayer.SHOULDER));
    }

    @Test
    void sameYTunnelApproachesOpenCrossingLining() {
        RoadNetwork network = tunnelCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        BlockPos lining = new BlockPos(0, 66, 0);
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            result.tunnelCount = 1;
            result.tunnelLength = edge.getLength();
            result.recordPlacementOverride(lining, "minecraft:stone", "minecraft:stone_bricks");
            result.placementLayers.put(lining, RoadSolidLayer.TUNNEL);
            result.placementMaterialPriorities.put(lining, RoadSolidMaterialPriority.STRUCTURAL);
            result.tunnelBlocks.add(lining);
            edgeResults.put(edge.getId(), result);
        }

        Map<String, JunctionConstructionReport> reports = new LinkedHashMap<>();
        TunnelJunctionConnectivityPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), 64),
            new RoadSystemConfig("two-tunnel-same-y"),
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0,
            null,
            reports);

        long air = edgeResults.values().stream()
            .filter(r -> {
                BlockRecord record = r.placementRecords.get(lining);
                return record != null && "minecraft:air".equals(record.newBlockId);
            })
            .count();
        assertTrue(air > 0, "same-Y tunnel arms at a node must open crossing lining");
        JunctionConstructionReport report = reports.get(junction.getId());
        assertTrue(report != null && report.tunnelConnectivityApplied());
    }

    @Test
    void differentYTunnelLayersDoNotOpenEachOther() {
        RoadNetwork network = tunnelCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        BlockPos lowerLining = new BlockPos(0, 66, 0);
        BlockPos upperLining = new BlockPos(0, 78, 0);
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            result.tunnelCount = 1;
            if ("roadA".equals(edge.getRoadId())) {
                result.recordPlacementOverride(upperLining, "minecraft:stone", "minecraft:stone_bricks");
                result.placementLayers.put(upperLining, RoadSolidLayer.TUNNEL);
                result.tunnelBlocks.add(upperLining);
            } else {
                result.recordPlacementOverride(lowerLining, "minecraft:stone", "minecraft:stone_bricks");
                result.placementLayers.put(lowerLining, RoadSolidLayer.TUNNEL);
                result.tunnelBlocks.add(lowerLining);
            }
            edgeResults.put(edge.getId(), result);
        }

        // Lower layer junction elevation 64; upper approaches would need elevation 76.
        // Only lower arms share elevation 64 → connectivity applies only within that layer.
        Map<String, Integer> elevations = new LinkedHashMap<>();
        elevations.put(junction.getId(), 64);
        TunnelJunctionConnectivityPass.apply(
            network,
            edgeResults,
            elevations,
            new RoadSystemConfig("two-tunnel-diff-y"),
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0);

        for (var entry : edgeResults.entrySet()) {
            var edge = network.getEdge(entry.getKey());
            if (edge != null && "roadA".equals(edge.getRoadId())) {
                BlockRecord upper = entry.getValue().placementRecords.get(upperLining);
                assertTrue(upper != null && "minecraft:stone_bricks".equals(upper.newBlockId),
                    "upper-Y tunnel lining must not be opened by lower-Y connectivity");
            }
        }
    }

    @Test
    void gradeSeparatedCrossJunctionKeepsElevatedDeck() {
        // API grade-sep requires degree-4 simple crossing (two roads × two arms).
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
                result.tunnelBlocks.add(underpassLining);
            }
            edgeResults.put(edge.getId(), result);
        }

        TunnelJunctionConnectivityPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), 64),
            new RoadSystemConfig("grade-sep-cross"),
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0);

        for (RoadGenerationResult result : edgeResults.values()) {
            BlockRecord deck = result.placementRecords.get(elevatedDeck);
            if (deck != null) {
                assertEquals("minecraft:stone", deck.newBlockId);
            }
        }
    }

    @Test
    void mixedWidthCrossJunctionHasContinuousSurface() {
        RoadSystemConfig config = new RoadSystemConfig("mixed-width");
        config.setRoadWidth(5);
        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);

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

        var blocks = junctionGenerator.generateJunction(
            junction, network, flatTerrain(64), Map.of(junction.getId(), 68));
        assertEquals(0, blocks.report().missingSurfaceColumns());
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 64;
            }
        };
    }

    private static RoadNetwork crossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), road.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), road.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), road.getId());
        return network;
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
            List.of(junction.getPosition(), new Vec2d(50, 0), new Vec2d(100, 0), east.getPosition()), roadB.getId());
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

    private static RoadNetwork tunnelTJunctionNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("roadA");
        Road roadB = network.createRoad("roadB");
        Road roadC = network.createRoad("roadC");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), roadA.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), roadB.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), roadC.getId());
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
