package com.plot.plugin.config;

import com.google.gson.Gson;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSystemConfigTest {

    @Test
    void defaultsToPreferredAutoGradeRunLength() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");

        assertEquals(180.0, config.getMaxContinuousSlopeLength(), 0.001);
    }

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
        config.setGenerateBridgePillars(false);
        config.setIncludeBridgeGuardrail(true);
        config.setBridgeGuardrailMaterial("minecraft:dark_oak_fence");
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.FOLLOW);
        config.setTunnelLiningMaterial("minecraft:deepslate_bricks");
        config.setTunnelAccentMaterial("minecraft:polished_andesite");
        config.setTunnelAccentSpacing(6);

        Path file = dir.resolve("workflow_test.json");
        config.saveTo(file);

        assertNotNull(Files.readString(file));
        assertFalse(Files.exists(dir.resolve("workflow_test.json.tmp")));

        RoadSystemConfig loaded = RoadSystemConfig.loadFrom(file, RoadSystemConfig.class, "workflow_test");
        assertNotNull(loaded);
        assertEquals(4, loaded.getLaneCount());
        assertFalse(loaded.isGenerateBridgePillars());
        assertTrue(loaded.isIncludeBridgeGuardrail());
        assertEquals("minecraft:dark_oak_fence", loaded.getBridgeGuardrailMaterial());
        assertEquals(com.plot.plugin.road.terrain.RoadTerrainStyle.FOLLOW, loaded.getTerrainStyle());
        assertEquals("minecraft:deepslate_bricks", loaded.getTunnelLiningMaterial());
        assertEquals("minecraft:polished_andesite", loaded.getTunnelAccentMaterial());
        assertEquals(6, loaded.getTunnelAccentSpacing());
    }

    @Test
    void tunnelAccentSettingsClampAndNullMaterialsRemainSafe() {
        RoadSystemConfig config = new Gson().fromJson(
            "{\"tunnelLiningMaterial\":null,\"tunnelAccentMaterial\":null,\"tunnelAccentSpacing\":99}",
            RoadSystemConfig.class);

        assertEquals("minecraft:stone_bricks", config.getTunnelLiningMaterial());
        assertEquals("", config.getTunnelAccentMaterial());
        assertEquals(32, config.getTunnelAccentSpacing());
    }

    @Test
    void legacyMinimalPresetJsonDeserializesAsRoadStyle() {
        RoadSystemConfig config = new Gson().fromJson(
            """
            {
              "selectedPreset": "legacy_custom",
              "presets": [
                {
                  "id": "legacy_custom",
                  "name": "Legacy Custom",
                  "width": 9,
                  "hasSidewalk": true,
                  "sidewalkWidth": 2
                }
              ]
            }
            """,
            RoadSystemConfig.class);

        assertEquals("legacy_custom", config.getSelectedPreset());
        assertEquals(1, config.getStyles().size());
        assertEquals("legacy_custom", config.getStyles().getFirst().id);
        assertEquals("Legacy Custom", config.getStyles().getFirst().name);
        assertEquals(9, config.getStyles().getFirst().width);
        assertEquals(2, config.getStyles().getFirst().sidewalkWidth);
    }

    @Test
    void loadFromMergesMissingBuiltinStylesForLegacyPresetList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("road_system.json");
        Files.writeString(file, """
            {
              "presets": [
                {
                  "id": "legacy_custom",
                  "name": "Legacy Custom",
                  "width": 9,
                  "hasSidewalk": false,
                  "sidewalkWidth": 0
                }
              ]
            }
            """);

        RoadSystemConfig loaded = RoadSystemConfig.loadFrom(file, RoadSystemConfig.class, "road_system");
        assertNotNull(loaded);
        assertTrue(loaded.getStyles().stream().anyMatch(style -> "legacy_custom".equals(style.id)));
        assertTrue(loaded.getStyles().stream().anyMatch(style -> "mountain".equals(style.id)));
    }
}
