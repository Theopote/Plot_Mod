package com.plot.plugin.road.model.serialization.migration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkMigrationRegistryTest {

    @Test
    void migratesLegacyDocumentWithoutFormatVersion() throws Exception {
        JsonObject root = JsonParser.parseString("""
            {
              "nodes": [],
              "edges": [],
              "roads": []
            }
            """).getAsJsonObject();

        JsonObject migrated = RoadNetworkMigrationRegistry.getInstance().migrateToCurrent(root);

        assertEquals(RoadNetwork.CURRENT_FORMAT_VERSION, migrated.get("formatVersion").getAsInt());
        assertTrue(migrated.get("nodes").isJsonArray());
        assertTrue(migrated.get("edges").isJsonArray());
        assertTrue(migrated.get("roads").isJsonArray());
    }
}
