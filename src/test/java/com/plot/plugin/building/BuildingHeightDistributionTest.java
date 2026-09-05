package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingHeightDistributionTest {

    private static BuildingFootprint building(String id, double x, double y, double size) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(x, y),
            new Vec2d(x + size, y),
            new Vec2d(x + size, y + size),
            new Vec2d(x, y + size)
        ), true);
        footprint.setName(id);
        footprint.setFloors(1);
        return footprint;
    }

    @Test
    void uniformSetsAllToMaxFloors() {
        BuildingFootprint a = building("a", 0, 0, 5);
        BuildingFootprint b = building("b", 20, 0, 5);

        BuildingHeightDistribution.apply(
            List.of(a, b),
            BuildingHeightDistribution.Settings.uniform(6));

        assertEquals(6, a.getFloors());
        assertEquals(6, b.getFloors());
    }

    @Test
    void randomUsesSeedDeterministicallyAndStaysInRange() {
        BuildingFootprint a = building("a", 0, 0, 5);
        BuildingFootprint b = building("b", 10, 0, 5);
        BuildingFootprint c = building("c", 20, 0, 5);
        List<BuildingFootprint> buildings = List.of(a, b, c);

        BuildingHeightDistribution.Settings settings =
            BuildingHeightDistribution.Settings.of(
                BuildingHeightDistribution.Mode.RANDOM, 4, 8, 42L);

        int[] first = BuildingHeightDistribution.computeFloors(buildings, settings);
        int[] second = BuildingHeightDistribution.computeFloors(buildings, settings);

        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i]);
            assertTrue(first[i] >= 4 && first[i] <= 8);
        }
    }

    @Test
    void areaBasedMakesLargerFootprintTaller() {
        BuildingFootprint small = building("small", 0, 0, 4);
        BuildingFootprint large = building("large", 20, 0, 12);

        BuildingHeightDistribution.apply(
            List.of(small, large),
            BuildingHeightDistribution.Settings.of(
                BuildingHeightDistribution.Mode.AREA_BASED, 4, 12, 0L));

        assertTrue(large.getFloors() > small.getFloors());
        assertEquals(4, small.getFloors());
        assertEquals(12, large.getFloors());
    }

    @Test
    void centerHigherMakesCentralBuildingTaller() {
        // 选中中心约在 (20,20)；center 在原点附近，edge 在远处
        BuildingFootprint center = building("center", 18, 18, 4);
        BuildingFootprint edge = building("edge", 0, 0, 4);
        BuildingFootprint edge2 = building("edge2", 36, 36, 4);

        BuildingHeightDistribution.apply(
            List.of(center, edge, edge2),
            BuildingHeightDistribution.Settings.of(
                BuildingHeightDistribution.Mode.CENTER_HIGHER, 4, 12, 0L));

        assertTrue(center.getFloors() >= edge.getFloors());
        assertTrue(center.getFloors() >= edge2.getFloors());
        assertEquals(12, center.getFloors());
    }

    @Test
    void edgeHigherMakesOuterBuildingTaller() {
        BuildingFootprint center = building("center", 18, 18, 4);
        BuildingFootprint edge = building("edge", 0, 0, 4);
        BuildingFootprint edge2 = building("edge2", 36, 36, 4);

        BuildingHeightDistribution.apply(
            List.of(center, edge, edge2),
            BuildingHeightDistribution.Settings.of(
                BuildingHeightDistribution.Mode.EDGE_HIGHER, 4, 12, 0L));

        assertTrue(edge.getFloors() >= center.getFloors());
        assertTrue(edge2.getFloors() >= center.getFloors());
        assertEquals(4, center.getFloors());
    }

    @Test
    void gradientIncreasesAlongX() {
        BuildingFootprint west = building("west", 0, 0, 4);
        BuildingFootprint east = building("east", 40, 0, 4);

        BuildingHeightDistribution.apply(
            List.of(west, east),
            BuildingHeightDistribution.Settings.of(
                BuildingHeightDistribution.Mode.GRADIENT, 3, 9, 0L));

        assertEquals(3, west.getFloors());
        assertEquals(9, east.getFloors());
    }

    @Test
    void clampsFloorsToValidRange() {
        assertEquals(1, BuildingHeightDistribution.clampFloors(0));
        assertEquals(32, BuildingHeightDistribution.clampFloors(100));
        assertEquals(5, BuildingHeightDistribution.lerpFloors(1, 9, 0.5));
    }
}
