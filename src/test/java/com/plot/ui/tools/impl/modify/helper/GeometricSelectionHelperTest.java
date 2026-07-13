package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void polylineOutlineHitsSegmentAndMissesInterior() {
        Shape polyline = new com.plot.core.geometry.shapes.PolylineShape(
                List.of(new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 10)),
                false);

        assertTrue(GeometricSelectionHelper.isPointNearShapeOutline(polyline, new Vec2d(10, 0), 3));
        assertFalse(GeometricSelectionHelper.isPointNearShapeOutline(polyline, new Vec2d(10, 5), 3));
    }

    @Test
    void arcOutlineHitsCurveAndMissesInterior() {
        Shape arc = new com.plot.core.geometry.shapes.ArcShape(
                new Vec2d(0, 0), 10, 0, Math.PI / 2);

        assertTrue(GeometricSelectionHelper.isPointNearShapeOutline(arc, new Vec2d(10, 0), 3));
        assertFalse(GeometricSelectionHelper.isPointNearShapeOutline(arc, new Vec2d(3, 3), 3));
    }

    @Test
    void isPointInPolygonDetectsInsideAndOutside() {
        List<Vec2d> square = List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10));

        assertTrue(GeometricSelectionHelper.isPointInPolygon(new Vec2d(5, 5), square));
        assertFalse(GeometricSelectionHelper.isPointInPolygon(new Vec2d(15, 5), square));
    }

    @Test
    void rectangleSelectionDistinguishesWindowAndCrossingModes() {
        Shape line = new com.plot.core.geometry.shapes.LineShape(new Vec2d(2, 2), new Vec2d(8, 8));
        Vec2d topLeft = new Vec2d(0, 0);
        Vec2d bottomRight = new Vec2d(10, 10);
        Vec2d smallBoxEnd = new Vec2d(5, 5);

        assertTrue(GeometricSelectionHelper.isShapeInRectangleSelection(
                line, topLeft, bottomRight, true));
        assertTrue(GeometricSelectionHelper.isShapeInRectangleSelection(
                line, topLeft, smallBoxEnd, false));
        assertFalse(GeometricSelectionHelper.isShapeInRectangleSelection(
                line, topLeft, smallBoxEnd, true));
    }

    @Test
    void lassoIntersectsShapeWhenSegmentsCross() {
        Shape line = new com.plot.core.geometry.shapes.LineShape(new Vec2d(0, 5), new Vec2d(10, 5));
        List<Vec2d> lasso = List.of(
                new Vec2d(5, 0),
                new Vec2d(5, 10),
                new Vec2d(6, 10),
                new Vec2d(6, 0));

        assertTrue(GeometricSelectionHelper.isLassoIntersectsShape(line, lasso));
    }
}
