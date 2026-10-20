package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrthogonalConstraintTest {

    private static final double EPS = 1e-9;

    @Test
    void snapsNearlyHorizontalToHorizontal() {
        Vec2d start = new Vec2d(0, 0);
        Vec2d end = new Vec2d(10, 1);
        Vec2d constrained = OrthogonalConstraint.apply45Degree(start, end);

        assertEquals(end.distance(start), constrained.distance(start), EPS);
        assertEquals(0.0, constrained.y, 0.1);
        assertEquals(Math.signum(end.x), Math.signum(constrained.x), EPS);
    }

    @Test
    void snapsNear45DegreesToExact45() {
        Vec2d start = new Vec2d(0, 0);
        Vec2d end = new Vec2d(10, 11);
        Vec2d constrained = OrthogonalConstraint.apply45Degree(start, end);

        double length = end.distance(start);
        double expected = length / Math.sqrt(2.0);
        assertEquals(expected, constrained.x, 0.2);
        assertEquals(expected, constrained.y, 0.2);
    }

    @Test
    void returnsEndWhenLengthIsZero() {
        Vec2d start = new Vec2d(3, 4);
        Vec2d end = new Vec2d(3, 4);
        Vec2d constrained = OrthogonalConstraint.apply45Degree(start, end);
        assertEquals(end.x, constrained.x, EPS);
        assertEquals(end.y, constrained.y, EPS);
    }
}
