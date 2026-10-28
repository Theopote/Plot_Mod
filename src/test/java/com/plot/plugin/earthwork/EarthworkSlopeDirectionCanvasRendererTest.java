package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.grading.GradingPlane;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EarthworkSlopeDirectionCanvasRendererTest {

    @Test
    void fixedSlopeUsesExplicitDirection() {
        GradingRegion region = new GradingRegion(square(20));
        region.setSurfaceMode(GradingSurfaceMode.SINGLE_SLOPE_PLANE);
        region.setSlopeDirectionDegrees(135.0);

        assertEquals(135.0, EarthworkSlopeDirectionCanvasRenderer.resolveArrowDirectionDegrees(region), 1e-6);
    }

    @Test
    void threePointRisingAlongPlusXPointsEast() {
        GradingRegion region = new GradingRegion(square(20));
        region.setSurfaceMode(GradingSurfaceMode.THREE_POINT_PLANE);
        region.setThreePointControl(0, new Vec2d(0, 0), 60);
        region.setThreePointControl(1, new Vec2d(10, 0), 70);
        region.setThreePointControl(2, new Vec2d(0, 10), 60);

        assertEquals(0.0, EarthworkSlopeDirectionCanvasRenderer.resolveArrowDirectionDegrees(region), 1e-6);
    }

    @Test
    void threePointRisingAlongPlusZPointsNorth() {
        GradingRegion region = new GradingRegion(square(20));
        region.setSurfaceMode(GradingSurfaceMode.THREE_POINT_PLANE);
        region.setThreePointControl(0, new Vec2d(0, 0), 60);
        region.setThreePointControl(1, new Vec2d(10, 0), 60);
        region.setThreePointControl(2, new Vec2d(0, 10), 70);

        assertEquals(90.0, EarthworkSlopeDirectionCanvasRenderer.resolveArrowDirectionDegrees(region), 1e-6);
    }

    @Test
    void levelThreePointPlaneHidesArrow() {
        GradingRegion region = new GradingRegion(square(20));
        region.setSurfaceMode(GradingSurfaceMode.THREE_POINT_PLANE);
        region.setThreePointControl(0, new Vec2d(0, 0), 64);
        region.setThreePointControl(1, new Vec2d(10, 0), 64);
        region.setThreePointControl(2, new Vec2d(0, 10), 64);

        assertNull(EarthworkSlopeDirectionCanvasRenderer.resolveArrowDirectionDegrees(region));
    }

    @Test
    void gradingPlaneUphillMatchesAtan2Convention() {
        assertEquals(0.0, new GradingPlane(0.5, 0.0, 64).uphillDirectionDegreesOrNull(), 1e-6);
        assertEquals(90.0, new GradingPlane(0.0, 0.25, 64).uphillDirectionDegreesOrNull(), 1e-6);
        assertNull(GradingPlane.flat(64).uphillDirectionDegreesOrNull());
    }

    private static List<Vec2d> square(double size) {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size));
    }
}
