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
    void openFilletWithholdsTunnelCapAndClearsTowardSurface() {
        // 节点有隧道 clearHeight，但本列覆土不足封闭 → 不得施加洞顶 cap，圆角上方应露天清障
        int roadY = 64;
        int clearHeight = 5;
        int flatCap = roadY + clearHeight; // 69
        int surfaceY = 67; // cover=3，不满足隧道封闭覆土
        int tunnelThreshold = 4;
        Vec2d plan = new Vec2d(14, 14);
        ColumnKey column = new ColumnKey(14, 14);
        TerrainSampler terrain = surfaceAt(surfaceY);

        assertFalse(TunnelJunctionShellPass.columnHasEnclosedCover(
            terrain, plan, column, roadY, clearHeight, 1, tunnelThreshold));
        Integer gated = com.plot.plugin.road.RoadJunctionGenerator.enclosedTunnelColumnClearCap(
            terrain, plan, column, roadY, clearHeight, flatCap, null, 1, tunnelThreshold);
        assertEquals(null, gated, "shallow fillet must withhold tunnel cap");

        RoadSolidModel solids = new RoadSolidModel();
        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, tunnelThreshold, terrain, 14, 14, plan, null, surfaceY, gated);
        long cavityAir = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.elevation() > roadY && p.elevation() <= surfaceY)
            .count();
        assertTrue(cavityAir > 0, "open/shallow fillet must clear terrain above pavement");
        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == surfaceY && "minecraft:air".equals(p.materialId())),
            "open fillet must clear the top engineering surface block itself");
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
}
