package com.plot.plugin.road.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalProfileEditorChartCacheTest {

    @Test
    void chartFallbackRequiresMatchingRoadRevisionTerrainAndLength() {
        assertTrue(VerticalProfileEditor.matchesChartFallbackIdentity(
            "road-a", "road-a", 3L, 3L, 7L, 7L, 100.0, 100.0));
        assertFalse(VerticalProfileEditor.matchesChartFallbackIdentity(
            "road-a", "road-b", 3L, 3L, 7L, 7L, 100.0, 100.0));
        assertFalse(VerticalProfileEditor.matchesChartFallbackIdentity(
            "road-a", "road-a", 3L, 4L, 7L, 7L, 100.0, 100.0));
        assertFalse(VerticalProfileEditor.matchesChartFallbackIdentity(
            "road-a", "road-a", 3L, 3L, 7L, 8L, 100.0, 100.0));
        assertFalse(VerticalProfileEditor.matchesChartFallbackIdentity(
            "road-a", "road-a", 3L, 3L, 7L, 7L, 100.0, 80.0));
    }
}
