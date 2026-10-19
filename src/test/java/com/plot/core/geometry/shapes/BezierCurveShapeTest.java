package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BezierCurveShapeTest {

    @Test
    void getBoundingBoxWrapsStraightSegment() {
        BezierCurveShape curve = straightBezier(new Vec2d(0, 0), new Vec2d(10, 0));

        BoundingBox bounds = curve.getBoundingBox();

        assertEquals(0.0, bounds.getMin().x, 1e-6);
        assertEquals(0.0, bounds.getMin().y, 1e-6);
        assertEquals(10.0, bounds.getMax().x, 1e-6);
        assertEquals(0.0, bounds.getMax().y, 1e-6);
    }

    @Test
    void containsPointHitsCurveMidpoint() {
        BezierCurveShape curve = straightBezier(new Vec2d(0, 0), new Vec2d(10, 0));

        assertTrue(curve.containsPoint(new Vec2d(5, 0), 0.5));
    }

    @Test
    void containsPointMissesPerpendicularOffset() {
        BezierCurveShape curve = straightBezier(new Vec2d(0, 0), new Vec2d(10, 0));

        assertFalse(curve.containsPoint(new Vec2d(5, 5), 0.5));
        assertFalse(curve.containsPoint(new Vec2d(5, -5), 0.5));
    }

    private static BezierCurveShape straightBezier(Vec2d start, Vec2d end) {
        List<Vec2d[]> controls = new ArrayList<>();
        controls.add(new Vec2d[]{lerp(start, end, 0.3), lerp(start, end, 0.7)});
        BezierCurveShape curve = new BezierCurveShape(
                List.of(start, end),
                controls,
                false);
        curve.setPreferredSamplingSteps(16);
        return curve;
    }

    private static Vec2d lerp(Vec2d a, Vec2d b, double t) {
        return a.add(b.subtract(a).multiply(t));
    }
}
