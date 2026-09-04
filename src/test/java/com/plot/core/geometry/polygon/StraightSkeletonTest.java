package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightSkeletonTest {

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
            new Vec2d(10, 0),
            new Vec2d(10, 4),
            new Vec2d(4, 4),
            new Vec2d(4, 10),
            new Vec2d(0, 10)
        );
    }

    private static List<Vec2d> narrowLShape() {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(6, 0),
            new Vec2d(6, 2),
            new Vec2d(2, 2),
            new Vec2d(2, 6),
            new Vec2d(0, 6)
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
    void rectangleSkeletonHasPeakAtCenter() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(rectangle(20, 10));
        assertTrue(skeleton.success());
        assertTrue(skeleton.maxSkeletalTime() >= 4.0);
        assertTrue(skeleton.skeletalTime(new Vec2d(10.5, 5.5)) >= 4.0);
        assertNotNull(skeleton.primaryRidgeDirection());
    }

    @Test
    void lShapeSkeletonProducesInteriorRidge() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(lShape());
        assertTrue(skeleton.success());
        assertTrue(skeleton.maxSkeletalTime() >= 2.0);
        assertTrue(skeleton.skeletalTime(new Vec2d(2.5, 2.5)) > 0.0);
        assertFalse(skeleton.nodes().isEmpty());
    }

    @Test
    void rotatedRectangleSkeletonSucceeds() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(rotatedRectangle());
        assertTrue(skeleton.success());
        assertTrue(skeleton.maxSkeletalTime() >= 2.0);
    }

    @Test
    void narrowLShapeHasLimitedSkeletalTime() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(narrowLShape());
        assertTrue(skeleton.success());
        assertTrue(skeleton.maxSkeletalTime() < 2.0);
    }
}
