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
import com.plot.plugin.road.solid.RoadSolidModel;
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
    void missingLiningDoesNotCountNetworkWideExpectations() {
        RoadSystemConfig config = new RoadSystemConfig("lining-scope");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");
        RoadNetwork network = crossNetwork(true);
        RoadNode junction = hub(network);
        Map<String, RoadGenerationResult> edgeResults = tunnelEdges(network);
        RoadJunctionGenerator.JunctionBlocks blocks = generate(
            config, network, junction, surfaceAt(90), 64, edgeResults);

        RoadGenerationResult aggregate = rasterize(blocks);
        // 全网远处衬砌期望缺失，不得计入本路口 missingLining。
        for (int i = 0; i < 100; i++) {
            net.minecraft.util.math.BlockPos far =
                new net.minecraft.util.math.BlockPos(1000 + i, 70, 1000);
            aggregate.expectedTunnelLining.add(far);
        }
        int scoped = JunctionProtectedOverburdenValidator.countMissingLiningInJunctionScope(
            blocks, aggregate);
        assertEquals(0, scoped, "far-away expectedTunnelLining must not inflate junction missingLining");

        JunctionProtectedOverburdenValidator.Metrics metrics =
            JunctionProtectedOverburdenValidator.evaluate(blocks, aggregate, surfaceAt(90));
        assertEquals(0, metrics.missingAuthorizedLiningCount(),
            "evaluate must scope missing lining to this junction");
    }

    @Test
    void stripUnauthorizedOverburdenAirRemovesAggregatePunches() {
        RoadSystemConfig config = new RoadSystemConfig("strip-overburden");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");
        RoadNetwork network = crossNetwork(true);
        RoadNode junction = hub(network);
        Map<String, RoadGenerationResult> edgeResults = tunnelEdges(network);
        int surfaceY = 90;
        RoadJunctionGenerator.JunctionBlocks blocks = generate(
            config, network, junction, surfaceAt(surfaceY), 64, edgeResults);
        RoadGenerationResult aggregate = rasterize(blocks);

        ColumnKey column = null;
        Integer roofY = null;
        for (ColumnKey candidate : blocks.constructionContext().protectedCavityColumns()) {
            JunctionConstructionContext.ColumnRule rule = blocks.constructionContext().rule(candidate);
            if (rule != null
                    && rule.role() == JunctionConstructionContext.ColumnRole.ENCLOSED_TUNNEL
                    && rule.cavityRoofY() != null) {
                column = candidate;
                roofY = rule.cavityRoofY();
                break;
            }
        }
        assertNotNull(column, "need an enclosed protected column");
        assertNotNull(roofY);
        net.minecraft.util.math.BlockPos punch = new net.minecraft.util.math.BlockPos(
            column.worldX(), roofY + 2, column.worldZ());
        aggregate.placementRecords.put(
            punch,
            new com.plot.core.command.BlockRecord(punch, "minecraft:stone", "minecraft:air"));
        aggregate.placementLayers.put(punch, com.plot.plugin.road.solid.RoadSolidLayer.TUNNEL);

        assertTrue(JunctionProtectedOverburdenValidator.evaluate(
            blocks, aggregate, surfaceAt(surfaceY)).protectedOverburdenAirCount() > 0);

        int stripped = JunctionProtectedOverburdenValidator.stripUnauthorizedOverburdenAir(
            blocks, aggregate);
        assertEquals(1, stripped);
        assertNull(aggregate.placementRecords.get(punch));
        assertEquals(0, JunctionProtectedOverburdenValidator.evaluate(
            blocks, aggregate, surfaceAt(surfaceY)).protectedOverburdenAirCount());
    }

    @Test
    void missingLiningIgnoresAuthorizedCavityAir() {
        RoadSystemConfig config = new RoadSystemConfig("lining-cavity");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");
        RoadNetwork network = crossNetwork(true);
        RoadNode junction = hub(network);
        Map<String, RoadGenerationResult> edgeResults = tunnelEdges(network);
        RoadJunctionGenerator.JunctionBlocks blocks = generate(
            config, network, junction, surfaceAt(90), 64, edgeResults);
        RoadGenerationResult aggregate = rasterize(blocks);

        // 保护列内：走廊衬砌期望 + 连通清障洞腔空气，不得计 missingLining。
        ColumnKey column = blocks.constructionContext().protectedCavityColumns().iterator().next();
        net.minecraft.util.math.BlockPos opened = new net.minecraft.util.math.BlockPos(
            column.worldX(), 70, column.worldZ());
        aggregate.expectedTunnelLining.add(opened);
        aggregate.expectedTunnelCavity.add(opened);
        aggregate.placementRecords.put(
            opened,
            new com.plot.core.command.BlockRecord(opened, "minecraft:stone", "minecraft:air"));

        assertEquals(0, JunctionProtectedOverburdenValidator.countMissingLiningInJunctionScope(
            blocks, aggregate),
            "authorized cavity air must not count as missing lining");
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
            // 全链路：resolveColumnClearCap 不得因 tunnelEntryPlaza 覆盖 OPEN/null。
            Integer resolved = RoadJunctionGenerator.resolveColumnClearCap(
                ctx, surfaceAt(surfaceY), new Vec2d(0.5, 12.5), openCol, roadY,
                List.copyOf(network.getEdges().values()), edgeResults,
                com.plot.plugin.road.alignment.RoadJunctionCenterlineResolver.forNetwork(network),
                ctx.tunnelClearHeight(), null, null, 1, 8);
            assertNull(resolved, "resolveColumnClearCap must honor OPEN daylight null");
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
    void tunnelEntryPlazaDetectionIsDirectionInvariant() {
        RoadEdge forward = new RoadEdge(
            "e-fwd", "n-hub", "n-out",
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)), "r1", null);
        RoadEdge reverse = new RoadEdge(
            "e-rev", "n-out", "n-hub",
            List.of(new Vec2d(40, 0), new Vec2d(0, 0)), "r1", null);

        // 进口靠近 hub：TUNNEL→ROAD 或反向绘制 ROAD→TUNNEL（末端为 TUNNEL）
        RoadGenerationResult fromStart = new RoadGenerationResult(40);
        fromStart.constructionTypes.add(RoadConstructionType.TUNNEL);
        fromStart.constructionTypes.add(RoadConstructionType.ROAD);

        RoadGenerationResult fromEnd = new RoadGenerationResult(40);
        fromEnd.constructionTypes.add(RoadConstructionType.ROAD);
        fromEnd.constructionTypes.add(RoadConstructionType.TUNNEL);

        var approachFwd = new TunnelJunctionSeedResolver.TunnelApproach(
            forward, "n-hub",
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            null, 1.0, null, null, 64);
        var approachRev = new TunnelJunctionSeedResolver.TunnelApproach(
            reverse, "n-hub",
            List.of(new Vec2d(40, 0), new Vec2d(0, 0)),
            null, 1.0, null, null, 64);

        assertTrue(JunctionConstructionContext.approachesHaveTunnelFamilyAtEntry(
            List.of(approachFwd), Map.of(forward.getId(), fromStart)));
        assertTrue(JunctionConstructionContext.approachesHaveTunnelFamilyAtEntry(
            List.of(approachRev), Map.of(reverse.getId(), fromEnd)),
            "node at edge end must read terminal construction type, not index 0");
    }

    @Test
    void clearDeniedDoesNotReportPavementStampSuccess() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("deny-road");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = network.createEdge(
            a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        JunctionCrossSectionFill.MaterialResolver materials = new JunctionCrossSectionFill.MaterialResolver() {
            @Override
            public String resolve(String materialId) {
                return "minecraft:stone";
            }

            @Override
            public net.minecraft.util.math.BlockPos canvasToBlockPos(Vec2d planPoint) {
                return new net.minecraft.util.math.BlockPos(
                    (int) Math.floor(planPoint.x), 0, (int) Math.floor(planPoint.y));
            }
        };
        boolean stamped = JunctionCrossSectionFill.fillCell(
            solids,
            new Vec2d(0.5, 0.5),
            64,
            a.getId(),
            List.of(edge),
            network,
            new RoadSystemConfig("deny"),
            materials,
            null,
            1.0,
            surfaceAt(80),
            8,
            12,
            "minecraft:dirt",
            JunctionConstructionContext.CLEAR_DENIED,
            64);
        assertFalse(stamped, "CLEAR_DENIED must not report stamped=true");
        assertTrue(solids.isEmpty(), "CLEAR_DENIED must not place pavement into uncleared ground");
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
        assertFalse(blocks.constructionContext().failClosed(),
            "short ROAD→TUNNEL stub must collect approaches, not fail-closed");
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
    void denseTypeWindowTunnelIsCollectedNotFailClosed() {
        // 施工类型数组密、几何段粗：仅类型窗口能看见近节点 TUNNEL 时，必须仍能 collect，禁止 fail-closed。
        RoadSystemConfig config = new RoadSystemConfig("dense-type-window");
        config.setDefaultCornerRadius(6.0f);
        config.setPathSampleDistance(8.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork(true);
        RoadNode junction = hub(network);
        Map<String, Integer> elevations = new LinkedHashMap<>();
        elevations.put(junction.getId(), 64);
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            elevations.put(edge.getStartNodeId(), 64);
            elevations.put(edge.getEndNodeId(), 64);
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            // 节点端 1 格 ROAD stub，随后 TUNNEL（类型窗口能命中；几何段可能漏检）
            result.constructionTypes.add(RoadConstructionType.ROAD);
            for (int i = 0; i < 12; i++) {
                result.constructionTypes.add(RoadConstructionType.TUNNEL);
            }
            edgeResults.put(edge.getId(), result);
        }

        List<TunnelJunctionSeedResolver.TunnelApproach> approaches =
            TunnelJunctionSeedResolver.collectTunnelApproaches(
                network, junction, edgeResults, elevations, config, (path, segs) -> 1.0);
        assertFalse(approaches.isEmpty(),
            "type-window tunnel evidence must be collected as approaches");

        RoadJunctionGenerator.JunctionBlocks blocks = generate(
            config, network, junction, surfaceAt(90), 64, edgeResults);
        assertFalse(blocks.constructionContext().failClosed(),
            "must not fail-closed when approaches can be collected via type window");
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
    void tunnelFilletClearanceEdgeNeverGetsOpenDaylightCap() {
        RoadSystemConfig config = new RoadSystemConfig("fillet-edge-no-daylight");
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

        int clearanceOnly = 0;
        int abutFillet = 0;
        for (ColumnKey col : footprint.clearance()) {
            JunctionConstructionContext.ColumnRule rule = ctx.rule(col);
            if (rule == null) {
                continue;
            }
            if (footprint.elevationRole(col) == JunctionWorldFootprint.ElevationRole.CLEARANCE_ONLY) {
                clearanceOnly++;
                assertTrue(
                    rule.role() != JunctionConstructionContext.ColumnRole.OPEN
                        || rule.maxAuthorizedClearY() != null,
                    "CLEARANCE_ONLY fillet-edge must not daylight; col=" + col + " role=" + rule.role());
                assertNotNull(rule.maxAuthorizedClearY(),
                    "CLEARANCE_ONLY edge needs roof cap; col=" + col);
                assertTrue(rule.maxAuthorizedClearY() < surfaceY);
            }
            if (JunctionConstructionContext.isAdjacentToFillet(col, footprint.filletAdditions())) {
                abutFillet++;
                assertNotNull(rule.maxAuthorizedClearY(),
                    "fillet-adjacent edge needs roof cap; col=" + col);
            }
        }
        assertTrue(clearanceOnly > 0 || abutFillet > 0,
            "precondition: junction has clearance-edge or fillet-adjacent columns");

        for (RoadSolidPrimitive p : blocks.getSolids().primitives()) {
            if (p == null || !"minecraft:air".equals(p.materialId())) {
                continue;
            }
            assertTrue(p.elevation() < surfaceY,
                "fillet-edge solids must not daylight surface; airY=" + p.elevation());
        }
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
