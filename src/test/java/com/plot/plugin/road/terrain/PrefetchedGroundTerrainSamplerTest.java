package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.ProfileGroundSampler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefetchedGroundTerrainSamplerTest {

    @Test
    void cachedGroundOverridesFallbackAndEmptyStaysUnloaded() {
        TerrainSampler fallback = new FlatTerrainSampler(64);
        PrefetchedGroundTerrainSampler.Builder builder = new PrefetchedGroundTerrainSampler.Builder();
        builder.putGround(0, 0, 88);
        builder.putEmpty(10, 0);

        PrefetchedGroundTerrainSampler sampler = builder.build(fallback, null);

        assertEquals(88, sampler.sampleLoadedSurfaceY(new Vec2d(0, 0)).orElseThrow());
        assertTrue(sampler.sampleLoadedSurfaceY(new Vec2d(10, 0)).isEmpty());
        assertEquals(64, sampler.sampleLoadedSurfaceY(new Vec2d(20, 0)).orElseThrow());
    }

    @Test
    void knownColumnWaterUsesSnapshotNotFallback() {
        AtomicInteger fallbackWaterCalls = new AtomicInteger();
        TerrainSampler fallback = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }

            @Override
            public OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                fallbackWaterCalls.incrementAndGet();
                return OptionalInt.of(99);
            }
        };

        PrefetchedGroundTerrainSampler.Builder builder = new PrefetchedGroundTerrainSampler.Builder();
        builder.putColumn(0, 0, OptionalInt.of(70), OptionalInt.of(72));
        builder.putColumn(10, 0, OptionalInt.of(70), OptionalInt.empty());
        PrefetchedGroundTerrainSampler sampler = builder.build(fallback, null);

        assertEquals(72, sampler.findExposedWaterSurface(new Vec2d(0, 0)).orElseThrow());
        assertTrue(sampler.findExposedWaterSurface(new Vec2d(10, 0)).isEmpty());
        assertEquals(0, fallbackWaterCalls.get(), "known columns must not hit client water fallback");
        assertEquals(99, sampler.findExposedWaterSurface(new Vec2d(20, 0)).orElseThrow());
        assertEquals(1, fallbackWaterCalls.get());
    }

    @Test
    void profileCollectDoesNotCliffToWorldBottomAcrossEmptyPrefetchGap() {
        TerrainSampler fallback = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return -64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }

            @Override
            public OptionalInt sampleLoadedSurfaceY(Vec2d planPoint) {
                return OptionalInt.of(-64);
            }
        };

        PrefetchedGroundTerrainSampler.Builder builder = new PrefetchedGroundTerrainSampler.Builder();
        builder.putGround(0, 0, 80);
        builder.putEmpty(10, 0);
        builder.putGround(20, 0, 100);
        PrefetchedGroundTerrainSampler sampler = builder.build(fallback, null);

        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0)),
            new PathSegment(new Vec2d(10, 0), new Vec2d(20, 0)));
        ProfileGroundSampler.SampleData data = ProfileGroundSampler.collect(segments, sampler, 0);

        assertEquals(List.of(80, 90, 100), data.groundSamples());
        assertTrue(data.groundSamples().stream().noneMatch(y -> y <= -64));
    }
}
