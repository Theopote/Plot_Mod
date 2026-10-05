package com.plot.plugin.road.vertical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadElevationBoundsTest {

    @Test
    void fallbackMaxYIsInclusiveTopBlock() {
        RoadElevationBounds bounds = RoadWorldElevationBounds.fallback();
        assertEquals(-64.0, bounds.minY());
        assertEquals(319.0, bounds.maxY());
    }

    @Test
    void clampRespectsInclusiveRange() {
        RoadElevationBounds bounds = new RoadElevationBounds(-64, 319);
        assertEquals(-64.0, bounds.clamp(-100.0));
        assertEquals(319.0, bounds.clamp(400.0));
        assertEquals(128.0, bounds.clamp(128.0));
    }

    @Test
    void containsChecksInclusiveEndpoints() {
        RoadElevationBounds bounds = new RoadElevationBounds(-64, 319);
        assertTrue(bounds.contains(-64.0));
        assertTrue(bounds.contains(319.0));
        assertFalse(bounds.contains(320.0));
        assertFalse(bounds.contains(-65.0));
    }
}
