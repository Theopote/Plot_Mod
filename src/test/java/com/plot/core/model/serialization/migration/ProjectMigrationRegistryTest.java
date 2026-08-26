package com.plot.core.model.serialization.migration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plot.core.model.ProjectFormatException;
import com.plot.core.model.serialization.ProjectSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMigrationRegistryTest {

    @Test
    void migratesV1ToCurrentAndFillsLayerDefaults() throws ProjectFormatException {
        JsonObject root = JsonParser.parseString("""
                {
                  "formatVersion": 1,
                  "name": "Demo",
                  "layers": [ { "id": "a", "name": "A" } ]
                }
                """).getAsJsonObject();

        ProjectMigrationRegistry.getInstance().migrateToCurrent(root);

        assertEquals(ProjectSnapshot.CURRENT_FORMAT_VERSION, root.get("formatVersion").getAsInt());
        JsonObject layer = root.getAsJsonArray("layers").get(0).getAsJsonObject();
        assertEquals(1.0, layer.get("opacity").getAsDouble(), 0.0001);
        assertTrue(layer.has("shapes"));
        assertTrue(layer.has("visible"));
    }

    @Test
    void futureVersionThrowsUnsupported() {
        JsonObject root = JsonParser.parseString("""
                { "formatVersion": 99, "name": "Future", "layers": [] }
                """).getAsJsonObject();

        ProjectFormatException ex = assertThrows(ProjectFormatException.class,
            () -> ProjectMigrationRegistry.getInstance().migrateToCurrent(root));
        assertEquals(ProjectFormatException.Reason.UNSUPPORTED_FORMAT_VERSION, ex.getReason());
    }

    @Test
    void currentVersionIsNoOp() throws ProjectFormatException {
        JsonObject root = JsonParser.parseString("""
                {
                  "formatVersion": %d,
                  "name": "Current",
                  "layers": []
                }
                """.formatted(ProjectSnapshot.CURRENT_FORMAT_VERSION)).getAsJsonObject();

        ProjectMigrationRegistry.getInstance().migrateToCurrent(root);
        assertEquals(ProjectSnapshot.CURRENT_FORMAT_VERSION, root.get("formatVersion").getAsInt());
    }
}
