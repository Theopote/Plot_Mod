package com.plot.plugin.road.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonUtils;
import com.plot.core.geometry.polygon.PolygonValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCorridorGeometryBuilderTest {

    private static final double HALF_WIDTH = 3.0;

    @Test
    void horizontalStraightRoad_hasParallelBoundariesAtHalfWidth() {
        List<Vec2d> centerline = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, HALF_WIDTH);

        assertFalse(geometry.closed());
        assertEquals(1, geometry.fillContours().size());
        assertEquals(4, geometry.fillContours().getFirst().size());
        assertEquals(HALF_WIDTH, geometry.leftBoundary().getFirst().y, 1e-6);
        assertEquals(-HALF_WIDTH, geometry.rightBoundary().getFirst().y, 1e-6);
        assertFalse(PolygonValidator.hasSelfIntersection(geometry.fillContours().getFirst()));
    }

    @Test
    void rightAngleTurn_joinsWithoutSelfIntersection() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, HALF_WIDTH);

        assertFalse(geometry.closed());
        assertFalse(PolygonValidator.hasSelfIntersection(geometry.fillContours().getFirst()));
        assertEquals(3.0, distancePointToSegment(
            new Vec2d(5, HALF_WIDTH), centerline.get(0), centerline.get(1)), 0.2);
        assertEquals(3.0, distancePointToSegment(
            new Vec2d(10 + HALF_WIDTH, 5), centerline.get(1), centerline.get(2)), 0.2);
    }

    @Test
    void acuteTurn_respectsMiterLimit() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 10),
            new Vec2d(0, 0),
            new Vec2d(10, -2));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, HALF_WIDTH);

        assertFalse(PolygonValidator.hasSelfIntersection(geometry.fillContours().getFirst()));
        double maxMiter = maxVertexDistanceFromCorner(
            geometry.leftBoundary(), centerline.get(1));
        assertTrue(maxMiter <= HALF_WIDTH * PolygonUtils.DEFAULT_MITER_LIMIT + 0.5);
    }

    @Test
    void obtuseTurn_keepsConsistentOffsetDistance() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(20, 5));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, HALF_WIDTH);

        assertFalse(PolygonValidator.hasSelfIntersection(geometry.fillContours().getFirst()));
        assertTrue(geometry.leftBoundary().size() >= 3);
        assertTrue(geometry.rightBoundary().size() >= 3);
    }

    @Test
    void sCurve_doesNotSwapLeftAndRightBoundaries() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(5, 5),
            new Vec2d(10, 0),
            new Vec2d(15, -5));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, HALF_WIDTH);

        assertFalse(PolygonValidator.hasSelfIntersection(geometry.fillContours().getFirst()));
        assertTrue(meanDistanceToCenterline(geometry.leftBoundary(), centerline) > 0);
        assertTrue(meanDistanceToCenterline(geometry.rightBoundary(), centerline) > 0);
    }

    @Test
    void uShape_handlesInnerCornerWithoutCrossing() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 10),
            new Vec2d(10, 10),
            new Vec2d(10, 0));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, HALF_WIDTH);

        assertFalse(PolygonValidator.hasSelfIntersection(geometry.fillContours().getFirst()));
    }

    @Test
    void closedRectangle_producesOuterAndInnerRings() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 10),
            new Vec2d(0, 10),
            new Vec2d(0, 0));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, HALF_WIDTH, true);

        assertTrue(geometry.closed());
        assertEquals(2, geometry.fillContours().size());
        assertEquals(4, geometry.outerContour().size());
        assertEquals(4, geometry.innerHole().size());
        assertTrue(PolygonUtils.absoluteArea(geometry.outerContour())
            > PolygonUtils.absoluteArea(geometry.innerHole()));
        assertFalse(PolygonValidator.hasSelfIntersection(geometry.outerContour()));
        assertFalse(PolygonValidator.hasSelfIntersection(geometry.innerHole()));
    }

    @Test
    void rotatedRectangle_closedOffsetKeepsFourCorners() {
        List<Vec2d> centerline = List.of(
            new Vec2d(2, 0),
            new Vec2d(12, 2),
            new Vec2d(10, 12),
            new Vec2d(0, 10),
            new Vec2d(2, 0));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, 2.0, true);

        assertTrue(geometry.closed());
        assertTrue(geometry.outerContour().size() >= 4);
        assertTrue(geometry.innerHole().size() >= 4);
    }

    @Test
    void closedTriangle_hasValidHole() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(6, 10),
            new Vec2d(0, 0));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, 1.5, true);

        assertTrue(geometry.closed());
        assertEquals(3, geometry.outerContour().size());
        assertEquals(3, geometry.innerHole().size());
    }

    @Test
    void closedCircleSample_staysClosed() {
        List<Vec2d> centerline = sampleCircle(0, 0, 10, 24);
        centerline = new ArrayList<>(centerline);
        centerline.add(centerline.getFirst().copy());
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, 2.0, true);

        assertTrue(geometry.closed());
        assertTrue(geometry.outerContour().size() >= 8);
        assertTrue(geometry.innerHole().size() >= 8);
        assertTrue(PolygonUtils.absoluteArea(geometry.outerContour())
            > PolygonUtils.absoluteArea(geometry.innerHole()));
    }

    @Test
    void duplicatePoints_doNotProduceNaN() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 0),
            new Vec2d(5, 0),
            new Vec2d(5, 0),
            new Vec2d(10, 0));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, HALF_WIDTH);

        assertFalse(geometry.leftBoundary().isEmpty());
        assertTrue(PolygonValidator.hasFiniteCoordinates(geometry.leftBoundary()));
    }

    @Test
    void veryShortSegment_remainsStable() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(1e-8, 0),
            new Vec2d(10, 0));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, HALF_WIDTH);

        assertTrue(geometry.leftBoundary().size() >= 2);
        assertTrue(PolygonValidator.hasFiniteCoordinates(geometry.leftBoundary()));
    }

    @Test
    void screenshotRegression_sharpTurnPolyline() {
        List<Vec2d> centerline = List.of(
            new Vec2d(5, 20),
            new Vec2d(5, 10),
            new Vec2d(12, 2),
            new Vec2d(20, -4));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, 4.0);

        assertFalse(PolygonValidator.hasSelfIntersection(geometry.fillContours().getFirst()));
        assertTrue(geometry.leftBoundary().size() >= 4);
        assertTrue(geometry.rightBoundary().size() >= 4);
    }

    @Test
    void screenshotRegression_closedRectangle() {
        List<Vec2d> centerline = List.of(
            new Vec2d(0, 0),
            new Vec2d(30, 0),
            new Vec2d(30, 15),
            new Vec2d(0, 15),
            new Vec2d(0, 0));
        RoadCorridorGeometry geometry = RoadCorridorGeometryBuilder.build(centerline, 4.0, true);

        assertTrue(geometry.closed());
        assertEquals(2, geometry.fillContours().size());
        assertTrue(allPointsInsideRing(geometry.innerHole(), geometry.outerContour()));
    }

    private static List<Vec2d> sampleCircle(double cx, double cy, double radius, int segments) {
        List<Vec2d> points = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2.0 * i) / segments;
            points.add(new Vec2d(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius));
        }
        return points;
    }

    private static double distancePointToSegment(Vec2d point, Vec2d start, Vec2d end) {
        Vec2d direction = end.subtract(start);
        double lengthSquared = direction.lengthSquared();
        if (lengthSquared < 1e-12) {
            return point.distance(start);
        }
        double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(direction) / lengthSquared));
        Vec2d projection = start.add(direction.multiply(t));
        return point.distance(projection);
    }

    private static double meanDistanceToCenterline(List<Vec2d> boundary, List<Vec2d> centerline) {
        double sum = 0.0;
        int count = 0;
        for (Vec2d point : boundary) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < centerline.size() - 1; i++) {
                best = Math.min(best, distancePointToSegment(point, centerline.get(i), centerline.get(i + 1)));
            }
            sum += best;
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private static double maxVertexDistanceFromCorner(List<Vec2d> boundary, Vec2d corner) {
        double max = 0.0;
        for (Vec2d point : boundary) {
            max = Math.max(max, point.distance(corner));
        }
        return max;
    }

    private static boolean allPointsInsideRing(List<Vec2d> inner, List<Vec2d> outer) {
        for (Vec2d point : inner) {
            if (!pointInsideRing(point, outer)) {
                return false;
            }
        }
        return true;
    }

    private static boolean pointInsideRing(Vec2d point, List<Vec2d> ring) {
        boolean inside = false;
        int count = ring.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            Vec2d pi = ring.get(i);
            Vec2d pj = ring.get(j);
            if (((pi.y > point.y) != (pj.y > point.y))
                    && (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y + 1e-12) + pi.x)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
