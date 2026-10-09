package com.plot.core.terrain;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainSamplerTest {

    @Test
    void flatSamplerReturnsConfiguredElevation() {
        TerrainSampler terrain = new FlatTerrainSampler(72);
        assertEquals(72, terrain.sampleSurfaceY(new Vec2d(10, 20)));
        assertEquals(72, terrain.sampleCrossSectionGroundY(new Vec2d(0, 0), new Vec2d(1, 0), 3));
    }

    @Test
    void crossSectionGroundYAveragesOffsets() {
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return (int) Math.round(planPoint.x + planPoint.y);
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }
        };

        int ground = terrain.sampleCrossSectionGroundY(
            new Vec2d(0, 0),
            new Vec2d(1, 0),
            1.0);
        assertTrue(ground >= 0 && ground <= 1);
    }

    @Test
    void flatSamplerSolidFlagIsConfigurable() {
        assertTrue(new FlatTerrainSampler(64, true).isSolidBlock(0, 0, 0));
        assertFalse(new FlatTerrainSampler(64, false).isSolidBlock(0, 0, 0));
    }

    @Test
    void defaultChunkLoadedIsTrue() {
        TerrainSampler terrain = new FlatTerrainSampler(64);
        assertTrue(terrain.isChunkLoaded(0, 0));
        assertTrue(terrain.isChunkLoaded(new Vec2d(10, 20)));
    }

    @Test
    void loadedCrossSectionSkipsUnloadedColumns() {
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return planPoint.x >= 0 ? 80 : 64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return planPoint.x >= 0;
            }
        };

        assertTrue(terrain.sampleLoadedSurfaceY(new Vec2d(-1, 0)).isEmpty());
        assertEquals(80, terrain.sampleLoadedSurfaceY(new Vec2d(1, 0)).orElseThrow());
        assertEquals(80, terrain.sampleLoadedCrossSectionGroundY(new Vec2d(0, 0), new Vec2d(1, 0), 1.0).orElseThrow());
        assertTrue(terrain.sampleLoadedCrossSectionGroundY(new Vec2d(-2, 0), new Vec2d(1, 0), 0).isEmpty());
    }
}
