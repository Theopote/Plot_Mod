package com.plot.plugin.building.generation.opening;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.opening.WindowLayoutResolver.PlannedWindow;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;
import com.plot.test.building.BuildingCanvasScales;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowLayoutResolverTest {

    private static final List<Vec2d> RECT_8 = List.of(
        new Vec2d(0, 0),
        new Vec2d(8, 0),
        new Vec2d(8, 8),
        new Vec2d(0, 8)
    );

    @Test
    void shortSouthFaceWrapsToEastInsteadOfOverflowing() {
        // 南墙仅 3 格，窗宽 2：放不下时应转角续排，而非超出轮廓。
        List<Vec2d> shortSouth = List.of(
            new Vec2d(0, 0),
            new Vec2d(3, 0),
            new Vec2d(3, 8),
            new Vec2d(0, 8)
        );
        FacadeSpec facade = facade(2, 4);
        BuildingCanvasScale scale = BuildingCanvasScales.capture(shortSouth);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            shortSouth, facade, shortSouth, FacadeEdgeScope.FLOOR_LOCAL, scale, 3);

        assertFalse(windows.isEmpty());
        Polygon outer = BuildingGeometryUtils.toPolygon(shortSouth);
        for (PlannedWindow window : windows) {
            assertWindowOnPerimeter(shortSouth, scale, outer, window);
        }
    }

    @Test
    void rectangleProducesWindowsWithinPerimeter() {
        FacadeSpec facade = facade(1, 4);
        BuildingCanvasScale scale = BuildingCanvasScales.capture(RECT_8);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            RECT_8, facade, RECT_8, FacadeEdgeScope.FLOOR_LOCAL, scale, 3);

        assertTrue(windows.size() >= 2);
        Polygon outer = BuildingGeometryUtils.toPolygon(RECT_8);
        for (PlannedWindow window : windows) {
            assertWindowOnPerimeter(RECT_8, scale, outer, window);
        }
    }

    @Test
    void lastWindowMayBeSkippedOnVeryShortPerimeter() {
        List<Vec2d> mini = List.of(
            new Vec2d(0, 0),
            new Vec2d(2, 0),
            new Vec2d(2, 2),
            new Vec2d(0, 2)
        );
        FacadeSpec facade = facade(2, 3);
        BuildingCanvasScale scale = BuildingCanvasScales.capture(mini);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            mini, facade, mini, FacadeEdgeScope.FLOOR_LOCAL, scale, 3);

        double perimeterBlocks = BuildingGeometryUtils.calculateClosedPathLength(mini);
        int maxPossible = (int) Math.floor(perimeterBlocks / 3.0);
        assertTrue(windows.size() <= maxPossible + 1);
    }

    private static FacadeSpec facade(int width, int spacing) {
        return new FacadeSpec(
            new WindowPatternSpec(spacing, width, 2, 1),
            List.of(),
            List.of());
    }

    private static void assertWindowOnPerimeter(
            List<Vec2d> outerPoints,
            BuildingCanvasScale scale,
            Polygon outerPolygon,
            PlannedWindow window) {
        var center = BuildingGeometryUtils.wallSampleAtClosedDistance(
            outerPoints, window.centerArcCanvas());
        assertTrue(center != null);
        for (int w = 0; w < window.widthBlocks(); w++) {
            double lateralBlocks = w - (window.widthBlocks() - 1) / 2.0;
            double offsetCanvas = scale.blocksToCanvas(lateralBlocks, center.point(), center.tangent());
            var sample = BuildingGeometryUtils.wallSampleAtClosedDistance(
                outerPoints, window.centerArcCanvas() + offsetCanvas);
            assertTrue(sample != null);
            double boundaryDistance = Math.abs(outerPolygon.getSignedDistance(sample.point()));
            assertTrue(boundaryDistance < 0.75,
                "window sample must stay on outer wall, distance=" + boundaryDistance);
        }
    }
}
