package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingPlaneDiscretizationTest {

    @Test
    void slopedPlaneUsesEvenStepsAlongDirection() {
        GradingPlane plane = new GradingPlane(0.25, 0.0, 64.0);
        List<Integer> rises = new ArrayList<>();
        int previous = plane.evaluateAt(0, 0);
        for (int x = 1; x <= 40; x++) {
            int current = plane.evaluateAt(x, 0);
            if (current > previous) {
                rises.add(x);
            }
            previous = current;
        }
        assertTrue(rises.size() >= 8);
        for (int i = 1; i < rises.size(); i++) {
            int gap = rises.get(i) - rises.get(i - 1);
            assertTrue(gap == 4 || gap == 5, "unexpected rise gap: " + gap);
        }
    }

    @Test
    void resolverBuildsVoxelProfileForFixedSlopeRegion() {
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(40, 0),
            new Vec2d(40, 10),
            new Vec2d(0, 10)
        ));
        region.setSurfaceMode(GradingSurfaceMode.SINGLE_SLOPE_PLANE);
        region.setSlopeDirectionDegrees(0.0);
        region.setSlopePitchRatio(4);
        region.setSlopeAnchorCanvas(new Vec2d(0, 0));
        region.setSlopeAnchorElevation(64);

        GradingSurfaceResolver.ResolvedSurface surface = GradingSurfaceResolver.resolve(
            region, List.of(), List.of(), null);

        assertTrue(surface.plane().voxelProfile() != null);
        assertEquals(64, surface.plane().evaluateAt(0, 0));
        assertEquals(65, surface.plane().evaluateAt(4, 0));
    }
}
