package com.plot.plugin.road.pipeline.profile.terrain;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OscillationReductionConfigTest {

    @Test
    void globalConfigDefaultsToDisabled() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        assertFalse(config.isOscillationReductionEnabled());
    }

    @Test
    void roadInheritsGlobalWhenNoOverride() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setOscillationReductionEnabled(true);
        Road road = new Road("r1");
        assertTrue(road.isOscillationReductionEnabled(config));

        config.setOscillationReductionEnabled(false);
        assertFalse(road.isOscillationReductionEnabled(config));
    }

    @Test
    void roadOverrideTakesPrecedenceOverGlobal() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setOscillationReductionEnabled(false);
        Road road = new Road("r1");
        road.setOscillationReductionEnabled(true);
        assertTrue(road.isOscillationReductionEnabled(config));

        config.setOscillationReductionEnabled(true);
        road.setOscillationReductionEnabled(false);
        assertFalse(road.isOscillationReductionEnabled(config));
    }

    @Test
    void solverSkipsReducerWhenConfigDisabled() {
        List<Double> trend = List.of(64.0, 67.0, 65.0, 68.0, 66.0, 69.0, 64.0);
        List<Double> distances = List.of(7.0, 7.0, 7.0, 7.0, 7.0, 7.0);
        List<Float> slopes = List.of(12.0f, 12.0f, 12.0f, 12.0f, 12.0f, 12.0f);

        GradeLimitedProfileSolver.clearOscillationReductionState();
        GradeLimitedProfileSolver.solveStationElevations(
            trend, null, distances, slopes, null, null,
            TerrainFollowPreset.STANDARD, null, false);

        OscillationReductionSummary summary = GradeLimitedProfileSolver.lastOscillationReduction();
        assertFalse(summary.attempted());
    }

    @Test
    void solverAttemptsReducerWhenConfigEnabled() {
        List<Double> trend = List.of(64.0, 67.0, 65.0, 68.0, 66.0, 69.0, 64.0);
        List<Double> distances = List.of(7.0, 7.0, 7.0, 7.0, 7.0, 7.0);
        List<Float> slopes = List.of(12.0f, 12.0f, 12.0f, 12.0f, 12.0f, 12.0f);

        GradeLimitedProfileSolver.clearOscillationReductionState();
        GradeLimitedProfileSolver.solveStationElevations(
            trend, null, distances, slopes, null, null,
            TerrainFollowPreset.STANDARD, null, true);

        OscillationReductionSummary summary = GradeLimitedProfileSolver.lastOscillationReduction();
        assertTrue(summary.attempted());
    }
}
