package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BezierUtilsTest {

    @Test
    void convertPointsToCurveDataExtractsAnchorsAndControlPairs() {
        List<Vec2d> points = List.of(
                new Vec2d(0, 0),
                new Vec2d(0, 10),
                new Vec2d(10, 10),
                new Vec2d(10, 0)
        );

        BezierUtils.BezierData data = BezierUtils.convertPointsToCurveData(points, false);

        assertEquals(2, data.getAnchors().size());
        assertEquals(0.0, data.getAnchors().get(0).x, 1e-6);
        assertEquals(10.0, data.getAnchors().get(1).x, 1e-6);
        assertEquals(1, data.getControls().size());
        assertEquals(0.0, data.getControls().get(0)[0].x, 1e-6);
        assertEquals(10.0, data.getControls().get(0)[0].y, 1e-6);
        assertEquals(10.0, data.getControls().get(0)[1].x, 1e-6);
        assertFalse(data.shouldClose());
    }

    @Test
    void validateBezierDataAcceptsWellFormedCurve() {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d[]> controls = List.of(new Vec2d[]{new Vec2d(0, 10), new Vec2d(10, 10)});

        assertTrue(BezierUtils.validateBezierData(new BezierUtils.BezierData(anchors, controls, false)));
    }

    @Test
    void validateBezierDataRejectsInsufficientAnchors() {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0));
        List<Vec2d[]> controls = List.of();

        assertFalse(BezierUtils.validateBezierData(new BezierUtils.BezierData(anchors, controls, false)));
    }

    @Test
    void validateBezierDataRejectsMismatchedControlPairCount() {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(20, 0));
        List<Vec2d[]> controls = List.of(new Vec2d[]{new Vec2d(0, 10), new Vec2d(10, 10)});

        assertFalse(BezierUtils.validateBezierData(new BezierUtils.BezierData(anchors, controls, false)));
    }

    @Test
    void evaluateCubicBezierReturnsEndpointsAtZeroAndOne() {
        Vec2d p0 = new Vec2d(0, 0);
        Vec2d p1 = new Vec2d(0, 20);
        Vec2d p2 = new Vec2d(10, 20);
        Vec2d p3 = new Vec2d(10, 0);

        Vec2d start = BezierUtils.evaluateCubicBezier(p0, p1, p2, p3, 0.0);
        Vec2d end = BezierUtils.evaluateCubicBezier(p0, p1, p2, p3, 1.0);

        assertEquals(0.0, start.x, 1e-6);
        assertEquals(0.0, start.y, 1e-6);
        assertEquals(10.0, end.x, 1e-6);
        assertEquals(0.0, end.y, 1e-6);
    }

    @Test
    void evaluateCubicBezierMidpointArchesAboveStraightLine() {
        Vec2d p0 = new Vec2d(0, 0);
        Vec2d p1 = new Vec2d(0, 20);
        Vec2d p2 = new Vec2d(10, 20);
        Vec2d p3 = new Vec2d(10, 0);

        Vec2d midpoint = BezierUtils.evaluateCubicBezier(p0, p1, p2, p3, 0.5);

        assertEquals(5.0, midpoint.x, 1e-6);
        assertTrue(midpoint.y > 5.0, "cubic midpoint should rise above the chord");
    }

    @Test
    void approximateBezierLengthUsesControlPolygon() {
        Vec2d p0 = new Vec2d(0, 0);
        Vec2d p1 = new Vec2d(0, 10);
        Vec2d p2 = new Vec2d(10, 10);
        Vec2d p3 = new Vec2d(10, 0);

        double length = BezierUtils.approximateBezierLength(p0, p1, p2, p3);

        assertEquals(30.0, length, 1e-6);
    }

    @Test
    void calculateApproximateLengthSumsSegments() {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d[]> controls = List.of(new Vec2d[]{new Vec2d(0, 10), new Vec2d(10, 10)});

        double length = BezierUtils.calculateApproximateLength(anchors, controls);

        assertTrue(length > 10.0, "curved segment should be longer than the chord");
        assertTrue(length < 30.0, "length should stay within control polygon bounds");
    }

    @Test
    void createFromSeparatePointsBuildsControlPairs() {
        List<Vec2d> anchors = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d> flatControls = List.of(new Vec2d(0, 10), new Vec2d(10, 10));

        BezierUtils.BezierData data = BezierUtils.createFromSeparatePoints(anchors, flatControls, true);

        assertEquals(2, data.getAnchors().size());
        assertEquals(1, data.getControls().size());
        assertTrue(data.shouldClose());
        assertTrue(BezierUtils.validateBezierData(data));
    }

    @Test
    void calculateOptimalStepsClampsToConfiguredRange() {
        Vec2d p0 = new Vec2d(0, 0);
        Vec2d p1 = new Vec2d(0, 5);
        Vec2d p2 = new Vec2d(5, 5);
        Vec2d p3 = new Vec2d(5, 0);

        int steps = BezierUtils.calculateOptimalSteps(p0, p1, p2, p3, 8, 64);

        assertTrue(steps >= 8);
        assertTrue(steps <= 64);
    }

    @Test
    void convertPointsToCurveDataReturnsEmptyForInsufficientPoints() {
        BezierUtils.BezierData data = BezierUtils.convertPointsToCurveData(new ArrayList<>(), false);

        assertTrue(data.getAnchors().isEmpty());
        assertTrue(data.getControls().isEmpty());
        assertFalse(BezierUtils.validateBezierData(data));
    }
}
