package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileGroundSamplerTest {

    @Test
    void fillLoadedGapsInterpolatesInteriorAndHoldsEnds() {
        List<Integer> filled = ProfileGroundSampler.fillLoadedGaps(List.of(
            OptionalInt.empty(),
            OptionalInt.of(80),
            OptionalInt.empty(),
            OptionalInt.of(100),
            OptionalInt.empty()));

        assertEquals(List.of(80, 80, 90, 100, 100), filled);
    }

    @Test
    void collectDoesNotUseDefault64AcrossUnloadedGap() {
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return planPoint.x <= 0.1 || planPoint.x >= 19.9;
            }

            @Override
            public java.util.OptionalInt sampleLoadedSurfaceY(Vec2d planPoint) {
                if (!isChunkLoaded(planPoint)) {
                    return java.util.OptionalInt.empty();
                }
                return java.util.OptionalInt.of(planPoint.x < 10 ? 80 : 100);
            }
        };

        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0)),
            new PathSegment(new Vec2d(10, 0), new Vec2d(20, 0)));
        ProfileGroundSampler.SampleData data = ProfileGroundSampler.collect(segments, terrain, 0);

        assertEquals(3, data.groundSamples().size());
        assertEquals(80, data.groundStarts().getFirst());
        assertEquals(100, data.groundEnds().getLast());
        assertEquals(90, data.groundEnds().getFirst());
        assertTrue(data.groundSamples().stream().noneMatch(y -> y == 64));
    }
}
