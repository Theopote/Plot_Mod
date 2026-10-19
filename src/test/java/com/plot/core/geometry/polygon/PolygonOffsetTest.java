package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolygonOffsetTest {

    private static List<Vec2d> axisAlignedSquare(double size) {
        return List.of(
                new Vec2d(0, 0),
                new Vec2d(size, 0),
                new Vec2d(size, size),
                new Vec2d(0, size));
    }

    @Test
    void offsetOutwardExpandsAxisAlignedSquare() {
        List<Vec2d> square = axisAlignedSquare(10);
        double sourceArea = PolygonUtils.absoluteArea(square);

        PolygonOffset.OffsetResult result = PolygonOffset.offsetOutward(square, 2.0);

        assertTrue(result.success());
        assertFalse(result.points().isEmpty());
        assertEquals(4, result.points().size());
        assertEquals(14.0 * 14.0, PolygonUtils.absoluteArea(result.points()), 1e-3);
        assertTrue(PolygonUtils.absoluteArea(result.points()) > sourceArea + 1.0);
    }

    @Test
    void offsetInwardShrinksAxisAlignedSquare() {
        List<Vec2d> square = axisAlignedSquare(10);
        double sourceArea = PolygonUtils.absoluteArea(square);

        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(square, 2.0);

        assertTrue(result.success());
        assertFalse(result.points().isEmpty());
        assertEquals(4, result.points().size());
        assertEquals(6.0 * 6.0, PolygonUtils.absoluteArea(result.points()), 1e-3);
        assertTrue(PolygonUtils.absoluteArea(result.points()) + 1.0 < sourceArea);
    }

    @Test
    void offsetRejectsTooFewVertices() {
        PolygonOffset.OffsetResult result = PolygonOffset.offset(
                List.of(new Vec2d(0, 0), new Vec2d(1, 0)),
                1.0);

        assertFalse(result.success());
        assertEquals("too_few_vertices", result.warnings().getFirst());
    }

    @Test
    void zeroDistanceReturnsNormalizedOutline() {
        List<Vec2d> square = axisAlignedSquare(10);

        PolygonOffset.OffsetResult result = PolygonOffset.offset(square, 0.0);

        assertTrue(result.success());
        assertEquals(PolygonNormalizer.normalizeOutline(square), result.points());
    }

    @Test
    void inwardOffsetOnConcavePolygonBevelsReflexCorners() {
        List<Vec2d> lShape = List.of(
                new Vec2d(0, 0),
                new Vec2d(6, 0),
                new Vec2d(6, 2),
                new Vec2d(2, 2),
                new Vec2d(2, 6),
                new Vec2d(0, 6));

        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(lShape, 0.5);

        assertTrue(result.success());
        assertTrue(result.warnings().contains("reflex_bevel"));
        assertTrue(PolygonUtils.absoluteArea(result.points()) < PolygonUtils.absoluteArea(lShape));
    }

    @Test
    void excessiveInwardOffsetCollapsesPolygon() {
        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(axisAlignedSquare(10), 20.0);

        assertFalse(result.success());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void offsetInwardHelperRejectsNonPositiveDistance() {
        List<Vec2d> square = axisAlignedSquare(10);

        PolygonOffset.OffsetResult result = PolygonOffset.offsetInward(square, 0.0);

        assertTrue(result.success());
        assertEquals(square.size(), result.points().size());
    }
}
