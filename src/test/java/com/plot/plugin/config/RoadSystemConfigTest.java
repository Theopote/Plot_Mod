package com.plot.plugin.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoadSystemConfigTest {

    @Test
    void slopeMaterialGettersHandleNullFromJson() {
        RoadSystemConfig config = new Gson().fromJson(
            "{\"fillSlopeMaterial\":null,\"cutSlopeMaterial\":null}",
            RoadSystemConfig.class);

        assertFalse(config.getFillSlopeMaterial().isBlank());
        assertFalse(config.getCutSlopeMaterial().isBlank());
        assertEquals(config.getFillSlopeMaterial(), config.getCutSlopeMaterial());
    }

    @Test
    void cutSlopeMaterialFallsBackToFillWhenBlank() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setFillSlopeMaterial("material.plot.gravel");
        config.setCutSlopeMaterial("");

        assertEquals("material.plot.gravel", config.getCutSlopeMaterial());
    }

    @Test
    void saveToUsesAtomicWriter(@TempDir Path dir) throws IOException {
        RoadSystemConfig config = new RoadSystemConfig("workflow_test");
        config.setLaneCount(4);

        Path file = dir.resolve("workflow_test.json");
        config.saveTo(file);

        assertNotNull(Files.readString(file));
        assertFalse(Files.exists(dir.resolve("workflow_test.json.tmp")));

        RoadSystemConfig loaded = RoadSystemConfig.loadFrom(file, RoadSystemConfig.class, "workflow_test");
        assertNotNull(loaded);
        assertEquals(4, loaded.getLaneCount());
    }
}
