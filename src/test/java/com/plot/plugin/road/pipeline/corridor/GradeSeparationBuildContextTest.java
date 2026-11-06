package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeSeparationBuildContextTest {

    @Test
    void excavationCanClearUnderpassAirWhileFillMustKeepItOpen() {
        var polygon = List.of(new Vec2d(-5, -5), new Vec2d(5, -5), new Vec2d(5, 5), new Vec2d(-5, 5));
        var context = new GradeSeparationBuildContext(true,
            List.of(new GradeSeparationBuildContext.ProtectedUnderpassZone(polygon, 70, 64)));
        assertTrue(context.protects(new Vec2d(0, 0), 69));
        assertFalse(context.protectsClearance(new Vec2d(0, 0), 69));
        assertTrue(context.protectsClearance(new Vec2d(0, 0), 64));
    }

    @Test
    void protectsUnderpassLayerInsidePolygon() {
        List<Vec2d> square = List.of(
            new Vec2d(-5, -5),
            new Vec2d(5, -5),
            new Vec2d(5, 5),
            new Vec2d(-5, 5));
        GradeSeparationBuildContext context = new GradeSeparationBuildContext(
            true,
            List.of(new GradeSeparationBuildContext.ProtectedUnderpassZone(square, 73)));

        assertTrue(context.protects(new Vec2d(0, 0), 70));
        assertTrue(context.protects(new Vec2d(0, 0), 73));
        assertFalse(context.protects(new Vec2d(0, 0), 74));
        assertFalse(context.protects(new Vec2d(10, 0), 70));
    }

    @Test
    void maxOpenClearTopYDoesNotAffectFarCorridorColumns() {
        List<Vec2d> square = List.of(
            new Vec2d(-5, -5),
            new Vec2d(5, -5),
            new Vec2d(5, 5),
            new Vec2d(-5, 5));
        // floorY=72 → supportBottom=71 → cap=70
        GradeSeparationBuildContext underpass = new GradeSeparationBuildContext(
            false,
            List.of(new GradeSeparationBuildContext.ProtectedUnderpassZone(square, 72)));

        assertEquals(70, underpass.maxOpenClearTopY(new Vec2d(0, 0)));
        assertEquals(70, underpass.maxOpenClearTopY(new Vec2d(5.5, 0)),
            "one-block residual outside polygon still under deck projection");
        assertNull(underpass.maxOpenClearTopY(new Vec2d(500, 500)),
            "far corridor columns must keep local clear height");
        assertNull(underpass.maxOpenClearTopY(new Vec2d(20, 0)),
            "columns well outside junction polygon must not inherit deckCap");
    }
}
