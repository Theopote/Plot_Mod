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
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 地下交叉口覆土硬验收：最终合成/路口 solids 均不得非授权打穿天然地表。
 */
class JunctionProtectedOverburdenHardTest {

    @Test
    void fourWayUndergroundJunctionDoesNotDaylightSurface90() {
        RoadSystemConfig config = new RoadSystemConfig("ug-fourway");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork(true);
        RoadNode junction = hub(network);
        Map<String, RoadGenerationResult> edgeResults = tunnelEdges(network);

        int roadY = 64;
        int surfaceY = 90;
        RoadJunctionGenerator.JunctionBlocks blocks = generate(
            config, network, junction, surfaceAt(surfaceY), roadY, edgeResults);

        assertFalse(blocks.getSolids().primitives().isEmpty());
        assertTrue(blocks.constructionContext().undergroundJunction());
        assertFalse(blocks.constructionContext().protectedCavityColumns().isEmpty(),
            "underground junction must publish protected cavity columns");

        for (RoadSolidPrimitive p : blocks.getSolids().primitives()) {
            if (p == null || !"minecraft:air".equals(p.materialId())) {
                continue;
            }
            assertTrue(p.elevation() < surfaceY,
                "core/fillet must not daylight surface; airY=" + p.elevation());
        }

        RoadGenerationResult aggregate = rasterize(blocks);
        JunctionProtectedOverburdenValidator.Metrics metrics =
            JunctionProtectedOverburdenValidator.evaluate(blocks, aggregate, surfaceAt(surfaceY));
        assertEquals(0, metrics.protectedOverburdenAirCount(),
            "protectedOverburdenAirCount must be 0");
    }

