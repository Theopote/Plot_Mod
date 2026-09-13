package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
    void closedPolylineIsAdoptableViaPathAdapters() {
        PolylineShape closed = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(20, 30)),
            true);
        assertTrue(PowerLinePathUtils.isAdoptableLine(closed));
    }
}
