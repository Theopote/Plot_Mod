package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SineCurveShapeTest {

    private static SineCurveShape horizontalSine() {
        return new SineCurveShape(
                new Vec2d(0, 0),
                new Vec2d(20, 0),
                2.0,
                20.0,
                0.0);
    }

    @Test
    void getBoundingBoxIncludesAmplitude() {
        SineCurveShape sine = horizontalSine();

        BoundingBox bounds = sine.getBoundingBox();

        assertEquals(0.0, bounds.getMin().x, 1e-6);
        assertEquals(20.0, bounds.getMax().x, 1e-6);
        // 采样点未必精确落在波峰/波谷，但应体现振幅带来的垂直跨度
        assertTrue(bounds.getMin().y <= -1.0);
        assertTrue(bounds.getMax().y >= 1.0);
    }

    @Test
    void containsPointHitsStartOfCurve() {
        SineCurveShape sine = horizontalSine();

        assertTrue(sine.containsPoint(new Vec2d(0, 0), 1.0));
    }

    @Test
    void containsPointMissesPointFarFromCurve() {
        SineCurveShape sine = horizontalSine();

        assertFalse(sine.containsPoint(new Vec2d(10, 20), 1.0));
    }

    @Test
    void containsDelegatesToDefaultTolerance() {
        SineCurveShape sine = horizontalSine();

        assertTrue(sine.contains(new Vec2d(0, 0)));
    }
}
