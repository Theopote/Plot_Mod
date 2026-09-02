package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
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
    void holedRectangleExcludesInteriorHole() {
        List<Vec2d> outer = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        );
        List<Vec2d> hole = List.of(
            new Vec2d(3, 3),
            new Vec2d(7, 3),
            new Vec2d(7, 7),
            new Vec2d(3, 7)
        );
        RegionGeometry geometry = RegionGeometry.of(outer, List.of(hole));

        assertTrue(geometry.contains(new Vec2d(1.5, 1.5)));
        assertFalse(geometry.contains(new Vec2d(5.5, 5.5)));
        assertEquals(84.0, geometry.area(), 1e-6);
        assertEquals(84, PolygonRegionUtils.collectFootprintCellCenters(geometry).size());
    }

    @Test
    void signedAreaSubtractsHoles() {
        List<Vec2d> outer = List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)
        );
        List<Vec2d> hole = List.of(
            new Vec2d(1, 1),
            new Vec2d(3, 1),
            new Vec2d(3, 3),
            new Vec2d(1, 3)
        );
        assertEquals(12.0, PolygonRegionUtils.computeSignedArea(outer, List.of(hole)), 1e-6);
    }
}
