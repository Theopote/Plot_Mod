package com.plot.plugin.building.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingProjectHistoryTest {

    @Test
    void undoRedoRestoresBuildingParameters() {
        BuildingProjectHistory history = new BuildingProjectHistory();
        BuildingProject project = new BuildingProject();
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        ), true);
        footprint.setName("Tower A");
        footprint.setFloors(4);
        project.addBuilding(footprint);
        String buildingId = footprint.getId();

        history.push(project);
        footprint.setFloors(7);
        footprint.setName("Tower B");

        assertTrue(history.canUndo());
        assertFalse(history.canRedo());

        BuildingProject undone = history.undo(project);
        BuildingFootprint restored = undone.getBuilding(buildingId);
        assertNotNull(restored);
        assertEquals(4, restored.getFloors());
        assertEquals("Tower A", restored.getName());
        assertTrue(history.canRedo());

        BuildingProject redone = history.redo(undone);
        BuildingFootprint redoneFootprint = redone.getBuilding(buildingId);
        assertNotNull(redoneFootprint);
        assertEquals(7, redoneFootprint.getFloors());
        assertEquals("Tower B", redoneFootprint.getName());
    }

    @Test
    void undoRedoRestoresDeletedBuilding() {
        BuildingProjectHistory history = new BuildingProjectHistory();
        BuildingProject project = new BuildingProject();
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(6, 0),
            new Vec2d(6, 6),
            new Vec2d(0, 6)
        ), true);
        project.addBuilding(footprint);
        String buildingId = footprint.getId();

        history.push(project);
        project.removeBuilding(buildingId);
        assertEquals(0, project.getBuildingCount());

        BuildingProject undone = history.undo(project);
        assertEquals(1, undone.getBuildingCount());
        assertNotNull(undone.getBuilding(buildingId));

        BuildingProject redone = history.redo(undone);
        assertEquals(0, redone.getBuildingCount());
    }
}
