package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道路口广场清障：浅覆土圆角不得掏穿自然地表；深覆土不得挖掉洞顶衬砌格。
 */
class JunctionTerrainClearanceTunnelCoverTest {

    @Test
    void tunnelOverburdenDoesNotClearSurfaceDecorationAsHole() {
        // 山上草/雪若被当装饰清掉，会出现「零星穿孔」到地表。
        int roadY = 64;
        int roofY = 69;
        int surfaceY = 80;
        Vec2d plan = new Vec2d(5.5, 5.5);
        TerrainSampler terrain = new TerrainSampler() {
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
                return y == surfaceY; // 地表草
            }
        };
        RoadSolidModel solids = new RoadSolidModel();
        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, 4, terrain, 5, 5, plan, null, surfaceY, roofY);
        assertFalse(solids.primitives().stream().anyMatch(p ->
                p != null
                    && "minecraft:air".equals(p.materialId())
                    && p.elevation() >= surfaceY),
            "tunnel overburden must not clear surface decoration into a hole");
    }

    @Test
    void shallowTunnelCoverDoesNotDaylightUnderSurface() {
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int roofY = 69;
        int surfaceY = 67; // 浅覆土：cover=3，地表落在路面与洞顶之间
        int tunnelThreshold = 4;
        Vec2d plan = new Vec2d(10, 10);
        TerrainSampler terrain = surfaceAt(surfaceY);

        assertTrue(JunctionTerrainClearance.isShallowTunnelCover(
            roadY, surfaceY, tunnelThreshold, true));
        assertEquals(roadY, JunctionTerrainClearance.resolveTunnelSolidPunchTop(roadY, surfaceY, roofY));

        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, tunnelThreshold, terrain, 10, 10, plan, null, surfaceY, roofY);

        long underSurfaceAir = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.elevation() > roadY && p.elevation() < surfaceY)
            .count();
        assertEquals(0L, underSurfaceAir,
            "shallow tunnel cover must not excavate under natural surface");
        long airAtOrAboveSurface = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.elevation() >= surfaceY)
            .count();
        assertEquals(0L, airAtOrAboveSurface, "must not punch natural surface or above");
    }

    @Test
    void shallowTunnelContextKeepsCavityCapAndDoesNotDaylight() {
        // 有隧道进口参数时，即使浅覆土也必须安装洞顶 cap（与走廊一致），不得露天掏穿。
        int roadY = 64;
        int clearHeight = 5;
        int flatCap = roadY + clearHeight; // 69
        int surfaceY = 67; // 地表落在路面与洞顶之间
        int tunnelThreshold = 4;
        Vec2d plan = new Vec2d(14, 14);
        ColumnKey column = new ColumnKey(14, 14);
        TerrainSampler terrain = surfaceAt(surfaceY);

        Integer gated = com.plot.plugin.road.RoadJunctionGenerator.enclosedTunnelColumnClearCap(
            terrain, plan, column, roadY, clearHeight, flatCap, null, 1, tunnelThreshold);
        assertEquals(flatCap, gated, "tunnel context must keep cavity roof cap even when cover is shallow");

        RoadSolidModel solids = new RoadSolidModel();
        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, tunnelThreshold, terrain, 14, 14, plan, null, surfaceY, gated);
        assertEquals(0L, solids.primitives().stream()
                .filter(p -> "minecraft:air".equals(p.materialId()))
                .filter(p -> p.elevation() > roadY && p.elevation() <= surfaceY)
                .count(),
            "shallow tunnel cover must not excavate under natural surface");
        assertFalse(solids.primitives().stream().anyMatch(p ->
                p.elevation() == surfaceY && "minecraft:air".equals(p.materialId())),
            "must not daylight shallow overburden");
    }

    @Test
    void enclosedTunnelFilletKeepsOverburdenAboveRoof() {
        // 有隧道上下文：清到洞顶下，保留覆土。
        int roadY = 64;
        int clearHeight = 5;
        int flatCap = roadY + clearHeight; // 69
        int lining = 1;
        int surfaceY = 80;
        int tunnelThreshold = 4;
        Vec2d plan = new Vec2d(20, 20);
        ColumnKey column = new ColumnKey(20, 20);
        TerrainSampler terrain = surfaceAt(surfaceY);

        Integer gated = com.plot.plugin.road.RoadJunctionGenerator.enclosedTunnelColumnClearCap(
            terrain, plan, column, roadY, clearHeight, flatCap, null, lining, tunnelThreshold);
        assertEquals(flatCap, gated, "enclosed column must keep cavity roof cap");

        RoadSolidModel solids = new RoadSolidModel();
        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, tunnelThreshold, terrain, 20, 20, plan, null, surfaceY, gated);

        int maxAirY = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .mapToInt(p -> p.elevation())
            .max()
            .orElse(roadY);
        assertTrue(maxAirY < surfaceY, "must not daylight mountain above tunnel fillet; maxAir=" + maxAirY);
        assertTrue(maxAirY < flatCap, "must stop below design roof; maxAir=" + maxAirY);
        assertTrue(maxAirY > roadY, "enclosed fillet should still excavate cavity");
    }

    @Test
    void thinCoverWithTunnelContextKeepsCapAndStopsSolidPunch() {
        // 地表落在路面与洞顶之间：浅覆土；仍保留洞顶 cap，实体开挖停于路面。
        int roadY = 64;
        int clearHeight = 5;
        int flatCap = roadY + clearHeight; // 69
        int lining = 3;
        int surfaceY = 67;
        int tunnelThreshold = 4;
        Vec2d plan = new Vec2d(21, 21);
        ColumnKey column = new ColumnKey(21, 21);
        TerrainSampler terrain = surfaceAt(surfaceY);

        Integer gated = com.plot.plugin.road.RoadJunctionGenerator.enclosedTunnelColumnClearCap(
            terrain, plan, column, roadY, clearHeight, flatCap, null, lining, tunnelThreshold);
        assertEquals(flatCap, gated, "tunnel context must keep cavity cap on thin cover");
        assertEquals(roadY, JunctionTerrainClearance.resolveTunnelSolidPunchTop(roadY, surfaceY, gated));

        RoadSolidModel solids = new RoadSolidModel();
        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, tunnelThreshold, terrain, 21, 21, plan, null, surfaceY, gated);
        assertFalse(solids.primitives().stream().anyMatch(p ->
                p.elevation() >= surfaceY && "minecraft:air".equals(p.materialId())),
            "thin-cover tunnel fillet must not punch natural surface");
    }

    @Test
    void openJunctionWithoutTunnelContextDaylightsPlazaAndFillet() {
        // 无隧道进口：不得施加洞顶 cap，交叉核/圆角上方应露天清到地表。
        int roadY = 64;
        int surfaceY = 70;
        int tunnelThreshold = 4;
        Vec2d plan = new Vec2d(30, 30);
        ColumnKey column = new ColumnKey(30, 30);
        TerrainSampler terrain = surfaceAt(surfaceY);

        Integer gated = com.plot.plugin.road.RoadJunctionGenerator.enclosedTunnelColumnClearCap(
            terrain, plan, column, roadY, null, null, null, 1, tunnelThreshold);
        assertEquals(null, gated, "open junction must not invent tunnel roof cap");

        RoadSolidModel solids = new RoadSolidModel();
        // leftover 缺省取自然地表（与生成器一致），避免用路面 Y 漏清。
        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, tunnelThreshold, terrain, 30, 30, plan, null,
            Math.max(roadY, surfaceY), gated);
        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == surfaceY && "minecraft:air".equals(p.materialId())),
            "open plaza/fillet must clear the engineering surface block");
        long cavityAir = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.elevation() > roadY && p.elevation() <= surfaceY)
            .count();
        assertTrue(cavityAir > 0, "open plaza must clear leftover terrain above pavement");
    }

    @Test
    void openPlazaWithoutTunnelCapStillClearsTowardSurface() {
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int surfaceY = 67;
        Vec2d plan = new Vec2d(11, 11);
        TerrainSampler terrain = surfaceAt(surfaceY);

        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, 4, terrain, 11, 11, plan, null, surfaceY, null);

        long cavityAir = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.elevation() > roadY && p.elevation() <= surfaceY)
            .count();
        assertTrue(cavityAir > 0, "open plaza must clear toward and including surface");
        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == surfaceY && "minecraft:air".equals(p.materialId())),
            "open plaza must clear the top engineering terrain block at surfaceY");
        long airAboveSurface = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.elevation() > surfaceY)
            .count();
        assertEquals(0L, airAboveSurface,
            "with equal columnTop, open plaza must not invent air above surface");
    }

    @Test
    void deepCoverStopsBelowRoofAndSurface() {
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int roofY = 69;
        int surfaceY = 80;
        Vec2d plan = new Vec2d(5, 5);
        TerrainSampler terrain = surfaceAt(surfaceY);

        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, 4, terrain, 5, 5, plan, null, surfaceY, roofY);

        int maxAirY = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.layer() == RoadSolidLayer.TUNNEL)
            .mapToInt(p -> p.elevation())
            .max()
            .orElse(roadY);
        assertTrue(maxAirY < roofY, "solid punch must stop below design roof lining cell");
        assertTrue(maxAirY < surfaceY, "solid punch must stop below natural surface");
        assertTrue(maxAirY > roadY, "deep cover should still excavate cavity air");
    }

    @Test
    void gradeColumnWithTunnelCapDoesNotPunchShallowFilletSurface() {
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int roofY = 69;
        int surfaceY = 67;
        Vec2d plan = new Vec2d(12, 12);
        TerrainSampler terrain = surfaceAt(surfaceY);

        int cutTop = JunctionTerrainClearance.resolveTunnelSolidPunchTop(roadY, surfaceY, roofY);
        assertEquals(roadY, cutTop, "shallow cover must request no solid punch");
        JunctionTerrainClearance.punchColumnBelowSurface(solids, plan, roadY, cutTop, terrain);
        assertTrue(solids.primitives().isEmpty(), "gradeColumn path must not daylight shallow fillet");
    }

    @Test
    void resolveTunnelSolidPunchTopNeverReachesSurfaceOrRoof() {
        assertEquals(64, JunctionTerrainClearance.resolveTunnelSolidPunchTop(64, 67, 69));
        assertEquals(68, JunctionTerrainClearance.resolveTunnelSolidPunchTop(64, 80, 69));
        assertTrue(JunctionTerrainClearance.resolveTunnelSolidPunchTop(64, 80, 69) < 69);
        assertTrue(JunctionTerrainClearance.resolveTunnelSolidPunchTop(64, 80, 69) < 80);
    }

    @Test
    void trenchSurfaceBelowRoadStillClearsCavityLiningUnderRoof() {
        // 路堑：heightmap 低于路面时不得被当成浅覆土而整列停挖
        assertEquals(68, JunctionTerrainClearance.resolveTunnelSolidPunchTop(64, 50, 69));

        RoadSolidModel solids = new RoadSolidModel();
        Vec2d plan = new Vec2d(5, -3);
        TerrainSampler trench = surfaceAt(50);
        JunctionTerrainClearance.clearOverheadColumn(
            solids, 64, 4, trench, 5, -3, plan, null, 68, 69);

        int maxAirY = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .mapToInt(p -> p.elevation())
            .max()
            .orElse(64);
        assertTrue(maxAirY >= 67 && maxAirY < 69,
            "trench column must clear cavity lining under roof, maxAir=" + maxAirY);
    }

    @Test
    void gradedFilletUsesLoftedRoofCapNotFlatJunctionCap() {
        // 坡降圆角：路面 loft 到 60，若仍用平坦 junction 洞顶 69，会挖穿应落顶衬的 65
        int loftRoadY = 60;
        int clearHeight = 5;
        int loftRoofY = loftRoadY + clearHeight; // 65
        int flatJunctionRoofY = 69;
        int surfaceY = 80;
        Vec2d plan = new Vec2d(8, 8);
        TerrainSampler terrain = surfaceAt(surfaceY);

        Integer columnCap = com.plot.plugin.road.RoadJunctionGenerator.columnClearCap(
            loftRoadY, clearHeight, flatJunctionRoofY, null);
        assertEquals(loftRoofY, columnCap);

        RoadSolidModel solids = new RoadSolidModel();
        JunctionTerrainClearance.clearOverheadColumn(
            solids, loftRoadY, 4, terrain, 8, 8, plan, null, surfaceY, columnCap);

        int maxAirY = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.layer() == RoadSolidLayer.TUNNEL)
            .mapToInt(p -> p.elevation())
            .max()
            .orElse(loftRoadY);
        assertTrue(maxAirY < loftRoofY, "punch must stop below lofted roof, maxAir=" + maxAirY);
        assertTrue(maxAirY < flatJunctionRoofY - 1 || maxAirY < loftRoofY,
            "must not excavate up to flat junction roof through lofted lining band");
    }

    @Test
    void openPlazaFloatingClearDoesNotChainThroughOreVeinToSurface() {
        // 开敞清障：矿石曾被 isSolidBlock=false 当成「下方空气」，会沿矿脉把上方山体连锁掏成竖井。
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int solidTop = 66;   // 实体开挖顶
        int oreY = 68;       // 矿石（原 OTHER_SOLID）
        int rockY = 69;      // 矿石上方自然岩石
        int surfaceY = 75;
        Vec2d plan = new Vec2d(50, 50);
        TerrainSampler terrain = new TerrainSampler() {
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
                // 模拟修复前：矿石不算工程土石；修复后真实采样会把矿石算实心，两路都应挡住连锁。
                if (y == oreY) {
                    return false;
                }
                return y < surfaceY && y != oreY;
            }

            @Override
            public boolean isConstructionVolumeObstruction(int x, int y, int z) {
                if (y == oreY) {
                    return true; // 矿石仍是构筑障碍
                }
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
        };

        JunctionTerrainClearance.clearFloatingAndDecorationsAbove(
            solids, plan, terrain, 50, 50, roadY, solidTop, surfaceY + 2, false);

        assertFalse(solids.primitives().stream().anyMatch(p ->
                p.elevation() == rockY && "minecraft:air".equals(p.materialId())),
            "must not chain-clear rock above an ore vein as if the ore were air");
        assertFalse(solids.primitives().stream().anyMatch(p ->
                p.elevation() >= surfaceY && "minecraft:air".equals(p.materialId())),
            "ore vein must not daylight the natural surface via floating clearance");
    }

    @Test
    void tunnelContextDoesNotChainDeleteNaturalRockAboveCave() {
        // 浅覆土隧道列：覆土下有天然洞穴时，不得因 belowAir 把上方山体连锁清成竖井。
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int roofY = 69;
        int caveY = 71;       // 天然空腔
        int rockY = 72;       // 空腔上方自然岩石
        int surfaceY = 75;
        Vec2d plan = new Vec2d(40, 40);
        TerrainSampler terrain = new TerrainSampler() {
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
                if (y == caveY) {
                    return false;
                }
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
        };

        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, 4, terrain, 40, 40, plan, null, surfaceY, roofY);

        assertFalse(solids.primitives().stream().anyMatch(p ->
                p.elevation() == rockY && "minecraft:air".equals(p.materialId())),
            "tunnel context must not clear natural rock above a pre-existing cave");
        assertFalse(solids.primitives().stream().anyMatch(p ->
                p.elevation() >= surfaceY && "minecraft:air".equals(p.materialId())),
            "must not punch natural surface via floating chain");
    }

    @Test
    void floatingRemnantAboveOpenPlazaIsCleared() {
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int surfaceY = 66;
        int floatingY = 70;
        Vec2d plan = new Vec2d(3, 3);
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return surfaceY;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return floatingY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                if (y == floatingY) {
                    return true;
                }
                return y < surfaceY;
            }

            @Override
            public boolean isConstructionVolumeObstruction(int x, int y, int z) {
                return y == floatingY;
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
        };

        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, 4, terrain, 3, 3, plan, null, floatingY, null);

        boolean clearedFloating = solids.primitives().stream()
            .anyMatch(p -> p.elevation() == floatingY && "minecraft:air".equals(p.materialId()));
        assertTrue(clearedFloating, "floating remnant above open plaza must be cleared");
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
        };
    }

    @Test
    void enclosedFilletColumnKeepsTunnelCapEvenWhenNearestApproachIsOpen() {
        // 圆角列覆土已封闭，但最近中心线落在开敞段：仍须洞顶 cap，不得露天掏山。
        int roadY = 64;
        int clearHeight = 5;
        int surfaceY = 80;
        Vec2d plan = new Vec2d(12, 12);
        TerrainSampler terrain = surfaceAt(surfaceY);
        ColumnKey column = new ColumnKey(12, 12);

        Integer cap = com.plot.plugin.road.RoadJunctionGenerator.resolvePlazaColumnClearCap(
            terrain, plan, column, roadY,
            java.util.List.of(), java.util.Map.of(), edge -> java.util.List.of(),
            clearHeight, null, null, 1, 4);
        assertEquals(roadY + clearHeight, cap,
            "enclosed cover must install tunnel roof cap without tunnel-family nearest edge");

        RoadSolidModel solids = new RoadSolidModel();
        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, 4, terrain, 12, 12, plan, null, surfaceY, cap);
        int maxAirY = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .mapToInt(p -> p.elevation())
            .max()
            .orElse(roadY);
        assertTrue(maxAirY < surfaceY, "must not daylight mountain above enclosed fillet");
        assertTrue(maxAirY < roadY + clearHeight, "must stop below design roof");
    }
}
