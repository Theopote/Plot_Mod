package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.EngineeringTerrainBlockRole;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.road.RoadRoadbedGradingUtils;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 道路地形语义审计：与 {@link EngineeringTerrainService} 对齐，防止误删人工结构。
 */
class RoadTerrainSemanticsTest {

    @Test
    void flatTerrainSamplerDoesNotClearDecorationsByDefault() {
        TerrainSampler terrain = new FlatTerrainSampler(64);
        assertFalse(terrain.isRoadClearableDecoration(0, 65, 0));
    }

    @Test
    void clearRoadDecorationsOnlyRemovesWhenSamplerMarksClearable() {
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler woodedColumn = new TerrainSampler() {
            @Override public int sampleSurfaceY(Vec2d point) { return 64; }
            @Override public int sampleColumnTopY(Vec2d point) { return 70; }
            @Override public boolean isSolidBlock(int x, int y, int z) { return y <= 64; }
            @Override public boolean isRoadClearableDecoration(int x, int y, int z) {
                return y >= 65 && y <= 67;
            }
        };

        RoadRoadbedGradingUtils.clearRoadDecorations(
            solids, new Vec2d(0, 0), new Vec2d(0, 1), 3,
            woodedColumn, new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override public int worldX(Vec2d point) { return (int) Math.round(point.x); }
                @Override public int worldZ(Vec2d point) { return (int) Math.round(point.y); }
            }, 1.0);

        assertEquals(9, solids.primitives().size());
        assertTrue(solids.primitives().stream().allMatch(p -> p.elevation() >= 65 && p.elevation() <= 67));
    }

    @Test
    void playerLogColumnPreservedWhenNotMarkedClearable() {
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler playerStructure = new TerrainSampler() {
            @Override public int sampleSurfaceY(Vec2d point) { return 64; }
            @Override public int sampleColumnTopY(Vec2d point) { return 68; }
            @Override public boolean isSolidBlock(int x, int y, int z) { return y <= 64; }
            @Override public boolean isRoadClearableDecoration(int x, int y, int z) {
                return false;
            }
        };

        RoadRoadbedGradingUtils.clearRoadDecorations(
            solids, new Vec2d(0, 0), new Vec2d(0, 1), 3,
            playerStructure, new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override public int worldX(Vec2d point) { return (int) Math.round(point.x); }
                @Override public int worldZ(Vec2d point) { return (int) Math.round(point.y); }
            }, 1.0);

        assertTrue(solids.isEmpty(), "player log column must not be cleared when sampler returns false");
    }

    @Test
    void undergroundWaterIgnoredByExposedSurfaceScan() {
        assertTrue(EngineeringTerrainService.findExposedWaterInRange(
            80, 80, y -> y == 40).isEmpty());
    }

    @Test
    void surfaceWaterDetectedInExposedRange() {
        var lake = EngineeringTerrainService.findExposedWaterInRange(
            64, 60, y -> y >= 61 && y <= 64);
        assertTrue(lake.isPresent());
        assertEquals(64, lake.getAsInt());
    }

    @Test
    void nullBlockStateIsNotClearableEngineeringTerrain() {
        assertFalse(EngineeringTerrainService.isEngineeringTerrain(null));
        assertFalse(EngineeringTerrainService.isNaturalDecoration(null));
        assertEquals(EngineeringTerrainBlockRole.AIR, EngineeringTerrainService.classifyBlock(null));
    }
}
