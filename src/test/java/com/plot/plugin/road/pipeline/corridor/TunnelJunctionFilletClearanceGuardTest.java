package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
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
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道路口圆角：不得把覆土清到自然地表；开敞平交仍须清干净。
 */
class TunnelJunctionFilletClearanceGuardTest {

    @Test
    void tunnelJunctionDoesNotDaylightMountainOverFillet() {
        RoadSystemConfig config = new RoadSystemConfig("tunnel-fillet-guard");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork(true);
        RoadNode junction = network.getNodes().values().stream()
            .filter(n -> n.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }

        int roadY = 64;
        int surfaceY = 90;
        TerrainSampler mountain = surfaceAt(surfaceY);
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator.JunctionBlocks blocks = new RoadJunctionGenerator(generator)
            .generateJunction(junction, network, mountain, Map.of(junction.getId(), roadY), edgeResults);

        assertFalse(blocks.getSolids().primitives().isEmpty(), "tunnel junction must emit solids");
        for (RoadSolidPrimitive p : blocks.getSolids().primitives()) {
            if (p == null || !"minecraft:air".equals(p.materialId())) {
                continue;
            }
            assertTrue(p.elevation() < surfaceY,
                "tunnel fillet/plaza must not daylight natural surface; airY=" + p.elevation());
        }
    }

    @Test
    void tunnelAtNodeDetectsTunnelAfterShortRoadApproachSegment() {
        RoadNetwork network = crossNetwork(true);
        RoadNode junction = network.getNodes().values().stream()
            .filter(n -> n.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        RoadEdge edge = network.getEdges().values().iterator().next();
        List<Vec2d> centerline = List.of(
            junction.getPosition(),
            new Vec2d(8, 0),
            new Vec2d(40, 0));
        List<PathSegment> approach = List.of(
            new PathSegment(centerline.get(0), centerline.get(1)),
            new PathSegment(centerline.get(1), centerline.get(2)));

        RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
        result.constructionTypes.add(RoadConstructionType.ROAD);
        result.constructionTypes.add(RoadConstructionType.TUNNEL);

        assertTrue(TunnelJunctionConnectivityPass.tunnelAtNode(
            network,
            junction,
            edge,
            result,
            new RoadSystemConfig("tunnel-window"),
            centerline,
            approach,
            1.0),
            "short ROAD stub before TUNNEL within expanded window must still count as tunnel approach");
    }

    @Test
    void openJunctionStillClearsThroughNaturalSurface() {
        RoadSystemConfig config = new RoadSystemConfig("open-fillet-clear");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork(false);
        RoadNode junction = network.getNodes().values().stream()
            .filter(n -> n.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.ROAD);
            edgeResults.put(edge.getId(), result);
        }

        int roadY = 64;
        int surfaceY = 70;
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator.JunctionBlocks blocks = new RoadJunctionGenerator(generator)
            .generateJunction(
                junction, network, surfaceAt(surfaceY), Map.of(junction.getId(), roadY), edgeResults);

        boolean clearedSurface = blocks.getSolids().primitives().stream().anyMatch(p ->
            p != null
                && "minecraft:air".equals(p.materialId())
                && p.elevation() == surfaceY);
        assertTrue(clearedSurface, "open fillet/plaza must clear engineering surface block");
    }

    @Test
    void hillsidePlazaClearsEvenWhenApproachTypesClaimTunnelButCoverIsShallow() {
        // 分类器写了 TUNNEL，但覆土未封闭：仍按露天清干净，不能整列停挖留残土。
        RoadSystemConfig config = new RoadSystemConfig("hillside-false-tunnel");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork(true);
        RoadNode junction = network.getNodes().values().stream()
            .filter(n -> n.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }

        int roadY = 64;
        int surfaceY = 67; // 浅覆，未达封闭门槛
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator.JunctionBlocks blocks = new RoadJunctionGenerator(generator)
            .generateJunction(
                junction, network, surfaceAt(surfaceY), Map.of(junction.getId(), roadY), edgeResults);

        assertTrue(blocks.getSolids().primitives().stream().anyMatch(p ->
                p != null
                    && "minecraft:air".equals(p.materialId())
                    && p.elevation() == surfaceY),
            "non-enclosed hillside plaza/fillet must clear surface dirt");
    }

    private static RoadNetwork crossNetwork(boolean tunnelStyle) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("cross");
        if (tunnelStyle) {
            TunnelStyle style = new TunnelStyle();
            style.setClearHeight(5);
            style.setLiningThickness(1);
            road.setTunnelStyle(style);
        }
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), n.getId(), List.of(junction.getPosition(), n.getPosition()), road.getId());
        network.createEdge(junction.getId(), s.getId(), List.of(junction.getPosition(), s.getPosition()), road.getId());
        network.createEdge(junction.getId(), e.getId(), List.of(junction.getPosition(), e.getPosition()), road.getId());
        network.createEdge(junction.getId(), w.getId(), List.of(junction.getPosition(), w.getPosition()), road.getId());
        return network;
    }

    private static TerrainSampler surfaceAt(int surfaceY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return surfaceY;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return surfaceY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y < surfaceY;
            }

            @Override
            public boolean isChunkLoaded(int x, int z) {
                return true;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }

            @Override
            public boolean isRoadClearableDecoration(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                return false;
            }
        };
    }
}
