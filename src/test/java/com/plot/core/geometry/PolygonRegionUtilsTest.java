package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolygonRegionUtilsTest {

    @Test
    void polygonContainsCenterOfLargeRectangle() {
        List<Vec2d> points = List.of(
            new Vec2d(0, 0),
            new Vec2d(40, 0),
            new Vec2d(40, 30),
            new Vec2d(0, 30)
        );
        Polygon polygon = PolygonRegionUtils.toPolygon(points);
        assertTrue(polygon.contains(new Vec2d(20, 15)),
            "Center of rectangle must be inside when using Polygon.contains()");
    }

    @Test
    void footprintCellCollectionCoversRectangleInterior() {
        List<Vec2d> rectangle = List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 3),
            new Vec2d(0, 3)
        );
        List<Vec2d> centers = PolygonRegionUtils.collectFootprintCellCenters(rectangle);
        assertEquals(12, centers.size());
    }

    @Test
    void computeCentroidReturnsAveragePoint() {
        Vec2d centroid = PolygonRegionUtils.computeCentroid(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        assertEquals(5.0, centroid.x, 1e-6);
        assertEquals(5.0, centroid.y, 1e-6);
    }

    @Test
    void openPolylineOutlineIsTreatedAsClosedForSampling() {
        List<Vec2d> openTriangle = List.of(new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(2, 3));
        List<Vec2d> centers = PolygonRegionUtils.collectFootprintCellCenters(openTriangle);
        assertFalse(centers.isEmpty());
    }
}
