package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RectangleShapeTest {

    @Test
    void getBoundingBoxMatchesCornerAndSize() {
        RectangleShape rectangle = new RectangleShape(new Vec2d(2, 3), 10, 6, 0);

        BoundingBox bounds = rectangle.getBoundingBox();

        assertEquals(2.0, bounds.getMin().x, 1e-6);
        assertEquals(3.0, bounds.getMin().y, 1e-6);
        assertEquals(12.0, bounds.getMax().x, 1e-6);
        assertEquals(9.0, bounds.getMax().y, 1e-6);
    }

    @Test
    void containsPointHitsEdgeWithoutFill() {
        RectangleShape rectangle = new RectangleShape(new Vec2d(0, 0), 10, 10, 0);

        assertTrue(rectangle.containsPoint(new Vec2d(5, 0), 0.5));
        assertTrue(rectangle.containsPoint(new Vec2d(0, 5), 0.5));
    }

    @Test
    void containsPointHitsInteriorWhenFilled() {
        RectangleShape rectangle = new RectangleShape(new Vec2d(0, 0), 10, 10, 0);
        rectangle.getStyle().getFillStyle().setVisible(true);

        assertTrue(rectangle.containsPoint(new Vec2d(5, 5), 0.5));
        assertFalse(rectangle.containsPoint(new Vec2d(15, 5), 0.5));
    }
}
