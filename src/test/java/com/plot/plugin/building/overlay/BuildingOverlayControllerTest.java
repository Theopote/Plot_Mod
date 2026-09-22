package com.plot.plugin.building.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingSelectionSet;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingOverlayControllerTest {

    private static BuildingFootprint building(String id, double size) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size)
        ), true);
        footprint.setName(id);
        footprint.setFloors(4);
        return footprint;
    }

    @Test
    void snapshotDisabledReturnsEmpty() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(building("a", 10));

        assertTrue(BuildingOverlayController.snapshot(
            project, new BuildingSelectionSet(), List.of(), false, false).isEmpty());
    }

    @Test
    void snapshotMapsRegisteredWhenNothingSelected() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(building("a", 10));
        project.addBuilding(building("b", 12));

        Map<String, BuildingOverlayState> states = BuildingOverlayController
            .snapshot(project, new BuildingSelectionSet(), List.of(), false, true)
            .stream()
            .collect(Collectors.toMap(BuildingOverlayEntry::buildingId, BuildingOverlayEntry::state));

        assertEquals(BuildingOverlayState.REGISTERED, states.get("a"));
        assertEquals(BuildingOverlayState.REGISTERED, states.get("b"));
    }

    @Test
    void snapshotMapsPrimaryAndSelectedStates() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(building("a", 10));
        project.addBuilding(building("b", 12));
        project.addBuilding(building("c", 14));

        BuildingSelectionSet selection = new BuildingSelectionSet();
        selection.select("a", false);
        selection.select("b", true);
        selection.select("c", true);

        Map<String, BuildingOverlayState> states = BuildingOverlayController
            .snapshot(project, selection, List.of(), false, true)
            .stream()
            .collect(Collectors.toMap(BuildingOverlayEntry::buildingId, BuildingOverlayEntry::state));

        assertEquals(BuildingOverlayState.PRIMARY, states.get("c"));
        assertEquals(BuildingOverlayState.SELECTED, states.get("a"));
        assertEquals(BuildingOverlayState.SELECTED, states.get("b"));
    }

    @Test
    void snapshotSkipsDegenerateFootprints() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(new BuildingFootprint("tiny", List.of(new Vec2d(0, 0), new Vec2d(1, 0)), true));

        assertTrue(BuildingOverlayController.snapshot(
            project, new BuildingSelectionSet(), List.of(), false, true).isEmpty());
    }

    @Test
    void snapshotIncludesDisplayNameAndFloors() {
        BuildingProject project = new BuildingProject();
        BuildingFootprint footprint = building("tower", 20);
        footprint.setName("B-024");
        footprint.setFloors(8);
        project.addBuilding(footprint);

        BuildingOverlayEntry entry = BuildingOverlayController
            .snapshot(project, new BuildingSelectionSet(), List.of(), false, true)
            .getFirst();

        assertEquals("tower", entry.buildingId());
        assertEquals("B-024", entry.displayName());
        assertEquals(8, entry.floors());
        assertEquals(4, entry.outerPoints().size());
    }
}
