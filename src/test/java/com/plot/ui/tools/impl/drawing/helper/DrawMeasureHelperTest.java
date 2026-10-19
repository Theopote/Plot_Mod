package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawMeasureHelperTest {

    @Test
    void distanceBlocksFallsBackToCanvasDistanceWhenWorldConversionUnavailable() {
        Vec2d p1 = new Vec2d(0, 0);
        Vec2d p2 = new Vec2d(3, 4);
        double distance = DrawMeasureHelper.distanceBlocks(p1, p2);
        assertEquals(5.0, distance, 1e-6);
    }

    @Test
    void formatBlocksAlwaysUsesTwoDecimalPlaces() {
        String formatted = DrawMeasureHelper.formatBlocks(3.0);
        assertTrue(formatted.matches(".*3\\.00.*"), () -> "expected two decimals, got: " + formatted);
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
