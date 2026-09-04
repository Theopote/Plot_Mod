package com.plot.plugin.building.golden;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.spec.FloorPlateSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stabilization Sprint 手工 smoke 用建筑项目（B07 / L / U / 厚墙）。
 */
public final class BuildingStabilizationSmokeProject {
    public record SmokeBuilding(
            String name,
            GoldenBuildingCaseFactory.Case goldenCase,
            double offsetX,
            double offsetY) {
    }

    public static final List<SmokeBuilding> BUILDINGS = List.of(
        new SmokeBuilding(
            "B07 Narrow Corridor",
            GoldenBuildingCaseFactory.b07NarrowCorridor(),
            0,
            0),
        new SmokeBuilding(
            "B04 L-Shape",
            GoldenBuildingCaseFactory.b04LShape(),
            18,
            0),
        new SmokeBuilding(
            "B05 U-Shape",
            GoldenBuildingCaseFactory.b05UShape(),
            36,
            0),
        new SmokeBuilding(
            "B10 Thick Wall",
            GoldenBuildingCaseFactory.b10ThickWall(),
            54,
            0));

    private BuildingStabilizationSmokeProject() {
    }

    public static BuildingProject create() {
        BuildingProject project = new BuildingProject();
        for (SmokeBuilding smoke : BUILDINGS) {
            project.addBuilding(cloneOffset(smoke));
        }
        return project;
    }

    public static void exportTo(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        create().saveTo(file);
    }

    private static BuildingFootprint cloneOffset(SmokeBuilding smoke) {
        BuildingProject single = new BuildingProject();
        single.addBuilding(smoke.goldenCase().footprint());
        BuildingFootprint source = smoke.goldenCase().footprint();
        BuildingFootprint copy = BuildingProject.fromJson(single.toJson()).getBuilding(source.getId());
        copy.setName(smoke.name());
        copy.setOuterPoints(translate(copy.getOuterPoints(), smoke.offsetX(), smoke.offsetY()));
        if (!copy.getFloorPlates().isEmpty()) {
            List<FloorPlateSpec> plates = new ArrayList<>(copy.getFloorPlates().size());
            for (FloorPlateSpec plate : copy.getFloorPlates()) {
                plates.add(FloorPlateSpec.of(
                    plate.floorStart(),
                    plate.floorEnd(),
                    translate(plate.outerPoints(), smoke.offsetX(), smoke.offsetY())));
            }
            copy.setFloorPlates(plates);
        }
        return copy;
    }

    private static List<Vec2d> translate(List<Vec2d> points, double dx, double dy) {
        List<Vec2d> translated = new ArrayList<>(points.size());
        for (Vec2d point : points) {
            translated.add(new Vec2d(point.x + dx, point.y + dy));
        }
        return translated;
    }
}
