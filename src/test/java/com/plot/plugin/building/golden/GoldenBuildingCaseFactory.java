package com.plot.plugin.building.golden;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.OpeningSpec;

import java.util.List;

/**
 * Golden Building 测试用轮廓工厂与桩服务。
 */
public final class GoldenBuildingCaseFactory {
    private GoldenBuildingCaseFactory() {
    }

    public record Case(String id, String description, BuildingFootprint footprint) {
    }

    public static List<Case> all() {
        return List.of(
            b01SmallRectangle(),
            b02MediumRectangle(),
            b03RotatedRectangle(),
            b04LShape(),
            b05UShape(),
            b06ConcavePolygon(),
            b07NarrowCorridor(),
            b08ManualElevation(),
            b09MultiFloor(),
            b10ThickWall(),
            b11DoorsAndWindows(),
            b12PitchedRoof());
    }

    public static Case b01SmallRectangle() {
        return new Case("B01", "4x4 rectangle", rectangle(4, 4, 1, 3, 1));
    }

    public static Case b02MediumRectangle() {
        return new Case("B02", "10x6 rectangle", rectangle(10, 6, 2, 3, 1));
    }

    public static Case b03RotatedRectangle() {
        BuildingFootprint fp = new BuildingFootprint(List.of(
            new Vec2d(10, 5),
            new Vec2d(15, 10),
            new Vec2d(10, 15),
            new Vec2d(5, 10)
        ), false);
        applyDefaults(fp, 1, 3, 1);
        fp.setRoofType(BuildingFootprint.RoofType.HIP);
        fp.setRoofPitchRatio(2);
        return new Case("B03", "rotated rectangle hip", fp);
    }

    public static Case b04LShape() {
        BuildingFootprint fp = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 4),
            new Vec2d(4, 4),
            new Vec2d(4, 10),
            new Vec2d(0, 10)
        ), false);
        applyDefaults(fp, 2, 3, 1);
        fp.setRoofType(BuildingFootprint.RoofType.HIP);
        fp.setRoofPitchRatio(2);
        return new Case("B04", "L shape", fp);
    }

    public static Case b05UShape() {
        BuildingFootprint fp = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(8, 10),
            new Vec2d(8, 2),
            new Vec2d(2, 2),
            new Vec2d(2, 10),
            new Vec2d(0, 10)
        ), false);
        applyDefaults(fp, 2, 3, 1);
        fp.setRoofType(BuildingFootprint.RoofType.FLAT);
        return new Case("B05", "U shape", fp);
    }

    public static Case b06ConcavePolygon() {
        BuildingFootprint fp = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 12),
            new Vec2d(8, 12),
            new Vec2d(8, 8),
            new Vec2d(4, 8),
            new Vec2d(4, 12),
            new Vec2d(0, 12)
        ), false);
        applyDefaults(fp, 2, 3, 1);
        fp.setRoofType(BuildingFootprint.RoofType.GABLE);
        fp.setRoofPitchRatio(2);
        return new Case("B06", "concave polygon", fp);
    }

    public static Case b07NarrowCorridor() {
        // 降级回归用例：inner offset 失败 + 坡顶 downgrade；墙体必须为实心体量。
        BuildingFootprint fp = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 2),
            new Vec2d(0, 2)
        ), false);
        applyDefaults(fp, 1, 3, 1);
        fp.setRoofType(BuildingFootprint.RoofType.GABLE);
        fp.setRoofPitchRatio(4);
        return new Case("B07", "narrow corridor roof downgrade", fp);
    }

    public static Case b08ManualElevation() {
        BuildingFootprint fp = rectangle(8, 8, 2, 3, 1);
        fp.setManualBaseElevation(72);
        return new Case("B08", "manual base elevation", fp);
    }

    public static Case b09MultiFloor() {
        return new Case("B09", "multi floor", rectangle(8, 8, 5, 3, 1));
    }

    public static Case b10ThickWall() {
        // 厚墙回归：wallThickness=3 时墙环更厚；开洞必须向内镂空，bounds 不得为负。
        return new Case("B10", "thick wall", rectangle(10, 8, 2, 3, 3));
    }

    public static Case b11DoorsAndWindows() {
        BuildingFootprint fp = rectangle(10, 8, 2, 3, 1);
        fp.setWindowSpacing(3);
        fp.setOpenings(List.of(
            OpeningSpec.door(0, 0.5, 0, 2, 2),
            OpeningSpec.window(2, 0.5, 1, 2, 2, 1)
        ));
        return new Case("B11", "doors and windows", fp);
    }

    public static Case b12PitchedRoof() {
        BuildingFootprint fp = rectangle(10, 6, 1, 3, 1);
        fp.setRoofType(BuildingFootprint.RoofType.GABLE);
        fp.setRoofPitchRatio(2);
        return new Case("B12", "pitched gable roof", fp);
    }

    public static BuildingFootprint rectangle(int width, int depth, int floors, int floorHeight, int wallThickness) {
        BuildingFootprint fp = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(width, 0),
            new Vec2d(width, depth),
            new Vec2d(0, depth)
        ), true);
        applyDefaults(fp, floors, floorHeight, wallThickness);
        return fp;
    }

    public static void applyDefaults(BuildingFootprint fp, int floors, int floorHeight, int wallThickness) {
        fp.setFloors(floors);
        fp.setFloorHeight(floorHeight);
        fp.setWallThickness(wallThickness);
        fp.setWallMaterial(BuildingFootprint.DEFAULT_WALL_MATERIAL);
        fp.setFloorMaterial(BuildingFootprint.DEFAULT_FLOOR_MATERIAL);
        fp.setRoofMaterial(BuildingFootprint.DEFAULT_ROOF_MATERIAL);
        fp.setFoundationFillMaterial(BuildingFootprint.DEFAULT_FOUNDATION_FILL);
        fp.setRoofType(BuildingFootprint.RoofType.FLAT);
        fp.setRoofPitchRatio(2);
        fp.setWindowSpacing(4);
        fp.setWindowWidth(1);
        fp.setWindowHeight(2);
        fp.setWindowSillHeight(1);
    }
}
