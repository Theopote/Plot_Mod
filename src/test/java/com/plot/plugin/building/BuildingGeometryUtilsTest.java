package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingGeometryUtilsTest {

    @Test
    void polygonContainsCenterOfLargeRectangle() {
        List<Vec2d> points = List.of(
            new Vec2d(0, 0),
            new Vec2d(40, 0),
            new Vec2d(40, 30),
            new Vec2d(0, 30)
        );
        Polygon polygon = BuildingGeometryUtils.toPolygon(points);
        assertTrue(polygon.contains(new Vec2d(20, 15)),
            "Center of rectangle must be inside when using Polygon.contains()");
    }

    @Test
    void detectRectangularFootprint() {
        List<Vec2d> rect = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        );
        assertTrue(BuildingGeometryUtils.detectRectangular(rect));

        List<Vec2d> pentagon = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(12, 5),
            new Vec2d(5, 10),
            new Vec2d(-2, 4)
        );
        assertFalse(BuildingGeometryUtils.detectRectangular(pentagon));
    }

    @Test
    void windowSamplingAlongSquareProducesExpectedCount() {
        List<Vec2d> square = List.of(
            new Vec2d(0, 0),
            new Vec2d(16, 0),
            new Vec2d(16, 16),
            new Vec2d(0, 16)
        );
        List<BuildingGeometryUtils.WallSample> samples =
            BuildingGeometryUtils.sampleAlongWallSegments(square, 4.0);

        assertEquals(16, samples.size(), "Each 16-block edge should get ~4 windows at spacing 4");
        long segmentZero = samples.stream().filter(s -> s.segmentIndex() == 0).count();
        assertEquals(4, segmentZero);
    }

    @Test
    void gableRiseIsSymmetric() {
        BuildingGeometryUtils.RectBounds bounds = new BuildingGeometryUtils.RectBounds(0, 20, 0, 10);
        int left = BuildingRoofGenerator.computeGableRise(5, 2, bounds, true, 5, 2);
        int right = BuildingRoofGenerator.computeGableRise(5, 8, bounds, true, 5, 2);
        assertEquals(left, right);
        assertEquals(1, left);
    }
}
