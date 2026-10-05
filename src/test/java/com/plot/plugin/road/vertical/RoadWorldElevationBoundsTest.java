package com.plot.plugin.road.vertical;

import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadWorldElevationBoundsTest {

    @Test
    void resolveUsesTopExclusiveMinusOne() {
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int worldBottomY() {
                return -64;
            }

            @Override
            public int worldTopExclusiveY() {
                return 320;
            }

            @Override
            public int sampleSurfaceY(com.plot.api.geometry.Vec2d planPoint) {
                return 64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
        RoadElevationBounds bounds = RoadWorldElevationBounds.resolve(terrain);
        assertEquals(-64.0, bounds.minY());
        assertEquals(319.0, bounds.maxY());
    }

    @Test
    void resolveNullTerrainUsesFallback() {
        assertEquals(RoadWorldElevationBounds.fallback(), RoadWorldElevationBounds.resolve(null));
    }
}
