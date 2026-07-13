package com.plot.plugin.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
