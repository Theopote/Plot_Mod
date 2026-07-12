package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.GradingRegion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarthworkRegionListHelperTest {

    private static List<Vec2d> square(double size) {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size));
    }

    private static EarthworkProject sampleProject() {
        EarthworkProject project = new EarthworkProject();

        GradingRegion small = new GradingRegion("region-small", square(5));
        small.setName("Charlie");
        GradingRegion large = new GradingRegion("region-large", square(20));
        large.setName("Alpha");
        GradingRegion medium = new GradingRegion("region-medium", square(10));
        medium.setName("Bravo");

        project.addRegion(small);
        project.addRegion(large);
        project.addRegion(medium);
        return project;
    }

    private static List<String> ids(EarthworkProject project, EarthworkRegionListHelper.SortMode mode) {
        return EarthworkRegionListHelper.sorted(project, mode).stream()
            .map(GradingRegion::getId)
            .collect(Collectors.toList());
    }

    @Test
    void insertionKeepsAdoptionOrder() {
        EarthworkProject project = sampleProject();
        assertEquals(
            List.of("region-small", "region-large", "region-medium"),
            ids(project, EarthworkRegionListHelper.SortMode.INSERTION));
    }

    @Test
    void sortByAreaAscending() {
        EarthworkProject project = sampleProject();
        assertEquals(
            List.of("region-small", "region-medium", "region-large"),
            ids(project, EarthworkRegionListHelper.SortMode.AREA_ASC));
    }

    @Test
    void sortByAreaDescending() {
        EarthworkProject project = sampleProject();
        assertEquals(
            List.of("region-large", "region-medium", "region-small"),
            ids(project, EarthworkRegionListHelper.SortMode.AREA_DESC));
    }

    @Test
    void sortByName() {
        EarthworkProject project = sampleProject();
        assertEquals(
            List.of("region-large", "region-medium", "region-small"),
            ids(project, EarthworkRegionListHelper.SortMode.NAME));
    }
}
