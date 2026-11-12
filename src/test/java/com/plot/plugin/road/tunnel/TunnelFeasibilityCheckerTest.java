package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void singleBlockCoverIsInvalid() {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = 64 + style.roofTopOffset();
        TunnelFeasibility feasibility = TunnelFeasibilityChecker.check(
            solidUpTo(roofY + 1),
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            64,
            5,
            style,
            columnResolver(),
            1.0);
        assertFalse(feasibility.valid(),
            "one-block roof cover must not validate as a tunnel");
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

    @Test
    void cavityAtRoadLevelWithIntactRoofCoverRemainsValid() {
        // 模拟沙砾塌落后路面格变空，但洞顶山体覆土仍在
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = 64 + style.roofTopOffset();
        TerrainSampler gravelCavity = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 80;
            }

            @Override
            public int sampleColumnTopY(Vec2d point) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                if (y == 64) {
                    return false; // road cell emptied by gravity block fall
                }
                return y <= 80;
            }
        };
        TunnelFeasibility feasibility = TunnelFeasibilityChecker.check(
            gravelCavity,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            64,
            5,
            style,
            columnResolver(),
            1.0);
        assertTrue(feasibility.valid(), "intact overburden must keep tunnel even if road cell is air");
        assertTrue(feasibility.minimumCover() >= TunnelFeasibility.MINIMUM_COVER_BLOCKS);
        assertTrue(roofY < 80);
    }

    @Test
    void floatingRockAboveLargeVoidDoesNotCountAsCover() {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = 64 + style.roofTopOffset();
        int gap = TunnelOverburdenPolicy.SMALL_AIR_POCKET_MAX_BLOCKS + 1;
        // 大于小孔阈值的空腔：上方浮石不得算盖层。
        TerrainSampler floatingLedge = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return roofY + gap + 4;
            }

            @Override
            public int sampleColumnTopY(Vec2d point) {
                return roofY + gap + 4;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                if (y <= 64) {
                    return true; // road / ground
                }
                return y >= roofY + gap + 1 && y <= roofY + gap + 4;
            }
        };
        assertEquals(0, TunnelFeasibilityChecker.measureContinuousCoverAbove(
            floatingLedge, 0, 0, roofY));
        TunnelFeasibility feasibility = TunnelFeasibilityChecker.check(
            floatingLedge,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            64,
            5,
            style,
            columnResolver(),
            1.0);
        assertFalse(feasibility.valid());
        assertEquals(0, feasibility.minimumCover());
    }

    @Test
    void smallAirPocketAboveRoofDoesNotBreakCover() {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = 64 + style.roofTopOffset();
        // 洞顶上 2 格小孔再接厚覆土：应视为可封填噪点，隧道仍成立。
        TerrainSampler noisyCover = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return roofY + 8;
            }

            @Override
            public int sampleColumnTopY(Vec2d point) {
                return roofY + 8;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                if (y <= 64) {
                    return true;
                }
                if (y == roofY + 1 || y == roofY + 2) {
                    return false;
                }
                return y <= roofY + 8;
            }
        };
        int cover = TunnelFeasibilityChecker.measureContinuousCoverAbove(noisyCover, 0, 0, roofY);
        assertTrue(cover >= TunnelFeasibility.MINIMUM_COVER_BLOCKS,
            "small pocket must be skipped so overburden above still counts; cover=" + cover);
        TunnelFeasibility feasibility = TunnelFeasibilityChecker.check(
            noisyCover,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            64,
            5,
            style,
            columnResolver(),
            1.0);
        assertTrue(feasibility.valid(), "noisy small voids must not invalidate enclosed tunnel");
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
