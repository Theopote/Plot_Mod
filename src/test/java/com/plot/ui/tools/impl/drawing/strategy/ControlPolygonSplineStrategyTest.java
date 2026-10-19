package com.plot.ui.tools.impl.drawing.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.ui.tools.impl.drawing.config.SplineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPolygonSplineStrategyTest {

    private ControlPolygonSplineStrategy strategy;
    private SplineConfig config;

    @BeforeEach
    void setUp() {
        strategy = new ControlPolygonSplineStrategy();
        config = new SplineConfig();
    }

    @Test
    void generateCurveFromFourControlPoints() {
        List<Vec2d> points = List.of(
                new Vec2d(0, 0),
                new Vec2d(0, 10),
                new Vec2d(10, 10),
                new Vec2d(10, 0));

        BezierCurveShape curve = strategy.generateCurve(points, config);

        assertNotNull(curve);
        assertTrue(curve.getAnchorPoints().size() >= 2);
    }

    @Test
    void generateFallbackCurveForTwoPoints() {
        BezierCurveShape curve = strategy.generateCurve(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0)), config);

        assertNotNull(curve);
        assertEquals(2, curve.getAnchorPoints().size());
    }

    @Test
    void generateClosedCurveRequiresThreePoints() {
        assertNull(strategy.generateClosedCurve(
                List.of(new Vec2d(0, 0), new Vec2d(5, 0)), config));

        BezierCurveShape curve = strategy.generateClosedCurve(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10)), config);

        assertNotNull(curve);
        assertTrue(curve.isClosed());
    }
}
