package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkGeometryUtilsTest {

    @Test
    void adoptableShapesIncludePolygonRectangleCircleAndEllipse() {
        List<Shape> shapes = List.of(
            new Polygon(List.of(new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(4, 4))),
            new RectangleShape(new Vec2d(0, 0), 6, 4, 0),
            new CircleShape(new Vec2d(5, 5), 3),
            new EllipseShape(new Vec2d(8, 8), 4, 2, 0)
        );

        assertEquals(4, EarthworkGeometryUtils.findAdoptableRegions(shapes).size());
    }

    @Test
    void openPolylineIsAdoptableAndAutoClosedForArea() {
        PolylineShape openTriangle = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(2, 3)),
            false);

        assertTrue(EarthworkGeometryUtils.isAdoptableRegion(openTriangle));
        List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(openTriangle);
        assertEquals(3, points.size());
        assertEquals(6.0, Math.abs(com.plot.plugin.earthwork.model.GradingRegion.signedArea(points)), 1e-6);
    }

    @Test
    void closedPolylineWithDuplicateClosingPointIsNormalized() {
        PolylineShape closed = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(4, 0), new Vec2d(4, 4), new Vec2d(0, 0)),
            true);

        List<Vec2d> points = EarthworkGeometryUtils.extractRegionPoints(closed);
        assertEquals(3, points.size());
    }

    @Test
    void lineShapeIsNotAdoptable() {
        assertFalse(EarthworkGeometryUtils.isAdoptableRegion(
            new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))));
    }

    @Test
    void collinearPolylineIsNotAdoptable() {
        PolylineShape lineLike = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(2, 0), new Vec2d(4, 0)),
            false);
        assertFalse(EarthworkGeometryUtils.isAdoptableRegion(lineLike));
    }

    @Test
    void extractRegionPointsPreservesShapeOutline() {
        RectangleShape rectangle = new RectangleShape(new Vec2d(0, 0), 10, 8, 0);
        List<Vec2d> rectanglePoints = EarthworkGeometryUtils.extractRegionPoints(rectangle);
        assertTrue(rectanglePoints.size() >= 4);

        CircleShape circle = new CircleShape(new Vec2d(12, 12), 5);
        List<Vec2d> circlePoints = EarthworkGeometryUtils.extractRegionPoints(circle);
        assertTrue(circlePoints.size() >= 3);

        EllipseShape ellipse = new EllipseShape(new Vec2d(20, 20), 6, 3, 0);
        List<Vec2d> ellipsePoints = EarthworkGeometryUtils.extractRegionPoints(ellipse);
        assertTrue(ellipsePoints.size() >= 3);
    }

    @Test
    void footprintCellCollectionCoversRectangleInterior() {
        List<Vec2d> rectangle = List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 3),
            new Vec2d(0, 3)
        );
        List<Vec2d> centers = EarthworkGeometryUtils.collectFootprintCellCenters(rectangle);
        assertEquals(12, centers.size());
    }

    @Test
    void sampleCentersRespectGridSize() {
        List<Vec2d> rectangle = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        );
        List<Vec2d> sampled = EarthworkGeometryUtils.collectSampleCenters(rectangle, 5);
        assertFalse(sampled.isEmpty());
        assertTrue(sampled.size() < EarthworkGeometryUtils.collectFootprintCellCenters(rectangle).size());
    }
}
