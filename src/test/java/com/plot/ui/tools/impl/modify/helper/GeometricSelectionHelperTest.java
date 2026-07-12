package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometricSelectionHelperTest {

    @Test
    void circleInteriorDoesNotHitOutline() {
        Shape circle = new CircleShape(new Vec2d(0, 0), 10);
        assertFalse(GeometricSelectionHelper.isPointNearShapeOutline(circle, new Vec2d(0, 0), 3));
        assertFalse(GeometricSelectionHelper.isPointNearShapeOutline(circle, new Vec2d(5, 0), 3));
    }

    @Test
    void circleStrokeHitsOutline() {
        Shape circle = new CircleShape(new Vec2d(0, 0), 10);
        assertTrue(GeometricSelectionHelper.isPointNearShapeOutline(circle, new Vec2d(10, 0), 3));
        assertTrue(GeometricSelectionHelper.isPointNearShapeOutline(circle, new Vec2d(0, 10), 3));
    }

    @Test
    void rectangleInteriorDoesNotHitOutline() {
        Shape rectangle = new RectangleShape(new Vec2d(0, 0), 20, 10, 0);
        assertFalse(GeometricSelectionHelper.isPointNearShapeOutline(rectangle, new Vec2d(10, 5), 3));
    }

    @Test
    void rectangleEdgeHitsOutline() {
        Shape rectangle = new RectangleShape(new Vec2d(0, 0), 20, 10, 0);
        assertTrue(GeometricSelectionHelper.isPointNearShapeOutline(rectangle, new Vec2d(10, 0), 3));
        assertTrue(GeometricSelectionHelper.isPointNearShapeOutline(rectangle, new Vec2d(0, 5), 3));
    }

    @Test
    void ellipseInteriorDoesNotHitOutline() {
        Shape ellipse = new EllipseShape(new Vec2d(0, 0), 20, 10, 0);
        assertFalse(GeometricSelectionHelper.isPointNearShapeOutline(ellipse, new Vec2d(0, 0), 3));
        assertFalse(GeometricSelectionHelper.isPointNearShapeOutline(ellipse, new Vec2d(5, 0), 3));
    }

    @Test
    void ellipseStrokeHitsOutline() {
        Shape ellipse = new EllipseShape(new Vec2d(0, 0), 20, 10, 0);
        assertTrue(GeometricSelectionHelper.isPointNearShapeOutline(ellipse, new Vec2d(20, 0), 3));
        assertTrue(GeometricSelectionHelper.isPointNearShapeOutline(ellipse, new Vec2d(0, 10), 3));
    }
}
