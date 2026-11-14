package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 圆角开敞边坡：深覆土街角跳过；浅覆土允许；水面禁填与走廊一致。
 */
class JunctionFilletBatterContinuityTest {

    @Test
    void enclosedTunnelContextSkipsOpenBatter() {
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(8, 8),
            new Vec2d(4, 8),
            new Vec2d(8, 4),
            4.0,
            Math.PI / 2.0);
        TerrainSampler mountain = surfaceAt(80);
        assertFalse(JunctionCrossSectionFill.filletCornerAllowsOpenBatter(
            fillet, 64, mountain, 4, 1.0, true),
            "enclosed tunnel corner must not stamp open batters");
    }

    @Test
    void openCutFilletAllowsBatterEvenWithLargeCover() {
        // 开敞山地路堑：地表比路面高 8～15 格仍须允许挖方边坡（不再用覆土阈值误判隧道）。
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(8, 8),
            new Vec2d(4, 8),
            new Vec2d(8, 4),
            4.0,
            Math.PI / 2.0);
        TerrainSampler openCut = surfaceAt(64 + 12);
        assertTrue(JunctionCrossSectionFill.filletCornerAllowsOpenBatter(
            fillet, 64, openCut, 4, 1.0, false),
            "open cut with 12-block cover must still allow fillet batters");
        assertTrue(JunctionCrossSectionFill.filletSampleAllowsOpenBatter(
            new Vec2d(6, 6), 64, openCut, false, 5, 1));
    }

    @Test
    void filletBatterRunStopsBeforeCrossingCenter() {
        assertEquals(5, JunctionCrossSectionFill.maxFilletBatterRunBlocks(6.0, 1.0, 100));
        assertEquals(1, JunctionCrossSectionFill.maxFilletBatterRunBlocks(2.0, 1.0, 100));
        assertEquals(3, JunctionCrossSectionFill.maxFilletBatterRunBlocks(8.0, 1.0, 3),
            "global maxHorizontalRun still caps");
        // 小半径不得再靠「续坡 8 格」穿心：总跑距仍夹在圆心前。
        assertEquals(3, JunctionCrossSectionFill.maxFilletBatterRunBlocks(4.0, 1.0, 50),
            "R=4 continuous batter must stop before center, not arc+8 continue");
    }

    @Test
    void openSlopeColumnIgnoresJunctionTunnelClearCap() {
        // 混合路口：开敞边坡列必须露天挖到地表，不得被路口级洞顶 cap 提前截断。
        JunctionCrossSectionFill.ColumnClearCapResolver openDaylight =
            (column, plan, designY) -> null;
        JunctionCrossSectionFill.ColumnClearCapResolver tunnelCap =
            JunctionCrossSectionFill.constantClearCap(69);
        assertNull(openDaylight.resolve(new ColumnKey(1, 1), new Vec2d(1.5, 1.5), 64));
        assertEquals(69, tunnelCap.resolve(new ColumnKey(1, 1), new Vec2d(1.5, 1.5), 64));
    }

    @Test
    void withoutFilletsDoesNotEmitCenterBasedShoulderBatters() {
        RoadSystemConfig config = new RoadSystemConfig("no-center-batter");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);
        config.setRoadWidth(7);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r");
        road.setWidth(7);
        road.setIncludeSlopeBatter(true);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        network.createEdge(junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), road.getId());
        network.createEdge(junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), road.getId());

        TerrainSampler hillside = surfaceAt(70);
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadSolidModel solids = new RoadSolidModel();
        JunctionCrossSectionFill.stampApproachSlopeBatters(
            solids,
            junction.getPosition(),
            64,
            junction.getId(),
            network.getEdges().values().stream().toList(),
            network,
            config,
            materials(generator),
            columns(generator),
            1.0,
            hillside,
            8,
            null,
            List.of(),
            null,
            Set.of());

        long subgrade = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .count();
        assertEquals(0L, subgrade,
            "without fillet arcs, junction must not stamp batters from center");
    }

    @Test
    void pavedColumnsRejectFilletBatterIntrusion() {
        RoadSystemConfig config = new RoadSystemConfig("pave-reject-batter");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);
        config.setDefaultCornerRadius(6.0f);
        config.setRoadWidth(7);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r");
        road.setWidth(7);
        road.setIncludeSlopeBatter(true);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setCornerRadius(6.0);
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        network.createEdge(junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), road.getId());
        network.createEdge(junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), road.getId());

        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(6, 6),
            new Vec2d(0, 6),
            new Vec2d(6, 0),
            6.0,
            Math.PI / 2.0);
        TerrainSampler hillside = surfaceAt(58); // below road → fill batters
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());

        // Forbid a wide block around the fillet grass so any intrusion is rejected.
        // 含外缘续坡可达范围，避免仅检验弧内禁区。
        Set<ColumnKey> paved = new java.util.LinkedHashSet<>();
        for (int x = -4; x <= 24; x++) {
            for (int z = -4; z <= 24; z++) {
                paved.add(new ColumnKey(x, z));
            }
        }

        RoadSolidModel solids = new RoadSolidModel();
        JunctionCrossSectionFill.stampApproachSlopeBatters(
            solids,
            junction.getPosition(),
            64,
            junction.getId(),
            network.getEdges().values().stream().toList(),
            network,
            config,
            materials(generator),
            columns(generator),
            1.0,
            hillside,
            8,
            (Integer) null,
            List.of(fillet),
            null,
            Set.of(),
            paved);

        assertTrue(solids.primitives().stream().noneMatch(p -> {
                if (p.layer() != RoadSolidLayer.SUBGRADE || p.planPoint() == null) {
                    return false;
                }
                ColumnKey key = new ColumnKey(
                    (int) Math.floor(p.planPoint().x), (int) Math.floor(p.planPoint().y));
                return paved.contains(key);
            }),
            "fillet batter must not write subgrade into paved/forbidden columns");
    }

    @Test
    void widthCappedFilletFillEmitsRetainingFinish() {
        // 小半径限制跑距 + 大填方高差 → 截断尖端需竖向挡墙接到地面（复用走廊收尾）。
        RoadSystemConfig config = new RoadSystemConfig("fillet-retain");
        config.setTerrainStyle(RoadTerrainStyle.FOLLOW);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);
        config.setDefaultCornerRadius(4.0f);
        config.setRoadWidth(7);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r");
        road.setWidth(7);
        road.setIncludeSlopeBatter(true);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setCornerRadius(4.0);
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        network.createEdge(junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), road.getId());
        network.createEdge(junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), road.getId());

        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(6, 6),
            new Vec2d(2, 6),
            new Vec2d(6, 2),
            4.0,
            Math.PI / 2.0);
        TerrainSampler lowGround = surfaceAt(50);
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());

        RoadSolidModel solids = new RoadSolidModel();
        JunctionCrossSectionFill.stampApproachSlopeBatters(
            solids,
            junction.getPosition(),
            64,
            junction.getId(),
            network.getEdges().values().stream().toList(),
            network,
            config,
            materials(generator),
            columns(generator),
            1.0,
            lowGround,
            20, // 允许部分 SUBGRADE；尖端仍因跑距截断走挡墙
            (Integer) null,
            List.of(fillet),
            null,
            Set.of(),
            Set.of());

        long walls = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.RETAINING_WALL)
            .count();
        long fillSubgrade = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .count();
        assertTrue(walls > 0 || fillSubgrade > 0,
            "width-capped fillet fill must emit retaining tip or continued fill; walls="
                + walls + " fill=" + fillSubgrade);
    }

    @Test
    void adjacentArcRaysCanFormRingBand() {
        Vec2d center = new Vec2d(10, 10);
        double radius = 6.0;
        double start = 0.0;
        double sweep = Math.PI / 2.0;
        Vec2d a = FilletArcSweep.pointOnArc(center, radius, start);
        Vec2d b = FilletArcSweep.pointOnArc(center, radius, start + sweep / 2.0);
        Vec2d outA = center.subtract(a).normalize();
        Vec2d outB = center.subtract(b).normalize();
        int[] heights = {68, 67, 66, 65, 64};
        SlopeBandRasterizer.ShoulderRay rayA = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(1, SlopeRayId.ShoulderSide.LEFT),
            a, outA, heights, true, "minecraft:dirt", 1.5f,
            SlopeConstructionOutcome.COMPLETE, false, false, 1.0);
        SlopeBandRasterizer.ShoulderRay rayB = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(2, SlopeRayId.ShoulderSide.LEFT),
            b, outB, heights, true, "minecraft:dirt", 1.5f,
            SlopeConstructionOutcome.COMPLETE, false, false, 1.0);
        assertTrue(SlopeBandRasterizer.canConnectBand(rayA, rayB));

        TerrainSampler dry = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 60;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 60;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }

            @Override
            public java.util.OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return java.util.OptionalInt.empty();
            }
        };
        RoadTerrainClearanceUtilsProxy resolver = new RoadTerrainClearanceUtilsProxy();
        var band = SlopeBandRasterizer.fillBetweenRays(rayA, rayB, dry, resolver);
        assertFalse(band.isEmpty(), "adjacent fillet rays must emit ring-band samples");
    }

    private static TerrainSampler surfaceAt(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int x, int worldY, int z) {
                return worldY <= y;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }
        };
    }

    private static JunctionCrossSectionFill.MaterialResolver materials(RoadGenerator generator) {
        return new JunctionCrossSectionFill.MaterialResolver() {
            @Override
            public String resolve(String materialId) {
                return generator.getBlockIdFromMaterial(materialId);
            }

            @Override
            public net.minecraft.util.math.BlockPos canvasToBlockPos(Vec2d planPoint) {
                return generator.toBlockPos(planPoint, 0);
            }
        };
    }

    private static com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver columns(
            RoadGenerator generator) {
        return new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d planPoint) {
                return generator.toBlockPos(planPoint, 0).getX();
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                return generator.toBlockPos(planPoint, 0).getZ();
            }
        };
    }

    /** Minimal column resolver for band raster tests. */
    private static final class RoadTerrainClearanceUtilsProxy
            implements com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver {
        @Override
        public int worldX(Vec2d planPoint) {
            return (int) Math.round(planPoint.x);
        }

        @Override
        public int worldZ(Vec2d planPoint) {
            return (int) Math.round(planPoint.y);
        }
    }
}
