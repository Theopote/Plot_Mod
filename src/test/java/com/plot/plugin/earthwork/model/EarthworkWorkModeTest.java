package com.plot.plugin.earthwork.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkWorkModeTest {

    @Test
    void fromIdDefaultsToQuickAndMigratesLearnToBuilder() {
        assertEquals(EarthworkWorkMode.QUICK, EarthworkWorkMode.fromId(null));
        assertEquals(EarthworkWorkMode.QUICK, EarthworkWorkMode.fromId(""));
        assertEquals(EarthworkWorkMode.QUICK, EarthworkWorkMode.fromId("unknown"));
        assertEquals(EarthworkWorkMode.BUILDER, EarthworkWorkMode.fromId("learn"));
        assertEquals(EarthworkWorkMode.BUILDER, EarthworkWorkMode.fromId("LEARN"));
        assertEquals(EarthworkWorkMode.BUILDER, EarthworkWorkMode.fromId("BUILDER"));
        assertEquals(2, EarthworkWorkMode.values().length);
    }

    @Test
    void quickIsTaskOrientedBuilderOwnsEngineeringCapabilities() {
        assertFalse(EarthworkWorkMode.QUICK.showsEngineeringTabs());
        assertFalse(EarthworkWorkMode.QUICK.showsEngineeringMetrics());
        assertFalse(EarthworkWorkMode.QUICK.showsBuilderVisuals());
        assertFalse(EarthworkWorkMode.QUICK.showsAnalysisVisuals());
        assertFalse(EarthworkWorkMode.QUICK.allowsProjectBalanceScope());
        assertFalse(EarthworkWorkMode.QUICK.allowsConstrainedZoneOptimization());

        assertTrue(EarthworkWorkMode.BUILDER.showsEngineeringTabs());
        assertTrue(EarthworkWorkMode.BUILDER.showsEngineeringMetrics());
        assertTrue(EarthworkWorkMode.BUILDER.showsBuilderVisuals());
        assertTrue(EarthworkWorkMode.BUILDER.showsAnalysisVisuals());
        assertTrue(EarthworkWorkMode.BUILDER.allowsProjectBalanceScope());
        assertTrue(EarthworkWorkMode.BUILDER.allowsConstrainedZoneOptimization());
    }
}
