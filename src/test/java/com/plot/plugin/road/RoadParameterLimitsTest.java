package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadParameterLimitsTest {

    @Test
    void clampCarriagewayWidth() {
        assertEquals(3, RoadParameterLimits.clampCarriagewayWidth(1));
        assertEquals(24, RoadParameterLimits.clampCarriagewayWidth(99));
        assertEquals(9, RoadParameterLimits.clampCarriagewayWidth(9));
    }

    @Test
    void maxPerLaneWidth() {
        assertEquals(6, RoadParameterLimits.maxPerLaneWidth(24, 4));
        assertEquals(8, RoadParameterLimits.maxPerLaneWidth(8, 1));
        assertEquals(1, RoadParameterLimits.maxPerLaneWidth(3, 4));
    }

    @Test
    void clampLaneWidth() {
        assertEquals(6, RoadParameterLimits.clampLaneWidth(20, 24, 4));
        assertEquals(1, RoadParameterLimits.clampLaneWidth(0, 8, 4));
    }

    @Test
    void clampRelaxedSlopeLengthRespectsContinuousCap() {
        assertEquals(5.0, RoadParameterLimits.clampRelaxedSlopeLength(30.0, 5.0), 0.001);
        assertEquals(10.0, RoadParameterLimits.clampRelaxedSlopeLength(30.0, 30.0), 0.001);
        assertEquals(1.0, RoadParameterLimits.clampRelaxedSlopeLength(0.5, 30.0), 0.001);
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
        assertEquals(320.0, RoadParameterLimits.clampManualElevation(400.0));
    }
}
