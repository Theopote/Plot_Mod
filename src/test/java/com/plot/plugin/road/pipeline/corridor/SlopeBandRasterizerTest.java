package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SlopeBandRasterizerTest {

    @Test
    void fillBetweenRaysCoversChordBetweenDivergingShoulders() {
        // 两射线夹角约 45°，同偏移环带应在弦上留下柱。
        int[] heights = new int[9];
        for (int o = 0; o <= 8; o++) {
            heights[o] = 70 - o;
        }
        var a = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(1L, SlopeRayId.ShoulderSide.RIGHT),
            new Vec2d(0, 0),
            new Vec2d(0, -1),
            heights,
            false,
            "minecraft:stone",
            1.0f,
            SlopeConstructionOutcome.COMPLETE,
            false,
            false,
            1.0);
        var b = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(2L, SlopeRayId.ShoulderSide.RIGHT),
            new Vec2d(0, 0),
            new Vec2d(1, -1).normalize(),
            heights,
            false,
            "minecraft:stone",
            1.0f,
            SlopeConstructionOutcome.COMPLETE,
            false,
            false,
            1.0);

        var host = CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig());
        List<SlopeProfileSample> samples = SlopeBandRasterizer.fillBetweenRays(
            a, b, new FlatTerrainSampler(72), host);

        assertFalse(samples.isEmpty(), "diverging rays must emit band samples");
        boolean hitMidChord = samples.stream().anyMatch(sample -> {
            double x = sample.planPoint().x;
            double z = sample.planPoint().y;
            // offset≈6 时弦中点约在 (3, -6) 一带
            return sample.horizontalOffset() == 6
                && x >= 2 && x <= 4
                && z <= -5 && z >= -7;
        });
        assertTrue(hitMidChord, "mid-chord column near offset 6 must be stamped");

        for (SlopeProfileSample sample : samples) {
            assertTrue(sample.horizontalOffset() <= RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS);
            assertTrue(sample.horizontalOffset() >= 1);
            assertEquals(70 - sample.horizontalOffset(), sample.slopeSurfaceY());
        }
    }

    @Test
    void heightTableDoesNotExceedWidthCap() {
        List<int[]> profile = List.of(
            new int[]{0, 70},
            new int[]{10, 60},
            new int[]{40, 50});
        int[] table = SlopeBandRasterizer.heightTableFromProfile(
            profile, RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS + 8);
        assertEquals(RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS + 1, table.length);
        assertEquals(70, table[0]);
        assertEquals(60, table[10]);
    }

    @Test
    void canConnectBandRequiresMatchingFillAndWaterAuth() {
        int[] heights = {64, 63, 62};
        var authorizedFill = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(1L, SlopeRayId.ShoulderSide.LEFT),
            new Vec2d(0, 0), new Vec2d(1, 0), heights, true, "dirt", 1.0f,
            SlopeConstructionOutcome.COMPLETE, true, false, 1.0);
        var unauthorizedFill = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(2L, SlopeRayId.ShoulderSide.LEFT),
            new Vec2d(2, 0), new Vec2d(1, 0), heights, true, "dirt", 1.0f,
            SlopeConstructionOutcome.COMPLETE, false, false, 1.0);
        var cut = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(3L, SlopeRayId.ShoulderSide.LEFT),
            new Vec2d(4, 0), new Vec2d(1, 0), heights, false, "stone", 1.0f,
            SlopeConstructionOutcome.COMPLETE, false, false, 1.0);

        assertTrue(SlopeBandRasterizer.canConnectBand(authorizedFill, authorizedFill));
        assertFalse(SlopeBandRasterizer.canConnectBand(authorizedFill, unauthorizedFill),
            "causeway auth mismatch must break band connection");
        assertFalse(SlopeBandRasterizer.canConnectBand(unauthorizedFill, cut),
            "fill/cut mismatch must break band connection");
        assertTrue(SlopeBandRasterizer.fillBetweenRays(
            authorizedFill, unauthorizedFill, new FlatTerrainSampler(60),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig())).isEmpty());
    }

    @Test
    void mismatchedFillCutRaysEmitNothing() {
        int[] heights = {64, 63, 62, 61};
        var fill = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(1L, SlopeRayId.ShoulderSide.LEFT),
            new Vec2d(0, 0), new Vec2d(1, 0), heights, true, "dirt", 1.0f,
            SlopeConstructionOutcome.COMPLETE, false, false, 1.0);
        var cut = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(2L, SlopeRayId.ShoulderSide.LEFT),
            new Vec2d(0, 0), new Vec2d(0, 1), heights, false, "stone", 1.0f,
            SlopeConstructionOutcome.COMPLETE, false, false, 1.0);
        assertTrue(SlopeBandRasterizer.fillBetweenRays(
            fill, cut, new FlatTerrainSampler(60),
            CorridorTestFixtures.planHost(new RoadSystemConfig("test"))).isEmpty());
    }

    @Test
    void bandSamplesUseDistinctRayIds() {
        int[] heights = new int[6];
        for (int o = 0; o <= 5; o++) {
            heights[o] = 68 - o;
        }
        var a = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(10L, SlopeRayId.ShoulderSide.RIGHT),
            new Vec2d(40, 0), new Vec2d(0, -1), heights, false, "stone", 1.0f,
            SlopeConstructionOutcome.COMPLETE, false, false, 1.0);
        var b = new SlopeBandRasterizer.ShoulderRay(
            new SlopeRayId(11L, SlopeRayId.ShoulderSide.RIGHT),
            new Vec2d(40, 0), new Vec2d(1, 0), heights, false, "stone", 1.0f,
            SlopeConstructionOutcome.COMPLETE, false, false, 1.0);
        List<SlopeProfileSample> samples = SlopeBandRasterizer.fillBetweenRays(
            a, b, new FlatTerrainSampler(72),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));
        Set<Long> ids = new HashSet<>();
        for (SlopeProfileSample sample : samples) {
            assertTrue(sample.rayId().stationSampleId() < 0, "band ray ids must be synthetic negative");
            assertTrue(ids.add(sample.rayId().stationSampleId()), "each band stamp needs unique ray id");
        }
    }
}
