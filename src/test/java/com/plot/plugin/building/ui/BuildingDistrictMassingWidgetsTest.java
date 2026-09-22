package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingSelectionSet;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingDistrictMassingWidgetsTest {

    @Test
    void allTargetReturnsEntireProjectEvenWhenSelectionExists() {
        BuildingProject project = projectWith(
            building("a"),
            building("b"),
            building("c"));
        BuildingSelectionSet selection = new BuildingSelectionSet();
        selection.select("a", false);
        selection.select("b", true);

        List<BuildingFootprint> targets = BuildingDistrictMassingWidgets.resolveTargets(
            project, selection, BuildingDistrictMassingWidgets.DistrictMassingTarget.ALL);

        assertEquals(3, targets.size());
    }

    @Test
    void selectedOnlyTargetIgnoresUnselectedBuildings() {
        BuildingProject project = projectWith(
            building("a"),
            building("b"),
            building("c"));
        BuildingSelectionSet selection = new BuildingSelectionSet();
        selection.select("b", false);

        List<BuildingFootprint> targets = BuildingDistrictMassingWidgets.resolveTargets(
            project, selection, BuildingDistrictMassingWidgets.DistrictMassingTarget.SELECTED_ONLY);

        assertEquals(1, targets.size());
        assertEquals("b", targets.getFirst().getId());
    }

    @Test
    void selectedOnlyTargetReturnsEmptyWhenNothingSelected() {
        BuildingProject project = projectWith(building("a"), building("b"));
        BuildingSelectionSet selection = new BuildingSelectionSet();

        List<BuildingFootprint> targets = BuildingDistrictMassingWidgets.resolveTargets(
            project, selection, BuildingDistrictMassingWidgets.DistrictMassingTarget.SELECTED_ONLY);

        assertTrue(targets.isEmpty());
    }

    private static BuildingFootprint building(String id) {
        return new BuildingFootprint(id, List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ), true);
    }

    private static BuildingProject projectWith(BuildingFootprint... buildings) {
        BuildingProject project = new BuildingProject();
        for (BuildingFootprint building : buildings) {
            project.addBuilding(building);
        }
        return project;
    }
}
