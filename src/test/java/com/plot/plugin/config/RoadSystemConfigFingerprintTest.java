package com.plot.plugin.config;

import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelShape;
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
    void generationInputsFingerprintChangesWhenTunnelStyleChanges() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        long baseline = config.generationInputsFingerprint();

        config.getTunnelStyle().setShape(TunnelShape.HORSESHOE);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.getTunnelStyle().setClearHeight(8);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.getTunnelStyle().setLightingMode(TunnelLightingMode.CEILING_BAND);
        assertNotEquals(baseline, config.generationInputsFingerprint());
    }

    @Test
    void terrainStyleDefaultsToBalanced() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        assertEquals(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED, config.getTerrainStyle());
    }
}
