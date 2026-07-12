package com.plot.plugin.road.terrain;

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
}
