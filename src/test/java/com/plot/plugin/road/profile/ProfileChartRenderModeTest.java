package com.plot.plugin.road.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileChartRenderModeTest {

    @Test
    void miniModeSuppressesLabelsAndGuideLine() {
        ProfileChartRenderMode mode = ProfileChartRenderMode.MINI;
        assertFalse(mode.showElevationAxisLabels());
        assertFalse(mode.showIntersectionLabels());
        assertFalse(mode.showGuideLine());
        assertEquals(3, mode.maxStationTicks());
    }

    @Test
    void editorModeShowsFullDetail() {
        ProfileChartRenderMode mode = ProfileChartRenderMode.EDITOR;
        assertTrue(mode.showElevationAxisLabels());
        assertTrue(mode.showIntersectionLabels());
        assertTrue(mode.showGuideLine());
        assertTrue(mode.showIntersectionConnectors());
    }
}
