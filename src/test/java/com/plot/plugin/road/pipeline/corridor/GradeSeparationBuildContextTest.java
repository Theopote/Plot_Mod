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
    void maxOpenClearTopYAppliesNearProtectedFootprintWithSkewMargin() {
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
            "skew residual just outside polygon still capped within margin");
        assertEquals(70, underpass.maxOpenClearTopY(new Vec2d(8.5, 0)),
            "within DECK_CAP_PLAN_MARGIN_BLOCKS of polygon edge");
        assertNull(underpass.maxOpenClearTopY(new Vec2d(20, 0)),
            "distant approach on long lower edge must not inherit deckCap");
        assertNull(new GradeSeparationBuildContext(true, List.of(
                new GradeSeparationBuildContext.ProtectedUnderpassZone(square, 72)))
                .maxOpenClearTopY(new Vec2d(0, 0)),
            "elevated edges do not publish open-clearance deckCap");
        assertNull(GradeSeparationBuildContext.NONE.maxOpenClearTopY(new Vec2d(0, 0)));
    }
}
