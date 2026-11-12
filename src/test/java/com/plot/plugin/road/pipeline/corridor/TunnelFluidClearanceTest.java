package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道/走廊净空必须清掉水与岩浆，不得把流体当成「无障碍」而跳过。
 */
class TunnelFluidClearanceTest {

    @Test
    void hasObstructionTreatsWaterAndLavaAsClearable() {
        TerrainSampler terrain = fluidAt(70);
        assertTrue(RoadCorridorClearancePass.hasObstruction(terrain, 0, 70, 0),
            "water/lava inside clearance volume must count as obstruction");
        assertFalse(RoadCorridorClearancePass.hasObstruction(terrain, 0, 71, 0),
            "empty air above fluid is not an obstruction");
    }

    @Test
    void openPlazaFloatingClearRemovesFluidInHeadroom() {
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int solidTop = 66;
        int fluidY = 68;
        int surfaceY = 72;
        Vec2d plan = new Vec2d(3, 3);
        TerrainSampler terrain = fluidColumn(fluidY, surfaceY);

        JunctionTerrainClearance.clearFloatingAndDecorationsAbove(
            solids, plan, terrain, 3, 3, roadY, solidTop, surfaceY, false);

        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == fluidY && "minecraft:air".equals(p.materialId())),
            "open headroom must clear standing water/lava to air");
    }

    @Test
    void tunnelOverburdenProtectDoesNotDrainAquiferAboveRoof() {
        RoadSolidModel solids = new RoadSolidModel();
        int roadY = 64;
        int solidTop = 69; // 洞顶下
        int aquiferY = 74;
        int surfaceY = 80;
        Vec2d plan = new Vec2d(4, 4);
        TerrainSampler terrain = fluidColumn(aquiferY, surfaceY);

        JunctionTerrainClearance.clearFloatingAndDecorationsAbove(
            solids, plan, terrain, 4, 4, roadY, solidTop, surfaceY, true);

        assertFalse(solids.primitives().stream().anyMatch(p ->
                p.elevation() == aquiferY && "minecraft:air".equals(p.materialId())),
            "tunnel overburden protect must not drain aquifer/lake above the roof");
    }

    private static TerrainSampler fluidAt(int fluidY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return fluidY + 5;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }

            @Override
            public boolean isFluidBlock(int worldX, int y, int worldZ) {
                return y == fluidY;
            }

            @Override
            public boolean isChunkLoaded(int worldX, int worldZ) {
                return true;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }
        };
    }

    private static TerrainSampler fluidColumn(int fluidY, int surfaceY) {
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
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y < surfaceY && y != fluidY;
            }

            @Override
            public boolean isFluidBlock(int worldX, int y, int worldZ) {
                return y == fluidY;
            }

            @Override
            public boolean isChunkLoaded(int worldX, int worldZ) {
                return true;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }

            @Override
            public boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }
}
