package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePathUtilsTest {

    @Test
    void acceptsLineAndOpenPolyline() {
        assertTrue(PowerLinePathUtils.isAdoptableLine(
            new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))));
        assertTrue(PowerLinePathUtils.isAdoptableLine(
            new PolylineShape(List.of(new Vec2d(0, 0), new Vec2d(5, 0), new Vec2d(5, 5)), false)));
    }

    @Test
    void acceptsOpenBezierAndRejectsClosedBezier() {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{new Vec2d(0, 10), new Vec2d(10, 10)});
        BezierCurveShape open = new BezierCurveShape(anchors, controls, false);
        assertTrue(PowerLinePathUtils.isAdoptableLine(open));
        assertFalse(PowerLinePathUtils.isRejectedCurve(open));

        BezierCurveShape closed = new BezierCurveShape(anchors, controls, true);
        assertTrue(PowerLinePathUtils.isAdoptableLine(closed));
    }

    @Test
    void extractPathPointsCopiesVertices() {
        List<Vec2d> points = PowerLinePathUtils.extractPathPoints(
            new PolylineShape(List.of(new Vec2d(1, 2), new Vec2d(3, 4)), false));
        assertEquals(2, points.size());
        assertEquals(1.0, points.get(0).x, 1e-6);
        assertEquals(4.0, points.get(1).y, 1e-6);
    }
}
