package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircleShapeTest {

    @Test
    void getBoundingBoxIsCenteredSquare() {
        CircleShape circle = new CircleShape(new Vec2d(5, 5), 3);

        BoundingBox bounds = circle.getBoundingBox();

        assertEquals(2.0, bounds.getMin().x, 1e-6);
        assertEquals(2.0, bounds.getMin().y, 1e-6);
        assertEquals(8.0, bounds.getMax().x, 1e-6);
        assertEquals(8.0, bounds.getMax().y, 1e-6);
    }

    @Test
    void containsPointHitsCircumferenceForStroke() {
        CircleShape circle = new CircleShape(new Vec2d(0, 0), 10);
        circle.getStyle().getFillStyle().setVisible(false);

        assertTrue(circle.containsPoint(new Vec2d(10, 0), 0.5));
        assertTrue(circle.containsPoint(new Vec2d(0, 10), 0.5));
    }

    @Test
    void containsPointMissesPointOutsideCircle() {
        CircleShape circle = new CircleShape(new Vec2d(0, 0), 10);
        circle.getStyle().getFillStyle().setVisible(false);

        assertFalse(circle.containsPoint(new Vec2d(12, 0), 0.5));
        assertFalse(circle.containsPoint(new Vec2d(0, 0), 0.5));
    }

    @Test
    void containsPointHitsInteriorWhenFilled() {
        CircleShape circle = new CircleShape(new Vec2d(0, 0), 10);

        assertTrue(circle.containsPoint(new Vec2d(0, 0), 0.5));
        assertTrue(circle.containsPoint(new Vec2d(5, 0), 0.5));
    }
}
