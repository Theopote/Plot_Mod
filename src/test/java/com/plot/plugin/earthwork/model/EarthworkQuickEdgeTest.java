package com.plot.plugin.earthwork.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkQuickEdgeTest {

    @Test
    void fromSettingsMapsTreatments() {
        ZoneEdgeSettings settings = new ZoneEdgeSettings();
        settings.setDefaultTreatment(EdgeTreatment.VERTICAL);
        assertEquals(EarthworkQuickEdge.VERTICAL, EarthworkQuickEdge.fromSettings(settings));

        settings.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        assertEquals(EarthworkQuickEdge.NATURAL, EarthworkQuickEdge.fromSettings(settings));

        settings.setDefaultTreatment(EdgeTreatment.RETAINING_WALL);
        assertEquals(EarthworkQuickEdge.RETAINING, EarthworkQuickEdge.fromSettings(settings));
        assertEquals(EarthworkQuickEdge.VERTICAL, EarthworkQuickEdge.fromSettings(null));
    }

    @Test
    void naturalAppliesOneToOneSlopeAndMinimumReach() {
        ZoneEdgeSettings settings = new ZoneEdgeSettings();
        settings.setMaximumReachBlocks(0);
        settings.setFillSlopePitchNumerator(3);
        settings.setFillSlopePitchDenominator(2);
        EarthworkQuickEdge.NATURAL.applyTo(settings);

        assertEquals(EdgeTreatment.CUT_FILL_SLOPE, settings.getDefaultTreatment());
        assertEquals(1, settings.getCutSlopePitchRatio());
        assertEquals(1, settings.getFillSlopePitchNumerator());
        assertEquals(1, settings.getFillSlopePitchDenominator());
        assertTrue(settings.getMaximumReachBlocks() >= 8);
    }

    @Test
    void retainingAndVerticalMapTreatments() {
        ZoneEdgeSettings settings = new ZoneEdgeSettings();
        EarthworkQuickEdge.RETAINING.applyTo(settings);
        assertEquals(EdgeTreatment.RETAINING_WALL, settings.getDefaultTreatment());
        EarthworkQuickEdge.VERTICAL.applyTo(settings);
        assertEquals(EdgeTreatment.VERTICAL, settings.getDefaultTreatment());
    }
}
