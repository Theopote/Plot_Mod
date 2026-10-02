package com.plot.plugin.road.pipeline.profile.environment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WaterCrossingDetectorTest {

    @Test
    void waterCrossingDetectorFindsRiverSpan() {
        EnvironmentProfile profile = new EnvironmentProfile(
            List.of(
                EnvironmentSample.land(0.0, 68),
                EnvironmentSample.land(10.0, 68),
                new EnvironmentSample(20.0, 55, 69, 14, SurfaceContext.DEEP_WATER),
                new EnvironmentSample(30.0, 55, 69, 14, SurfaceContext.DEEP_WATER),
                new EnvironmentSample(40.0, 55, 69, 14, SurfaceContext.DEEP_WATER),
                EnvironmentSample.land(50.0, 68),
                EnvironmentSample.land(60.0, 68)),
            List.of(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0));

        List<WaterCrossing> crossings = WaterCrossingDetector.detect(profile);

        assertEquals(1, crossings.size());
        WaterCrossing crossing = crossings.getFirst();
        assertEquals(15.0, crossing.crossingStartStation(), 1e-6);
        assertEquals(45.0, crossing.crossingEndStation(), 1e-6);
        assertEquals(30.0, crossing.lengthMeters(), 1e-6);
        assertEquals(20.0, crossing.firstWaterSampleStation(), 1e-6);
        assertEquals(40.0, crossing.lastWaterSampleStation(), 1e-6);
        assertEquals(69, crossing.waterSurfaceY());
        assertEquals(14.0, crossing.maxDepth(), 1e-6);
        assertFalse(crossings.getFirst().containsStation(10.0));
        assertFalse(crossings.getFirst().containsStation(50.0));
    }
}
