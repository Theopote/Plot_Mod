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
    void overviewModeHidesSolverDesignLineInCompactLayout() {
        ProfileChartRenderMode mode = ProfileChartRenderMode.OVERVIEW;
        // 绿色设计线仅由编辑器 VA overlay 绘制，概览只用地形 + 建造台阶。
        assertFalse(mode.showDesignProfileLine());
        assertFalse(mode.showGuideLine());
        assertFalse(mode.showElevationAxisLabels());
        assertTrue(mode.useCompactLayout());
    }

    @Test
    void editorModeShowsFullDetail() {
        ProfileChartRenderMode mode = ProfileChartRenderMode.EDITOR;
        assertTrue(mode.showElevationAxisLabels());
        assertTrue(mode.showIntersectionLabels());
        assertFalse(mode.showGuideLine());
        assertFalse(mode.showBuildProfileLine());
        assertTrue(mode.showIntersectionConnectors());
        // 求解器 design 采样不再画绿线；绿线来自 VerticalAlignmentProfileOverlay。
        assertFalse(mode.showDesignProfileLine());
    }

    @Test
    void overviewModeShowsBuildProfileNotGuideLine() {
        ProfileChartRenderMode mode = ProfileChartRenderMode.OVERVIEW;
        assertTrue(mode.showBuildProfileLine());
        assertFalse(mode.showGuideLine());
    }
}
