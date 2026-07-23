package com.plot.plugin.building.model;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingProjectTest {

    @Test
    void jsonRoundTripPreservesAllFields() {
        BuildingProject project = new BuildingProject();
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        ), true);
        footprint.setName("Test Tower");
        footprint.setFloors(3);
        footprint.setFloorHeight(4);
        footprint.setWallThickness(2);
        footprint.setWallMaterial("minecraft:stone_bricks");
        footprint.setFloorMaterial("minecraft:oak_planks");
        footprint.setRoofMaterial("minecraft:dark_oak_planks");
        footprint.setFoundationFillMaterial("minecraft:cobblestone");
        footprint.setRoofType(BuildingFootprint.RoofType.GABLE);
        footprint.setRoofPitchRatio(2);
        footprint.setManualBaseElevation(72);
        footprint.setWindowSpacing(5);
        footprint.setWindowWidth(2);
        footprint.setWindowHeight(3);
        footprint.setWindowSillHeight(1);
        footprint.addDoor(new BuildingFootprint.DoorOpening(1, 0.5, 0, 2, 3));
        project.addBuilding(footprint);

        BuildingProject restored = BuildingProject.fromJson(project.toJson());
        BuildingFootprint restoredFootprint = restored.getBuilding(footprint.getId());
        assertNotNull(restoredFootprint);
        assertEquals("Test Tower", restoredFootprint.getName());
        assertEquals(3, restoredFootprint.getFloors());
        assertEquals(4, restoredFootprint.getFloorHeight());
        assertEquals(2, restoredFootprint.getWallThickness());
        assertEquals("minecraft:stone_bricks", restoredFootprint.getWallMaterial().getPrimaryMaterial());
        assertEquals("minecraft:oak_planks", restoredFootprint.getFloorMaterial().getPrimaryMaterial());
        assertEquals("minecraft:dark_oak_planks", restoredFootprint.getRoofMaterial());
        assertEquals("minecraft:cobblestone", restoredFootprint.getFoundationFillMaterial());
        assertEquals(BuildingFootprint.RoofType.GABLE, restoredFootprint.getRoofType());
        assertEquals(2, restoredFootprint.getRoofPitchRatio());
        assertEquals(72, restoredFootprint.getManualBaseElevation());
        assertEquals(5, restoredFootprint.getWindowSpacing());
        assertEquals(2, restoredFootprint.getWindowWidth());
        assertEquals(3, restoredFootprint.getWindowHeight());
        assertEquals(1, restoredFootprint.getWindowSillHeight());
        assertEquals(1, restoredFootprint.getDoors().size());
        assertEquals(1, restoredFootprint.getDoors().getFirst().wallSegmentIndex);
        assertEquals(0.5, restoredFootprint.getDoors().getFirst().positionRatio, 1e-6);
        assertTrue(restoredFootprint.isRectangular());
        assertEquals(4, restoredFootprint.getOuterPoints().size());
    }

    @Test
    void legacyStringWallMaterialLoadsAsMaterialMix() {
        String json = """
            {
              "buildings": [{
                "id": "b1",
                "name": "Legacy",
                "outerPoints": [{"x": 0, "y": 0}, {"x": 5, "y": 0}, {"x": 5, "y": 5}],
                "isRectangular": true,
                "floors": 1,
                "floorHeight": 3,
                "wallThickness": 1,
                "wallMaterial": "minecraft:stone_bricks",
                "floorMaterial": "minecraft:oak_planks",
                "roofMaterial": "minecraft:stone_bricks",
                "foundationFillMaterial": "minecraft:stone",
                "roofType": "FLAT",
                "roofPitchRatio": 1,
                "windowSpacing": 4,
                "windowWidth": 1,
                "windowHeight": 2,
                "windowSillHeight": 1,
                "doors": []
              }]
            }
            """;

        BuildingProject project = BuildingProject.fromJson(json);
        BuildingFootprint footprint = project.getBuilding("b1");
        assertNotNull(footprint);
        assertEquals("minecraft:stone_bricks", footprint.getWallMaterial().getPrimaryMaterial());
        assertFalse(footprint.getWallMaterial().hasAccent());
    }

    @Test
    void corruptJsonThrowsInsteadOfSilentEmptyProject() {
        assertThrows(IllegalArgumentException.class, () -> BuildingProject.fromJson("{not-valid-json"));
    }

    @Test
    void loadFromCorruptFileThrowsIoException(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{broken");
        assertThrows(IOException.class, () -> BuildingProject.loadFrom(file));
    }

    @Test
    void saveToIsAtomicAndRoundTrips(@TempDir Path dir) throws IOException {
        BuildingProject project = new BuildingProject();
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(6, 0),
            new Vec2d(6, 4),
            new Vec2d(0, 4)
        ), true);
        footprint.setName("Atomic");
        project.addBuilding(footprint);

        Path file = dir.resolve("buildings.json");
        project.saveTo(file);
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("buildings.json.tmp")));

        BuildingProject loaded = BuildingProject.loadFrom(file);
        assertEquals(1, loaded.getBuildingCount());
        assertEquals("Atomic", loaded.getBuilding(footprint.getId()).getName());
    }

    @Test
    void missingBuildingIdGetsGeneratedUuid() {
        String json = """
            {
              "buildings": [{
                "name": "NoId",
                "outerPoints": [{"x": 0, "y": 0}, {"x": 4, "y": 0}, {"x": 4, "y": 4}],
                "isRectangular": true,
                "floors": 1,
                "floorHeight": 3,
                "wallThickness": 1
              }]
            }
            """;
        BuildingProject project = BuildingProject.fromJson(json);
        assertEquals(1, project.getBuildingCount());
        BuildingFootprint fp = project.getBuildings().values().iterator().next();
        assertNotNull(fp.getId());
        assertFalse(fp.getId().isBlank());
    }
}
