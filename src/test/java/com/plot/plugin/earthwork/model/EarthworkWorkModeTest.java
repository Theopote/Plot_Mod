package com.plot.plugin.earthwork.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkWorkModeTest {

    @Test
    void fromIdDefaultsToQuick() {
        assertEquals(EarthworkWorkMode.QUICK, EarthworkWorkMode.fromId(null));
        assertEquals(EarthworkWorkMode.QUICK, EarthworkWorkMode.fromId(""));
        assertEquals(EarthworkWorkMode.QUICK, EarthworkWorkMode.fromId("unknown"));
        assertEquals(EarthworkWorkMode.LEARN, EarthworkWorkMode.fromId("learn"));
        assertEquals(EarthworkWorkMode.BUILDER, EarthworkWorkMode.fromId("BUILDER"));
    }

    @Test
    void quickHidesEngineeringTabsAndLearningMetrics() {
        assertFalse(EarthworkWorkMode.QUICK.showsEngineeringTabs());
        assertFalse(EarthworkWorkMode.QUICK.showsLearningMetrics());
        assertTrue(EarthworkWorkMode.BUILDER.showsEngineeringTabs());
        assertFalse(EarthworkWorkMode.BUILDER.showsLearningMetrics());
        assertTrue(EarthworkWorkMode.LEARN.showsEngineeringTabs());
        assertTrue(EarthworkWorkMode.LEARN.showsLearningMetrics());
        assertFalse(EarthworkWorkMode.QUICK.showsBuilderVisuals());
        assertTrue(EarthworkWorkMode.BUILDER.showsBuilderVisuals());
        assertTrue(EarthworkWorkMode.LEARN.showsBuilderVisuals());
        assertFalse(EarthworkWorkMode.QUICK.showsLearnVisuals());
        assertFalse(EarthworkWorkMode.BUILDER.showsLearnVisuals());
        assertTrue(EarthworkWorkMode.LEARN.showsLearnVisuals());
    }
}
