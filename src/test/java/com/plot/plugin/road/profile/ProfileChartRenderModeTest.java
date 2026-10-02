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
        assertFalse(mode.showStationAxisLabels());
        assertFalse(mode.showIntersectionLabels());
        assertFalse(mode.showGuideLine());
        assertFalse(mode.showDesignProfileLine());
        assertTrue(mode.useCompactLayout());
        assertEquals(3, mode.maxStationTicks());
    }

    @Test
    void overviewModeShowsDesignLineInCompactLayout() {
        ProfileChartRenderMode mode = ProfileChartRenderMode.OVERVIEW;
        assertTrue(mode.showDesignProfileLine());
        assertFalse(mode.showGuideLine());
        assertFalse(mode.showElevationAxisLabels());
        assertTrue(mode.useCompactLayout());
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
