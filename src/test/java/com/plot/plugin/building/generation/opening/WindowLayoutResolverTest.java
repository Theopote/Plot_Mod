package com.plot.plugin.building.generation.opening;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver;
import com.plot.plugin.building.generation.opening.WindowLayoutResolver.PlannedWindow;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.WallFacadeSpec;
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
    void pierWidthLeavesSpandrelBetweenWindowsOnStraightFace() {
        FacadeSpec facade = facade(1, 3);
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
            assertEquals(3, gapColumns, "pier 3 width 1 => 3 spandrel columns on same face");
        }
    }

    @Test
    void rectangleProducesWindowsOnWallColumns() {
        FacadeSpec facade = facade(1, 3);
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
        FacadeSpec facade = facade(2, 2);
        BuildingCanvasScale scale = BuildingCanvasScales.capture(RECT_8);
        var plate = FloorPlateGeometryResolver.resolve(
            FloorPlateSpec.of(0, 0, RECT_8), 1, scale);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            RECT_8, plate.outerPolygon(), plate.innerPolygon(), plate.outerCells(),
            facade, RECT_8, FacadeEdgeScope.FLOOR_LOCAL, scale, 3);

        assertTrue(windows.stream().anyMatch(w -> w.columnCenters().size() == 2));
    }

    @Test
    void eachWallSegmentResetsRhythm() {
        List<Vec2d> rect = List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 6),
            new Vec2d(0, 6)
        );
        FacadeSpec facade = facade(1, 2);
        BuildingCanvasScale scale = BuildingCanvasScales.capture(rect);
        var plate = FloorPlateGeometryResolver.resolve(
            FloorPlateSpec.of(0, 0, rect), 1, scale);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            rect, plate.outerPolygon(), plate.innerPolygon(), plate.outerCells(),
            facade, rect, FacadeEdgeScope.FLOOR_LOCAL, scale, 4);

        List<PlannedWindow> south = windows.stream()
            .filter(w -> w.segmentIndex() == 0)
            .toList();
        List<PlannedWindow> east = windows.stream()
            .filter(w -> w.segmentIndex() == 1)
            .toList();

        assertFalse(south.isEmpty());
        assertFalse(east.isEmpty());
        double southFirstX = south.getFirst().columnCenters().getFirst().x;
        double eastFirstZ = east.getFirst().columnCenters().getFirst().y;
        assertTrue(southFirstX >= 1.0, "south segment should leave corner pier");
        assertTrue(eastFirstZ >= 1.0, "east segment should leave corner pier");
    }

    @Test
    void windowColumnsStayOnSingleSegment() {
        FacadeSpec facade = facade(3, 1);
        BuildingCanvasScale scale = BuildingCanvasScales.capture(RECT_8);
        var plate = FloorPlateGeometryResolver.resolve(
            FloorPlateSpec.of(0, 0, RECT_8), 1, scale);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            RECT_8, plate.outerPolygon(), plate.innerPolygon(), plate.outerCells(),
            facade, RECT_8, FacadeEdgeScope.FLOOR_LOCAL, scale, 3);
        List<List<WallColumnRing.WallColumn>> perSegment = WallColumnRing.buildPerSegment(
            RECT_8, plate.outerPolygon(), plate.innerPolygon(), plate.outerCells());

        for (PlannedWindow window : windows) {
            for (Vec2d center : window.columnCenters()) {
                int segment = segmentOfColumn(perSegment, center);
                assertEquals(window.segmentIndex(), segment,
                    "window must not cross wall segment at " + center);
            }
        }
    }

    private static int segmentOfColumn(List<List<WallColumnRing.WallColumn>> perSegment, Vec2d center) {
        long key = pack(center);
        for (int seg = 0; seg < perSegment.size(); seg++) {
            for (WallColumnRing.WallColumn column : perSegment.get(seg)) {
                if (pack(column.center()) == key) {
                    return seg;
                }
            }
        }
        return -1;
    }

    private static long pack(Vec2d center) {
        long qx = Math.round(center.x * 1024.0);
        long qz = Math.round(center.y * 1024.0);
        return (qx << 32) ^ (qz & 0xffffffffL);
    }

    @Test
    void defaultDisabledStillHonorsSegmentOverride() {
        FacadeSpec facade = new FacadeSpec(
            WindowPatternSpec.disabled(),
            List.of(WallFacadeSpec.of(0, WindowPatternSpec.of(1, 2, 2, 1))),
            List.of());
        BuildingCanvasScale scale = BuildingCanvasScales.capture(RECT_8);
        var plate = FloorPlateGeometryResolver.resolve(
            FloorPlateSpec.of(0, 0, RECT_8), 1, scale);
        List<PlannedWindow> windows = WindowLayoutResolver.layout(
            RECT_8, plate.outerPolygon(), plate.innerPolygon(), plate.outerCells(),
            facade, RECT_8, FacadeEdgeScope.FLOOR_LOCAL, scale, 3);

        assertFalse(windows.isEmpty());
        assertTrue(windows.stream().allMatch(w -> w.segmentIndex() == 0));
    }

    private static FacadeSpec facade(int width, int pierWidth) {
        return new FacadeSpec(
            WindowPatternSpec.of(width, pierWidth, 2, 1),
            List.of(),
            List.of());
    }
}
