package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingSurfaceResolverTest {

    @Test
    void flatModeProducesConstantElevation() {
        GradingRegion region = new GradingRegion(square(10));
        region.setSurfaceMode(GradingSurfaceMode.FLAT);
        region.setAutoBalance(false);
        region.setManualTargetElevation(72);

        List<Vec2d> centers = List.of(new Vec2d(2, 2), new Vec2d(8, 8));
        List<Integer> heights = List.of(70, 74);

        GradingSurfaceResolver.ResolvedSurface surface = GradingSurfaceResolver.resolve(
            region, centers, heights, null);

        assertTrue(surface.plane().isFlat());
        assertEquals(72, surface.plane().evaluateAt(0, 0));
        assertEquals(72, surface.elevationMin());
        assertEquals(72, surface.elevationMax());
    }

    @Test
    void fixedSlopeChangesElevationAlongDirection() {
        GradingRegion region = new GradingRegion(square(10));
        region.setSurfaceMode(GradingSurfaceMode.FIXED_SLOPE);
        region.setSlopeDirectionDegrees(0.0);
        region.setSlopePitchRatio(4);
        region.setSlopeAnchorCanvas(new Vec2d(0, 0));
        region.setSlopeAnchorElevation(64);

        GradingSurfaceResolver.ResolvedSurface surface = GradingSurfaceResolver.resolve(
            region, List.of(), List.of(), null);

        assertFalse(surface.plane().isFlat());
        assertEquals(64, surface.plane().evaluateAt(0, 0));
        assertEquals(65, surface.plane().evaluateAt(4, 0));
        assertEquals(64, surface.plane().evaluateAt(0, 8));
    }

    @Test
    void threePointPlanePassesThroughControls() {
        GradingPlane plane = GradingSurfaceResolver.solveThreePointPlane(
            0, 0, 60,
            10, 0, 64,
            0, 10, 62);
        assertNotNull(plane);

        assertEquals(60, plane.evaluateAt(0, 0));
        assertEquals(64, plane.evaluateAt(10, 0));
        assertEquals(62, plane.evaluateAt(0, 10));
    }

    @Test
    void balancePlaneInterceptUsesGroundResidualsNotPlaneTargets() {
        // 平面 y=64 平坦；地面有高有低 → 残差非零，平衡后 intercept 应移动
        GradingPlane base = GradingPlane.flat(64);
        List<GradingSurfaceResolver.HeightSample> samples = List.of(
            new GradingSurfaceResolver.HeightSample(0, 0, 70),
            new GradingSurfaceResolver.HeightSample(1, 0, 70),
            new GradingSurfaceResolver.HeightSample(2, 0, 70),
            new GradingSurfaceResolver.HeightSample(3, 0, 70),
            new GradingSurfaceResolver.HeightSample(4, 0, 60),
            new GradingSurfaceResolver.HeightSample(5, 0, 60));

        GradingPlane balanced = GradingSurfaceResolver.balancePlaneIntercept(base, samples, 1.0f);
        assertNotNull(balanced);
        // 高地更多 → 平衡标高倾向抬高（相对 64 的残差多为正），intercept 应上升
        assertTrue(balanced.intercept() > base.intercept() - 0.01);
        // 旧算法对「全为 64 的平面目标」求平衡会得到 64、delta=0，无法抬升
        assertTrue(Math.abs(balanced.intercept() - 64.0) > 0.5
            || balanced.evaluateAt(0, 0) != 64);
    }

    @Test
    void balancePlaneInterceptOnMatchingTerrainKeepsPlane() {
        GradingPlane base = GradingPlane.flat(65);
        List<GradingSurfaceResolver.HeightSample> samples = List.of(
            new GradingSurfaceResolver.HeightSample(0, 0, 65),
            new GradingSurfaceResolver.HeightSample(5, 0, 65),
            new GradingSurfaceResolver.HeightSample(0, 5, 65),
            new GradingSurfaceResolver.HeightSample(5, 5, 65));
        GradingPlane balanced = GradingSurfaceResolver.balancePlaneIntercept(base, samples, 1.1f);
        assertEquals(65, balanced.evaluateAt(0, 0));
    }

    @Test
    void fitSlopeUsesLeastSquaresTrend() {
        GradingRegion region = new GradingRegion(square(10));
        region.setSurfaceMode(GradingSurfaceMode.FIT_SLOPE);
        region.setFitSlopeBalanceCutFill(false);

        List<GradingSurfaceResolver.HeightSample> samples = List.of(
            new GradingSurfaceResolver.HeightSample(0, 0, 60),
            new GradingSurfaceResolver.HeightSample(10, 0, 64),
            new GradingSurfaceResolver.HeightSample(0, 10, 62),
            new GradingSurfaceResolver.HeightSample(10, 10, 66));

        GradingPlane plane = GradingSurfaceResolver.fitLeastSquaresPlane(samples);
        assertFalse(plane.isFlat());
        for (GradingSurfaceResolver.HeightSample sample : samples) {
            int target = plane.evaluateAt(sample.worldX(), sample.worldZ());
            assertTrue(Math.abs(target - sample.groundY()) <= 2);
        }
    }

    private static List<Vec2d> square(double size) {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size));
    }
}
