package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingNameHelperTest {

    private static List<Vec2d> square(double size) {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size));
    }

    private static BuildingFootprint building(String id, String name) {
        BuildingFootprint footprint = new BuildingFootprint(id, square(10), true);
        footprint.setName(name);
        return footprint;
    }

    @Test
    void resolveUniqueNameIncrementsTrailingNumber() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(building("a", "Building 1"));

        assertEquals("Building 2", BuildingNameHelper.resolveUniqueName(project, "Building 1", "new"));
    }

    @Test
    void resolveUniqueNameAppendsSuffixForCustomNames() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(building("a", "Office"));

        assertEquals("Office 2", BuildingNameHelper.resolveUniqueName(project, "Office", "new"));
    }

    @Test
    void nameExistsIgnoresExcludedBuilding() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(building("a", "Tower"));

        assertFalse(BuildingNameHelper.nameExists(project, "Tower", "a"));
        assertTrue(BuildingNameHelper.nameExists(project, "Tower", null));
    }
}
