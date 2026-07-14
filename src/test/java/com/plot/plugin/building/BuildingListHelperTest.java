package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingListHelperTest {

    private static List<Vec2d> square(double size) {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size));
    }

    private static BuildingProject sampleProject() {
        BuildingProject project = new BuildingProject();

        BuildingFootprint small = new BuildingFootprint("building-small", square(5), true);
        small.setName("Charlie");
        small.setFloors(2);

        BuildingFootprint large = new BuildingFootprint("building-large", square(20), true);
        large.setName("Alpha");
        large.setFloors(8);

        BuildingFootprint medium = new BuildingFootprint("building-medium", square(10), false);
        medium.setName("Bravo");
        medium.setFloors(4);

        project.addBuilding(small);
        project.addBuilding(large);
        project.addBuilding(medium);
        return project;
    }

    private static List<String> ids(BuildingProject project, BuildingListHelper.SortMode mode) {
        return BuildingListHelper.sorted(project, mode).stream()
            .map(BuildingFootprint::getId)
            .collect(Collectors.toList());
    }

    @Test
    void insertionKeepsAdoptionOrder() {
        BuildingProject project = sampleProject();
        assertEquals(
            List.of("building-small", "building-large", "building-medium"),
            ids(project, BuildingListHelper.SortMode.INSERTION));
    }

    @Test
    void sortByAreaAscending() {
        BuildingProject project = sampleProject();
        assertEquals(
            List.of("building-small", "building-medium", "building-large"),
            ids(project, BuildingListHelper.SortMode.AREA_ASC));
    }

    @Test
    void sortByAreaDescending() {
        BuildingProject project = sampleProject();
        assertEquals(
            List.of("building-large", "building-medium", "building-small"),
            ids(project, BuildingListHelper.SortMode.AREA_DESC));
    }

    @Test
    void sortByFloorsDescending() {
        BuildingProject project = sampleProject();
        assertEquals(
            List.of("building-large", "building-medium", "building-small"),
            ids(project, BuildingListHelper.SortMode.FLOORS_DESC));
    }

    @Test
    void sortByName() {
        BuildingProject project = sampleProject();
        assertEquals(
            List.of("building-large", "building-medium", "building-small"),
            ids(project, BuildingListHelper.SortMode.NAME));
    }
}
