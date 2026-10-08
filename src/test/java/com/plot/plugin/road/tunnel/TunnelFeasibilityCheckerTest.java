package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelFeasibilityCheckerTest {

    @Test
    void thickCoverIsValid() {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        TerrainSampler mountain = solidUpTo(80);
        TunnelFeasibility feasibility = TunnelFeasibilityChecker.check(
            mountain,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            64,
            5,
            style,
            columnResolver(),
            1.0);
        assertTrue(feasibility.valid());
        assertTrue(feasibility.minimumCover() >= TunnelFeasibility.MINIMUM_COVER_BLOCKS);
    }

    @Test
    void thinRoofIsInvalid() {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = 64 + style.roofTopOffset();
        TerrainSampler thinCover = solidUpTo(roofY);
        TunnelFeasibility feasibility = TunnelFeasibilityChecker.check(
            thinCover,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            64,
            5,
            style,
            columnResolver(),
            1.0);
        assertFalse(feasibility.valid());
    }

    @Test
    void openAirAtRoadLevelIsInvalid() {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        TerrainSampler openAir = solidUpTo(60);
        TunnelFeasibility feasibility = TunnelFeasibilityChecker.check(
            openAir,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            64,
            5,
            style,
            columnResolver(),
            1.0);
        assertFalse(feasibility.valid());
    }

    private static TerrainSampler solidUpTo(int maxSolidY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return maxSolidY;
            }

            @Override
            public int sampleColumnTopY(Vec2d point) {
                return maxSolidY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= maxSolidY;
            }
        };
    }

    private static RoadTerrainClearanceUtils.BlockColumnResolver columnResolver() {
        return new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d point) {
                return (int) Math.round(point.x);
            }

            @Override
            public int worldZ(Vec2d point) {
                return (int) Math.round(point.y);
            }
        };
    }
}
