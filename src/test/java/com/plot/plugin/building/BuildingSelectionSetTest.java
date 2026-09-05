package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingSelectionSetTest {

    private static BuildingFootprint building(String id, double size, int floors) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size)
        ), true);
        footprint.setName(id);
        footprint.setFloors(floors);
        return footprint;
    }

    private static BuildingProject projectWith(BuildingFootprint... buildings) {
        BuildingProject project = new BuildingProject();
        for (BuildingFootprint building : buildings) {
            project.addBuilding(building);
        }
        return project;
    }

    @Test
    void singleSelectReplacesSelection() {
        BuildingSelectionSet selection = new BuildingSelectionSet();
        selection.select("a", false);
        selection.select("b", false);

        assertEquals(1, selection.size());
        assertTrue(selection.contains("b"));
        assertEquals("b", selection.primaryId());
    }

    @Test
    void multiToggleAddsAndRemoves() {
        BuildingSelectionSet selection = new BuildingSelectionSet();
        selection.select("a", false);
        selection.select("b", true);
        selection.select("c", true);

        assertEquals(3, selection.size());
        assertEquals("c", selection.primaryId());

        selection.select("b", true);
        assertFalse(selection.contains("b"));
        assertEquals(2, selection.size());
        assertEquals("c", selection.primaryId());
    }

    @Test
    void selectAllAndClear() {
        BuildingSelectionSet selection = new BuildingSelectionSet();
        selection.selectAll(List.of("a", "b", "c"));

        assertEquals(3, selection.size());
        assertEquals("a", selection.primaryId());

        selection.clear();
        assertTrue(selection.isEmpty());
        assertEquals("", selection.primaryId());
    }

    @Test
    void retainExistingDropsDeletedBuildings() {
        BuildingFootprint a = building("a", 5, 2);
        BuildingFootprint b = building("b", 10, 4);
        BuildingProject project = projectWith(a, b);

        BuildingSelectionSet selection = new BuildingSelectionSet();
        selection.selectAll(List.of("a", "b", "missing"));
        assertEquals(3, selection.size());

        selection.retainExisting(project);
        assertEquals(2, selection.size());
        assertTrue(selection.contains("a"));
        assertTrue(selection.contains("b"));
        assertEquals("a", selection.primaryId());

        project.removeBuilding("a");
        selection.retainExisting(project);
        assertEquals(1, selection.size());
        assertEquals("b", selection.primaryId());
        assertEquals(b, selection.primary(project));
    }

    @Test
    void totalAreaSumsSelectedOnly() {
        BuildingFootprint small = building("small", 5, 2);
        BuildingFootprint large = building("large", 10, 4);
        BuildingProject project = projectWith(small, large);

        BuildingSelectionSet selection = new BuildingSelectionSet();
        selection.select("small", false);
        selection.select("large", true);

        assertEquals(25.0 + 100.0, selection.totalArea(project), 1e-6);

        selection.select("large", true);
        assertEquals(25.0, selection.totalArea(project), 1e-6);
        assertEquals(List.of(small), selection.resolve(project));
    }

    @Test
    void primaryNullWhenEmpty() {
        BuildingSelectionSet selection = new BuildingSelectionSet();
        assertNull(selection.primary(new BuildingProject()));
    }

    @Test
    void addAllKeepsPrimaryIfAlreadySet() {
        BuildingSelectionSet selection = new BuildingSelectionSet();
        selection.select("a", false);
        selection.addAll(List.of("b", "c"));

        assertEquals(3, selection.size());
        assertEquals("a", selection.primaryId());
    }
}
