package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileSolveSupportTest {

    @Test
    void autoSolverReadsPreferredContinuousGradeLengthFromConfig() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setMaxContinuousSlopeLength(195.0);

        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, segments -> 1.0);

        assertEquals(195.0, support.maxContinuousSlopeLength(), 0.001);
    }
}
