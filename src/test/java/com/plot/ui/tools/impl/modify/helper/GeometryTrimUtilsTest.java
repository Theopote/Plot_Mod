package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryTrimUtilsTest {

    private GeometryTrimUtils utils;

    @BeforeEach
    void setUp() {
        utils = new GeometryTrimUtils();
    }

    @Test
    void calculateLineLineIntersectionFindsCrossingSegments() {
        Vec2d p1 = new Vec2d(0, 0);
        Vec2d p2 = new Vec2d(10, 10);
        Vec2d p3 = new Vec2d(0, 10);
        Vec2d p4 = new Vec2d(10, 0);

        Vec2d intersection = utils.calculateLineLineIntersection(p1, p2, p3, p4);

        assertNotNull(intersection);
        assertEquals(5.0, intersection.x, 1e-6);
        assertEquals(5.0, intersection.y, 1e-6);
    }

    @Test
    void calculateLineLineIntersectionReturnsNullForParallelSegments() {
        Vec2d p1 = new Vec2d(0, 0);
        Vec2d p2 = new Vec2d(10, 0);
        Vec2d p3 = new Vec2d(0, 5);
        Vec2d p4 = new Vec2d(10, 5);

        assertNull(utils.calculateLineLineIntersection(p1, p2, p3, p4));
    }

    @Test
    void calculateLineLineIntersectionReturnsNullWhenSegmentsDoNotOverlap() {
        Vec2d p1 = new Vec2d(0, 0);
        Vec2d p2 = new Vec2d(2, 0);
        Vec2d p3 = new Vec2d(5, 0);
        Vec2d p4 = new Vec2d(10, 0);

        assertNull(utils.calculateLineLineIntersection(p1, p2, p3, p4));
    }

    @Test
    void findIntersectionsBetweenLineAndCircle() {
        Shape line = new LineShape(new Vec2d(-10, 0), new Vec2d(10, 0));
        Shape circle = new CircleShape(new Vec2d(0, 0), 5);
        List<Shape> boundaries = List.of(circle);

        List<Vec2d> intersections = utils.findIntersections(line, boundaries);

        assertEquals(2, intersections.size());
        assertTrue(intersections.stream().anyMatch(p -> Math.abs(p.x + 5.0) < 1e-3 && Math.abs(p.y) < 1e-3));
        assertTrue(intersections.stream().anyMatch(p -> Math.abs(p.x - 5.0) < 1e-3 && Math.abs(p.y) < 1e-3));
    }

    @Test
    void findIntersectionsReturnsEmptyForNullOrEmptyBoundaries() {
        Shape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));

        assertTrue(utils.findIntersections(line, null).isEmpty());
        assertTrue(utils.findIntersections(line, List.of()).isEmpty());
    }

    @Test
    void removeDuplicatePointsCollapsesNearDuplicates() {
        List<Vec2d> points = List.of(
                new Vec2d(0, 0),
                new Vec2d(0.0000001, 0),
                new Vec2d(10, 0)
        );

        List<Vec2d> deduped = utils.removeDuplicatePoints(points);

        assertEquals(2, deduped.size());
    }

    @Test
    void normalizeAngleWrapsIntoZeroToTwoPi() {
        assertEquals(0.0, utils.normalizeAngle(0.0), 1e-9);
        assertEquals(Math.PI, utils.normalizeAngle(3 * Math.PI), 1e-9);
        assertEquals(Math.PI / 2, utils.normalizeAngle(-3 * Math.PI / 2), 1e-9);
    }

    @Test
    void isAngleInRangeHandlesWrappedIntervals() {
        assertTrue(utils.isAngleInRange(0.1, 0.0, Math.PI));
        assertFalse(utils.isAngleInRange(1.5 * Math.PI, 0.0, Math.PI));
        assertTrue(utils.isAngleInRange(0.1, 3 * Math.PI / 2, Math.PI / 2));
    }

    @Test
    void isPointOnShapeDetectsLineAndCirclePoints() {
        Shape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        Shape circle = new CircleShape(new Vec2d(0, 0), 10);

        assertTrue(utils.isPointOnShape(line, new Vec2d(5, 0)));
        assertFalse(utils.isPointOnShape(line, new Vec2d(5, 10)));
        assertTrue(utils.isPointOnShape(circle, new Vec2d(10, 0)));
        assertFalse(utils.isPointOnShape(circle, new Vec2d(0, 0)));
    }

    @Test
    void calculateCenterAndRadius() {
        List<Vec2d> points = List.of(new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10));

        Vec2d center = utils.calculateCenter(points);
        double radius = utils.calculateRadius(points, center);

        assertEquals(20.0 / 3.0, center.x, 1e-6);
        assertEquals(10.0 / 3.0, center.y, 1e-6);
        assertTrue(radius > 0.0);
    }

    @Test
    void splitShapeAtIntersectionsSplitsLineIntoSegments() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        List<Vec2d> intersections = List.of(new Vec2d(4, 0), new Vec2d(7, 0));

        List<Shape> segments = utils.splitShapeAtIntersections(line, intersections);

        assertEquals(3, segments.size());
        assertTrue(segments.stream().allMatch(shape -> shape instanceof LineShape));
    }

    @Test
    void splitShapeAtIntersectionsReturnsOriginalWhenNoIntersections() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));

        List<Shape> segments = utils.splitShapeAtIntersections(line, List.of());

        assertEquals(1, segments.size());
        assertEquals(line, segments.getFirst());
    }

    @Test
    void removeDuplicateShapesDeduplicatesEquivalentPolylines() {
        PolylineShape left = new PolylineShape(List.of(new Vec2d(0, 0), new Vec2d(10, 0)), false);
        PolylineShape right = new PolylineShape(List.of(new Vec2d(0, 0), new Vec2d(10, 0)), false);
        List<Shape> shapes = new ArrayList<>();
        shapes.add(left);
        shapes.add(right);

        List<Shape> unique = utils.removeDuplicateShapes(shapes);

        assertEquals(1, unique.size());
    }

    @Test
    void createDenseCirclePointsSamplesFullCircle() {
        CircleShape circle = new CircleShape(new Vec2d(0, 0), 10);

        List<Vec2d> points = utils.createDenseCirclePoints(circle);

        assertEquals(36, points.size());
        assertEquals(10.0, points.getFirst().x, 1e-6);
        assertEquals(0.0, points.getFirst().y, 1e-6);
        assertTrue(points.stream().allMatch(p -> Math.abs(p.distance(new Vec2d(0, 0)) - 10.0) < 1e-3));
    }
}
