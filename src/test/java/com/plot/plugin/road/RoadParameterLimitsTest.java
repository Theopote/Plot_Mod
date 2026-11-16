package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadParameterLimitsTest {

    @Test
    void clampCarriagewayWidth() {
        assertEquals(1, RoadParameterLimits.clampCarriagewayWidth(1));
        assertEquals(27, RoadParameterLimits.clampCarriagewayWidth(99));
        assertEquals(9, RoadParameterLimits.clampCarriagewayWidth(9));
    }

    @Test
    void maxPerLaneWidth() {
        assertEquals(6, RoadParameterLimits.maxPerLaneWidth(24, 4));
        assertEquals(6, RoadParameterLimits.maxPerLaneWidth(8, 1));
        assertEquals(6, RoadParameterLimits.maxPerLaneWidth(3, 4));
    }

    @Test
    void allowsDoublePierOnlyWhenWiderThanTwo() {
        assertFalse(RoadParameterLimits.allowsDoublePier(2));
        assertTrue(RoadParameterLimits.allowsDoublePier(3));
    }

    @Test
    void clampMedianHeight() {
        assertEquals(1, RoadParameterLimits.clampMedianHeightBlocks(0));
        assertEquals(3, RoadParameterLimits.clampMedianHeightBlocks(9));
        assertEquals(2, RoadParameterLimits.clampMedianHeightBlocks(2));
    }

    @Test
    void clampLaneTravelWidth() {
        assertEquals(6, RoadParameterLimits.clampLaneTravelWidth(20));
        assertEquals(1, RoadParameterLimits.clampLaneTravelWidth(0));
    }

    @Test
    void normalizeStreetlightSpacing() {
        assertNull(RoadParameterLimits.normalizeStreetlightSpacing(0));
        assertNull(RoadParameterLimits.normalizeStreetlightSpacing(-1));
        assertEquals(8, RoadParameterLimits.normalizeStreetlightSpacing(3));
        assertEquals(12, RoadParameterLimits.normalizeStreetlightSpacing(12));
        assertEquals(50, RoadParameterLimits.normalizeStreetlightSpacing(80));
    }

    @Test
    void clampManualElevation() {
        assertNull(RoadParameterLimits.clampManualElevation(null));
        assertEquals(-64.0, RoadParameterLimits.clampManualElevation(-100.0));
        assertEquals(319.0, RoadParameterLimits.clampManualElevation(400.0));
    }
}
