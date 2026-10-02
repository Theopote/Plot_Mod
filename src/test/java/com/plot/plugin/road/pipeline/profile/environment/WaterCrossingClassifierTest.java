package com.plot.plugin.road.pipeline.profile.environment;

import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterCrossingClassifierTest {

    @Test
    void shallowWidePondClassifiedAsBridgeNotCauseway() {
        EnvironmentProfile profile = new EnvironmentProfile(
            List.of(
                EnvironmentSample.land(8.0, 64),
                new EnvironmentSample(10.0, 62, 63, 1, SurfaceContext.SHALLOW_WATER),
                new EnvironmentSample(12.0, 62, 63, 1, SurfaceContext.SHALLOW_WATER),
                new EnvironmentSample(14.0, 62, 63, 1, SurfaceContext.SHALLOW_WATER),
                new EnvironmentSample(16.0, 62, 63, 1, SurfaceContext.SHALLOW_WATER),
                EnvironmentSample.land(18.0, 64)),
            List.of(8.0, 10.0, 12.0, 14.0, 16.0, 18.0));

        List<WaterCrossing> crossings = WaterCrossingClassifier.classify(
            WaterCrossingDetector.detect(profile),
            WaterCrossingSettings.defaults(),
            TerrainFollowPreset.STANDARD,
            18.0);

        assertEquals(1, crossings.size());
        WaterCrossing crossing = crossings.getFirst();
        assertEquals(WaterCrossingStrategy.BRIDGE, crossing.strategy());
        assertEquals(9.0, crossing.crossingStartStation(), 1e-6);
        assertEquals(17.0, crossing.crossingEndStation(), 1e-6);
        assertEquals(8.0, crossing.lengthMeters(), 1e-6);
    }
}
