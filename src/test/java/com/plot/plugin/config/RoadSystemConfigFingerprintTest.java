package com.plot.plugin.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RoadSystemConfigFingerprintTest {

    @Test
    void generationInputsFingerprintChangesWhenWaterSettingsChange() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        long baseline = config.generationInputsFingerprint();

        config.setWaterRoadClearanceBlocks(2);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.setCausewayMaxLengthMeters(8.0);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.setCausewayMaxDepthBlocks(3);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.setBridgePreferredMinLengthMeters(8.0);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.setLongBridgeLengthMeters(80.0);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.setAllowUnderwaterRoad(true);
        assertNotEquals(baseline, config.generationInputsFingerprint());
        baseline = config.generationInputsFingerprint();

        config.setEnvironmentSampleSpacingMeters(1.0);
        assertNotEquals(baseline, config.generationInputsFingerprint());
    }
}
