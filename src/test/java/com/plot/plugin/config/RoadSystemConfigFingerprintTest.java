package com.plot.plugin.config;

import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics.TerrainAdaptationPreset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RoadSystemConfigFingerprintTest {

    @Test
    void generationInputsFingerprintChangesWhenTerrainAdaptationChanges() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        long baseline = config.generationInputsFingerprint();

        config.setTerrainAdaptation(TerrainAdaptationPreset.FOLLOW);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.setTerrainAdaptation(TerrainAdaptationPreset.FLATTEN);
        assertNotEquals(baseline, config.generationInputsFingerprint());
    }

    @Test
    void terrainAdaptationDefaultsToBalanced() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        assertEquals(TerrainAdaptationPreset.BALANCED, config.getTerrainAdaptation());
    }
}
