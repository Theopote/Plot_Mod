package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPolygonBoundaryDistanceTest {

    @Test
    void triangleDistanceIsSmallerThanAxisAlignedBounds() {
        List<Vec2d> triangle = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(5, 5));
        PolygonRegionUtils.RectBounds bounds = PolygonRegionUtils.computeBounds(triangle);

        double polygonDistance = PatternPolygonBoundaryDistance.distanceToPolygonBoundary(
            9.0, 1.0, triangle, List.of(), bounds);
        double boundsDistance = PatternPolygonBoundaryDistance.distanceToPolygonBoundary(
            9.0, 1.0, List.of(), List.of(), bounds);

        assertTrue(polygonDistance < boundsDistance);
        assertTrue(polygonDistance < 1.0);
        assertEquals(1.0, boundsDistance, 1e-6);
    }

    @Test
    void holeBoundaryCountsAsNearestEdge() {
        List<Vec2d> outer = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10));
        List<Vec2d> hole = List.of(
            new Vec2d(4, 4),
            new Vec2d(6, 4),
            new Vec2d(6, 6),
            new Vec2d(4, 6));

        double nearHole = PatternPolygonBoundaryDistance.distanceToPolygonBoundary(
            3.5, 5.0, outer, List.of(hole), null);
        double nearOuter = PatternPolygonBoundaryDistance.distanceToPolygonBoundary(
            0.5, 5.0, outer, List.of(hole), null);

        assertEquals(0.5, nearHole, 1e-6);
        assertEquals(0.5, nearOuter, 1e-6);
    }
}
