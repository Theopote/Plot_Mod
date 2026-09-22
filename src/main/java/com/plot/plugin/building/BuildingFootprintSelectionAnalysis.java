package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;

import java.util.ArrayList;
import java.util.List;

/**
 * 画布当前选中图形的认领分类（对齐 {@link com.plot.plugin.powerline.PowerLinePathSelectionAnalysis}）。
 */
public record BuildingFootprintSelectionAnalysis(
        List<Shape> adoptable,
        List<Shape> invalid,
        List<Shape> unsupported,
        List<Shape> alreadyAdopted) {

    public static final BuildingFootprintSelectionAnalysis EMPTY =
        new BuildingFootprintSelectionAnalysis(List.of(), List.of(), List.of(), List.of());

    public BuildingFootprintSelectionAnalysis {
        adoptable = List.copyOf(adoptable);
        invalid = List.copyOf(invalid);
        unsupported = List.copyOf(unsupported);
        alreadyAdopted = List.copyOf(alreadyAdopted);
    }

    public static BuildingFootprintSelectionAnalysis analyze(List<Shape> shapes, BuildingProject project) {
        if (shapes == null || shapes.isEmpty()) {
            return EMPTY;
        }
        List<Shape> adoptable = new ArrayList<>();
        List<Shape> invalid = new ArrayList<>();
        List<Shape> unsupported = new ArrayList<>();
        List<Shape> alreadyAdopted = new ArrayList<>();
        for (Shape shape : shapes) {
            if (shape == null) {
                continue;
            }
            List<Vec2d> raw = BuildingGeometryUtils.extractFootprintPoints(shape);
            if (raw.isEmpty()) {
                unsupported.add(shape);
                continue;
            }
            if (!BuildingFootprintValidator.isAdoptable(raw)) {
                invalid.add(shape);
                continue;
            }
            if (matchesExistingBuilding(raw, project)) {
                alreadyAdopted.add(shape);
            } else {
                adoptable.add(shape);
            }
        }
        return new BuildingFootprintSelectionAnalysis(adoptable, invalid, unsupported, alreadyAdopted);
    }

    public boolean hasCanvasSelection() {
        return !adoptable.isEmpty() || !invalid.isEmpty() || !unsupported.isEmpty() || !alreadyAdopted.isEmpty();
    }

    public boolean canAdopt() {
        return !adoptable.isEmpty();
    }

    public int candidateCount() {
        return adoptable.size() + invalid.size() + alreadyAdopted.size();
    }

    private static boolean matchesExistingBuilding(List<Vec2d> points, BuildingProject project) {
        if (project == null || project.getBuildingCount() == 0 || points.size() < 3) {
            return false;
        }
        double candidateArea = Math.abs(BuildingFootprint.signedArea(points));
        Vec2d candidateCentroid = BuildingGeometryUtils.computeCentroid(points);
        for (BuildingFootprint building : project.getBuildings().values()) {
            List<Vec2d> existing = building.getOuterPoints();
            if (existing.size() != points.size()) {
                continue;
            }
            double existingArea = Math.abs(BuildingFootprint.signedArea(existing));
            if (Math.abs(existingArea - candidateArea) > 0.5) {
                continue;
            }
            Vec2d existingCentroid = BuildingGeometryUtils.computeCentroid(existing);
            if (candidateCentroid.distance(existingCentroid) > 0.5) {
                continue;
            }
            return true;
        }
        return false;
    }
}
