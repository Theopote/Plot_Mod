package com.plot.plugin.building.realistic;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;

/**
 * 贴近画布真实脏度的片区轮廓工厂（非理想网格矩形）。
 */
public final class RealisticDistrictFixtures {
    private RealisticDistrictFixtures() {
    }

    public static final int MIXED_DISTRICT_SIZE = 100;

    public static List<TaggedFootprint> mixedDistrict100() {
        List<TaggedFootprint> catalog = new ArrayList<>(MIXED_DISTRICT_SIZE);
        int index = 0;

        for (int i = 0; i < 55; i++) {
            catalog.add(tag(gridRectangleAt(index++, 4, (i % 20) * 12.0, ((double) i / 20) * 10.0),
                RealisticFootprintKind.RECT_GRID, BuildingFootprint.RoofType.FLAT));
        }
        // 55 + 8 + 6 + 8 + 4 + 4 + 4 + 4 + 3 + 4 = 100
        for (int i = 0; i < 8; i++) {
            catalog.add(tag(offsetFootprint(lShape(index++), index * 14.0, (index % 5) * 11.0),
                RealisticFootprintKind.L_SHAPE, BuildingFootprint.RoofType.HIP));
        }
        for (int i = 0; i < 6; i++) {
            catalog.add(tag(offsetFootprint(concave(index++), index * 16.0, (index % 4) * 13.0),
                RealisticFootprintKind.CONCAVE, BuildingFootprint.RoofType.GABLE));
        }
        for (int i = 0; i < 8; i++) {
            catalog.add(tag(ellipseFootprint(index++, 6 + (i % 3), 3 + (i % 2), i * 0.35),
                RealisticFootprintKind.ELLIPSE, BuildingFootprint.RoofType.FLAT));
        }
        for (int i = 0; i < 4; i++) {
            catalog.add(tag(circleFootprint(index++, 4 + i),
                RealisticFootprintKind.CIRCLE, BuildingFootprint.RoofType.FLAT));
        }
        for (int i = 0; i < 4; i++) {
            catalog.add(tag(offsetFootprint(narrowCorridor(index++), 200 + i * 14.0, 40 + i * 8.0),
                RealisticFootprintKind.NARROW_INNER_OFFSET, BuildingFootprint.RoofType.GABLE));
        }
        for (int i = 0; i < 4; i++) {
            BuildingFootprint small = gridRectangle(index++, 3);
            small.setWallThickness(3);
            catalog.add(tag(small, RealisticFootprintKind.THICK_WALL_SMALL,
                BuildingFootprint.RoofType.FLAT));
        }
        for (int i = 0; i < 2; i++) {
            BuildingFootprint base = gridRectangle(index, 4);
            double x = 300 + i * 0.5;
            double z = 80 + i * 0.5;
            catalog.add(tag(offsetFootprint(base, "overlap-a-" + i, x, z),
                RealisticFootprintKind.OVERLAP_DUPLICATE, BuildingFootprint.RoofType.FLAT));
            catalog.add(tag(offsetFootprint(copyGeometry(base, "overlap-b-" + i), x, z),
                RealisticFootprintKind.OVERLAP_DUPLICATE, BuildingFootprint.RoofType.FLAT));
            index++;
        }
        for (int i = 0; i < 3; i++) {
            catalog.add(tag(invalidTwoPoint(index++),
                RealisticFootprintKind.INVALID, BuildingFootprint.RoofType.FLAT));
        }
        for (int i = 0; i < 4; i++) {
            BuildingFootprint pitched = offsetFootprint(lShape(index), 400 + i * 18.0, 10 + i * 9.0);
            pitched.setRoofType(BuildingFootprint.RoofType.HIP);
            pitched.setRoofPitchRatio(2);
            catalog.add(tag(pitched, RealisticFootprintKind.PITCHED_COMPLEX,
                BuildingFootprint.RoofType.HIP));
            index++;
        }

        if (catalog.size() != MIXED_DISTRICT_SIZE) {
            throw new IllegalStateException("mixed district size mismatch: " + catalog.size());
        }
        return catalog;
    }

    public static BuildingFootprint gridRectangle(int index, int floors) {
        return gridRectangleAt(index, floors, (index % 20) * 12.0, ((double) index / 20) * 10.0);
    }

