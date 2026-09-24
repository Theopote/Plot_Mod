package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictBuildingPriorityTest {

    private static BuildingFootprint footprint(String id, int floors) {
        BuildingFootprint building = new BuildingFootprint(id, List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)
        ), true);
        building.setFloors(floors);
        building.setFloorHeight(3);
        return building;
    }

    @Test
    void sortsTallerBuildingsFirst() {
        BuildingFootprint shortBuilding = footprint("short", 2);
        BuildingFootprint tallBuilding = footprint("tall", 6);

        List<BuildingFootprint> ordered = DistrictBuildingPriority.sortedForGeneration(
            List.of(shortBuilding, tallBuilding));

        assertEquals("tall", ordered.getFirst().getId());
        assertEquals("short", ordered.get(1).getId());
    }

    @Test
    void sameHeightUsesStableIdTieBreak() {
        BuildingFootprint a = footprint("alpha", 4);
        BuildingFootprint b = footprint("beta", 4);

        List<BuildingFootprint> ordered = DistrictBuildingPriority.sortedForGeneration(List.of(a, b));
        assertEquals("beta", ordered.getFirst().getId());
    }

    @Test
    void dominatesPrefersTallerBuilding() {
        BuildingFootprint shortBuilding = footprint("short", 2);
        BuildingFootprint tallBuilding = footprint("tall", 5);

        assertTrue(DistrictBuildingPriority.dominates(tallBuilding, shortBuilding));
        assertFalse(DistrictBuildingPriority.dominates(shortBuilding, tallBuilding));
    }
}