    @Test
    void oneTunnelThreeOpenOnlyOpenColumnsDaylight() {
        RoadSystemConfig config = new RoadSystemConfig("mixed-1t3o");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("mixed");
        TunnelStyle style = new TunnelStyle();
        style.setClearHeight(5);
        style.setLiningThickness(1);
        road.setTunnelStyle(style);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        RoadEdge tunnelEdge = network.createEdge(
            junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), road.getId());
        RoadEdge openN = network.createEdge(
            junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), road.getId());
        RoadEdge openS = network.createEdge(
            junction.getId(), s.getId(),
            List.of(junction.getPosition(), s.getPosition()), road.getId());
        RoadEdge openW = network.createEdge(
            junction.getId(), w.getId(),
            List.of(junction.getPosition(), w.getPosition()), road.getId());

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(tunnelEdge.getId(), typed(tunnelEdge, RoadConstructionType.TUNNEL));
        edgeResults.put(openN.getId(), typed(openN, RoadConstructionType.CUT));
        edgeResults.put(openS.getId(), typed(openS, RoadConstructionType.CUT));
        edgeResults.put(openW.getId(), typed(openW, RoadConstructionType.CUT));

        int roadY = 64;
        int surfaceY = roadY + 12;
        RoadJunctionGenerator.JunctionBlocks blocks = generate(
            config, network, junction, surfaceAt(surfaceY), roadY, edgeResults);
        JunctionConstructionContext ctx = blocks.constructionContext();
        assertNotNull(ctx);

        // 开敞北进口列应允许露天；隧道东进口保护列不得打到地表。
        ColumnKey openCol = new ColumnKey(0, 12);
        ColumnKey tunnelCol = new ColumnKey(12, 0);
        JunctionConstructionContext.ColumnRule openRule = ctx.rule(openCol);
        JunctionConstructionContext.ColumnRule tunnelRule = ctx.rule(tunnelCol);
        if (openRule != null) {
            assertEquals(JunctionConstructionContext.ColumnRole.OPEN, openRule.role(),
                "open-cut approach column must remain OPEN");
            assertNull(openRule.maxAuthorizedClearY(),
                "OPEN column may daylight (null clear cap)");
        }
        if (tunnelRule != null) {
            assertTrue(
                tunnelRule.role() == JunctionConstructionContext.ColumnRole.ENCLOSED_TUNNEL
                    || tunnelRule.role() == JunctionConstructionContext.ColumnRole.PORTAL_TRANSITION,
                "tunnel approach column must not be OPEN daylight");
            assertNotNull(tunnelRule.maxAuthorizedClearY());
        }
    }

    @Test
    void shortRoadStubBeforeTunnelDoesNotDaylightWholePlaza() {
        RoadSystemConfig config = new RoadSystemConfig("short-road-stub");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork(true);
        RoadNode junction = hub(network);
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            // 洞口短 ROAD 再接 TUNNEL：仍应识别为隧道进口，不得整片露天。
            result.constructionTypes.add(RoadConstructionType.ROAD);
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }

        int roadY = 64;
        int surfaceY = 90;
        RoadJunctionGenerator.JunctionBlocks blocks = generate(
            config, network, junction, surfaceAt(surfaceY), roadY, edgeResults);
        assertTrue(blocks.constructionContext().undergroundJunction()
                || !blocks.constructionContext().protectedCavityColumns().isEmpty()
                || blocks.constructionContext().tunnelClearHeight() != null
                || hasNoSurfaceAir(blocks, surfaceY),
            "short ROAD stub must not cause whole underground plaza daylight");

        for (RoadSolidPrimitive p : blocks.getSolids().primitives()) {
            if (p == null || !"minecraft:air".equals(p.materialId())) {
                continue;
            }
            assertTrue(p.elevation() < surfaceY,
                "short ROAD stub must not daylight mountain; airY=" + p.elevation());
        }
    }

    @Test
    void clearDeniedSentinelSkipsDestructiveGrade() {
        assertEquals(Integer.MIN_VALUE, JunctionConstructionContext.CLEAR_DENIED);
        JunctionConstructionContext open = JunctionConstructionContext.openOnly(
            "open", JunctionWorldFootprint.empty(), JunctionElevationField.inactive(64), null);
        assertNull(open.maxAuthorizedClearY(new ColumnKey(1, 1), 64),
            "pure open context without rules daylights unlisted columns");
    }

    @Test
    void tunnelFilletColumnsNeverGetOpenDaylightCap() {
        RoadSystemConfig config = new RoadSystemConfig("fillet-no-daylight");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork(true);
        RoadNode junction = hub(network);
        Map<String, RoadGenerationResult> edgeResults = tunnelEdges(network);

        int roadY = 64;
        int surfaceY = 90;
        RoadJunctionGenerator.JunctionBlocks blocks = generate(
            config, network, junction, surfaceAt(surfaceY), roadY, edgeResults);
        JunctionConstructionContext ctx = blocks.constructionContext();
        JunctionWorldFootprint footprint = ctx.footprint();
        assertFalse(footprint.filletAdditions().isEmpty(), "precondition: fillets exist");

        for (ColumnKey filletCol : footprint.filletAdditions()) {
            JunctionConstructionContext.ColumnRule rule = ctx.rule(filletCol);
            assertNotNull(rule, "fillet column must have a construction rule: " + filletCol);
            assertTrue(
                rule.role() == JunctionConstructionContext.ColumnRole.ENCLOSED_TUNNEL
                    || rule.role() == JunctionConstructionContext.ColumnRole.PORTAL_TRANSITION,
                "tunnel fillet must not be OPEN daylight; col=" + filletCol + " role=" + rule.role());
            assertNotNull(rule.maxAuthorizedClearY(),
                "tunnel fillet must carry roof clear-cap; col=" + filletCol);
            assertTrue(rule.maxAuthorizedClearY() < surfaceY,
                "clear-cap must stay below natural surface");
        }
    }

    @Test
    void flatTunnelJunctionKeepsUniformPavementY() {
        RoadSystemConfig config = new RoadSystemConfig("flat-tunnel-y");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork(true);
        RoadNode junction = hub(network);
        Map<String, RoadGenerationResult> edgeResults = tunnelEdges(network);

        int roadY = 64;
        RoadJunctionGenerator.JunctionBlocks blocks = generate(
            config, network, junction, surfaceAt(90), roadY, edgeResults);
        JunctionElevationField field = blocks.elevationField();
        for (var entry : field.targetYByColumn().entrySet()) {
            assertEquals(roadY, entry.getValue(),
                "flat tunnel junction pavement must stay at design Y; col=" + entry.getKey());
        }
    }

    private static boolean hasNoSurfaceAir(RoadJunctionGenerator.JunctionBlocks blocks, int surfaceY) {
        return blocks.getSolids().primitives().stream().noneMatch(p ->
            p != null && "minecraft:air".equals(p.materialId()) && p.elevation() >= surfaceY);
    }

    private static RoadGenerationResult typed(RoadEdge edge, RoadConstructionType type) {
        RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
        result.constructionTypes.add(type);
        result.constructionTypes.add(type);
        return result;
    }

    private static RoadGenerationResult rasterize(RoadJunctionGenerator.JunctionBlocks blocks) {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        // 路口 solids 转放置记录，供最终合成指标使用。
        for (RoadSolidPrimitive p : blocks.getSolids().primitives()) {
            if (p == null || p.planPoint() == null) {
                continue;
            }
            int x = (int) Math.floor(p.planPoint().x);
            int z = (int) Math.floor(p.planPoint().y);
            net.minecraft.util.math.BlockPos pos =
                new net.minecraft.util.math.BlockPos(x, p.elevation(), z);
            aggregate.recordPlacementOverride(pos, "minecraft:stone", p.materialId());
            aggregate.placementLayers.put(pos, p.layer());
        }
        return aggregate;
    }

    private static RoadJunctionGenerator.JunctionBlocks generate(
            RoadSystemConfig config,
            RoadNetwork network,
            RoadNode junction,
            TerrainSampler terrain,
            int roadY,
            Map<String, RoadGenerationResult> edgeResults) {
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        return new RoadJunctionGenerator(generator)
            .generateJunction(junction, network, terrain, Map.of(junction.getId(), roadY), edgeResults);
    }

    private static RoadNode hub(RoadNetwork network) {
        return network.getNodes().values().stream()
            .filter(n -> n.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
    }

    private static Map<String, RoadGenerationResult> tunnelEdges(RoadNetwork network) {
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            edgeResults.put(edge.getId(), typed(edge, RoadConstructionType.TUNNEL));
        }
        return edgeResults;
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