    public static BuildingFootprint lShape(int index) {
        BuildingFootprint footprint = new BuildingFootprint("real-l-" + index, List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 4),
            new Vec2d(4, 4),
            new Vec2d(4, 10),
            new Vec2d(0, 10)
        ), false);
        applyMassingDefaults(footprint, 3);
        return footprint;
    }

    public static BuildingFootprint concave(int index) {
        BuildingFootprint footprint = new BuildingFootprint("real-c-" + index, List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 12),
            new Vec2d(8, 12),
            new Vec2d(8, 8),
            new Vec2d(4, 8),
            new Vec2d(4, 12),
            new Vec2d(0, 12)
        ), false);
        applyMassingDefaults(footprint, 3);
        return footprint;
    }

    public static BuildingFootprint narrowCorridor(int index) {
        BuildingFootprint source = GoldenBuildingCaseFactory.b07NarrowCorridor().footprint();
        BuildingFootprint footprint = new BuildingFootprint(
            "real-narrow-" + index,
            BuildingGeometryUtils.copyPoints(source.getOuterPoints()),
            false);
        applyMassingDefaults(footprint, source.getFloors());
        footprint.setRoofType(source.getRoofType());
        footprint.setRoofPitchRatio(source.getRoofPitchRatio());
        footprint.setWallThickness(source.getWallThickness());
        footprint.setWindowsEnabled(false);
        return footprint;
    }

    public static BuildingFootprint ellipseFootprint(int index, double radiusX, double radiusY, double rotation) {
        double x = (index % 20) * 11.0 + 20;
        double z = ((double) index / 20) * 9.0 + 20;
        EllipseShape ellipse = new EllipseShape(new Vec2d(x, z), radiusX, radiusY, rotation);
        List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(ellipse);
        BuildingFootprint footprint = new BuildingFootprint("real-ellipse-" + index, points, false);
        applyMassingDefaults(footprint, 5);
        return footprint;
    }

    public static BuildingFootprint circleFootprint(int index, double radius) {
        double x = (index % 20) * 11.0 + 30;
        double z = ((double) index / 20) * 9.0 + 30;
        CircleShape circle = new CircleShape(new Vec2d(x, z), radius);
        List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(circle);
        BuildingFootprint footprint = new BuildingFootprint("real-circle-" + index, points, false);
        applyMassingDefaults(footprint, 4);
        return footprint;
    }

    private static BuildingFootprint gridRectangleAt(int index, int floors, double x, double z) {
        BuildingFootprint footprint = new BuildingFootprint("real-rect-" + index, List.of(
            new Vec2d(x, z),
            new Vec2d(x + 8, z),
            new Vec2d(x + 8, z + 6),
            new Vec2d(x, z + 6)
        ), true);
        applyMassingDefaults(footprint, floors);
        return footprint;
    }

    private static BuildingFootprint invalidTwoPoint(int index) {
        return new BuildingFootprint("real-invalid-" + index, List.of(
            new Vec2d(500 + index, 500),
            new Vec2d(501 + index, 500)
        ), false);
    }

    private static BuildingFootprint copyGeometry(BuildingFootprint source, String id) {
        BuildingFootprint copy = new BuildingFootprint(id, source.getOuterPoints(), source.isRectangular());
        copy.setFloors(source.getFloors());
        copy.setFloorHeight(source.getFloorHeight());
        copy.setWallThickness(source.getWallThickness());
        copy.setRoofType(source.getRoofType());
        copy.setWindowsEnabled(source.isWindowsEnabled());
        copy.setWindowWidth(source.getWindowWidth());
        copy.setWindowPierWidth(source.getWindowPierWidth());
        return copy;
    }

    private static BuildingFootprint offsetFootprint(BuildingFootprint source, double dx, double dz) {
        return offsetFootprint(source, source.getId(), dx, dz);
    }

    private static BuildingFootprint offsetFootprint(
            BuildingFootprint source,
            String id,
            double dx,
            double dz) {
        List<Vec2d> shifted = new ArrayList<>(source.getOuterPoints().size());
        for (Vec2d point : source.getOuterPoints()) {
            shifted.add(new Vec2d(point.x + dx, point.y + dz));
        }
        BuildingFootprint footprint = new BuildingFootprint(id, shifted, source.isRectangular());
        applyMassingDefaults(footprint, source.getFloors());
        footprint.setWallThickness(source.getWallThickness());
        footprint.setRoofType(source.getRoofType());
        footprint.setRoofPitchRatio(source.getRoofPitchRatio());
        footprint.setWindowsEnabled(true);
        footprint.setWindowWidth(source.getWindowWidth());
        footprint.setWindowPierWidth(source.getWindowPierWidth());
        return footprint;
    }

    private static void applyMassingDefaults(BuildingFootprint footprint, int floors) {
        GoldenBuildingCaseFactory.applyDefaults(footprint, floors, 3, 1);
        footprint.setWindowsEnabled(false);
    }

    private static TaggedFootprint tag(
            BuildingFootprint footprint,
            RealisticFootprintKind kind,
            BuildingFootprint.RoofType requestedRoof) {
        footprint.setRoofType(requestedRoof);
        return new TaggedFootprint(footprint, kind, requestedRoof);
    }
}
