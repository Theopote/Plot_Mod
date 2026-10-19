package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EllipseShapeTest {

    @Test
    void getBoundingBoxIsAxisAlignedForUnrotatedEllipse() {
        EllipseShape ellipse = new EllipseShape(new Vec2d(0, 0), 10, 5, 0);

        BoundingBox bounds = ellipse.getBoundingBox();

        assertEquals(-10.0, bounds.getMin().x, 1e-6);
        assertEquals(-5.0, bounds.getMin().y, 1e-6);
        assertEquals(10.0, bounds.getMax().x, 1e-6);
        assertEquals(5.0, bounds.getMax().y, 1e-6);
    }

    @Test
    void containsPointHitsCircumferenceForStroke() {
        EllipseShape ellipse = new EllipseShape(new Vec2d(0, 0), 10, 5, 0);
        ellipse.getStyle().getFillStyle().setVisible(false);

        assertTrue(ellipse.containsPoint(new Vec2d(10, 0), 0.5));
        assertTrue(ellipse.containsPoint(new Vec2d(0, 5), 0.5));
    }

    @Test
    void containsPointMissesPointOutsideEllipse() {
        EllipseShape ellipse = new EllipseShape(new Vec2d(0, 0), 10, 5, 0);
        ellipse.getStyle().getFillStyle().setVisible(false);

        assertFalse(ellipse.containsPoint(new Vec2d(12, 0), 0.5));
        assertFalse(ellipse.containsPoint(new Vec2d(0, 0), 0.5));
    }

    @Test
    void containsPointHitsInteriorWhenFilled() {
        EllipseShape ellipse = new EllipseShape(new Vec2d(0, 0), 10, 5, 0);

        assertTrue(ellipse.containsPoint(new Vec2d(0, 0), 0.5));
        assertTrue(ellipse.containsPoint(new Vec2d(5, 0), 0.5));
    }
}
