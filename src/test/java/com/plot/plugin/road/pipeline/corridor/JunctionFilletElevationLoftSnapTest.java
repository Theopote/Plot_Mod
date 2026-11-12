package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JunctionFilletElevationLoftSnapTest {
    @Test
    void snapNearIntegerPlanFixesFloorBiasFromReconstructedSharp() {
        Vec2d sharp = new Vec2d(1.9999999999999991, 2.0);
        Vec2d snapped = JunctionFilletElevationLoft.snapNearIntegerPlan(sharp);
        assertEquals(2.0, snapped.x, 0.0);
        assertEquals(2.0, snapped.y, 0.0);
        assertEquals(2, (int) Math.floor(snapped.x));
    }
}
