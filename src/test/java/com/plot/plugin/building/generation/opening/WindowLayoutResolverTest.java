package com.plot.plugin.building.generation.opening;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver;
import com.plot.plugin.building.generation.opening.WindowLayoutResolver.PlannedWindow;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;
import com.plot.test.building.BuildingCanvasScales;
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
    void spacingLeavesSpandrelBetweenWindowsOnStraightFace() {
        FacadeSpec facade = facade(1, 4);
        BuildingCanvasScale scale = BuildingCanvasScales.capture(RECT_8);
        var plate = FloorPlateGeometryResolver.resolve(
            FloorPlateSpec.of(0, 0, RECT_8), 1, scale);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            RECT_8, plate.outerPolygon(), plate.innerPolygon(), plate.outerCells(),
            facade, RECT_8, FacadeEdgeScope.FLOOR_LOCAL, scale, 3);

        assertTrue(windows.size() >= 2);
        PlannedWindow first = windows.get(0);
        PlannedWindow second = windows.get(1);
        double z0 = first.columnCenters().getFirst().y;
        double z1 = second.columnCenters().getFirst().y;
        if (Math.abs(z0 - z1) < 0.1) {
            double x0 = first.columnCenters().getFirst().x;
            double x1 = second.columnCenters().getFirst().x;
            int gapColumns = (int) Math.round(x1 - x0) - 1;
            assertEquals(3, gapColumns, "spacing 4 width 1 => 3 spandrel columns on same face");
        }
    }

    @Test
    void rectangleProducesWindowsOnWallColumns() {
        FacadeSpec facade = facade(1, 4);
        BuildingCanvasScale scale = BuildingCanvasScales.capture(RECT_8);
        var plate = FloorPlateGeometryResolver.resolve(
            FloorPlateSpec.of(0, 0, RECT_8), 1, scale);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            RECT_8, plate.outerPolygon(), plate.innerPolygon(), plate.outerCells(),
            facade, RECT_8, FacadeEdgeScope.FLOOR_LOCAL, scale, 3);

        assertTrue(windows.size() >= 2);
        for (PlannedWindow window : windows) {
            assertFalse(window.columnCenters().isEmpty());
        }
    }

    @Test
    void wideWindowConsumesMultipleColumns() {
        FacadeSpec facade = facade(2, 4);
        BuildingCanvasScale scale = BuildingCanvasScales.capture(RECT_8);
        var plate = FloorPlateGeometryResolver.resolve(
            FloorPlateSpec.of(0, 0, RECT_8), 1, scale);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            RECT_8, plate.outerPolygon(), plate.innerPolygon(), plate.outerCells(),
            facade, RECT_8, FacadeEdgeScope.FLOOR_LOCAL, scale, 3);

        assertTrue(windows.stream().anyMatch(w -> w.columnCenters().size() == 2));
    }

    private static FacadeSpec facade(int width, int spacing) {
        return new FacadeSpec(
            new WindowPatternSpec(spacing, width, 2, 1),
            List.of(),
            List.of());
    }
}
