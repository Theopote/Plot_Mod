package com.plot.plugin.building.model.persistence;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.spec.OpeningSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuildingProjectPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void facadeRoundTripMatchesDirectJson() {
        BuildingProject project = sampleProject();
        String viaFacade = BuildingProjectPersistence.serialize(project);
        String direct = project.toJson();
        assertEquals(direct, viaFacade);

        BuildingProject restored = BuildingProjectPersistence.deserialize(viaFacade);
        assertEquals(project.getBuildingCount(), restored.getBuildingCount());
        assertNotNull(restored.getBuilding(project.getBuildings().keySet().iterator().next()));
    }

    @Test
    void facadeSnapshotIsolatedFromLiveProject() {
        BuildingProject project = sampleProject();
        BuildingProject snapshot = BuildingProjectPersistence.snapshot(project);

        String buildingId = project.getBuildings().keySet().iterator().next();
        project.getBuilding(buildingId).setName("mutated");

        assertNotEquals(
            project.getBuilding(buildingId).getName(),
            snapshot.getBuilding(buildingId).getName());
    }

    @Test
    void facadeSaveLoadPreservesContent() throws Exception {
        BuildingProject project = sampleProject();
        Path file = tempDir.resolve("buildings.json");

        BuildingProjectPersistence.save(project, file);
        BuildingProject loaded = BuildingProjectPersistence.load(file);

        assertEquals(project.getBuildingCount(), loaded.getBuildingCount());
        BuildingFootprint source = project.getBuilding(project.getBuildings().keySet().iterator().next());
        BuildingFootprint restored = loaded.getBuilding(source.getId());
        assertEquals(source.getName(), restored.getName());
        assertEquals(source.getFloors(), restored.getFloors());
    }

    private static BuildingProject sampleProject() {
        BuildingProject project = new BuildingProject();
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 8),
            new Vec2d(0, 8)
        ), true);
        footprint.setName("Persist Tower");
        footprint.setFloors(5);
        footprint.setFloorHeight(4);
        footprint.addOpening(OpeningSpec.door(0, 0.5, 0, 2, 3));
        project.addBuilding(footprint);
        return project;
    }
}
