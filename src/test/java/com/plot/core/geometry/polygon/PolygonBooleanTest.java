package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolygonBooleanTest {

    private static List<Vec2d> unitSquare() {
        return List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10));
    }

    @Test
    void containsDetectsInteriorPoint() {
        assertTrue(PolygonBoolean.contains(unitSquare(), new Vec2d(5, 5)));
    }

    @Test
    void containsRejectsExteriorPoint() {
        assertFalse(PolygonBoolean.contains(unitSquare(), new Vec2d(15, 5)));
    }

    @Test
    void intersectsSegmentDetectsCrossingEdge() {
        assertTrue(PolygonBoolean.intersectsSegment(
                unitSquare(),
                new Vec2d(-5, 5),
                new Vec2d(15, 5)));
    }

    @Test
    void intersectsSegmentIgnoresDisjointSegment() {
        assertFalse(PolygonBoolean.intersectsSegment(
                unitSquare(),
                new Vec2d(20, 0),
                new Vec2d(20, 10)));
    }

    @Test
    void intersectsDetectsOverlappingPolygons() {
        List<Vec2d> shifted = List.of(
                new Vec2d(5, 0),
                new Vec2d(15, 0),
                new Vec2d(15, 10),
                new Vec2d(5, 10));

        assertTrue(PolygonBoolean.intersects(unitSquare(), shifted));
    }

    @Test
    void intersectsDetectsSeparatedPolygons() {
        List<Vec2d> far = List.of(
                new Vec2d(20, 0),
                new Vec2d(30, 0),
                new Vec2d(30, 10),
                new Vec2d(20, 10));

        assertFalse(PolygonBoolean.intersects(unitSquare(), far));
    }
}
