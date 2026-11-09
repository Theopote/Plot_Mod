package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道路口广场清障：浅覆土圆角不得掏穿自然地表；深覆土不得挖掉洞顶衬砌格。
 */
class JunctionTerrainClearanceTunnelCoverTest {

    @Test
    void shallowCoverFilletColumnDoesNotPunchThroughSurface() {
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int roofY = 69; // clearCap
        int surfaceY = 67; // 圆角浅覆土：地表低于洞顶
        Vec2d plan = new Vec2d(10, 10);
        TerrainSampler terrain = surfaceAt(surfaceY);

        JunctionTerrainClearance.clearOverheadColumn(
            solids, roadY, 4, terrain, 10, 10, plan, null, surfaceY, roofY);

        long airAboveSurface = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.elevation() >= surfaceY)
            .count();
        assertEquals(0L, airAboveSurface, "shallow fillet must not punch natural surface or above");
        long cavityAir = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .filter(p -> p.elevation() > roadY && p.elevation() < surfaceY)
            .count();
        assertEquals(0L, cavityAir, "insufficient cover must skip solid cavity punch");
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
