package com.plot.plugin.config;

import com.plot.plugin.road.terrain.RoadTerrainStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RoadSystemConfigFingerprintTest {

    @Test
    void generationInputsFingerprintChangesWhenTerrainAdaptationChanges() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        long baseline = config.generationInputsFingerprint();

        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.FOLLOW);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.SMOOTH);
        assertNotEquals(baseline, config.generationInputsFingerprint());
    }

    @Test
    void terrainAdaptationDefaultsToBalanced() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        assertEquals(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED, config.getTerrainStyle());
    }
}
