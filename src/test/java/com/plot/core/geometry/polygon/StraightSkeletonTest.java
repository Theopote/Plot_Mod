package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Straight Skeleton 不变量：拓扑、屋脊连通、谷线、无反坡、旋转/绕序一致。
 */
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

    /** 旋转菱形（轴对齐矩形旋转 45°）。 */
    private static List<Vec2d> diamond() {
        return List.of(
            new Vec2d(10, 5),
            new Vec2d(15, 10),
            new Vec2d(10, 15),
            new Vec2d(5, 10)
        );
    }

    private static List<Vec2d> uShape() {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 8),
            new Vec2d(9, 8),
            new Vec2d(9, 3),
            new Vec2d(3, 3),
            new Vec2d(3, 8),
            new Vec2d(0, 8)
        );
    }

    static Stream<List<Vec2d>> representativeFootprints() {
        return Stream.of(rectangle(20, 10), lShape(), diamond(), uShape(), narrowLShape());
    }

    @Test
    void rectangleSkeletonHasPeakAtCenter() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(rectangle(20, 10));
        StraightSkeletonInvariants.assertSuccessful(skeleton);
        assertTrue(skeleton.maxSkeletalTime() >= 4.0);
        assertTrue(skeleton.skeletalTime(new Vec2d(10.5, 5.5)) >= 4.0);
    }

    @Test
    void lShapeSkeletonProducesInteriorRidge() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(lShape());
        StraightSkeletonInvariants.assertSuccessful(skeleton);
        assertTrue(skeleton.maxSkeletalTime() >= 2.0);
        assertTrue(skeleton.skeletalTime(new Vec2d(2.5, 2.5)) > 0.0);
    }

    @Test
    void rotatedRectangleSkeletonSucceeds() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(diamond());
        StraightSkeletonInvariants.assertSuccessful(skeleton);
        assertTrue(skeleton.maxSkeletalTime() >= 2.0);
    }

    @Test
    void narrowLShapeHasLimitedSkeletalTime() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(narrowLShape());
        StraightSkeletonInvariants.assertSuccessful(skeleton);
        assertTrue(skeleton.maxSkeletalTime() < 2.0);
    }

    @ParameterizedTest
    @MethodSource("representativeFootprints")
    void skeletonTopologyIsValid(List<Vec2d> footprint) {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(footprint);
        StraightSkeletonInvariants.assertTopology(skeleton);
        StraightSkeletonInvariants.assertRidgeConnected(skeleton, 2.5);
    }

    @ParameterizedTest
    @MethodSource("representativeFootprints")
    void distanceFieldHasNoReverseSlope(List<Vec2d> footprint) {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(footprint);
        StraightSkeletonInvariants.assertDistanceFieldLipschitz(skeleton, 1.0);
    }

    @ParameterizedTest
    @MethodSource("representativeFootprints")
    void hipHeightFieldHasNoPillarsOrHoles(List<Vec2d> footprint) {
        StraightSkeletonInvariants.assertHipHeightFieldInvariants(footprint, 1);
    }

    @Test
    void lShapeValleyIsLowerThanArmCenters() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(lShape());
        // 凹角 (4,4) 内侧 vs 两臂中心
        StraightSkeletonInvariants.assertValleyLowerThanArmCenters(
            skeleton,
            new Vec2d(3.5, 3.5),
            new Vec2d(2.5, 2.5),
            new Vec2d(7.5, 2.5)
        );
    }

    @Test
    void rectangleRidgeIsConnectedAlongLongAxis() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(rectangle(24, 8));
        StraightSkeletonInvariants.assertTopology(skeleton);
        StraightSkeletonInvariants.assertRidgeConnected(skeleton, 2.5);
        // 屋脊节点应靠近水平中线
        for (StraightSkeleton.SkeletonNode node : skeleton.nodes()) {
            assertTrue(Math.abs(node.point().y - 4.0) <= 2.0,
                "ridge node far from midline: " + node.point());
        }
    }

    @ParameterizedTest
    @MethodSource("representativeFootprints")
    void rotationPreservesSkeletalField(List<Vec2d> footprint) {
        StraightSkeletonInvariants.assertRotationConsistent(footprint, 90);
        StraightSkeletonInvariants.assertRotationConsistent(footprint, 45);
    }

    @ParameterizedTest
    @MethodSource("representativeFootprints")
    void windingDoesNotChangeSkeletalField(List<Vec2d> footprint) {
        StraightSkeletonInvariants.assertWindingConsistent(footprint);
    }

    @Test
    void failedOnDegeneratePolygon() {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(List.of(
            new Vec2d(0, 0),
            new Vec2d(1, 0)
        ));
        assertEquals(false, skeleton.success());
    }

    @Test
    void vertexShiftDoesNotFlipWindingSemantics() {
        List<Vec2d> base = rectangle(16, 8);
        List<Vec2d> shifted = new ArrayList<>();
        shifted.add(base.get(2));
        shifted.add(base.get(3));
        shifted.add(base.get(0));
        shifted.add(base.get(1));
        StraightSkeleton.Result a = StraightSkeleton.compute(base);
        StraightSkeleton.Result b = StraightSkeleton.compute(shifted);
        StraightSkeletonInvariants.assertSuccessful(a);
        StraightSkeletonInvariants.assertSuccessful(b);
        assertEquals(a.maxSkeletalTime(), b.maxSkeletalTime(), 1e-6);
    }
}
