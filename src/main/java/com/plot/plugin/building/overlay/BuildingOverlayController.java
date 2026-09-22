package com.plot.plugin.building.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.plugin.building.BuildingFootprintSelectionAnalysis;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingSelectionSet;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 从项目与选中集合成画布叠加层条目。 */
public final class BuildingOverlayController {

    private BuildingOverlayController() {
    }

    public static List<BuildingOverlayEntry> snapshot(
            BuildingProject project,
            BuildingSelectionSet selection,
            List<Shape> canvasShapes,
            boolean pickSessionActive,
            boolean overlayEnabled) {
        if (!overlayEnabled || project == null) {
            return List.of();
        }
        BuildingSelectionSet activeSelection = selection != null ? selection : new BuildingSelectionSet();
        BuildingFootprintSelectionAnalysis canvas = BuildingFootprintSelectionAnalysis.analyze(
            canvasShapes != null ? canvasShapes : List.of(),
            project);
        List<BuildingOverlayEntry> entries = new ArrayList<>();
        for (BuildingFootprint building : project.getBuildings().values()) {
            if (building == null) {
                continue;
            }
            List<Vec2d> points = building.getOuterPoints();
            if (points.size() < 3) {
                continue;
            }
            entries.add(new BuildingOverlayEntry(
                building.getId(),
                building.getName(),
                List.copyOf(points),
                building.getFloors(),
                resolveState(building.getId(), activeSelection)));
        }
        appendCanvasEntries(entries, canvas, pickSessionActive);
        entries.sort(Comparator.comparingInt(entry -> entry.state().renderPriority()));
        return entries;
    }

    private static void appendCanvasEntries(
            List<BuildingOverlayEntry> entries,
            BuildingFootprintSelectionAnalysis canvas,
            boolean pickSessionActive) {
        appendCanvasGroup(entries, canvas.adoptable(), pickSessionActive
            ? BuildingOverlayState.PICK_ACTIVE
            : BuildingOverlayState.CANDIDATE);
        appendCanvasGroup(entries, canvas.alreadyAdopted(), BuildingOverlayState.ALREADY_ADOPTED);
        appendCanvasGroup(entries, canvas.invalid(), BuildingOverlayState.INVALID);
    }

    private static void appendCanvasGroup(
            List<BuildingOverlayEntry> entries,
            List<Shape> shapes,
            BuildingOverlayState state) {
        for (Shape shape : shapes) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            if (points.size() < 3) {
                continue;
            }
            entries.add(new BuildingOverlayEntry(
                "shape:" + shape.getId(),
                shape.getId(),
                List.copyOf(points),
                0,
                state));
        }
    }

    static BuildingOverlayState resolveState(String buildingId, BuildingSelectionSet selection) {
        if (buildingId == null || buildingId.isBlank() || selection == null || selection.isEmpty()) {
            return BuildingOverlayState.REGISTERED;
        }
        if (buildingId.equals(selection.primaryId())) {
            return BuildingOverlayState.PRIMARY;
        }
        if (selection.contains(buildingId)) {
            return BuildingOverlayState.SELECTED;
        }
        return BuildingOverlayState.REGISTERED;
    }
}
