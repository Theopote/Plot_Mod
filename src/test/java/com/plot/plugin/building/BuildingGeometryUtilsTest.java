package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.BuildingGenerateCommand;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    void rotatedRectangleIsNotSlopedRoofEligible() {
        List<Vec2d> diamond = List.of(
            new Vec2d(10, 5),
            new Vec2d(15, 10),
            new Vec2d(10, 15),
            new Vec2d(5, 10)
        );
        assertFalse(BuildingGeometryUtils.isSlopedRoofEligible(diamond));
        assertFalse(BuildingGeometryUtils.isAxisAlignedRectangle(diamond, 1e-3));
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
    void gableRidgeIsHighestAndEavesAreZero() {
        BuildingGeometryUtils.RectBounds bounds = new BuildingGeometryUtils.RectBounds(0, 20, 0, 10);
        int ridge = BuildingRoofGenerator.computeGableRise(10, 5, bounds, true, 2);
        int eave = BuildingRoofGenerator.computeGableRise(10, 0, bounds, true, 2);
        assertEquals(0, eave, "Eave should have zero rise");
        assertEquals(2, ridge, "Ridge center rise = floor((depth/2) / pitch) = floor(5/2) = 2");
    }

    @Test
    void gableRiseIsSymmetricAcrossRidge() {
        BuildingGeometryUtils.RectBounds bounds = new BuildingGeometryUtils.RectBounds(0, 20, 0, 10);
        int north = BuildingRoofGenerator.computeGableRise(10, 2, bounds, true, 2);
        int south = BuildingRoofGenerator.computeGableRise(10, 8, bounds, true, 2);
        assertEquals(north, south);
    }

    @Test
    void hipCenterHigherThanCorners() {
        BuildingGeometryUtils.RectBounds bounds = new BuildingGeometryUtils.RectBounds(0, 20, 0, 10);
        int center = BuildingRoofGenerator.computeHipRise(10, 5, bounds, 2);
        int corner = BuildingRoofGenerator.computeHipRise(0, 0, bounds, 2);
        assertEquals(0, corner);
        assertTrue(center > corner, "Hip roof center should rise above corners");
        assertEquals(2, center);
    }
}
