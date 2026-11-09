package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 圆角开敞边坡：深覆土街角跳过；浅覆土允许；水面禁填与走廊一致。
 */
class JunctionFilletBatterContinuityTest {

    @Test
    void deepCoverFilletSkipsOpenBatter() {
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(8, 8),
            new Vec2d(4, 8),
            new Vec2d(8, 4),
            4.0,
            Math.PI / 2.0);
        TerrainSampler mountain = surfaceAt(80);
        assertFalse(JunctionCrossSectionFill.filletCornerAllowsOpenBatter(
            fillet, 64, mountain, 4, 1.0));
    }

    @Test
    void openCutFilletAllowsBatter() {
        JunctionGeometryResult.FilletCorner fillet = new JunctionGeometryResult.FilletCorner(
            new Vec2d(8, 8),
            new Vec2d(4, 8),
            new Vec2d(8, 4),
            4.0,
            Math.PI / 2.0);
        TerrainSampler open = surfaceAt(65);
        assertTrue(JunctionCrossSectionFill.filletCornerAllowsOpenBatter(
            fillet, 64, open, 4, 1.0));
    }

    @Test
    void adjacentArcRaysCanFormRingBand() {
        Vec2d center = new Vec2d(10, 10);
        double radius = 6.0;
        double start = 0.0;
        double sweep = Math.PI / 2.0;
        Vec2d a = FilletArcSweep.pointOnArc(center, radius, start);
        Vec2d b = FilletArcSweep.pointOnArc(center, radius, start + sweep / 2.0);
        Vec2d outA = center.subtract(a).normalize();
        Vec2d outB = center.subtract(b).normalize();
        int[] heights = {68, 67, 66, 65, 64};
        SlopeBandRasterizer.ShoulderRay rayA = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(1, SlopeRayId.ShoulderSide.LEFT),
            a, outA, heights, true, "minecraft:dirt", 1.5f,
            SlopeConstructionOutcome.COMPLETE, false, 1.0);
        SlopeBandRasterizer.ShoulderRay rayB = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(2, SlopeRayId.ShoulderSide.LEFT),
            b, outB, heights, true, "minecraft:dirt", 1.5f,
            SlopeConstructionOutcome.COMPLETE, false, 1.0);
        assertTrue(SlopeBandRasterizer.canConnectBand(rayA, rayB));

        TerrainSampler dry = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 60;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 60;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }

            @Override
            public java.util.OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return java.util.OptionalInt.empty();
            }
        };
        RoadTerrainClearanceUtilsProxy resolver = new RoadTerrainClearanceUtilsProxy();
        var band = SlopeBandRasterizer.fillBetweenRays(rayA, rayB, dry, resolver);
        assertFalse(band.isEmpty(), "adjacent fillet rays must emit ring-band samples");
    }

    private static TerrainSampler surfaceAt(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int x, int worldY, int z) {
                return worldY <= y;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }
        };
    }

    /** Minimal column resolver for band raster tests. */
    private static final class RoadTerrainClearanceUtilsProxy
            implements com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver {
        @Override
        public int worldX(Vec2d planPoint) {
            return (int) Math.round(planPoint.x);
        }

        @Override
        public int worldZ(Vec2d planPoint) {
            return (int) Math.round(planPoint.y);
        }
    }
}
