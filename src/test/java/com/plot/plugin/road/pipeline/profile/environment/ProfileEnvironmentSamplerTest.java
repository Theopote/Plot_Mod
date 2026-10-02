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

        EnvironmentProfile profile = ProfileEnvironmentSampler.collectDense(
            segments, terrain, 2.5, 1.0, 10.0, 2.0);

        assertTrue(profile.samples().size() >= 3);
        long waterCount = profile.samples().stream().filter(EnvironmentSample::hasWater).count();
        assertTrue(waterCount >= 3, "expected multiple water stations across river span");
        EnvironmentSample waterSample = profile.samples().stream()
            .filter(EnvironmentSample::hasWater)
            .findFirst()
            .orElseThrow();
        assertEquals(69, waterSample.waterSurfaceY());
        assertEquals(55, waterSample.terrainY());
        assertTrue(waterSample.waterDepth() > 0);
        assertNotNull(waterSample.context());
    }

    @Test
    void riverBetweenSegmentEndpointsProducesNonZeroCrossingWidth() {
        TerrainSampler terrain = riverTerrain(68, 55, 69, 20.0, 30.0);
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(50, 0)));

        EnvironmentProfile profile = ProfileEnvironmentSampler.collectDense(
            segments, terrain, 2.5, 1.0, 50.0, 2.0);
        List<WaterCrossing> crossings = WaterCrossingDetector.detect(profile);

        assertEquals(1, crossings.size());
        WaterCrossing crossing = crossings.getFirst();
        assertTrue(crossing.lengthMeters() > 0.0, "interior river must produce non-zero crossing width");
        assertTrue(crossing.crossingStartStation() < crossing.firstWaterSampleStation());
        assertTrue(crossing.crossingEndStation() > crossing.lastWaterSampleStation());
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
