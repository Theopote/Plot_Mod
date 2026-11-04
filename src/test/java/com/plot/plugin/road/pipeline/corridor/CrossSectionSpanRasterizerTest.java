package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionSpanRasterizerTest {

    @Test
    void crossesCenterlineWhenProjectedOnOppositeSide() {
        Vec2d center = new Vec2d(0, 0);
        Vec2d normal = new Vec2d(1, 0);
        assertTrue(CrossSectionSpanRasterizer.crossesCenterline(
            center, normal, new Vec2d(-1, 0), 2.0, 1.0));
        assertFalse(CrossSectionSpanRasterizer.crossesCenterline(
            center, normal, new Vec2d(2, 0), 2.0, 1.0));
        assertFalse(CrossSectionSpanRasterizer.crossesCenterline(
            center, normal, new Vec2d(-0.1, 0), 2.0, 1.0));
    }
}
