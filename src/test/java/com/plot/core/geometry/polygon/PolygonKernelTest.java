package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.shapes.Polygon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolygonKernelTest {

    private static List<Vec2d> rectangle(double w, double h) {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(w, 0),
            new Vec2d(w, h),
            new Vec2d(0, h)
        );
    }

    private static List<Vec2d> lShape() {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(6, 0),
            new Vec2d(6, 2),
            new Vec2d(2, 2),
            new Vec2d(2, 6),
            new Vec2d(0, 6)
        );
    }

    private static List<Vec2d> uShape() {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(6, 6),
            new Vec2d(6, 2),
            new Vec2d(2, 2),
            new Vec2d(2, 6),
            new Vec2d(0, 6)
        );
    }

    private static List<Vec2d> concavePentagon() {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(14, 6),
            new Vec2d(6, 12),
            new Vec2d(-2, 5)
        );
    }

    private static List<Vec2d> narrowCorridor() {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 1),
            new Vec2d(0, 1)
        );
    }

    private static List<Vec2d> rotatedRectangle() {
        return List.of(
            new Vec2d(10, 5),
            new Vec2d(15, 10),
            new Vec2d(10, 15),
            new Vec2d(5, 10)
        );
    }

    @Test
    void validateSimplePolygonAcceptsStandardShapes() {
        assertTrue(PolygonValidator.validateSimplePolygon(rectangle(4, 4)).valid());
        assertTrue(PolygonValidator.validateSimplePolygon(rotatedRectangle()).valid());
        assertTrue(PolygonValidator.validateSimplePolygon(lShape()).valid());
        assertTrue(PolygonValidator.validateSimplePolygon(uShape()).valid());
        assertTrue(PolygonValidator.validateSimplePolygon(concavePentagon()).valid());
        assertTrue(PolygonValidator.validateSimplePolygon(narrowCorridor()).valid());
    }

    @Test
    void normalizerRemovesDuplicateAndClosingPoint() {
        List<Vec2d> raw = List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4),
            new Vec2d(0, 0)
        );
        List<Vec2d> normalized = PolygonNormalizer.normalizeOutline(raw);
        assertEquals(4, normalized.size());
        assertTrue(PolygonValidator.validateSimplePolygon(normalized).valid());
    }

    @Test
    void normalizeWindingProducesCounterClockwise() {
        List<Vec2d> cw = List.of(
            new Vec2d(0, 0),
            new Vec2d(0, 4),
            new Vec2d(4, 4),
            new Vec2d(4, 0)
        );
        List<Vec2d> ccw = PolygonNormalizer.normalizeWinding(cw, PolygonUtils.Winding.COUNTER_CLOCKWISE);
        assertTrue(PolygonUtils.isCounterClockwise(ccw));
    }

    @Test
    void inwardOffsetShrinksAxisAlignedRectangle() {
        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(rectangle(4, 4), 1);
        assertTrue(result.success(), result.warnings().toString());
        assertEquals(4, result.points().size());
        assertTrue(PolygonUtils.absoluteArea(result.points()) < PolygonUtils.absoluteArea(rectangle(4, 4)));
        assertFalse(PolygonValidator.hasSelfIntersection(result.points()));

        PolygonRegionUtils.RectBounds bounds = PolygonRegionUtils.computeBounds(result.points());
        assertEquals(1.0, bounds.minX(), 1e-3);
        assertEquals(3.0, bounds.maxX(), 1e-3);
        assertEquals(1.0, bounds.minZ(), 1e-3);
        assertEquals(3.0, bounds.maxZ(), 1e-3);
    }

    @Test
    void inwardOffsetWorksForRotatedRectangle() {
        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(rotatedRectangle(), 1);
        assertTrue(result.success(), result.warnings().toString());
        assertTrue(PolygonUtils.absoluteArea(result.points()) > 0);
        assertTrue(PolygonValidator.validateSimplePolygon(result.points()).valid());
        assertFalse(PolygonValidator.hasSelfIntersection(result.points()));
    }

    @Test
    void inwardOffsetWorksForLShape() {
        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(lShape(), 1);
        assertTrue(result.success(), result.warnings().toString());
        assertTrue(PolygonUtils.absoluteArea(result.points()) > 0);
        assertFalse(PolygonValidator.hasSelfIntersection(result.points()));
        assertTrue(PolygonBoolean.contains(lShape(), result.points().getFirst()));
    }

    @Test
    void inwardOffsetWorksForUShape() {
        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(uShape(), 1);
        assertTrue(result.success(), result.warnings().toString());
        assertTrue(PolygonUtils.absoluteArea(result.points()) > 0);
        assertFalse(PolygonValidator.hasSelfIntersection(result.points()));
    }

    @Test
    void inwardOffsetWorksForConcavePolygon() {
        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(concavePentagon(), 1);
        assertTrue(result.success(), result.warnings().toString());
        assertTrue(PolygonUtils.absoluteArea(result.points()) > 0);
        assertFalse(PolygonValidator.hasSelfIntersection(result.points()));
    }

    @Test
    void excessiveInwardOffsetOnNarrowPolygonFailsSafely() {
        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(narrowCorridor(), 2);
        assertFalse(result.success());
        assertTrue(result.points().isEmpty());
    }

    @Test
    void offsetResultHasNoNonFiniteCoordinates() {
        for (List<Vec2d> shape : List.of(
            rectangle(10, 6), lShape(), uShape(), concavePentagon(), rotatedRectangle())) {
            PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(shape, 1);
            if (result.success()) {
                assertTrue(PolygonValidator.hasFiniteCoordinates(result.points()));
            }
        }
    }

    @Test
    void polygonBooleanContainsAndIntersects() {
        List<Vec2d> square = rectangle(4, 4);
        assertTrue(PolygonBoolean.contains(square, new Vec2d(2, 2)));
        assertFalse(PolygonBoolean.contains(square, new Vec2d(10, 10)));
        assertTrue(PolygonBoolean.intersectsSegment(
            square, new Vec2d(-1, 2), new Vec2d(5, 2)));
    }

    @Test
    void polygonRasterizerMatchesRegionUtils() {
        List<Vec2d> rect = rectangle(4, 3);
        assertEquals(
            PolygonRegionUtils.collectFootprintCellCenters(rect),
            PolygonRasterizer.collectCellCenters(rect));
    }

    @Test
    void polygonTriangulatorCoversRectangle() {
        PolygonTriangulator.TriangulationResult result =
            PolygonTriangulator.triangulate(rectangle(4, 4));
        assertTrue(result.success(), result.issue());
        assertEquals(2, result.triangles().size());
    }

    @Test
    void buildingGeometryUtilsDelegatesToKernel() {
        List<Vec2d> inner = com.plot.plugin.building.BuildingGeometryUtils.offsetInward(rectangle(6, 6), 1);
        assertEquals(4, inner.size());
        assertEquals(16.0, PolygonUtils.absoluteArea(inner), 1e-3);
        assertTrue(new Polygon(inner).contains(new Vec2d(3, 3)));
    }
}
