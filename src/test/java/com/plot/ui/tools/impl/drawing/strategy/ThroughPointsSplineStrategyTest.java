package com.plot.ui.tools.impl.drawing.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.ui.tools.impl.drawing.config.SplineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThroughPointsSplineStrategyTest {

    private ThroughPointsSplineStrategy strategy;
    private SplineConfig config;

    @BeforeEach
    void setUp() {
        strategy = new ThroughPointsSplineStrategy();
        config = new SplineConfig();
    }

    @Test
    void generateCurvePassesThroughInputPoints() {
        List<Vec2d> points = List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 5),
                new Vec2d(20, 0),
                new Vec2d(30, 8));

        BezierCurveShape curve = strategy.generateCurve(points, config);

        assertNotNull(curve);
        assertFalse(curve.isClosed());
        assertEquals(points.size(), curve.getAnchorPoints().size());
        assertEquals(0.0, curve.getAnchorPoints().getFirst().x, 1e-6);
        assertEquals(30.0, curve.getAnchorPoints().getLast().x, 1e-6);
    }

    @Test
    void generateClosedCurveCreatesClosedShape() {
        List<Vec2d> points = List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10));

        BezierCurveShape curve = strategy.generateClosedCurve(points, config);

        assertNotNull(curve);
        assertTrue(curve.isClosed());
        assertEquals(3, curve.getAnchorPoints().stream().distinct().count());
    }

    @Test
    void generateCurveReturnsNullForInsufficientPoints() {
        assertNull(strategy.generateCurve(List.of(new Vec2d(0, 0)), config));
    }

    @Test
    void isValidInputRequiresAtLeastTwoPoints() {
        assertFalse(strategy.isValidInput(List.of(new Vec2d(0, 0))));
        assertTrue(strategy.isValidInput(List.of(new Vec2d(0, 0), new Vec2d(1, 1))));
    }
}
