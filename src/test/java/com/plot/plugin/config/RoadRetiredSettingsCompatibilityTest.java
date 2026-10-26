package com.plot.plugin.config;

import com.google.gson.Gson;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RoadRetiredSettingsCompatibilityTest {
    @Test
    void oldSettingsDoNotChangeActiveConfigurationOrSurviveSave(@TempDir Path directory) throws Exception {
        var config = new RoadSystemConfig("road_system");
        config.setRoadWidth(9);
        config.setMaxSlope(7.5f);
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        var json = new Gson().toJsonTree(config).getAsJsonObject();
        json.addProperty("oscillationReductionEnabled", true);
        json.addProperty("maxContinuousSlopeLength", 15.0);
        json.addProperty("relaxedSlopeLength", 3.0);
        json.addProperty("relaxedSlopePercent", 0.5);
        json.addProperty("pathLength", 2000.0);
        Path file = directory.resolve("road_system.json");
        Files.writeString(file, json.toString());

        var loaded = RoadSystemConfig.loadFrom(file, RoadSystemConfig.class, "road_system");

        assertNotNull(loaded);
        assertEquals(9, loaded.getRoadWidth());
        assertEquals(7.5f, loaded.getMaxSlope());
        assertEquals(RoadTerrainStyle.SMOOTH, loaded.getTerrainStyle());
        assertEquals(config.generationInputsFingerprint(), loaded.generationInputsFingerprint());
        loaded.saveTo(file);
        String saved = Files.readString(file);
        for (String key : new String[] {"oscillationReductionEnabled", "maxContinuousSlopeLength",
                "relaxedSlopeLength", "relaxedSlopePercent", "pathLength"}) {
            assertFalse(saved.contains('"' + key + '"'), key);
        }
    }
}
