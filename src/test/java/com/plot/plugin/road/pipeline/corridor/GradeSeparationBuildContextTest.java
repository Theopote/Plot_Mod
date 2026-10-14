package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeSeparationBuildContextTest {

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
}
