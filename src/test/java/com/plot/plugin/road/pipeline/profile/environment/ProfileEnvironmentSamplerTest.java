package com.plot.plugin.road.pipeline.profile.environment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileEnvironmentSamplerTest {

    @Test
    void profileEnvironmentSamplerSamplesExposedWater() {
        TerrainSampler terrain = riverTerrain(68, 55, 69, 40.0, 80.0);
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(50, 0)),
            new PathSegment(new Vec2d(50, 0), new Vec2d(100, 0)));

        EnvironmentProfile profile = ProfileEnvironmentSampler.collect(segments, terrain, 2.5);

        assertTrue(profile.samples().size() >= 3);
        boolean sawWater = profile.samples().stream().anyMatch(EnvironmentSample::hasWater);
        assertTrue(sawWater, "expected at least one station with exposed water");
        EnvironmentSample waterSample = profile.samples().stream()
            .filter(EnvironmentSample::hasWater)
            .findFirst()
            .orElseThrow();
        assertEquals(69, waterSample.waterSurfaceY());
        assertEquals(55, waterSample.terrainY());
        assertTrue(waterSample.waterDepth() > 0);
        assertNotNull(waterSample.context());
    }

    static TerrainSampler riverTerrain(
            int landY,
            int bedY,
            int waterY,
            double riverStartX,
            double riverEndX) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return inRiver(planPoint.x) ? bedY : landY;
            }

            @Override
            public OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return inRiver(planPoint.x) ? OptionalInt.of(waterY) : OptionalInt.empty();
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= (inRiver(worldX) ? bedY : landY);
            }

            private boolean inRiver(double x) {
                return x >= riverStartX && x <= riverEndX;
            }
        };
    }
}
