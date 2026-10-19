package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawMeasureHelperTest {

    @AfterEach
    void restoreDefaults() {
        DrawMeasureSettings.setEnabled(true);
        DrawMeasureSettings.setDecimalPlaces(2);
        DrawMeasureSettings.setShowUnit(true);
    }

    @Test
    void distanceBlocksFallsBackToCanvasDistanceWhenWorldConversionUnavailable() {
        Vec2d p1 = new Vec2d(0, 0);
        Vec2d p2 = new Vec2d(3, 4);
        double distance = DrawMeasureHelper.distanceBlocks(p1, p2);
        assertEquals(5.0, distance, 1e-6);
    }

    @Test
    void formatValueRespectsDecimalPlacesSetting() {
        DrawMeasureSettings.setDecimalPlaces(2);
        assertEquals("3.14", DrawMeasureHelper.formatValue(3.14159));

        DrawMeasureSettings.setDecimalPlaces(0);
        assertEquals("3", DrawMeasureHelper.formatValue(3.4));
    }

    @Test
    void formatBlocksCanHideUnit() {
        DrawMeasureSettings.setShowUnit(false);
        assertEquals("12.35", DrawMeasureHelper.formatBlocks(12.345));
    }

    @Test
    void formatBlocksIncludesUnitWhenEnabled() {
        DrawMeasureSettings.setShowUnit(true);
        String formatted = DrawMeasureHelper.formatBlocks(3.0);
        assertTrue(formatted.matches(".*3\\.00.*"), () -> "expected two decimals, got: " + formatted);
        assertFalse(formatted.equals("3.00"));
    }

    @Test
    void polylineLengthBlocksSumsSegmentDistances() {
        List<Vec2d> points = List.of(
                new Vec2d(0, 0),
                new Vec2d(3, 0),
                new Vec2d(3, 4));
        assertEquals(7.0, DrawMeasureHelper.polylineLengthBlocks(points), 1e-6);
    }

    @Test
    void midpointReturnsSegmentCenter() {
        Vec2d mid = DrawMeasureHelper.midpoint(new Vec2d(0, 0), new Vec2d(4, 6));
        assertEquals(2.0, mid.x, 1e-6);
        assertEquals(3.0, mid.y, 1e-6);
    }

    @Test
    void radiusBlocksUsesSampledEdgeDistance() {
        double radius = DrawMeasureHelper.radiusBlocks(new Vec2d(10, 10), 5.0);
        assertEquals(5.0, radius, 1e-6);
    }
}
