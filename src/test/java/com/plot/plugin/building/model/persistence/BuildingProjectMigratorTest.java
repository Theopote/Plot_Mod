package com.plot.plugin.building.model.persistence;

import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingProjectMigratorTest {

    @Test
    void legacyJsonWithoutSchemaVersionMigratesToV1() {
        String legacy = """
            {
              "buildings": [{
                "id": "b1",
                "name": "Legacy",
                "outerPoints": [{"x": 0, "y": 0}, {"x": 5, "y": 0}, {"x": 5, "y": 5}],
                "isRectangular": true,
                "floors": 2,
                "floorHeight": 3,
                "wallThickness": 1
              }]
            }
            """;

        String normalized = BuildingProjectMigrator.normalizeJson(legacy);
        assertEquals(BuildingProjectSchema.CURRENT, BuildingProjectSchema.resolveStoredVersion(normalized));

        BuildingProject project = BuildingProject.fromJson(normalized);
        assertEquals(1, project.getBuildingCount());
        assertEquals("Legacy", project.getBuilding("b1").getName());
    }

    @Test
    void currentSchemaRoundTripsWithVersionField() {
        BuildingProject project = new BuildingProject();
        project.addBuilding(new com.plot.plugin.building.model.BuildingFootprint(
            "b1",
            java.util.List.of(
                new com.plot.api.geometry.Vec2d(0, 0),
                new com.plot.api.geometry.Vec2d(6, 0),
                new com.plot.api.geometry.Vec2d(6, 4),
                new com.plot.api.geometry.Vec2d(0, 4)
            ),
            true));

        String json = project.toJson();
        assertTrue(json.contains("\"schemaVersion\": 1"));

        BuildingProject restored = BuildingProject.fromJson(json);
        assertEquals(1, restored.getBuildingCount());
    }

    @Test
    void unsupportedFutureSchemaThrows() {
        String json = """
            {
              "schemaVersion": 99,
              "buildings": []
            }
            """;
        assertThrows(IllegalArgumentException.class, () -> BuildingProjectMigrator.normalizeJson(json));
    }
}
