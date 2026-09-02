package com.plot.plugin.earthwork.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkProjectMigratorTest {

    @Test
    void detectsV1WhenOnlyLegacyRegionsPresent() {
        String json = """
            {
              "regions": [{
                "id": "r1",
                "outerPoints": [
                  {"x": 0, "y": 0},
                  {"x": 10, "y": 0},
                  {"x": 10, "y": 10}
                ]
              }]
            }
            """;
        assertEquals(EarthworkProjectSchema.V1, EarthworkProjectSchema.resolveStoredVersion(json));
    }

    @Test
    void detectsV2WhenSitesPresentWithoutSchemaVersion() {
        String json = """
            {
              "sites": [{
                "id": "s1",
                "gradingZones": []
              }]
            }
            """;
        assertEquals(EarthworkProjectSchema.V2, EarthworkProjectSchema.resolveStoredVersion(json));
    }

    @Test
    void v1JsonMigratesThroughV2ToV3() {
        String json = """
            {
              "regions": [{
                "id": "r1",
                "name": "North",
                "outerPoints": [
                  {"x": 0, "y": 0},
                  {"x": 10, "y": 0},
                  {"x": 10, "y": 8}
                ],
                "surfaceMode": "THREE_POINT"
              }]
            }
            """;
        String normalized = EarthworkProjectMigrator.normalizeJson(json);
        JsonObject root = JsonParser.parseString(normalized).getAsJsonObject();
        assertEquals(EarthworkProjectSchema.V3, root.get("schemaVersion").getAsInt());
        assertFalse(root.has("regions"));

        EarthworkProject project = EarthworkProject.fromJson(json);
        assertEquals(EarthworkProjectSchema.CURRENT, project.getSchemaVersion());
        assertEquals(1, project.getRegionCount());
        assertEquals(GradingZoneType.SLOPED, project.getActiveSite().getZone("r1").getType());
    }

    @Test
    void v2JsonPromotesOuterRingAndUpgradesToV3() {
        String json = """
            {
              "schemaVersion": 2,
              "sites": [{
                "id": "s1",
                "gradingZones": [{
                  "id": "z1",
                  "outerPoints": [
                    {"x": 0, "y": 0},
                    {"x": 8, "y": 0},
                    {"x": 8, "y": 6},
                    {"x": 0, "y": 6}
                  ]
                }]
              }]
            }
            """;
        String normalized = EarthworkProjectMigrator.normalizeJson(json);
        JsonObject zone = rootZone(normalized);
        assertTrue(zone.has("outerRing"));
        assertEquals(4, zone.getAsJsonArray("outerRing").size());
        assertTrue(zone.has("designSurface"));
        assertTrue(zone.has("edgeSettings"));

        EarthworkProject project = EarthworkProject.fromJson(json);
        assertEquals(EarthworkProjectSchema.CURRENT, project.getSchemaVersion());
        GradingZone restored = project.getActiveSite().getZone("z1");
        assertEquals(4, restored.getOuterPoints().size());
    }

    @Test
    void currentProjectSavesAsSchemaV3() {
        EarthworkProject project = new EarthworkProject();
        project.getActiveSite().addZone(new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)
        )));

        String json = project.toJson();
        assertTrue(json.contains("\"schemaVersion\": 3"));

        EarthworkProject restored = EarthworkProject.fromJson(json);
        assertEquals(EarthworkProjectSchema.CURRENT, restored.getSchemaVersion());
    }

    @Test
    void newerSchemaVersionIsRejected() {
        String json = """
            {
              "schemaVersion": 99,
              "sites": []
            }
            """;
        assertThrows(IllegalArgumentException.class, () -> EarthworkProjectMigrator.load(json));
    }

  private static JsonObject rootZone(String normalizedJson) {
    JsonObject root = JsonParser.parseString(normalizedJson).getAsJsonObject();
    return root.getAsJsonArray("sites").get(0).getAsJsonObject()
        .getAsJsonArray("gradingZones").get(0).getAsJsonObject();
  }
}
