package com.plot.plugin.config;

import com.google.gson.Gson;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.tunnel.TunnelShape;
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
        config.setFillSlopeMaterial("minecraft:coarse_dirt");
        config.setCutSlopeMaterial("");

        assertEquals("minecraft:coarse_dirt", config.getCutSlopeMaterial());
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
    void tunnelStyleAccentSettingsClampAndDefaultsRemainSafe(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("road_system.json");
        Files.writeString(file, """
            {
              "tunnelStyle": {
                "liningMaterial": null,
                "accentMaterial": null,
                "accentSpacing": 99,
                "accentRings": true
              }
            }
            """);
        RoadSystemConfig config = RoadSystemConfig.loadFrom(file, RoadSystemConfig.class, "road_system");

        assertEquals("minecraft:stone_bricks", config.getTunnelLiningMaterial());
        assertEquals("", config.getTunnelAccentMaterial());
        assertEquals(32, config.getTunnelAccentSpacing());
    }

    @Test
    void tunnelStyleNestedFieldsRoundTrip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("road_system.json");
        Files.writeString(file, """
            {
              "tunnelStyle": {
                "liningMaterial": "minecraft:deepslate_bricks",
                "accentMaterial": "minecraft:polished_andesite",
                "accentSpacing": 6,
                "accentRings": true
              }
            }
            """);
        RoadSystemConfig config = RoadSystemConfig.loadFrom(file, RoadSystemConfig.class, "road_system");

        assertEquals("minecraft:deepslate_bricks", config.getTunnelLiningMaterial());
        assertEquals("minecraft:polished_andesite", config.getTunnelAccentMaterial());
        assertEquals(6, config.getTunnelAccentSpacing());
        assertTrue(config.getTunnelStyle().isAccentRings());
        assertEquals(TunnelShape.ARCH, config.getTunnelStyle().getShape());
    }

    @Test
    void minimalPresetJsonDeserializesAsRoadStyle() {
        RoadSystemConfig config = new Gson().fromJson(
            """
            {
              "selectedPreset": "custom_street",
              "presets": [
                {
                  "id": "custom_street",
                  "name": "Custom Street",
                  "width": 9,
                  "hasSidewalk": true,
                  "sidewalkWidth": 2
                }
              ]
            }
            """,
            RoadSystemConfig.class);

        assertEquals("custom_street", config.getSelectedPreset());
        assertEquals(1, config.getStyles().size());
        assertEquals("custom_street", config.getStyles().getFirst().id);
        assertEquals("Custom Street", config.getStyles().getFirst().name);
        assertEquals(9, config.getStyles().getFirst().width);
        assertEquals(2, config.getStyles().getFirst().sidewalkWidth);
    }

    @Test
    void loadFromMergesMissingBuiltinStyles(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("road_system.json");
        Files.writeString(file, """
            {
              "presets": [
                {
                  "id": "custom_street",
                  "name": "Custom Street",
                  "width": 9,
                  "hasSidewalk": false,
                  "sidewalkWidth": 0
                }
              ]
            }
            """);

        RoadSystemConfig loaded = RoadSystemConfig.loadFrom(file, RoadSystemConfig.class, "road_system");
        assertNotNull(loaded);
        assertTrue(loaded.getStyles().stream().anyMatch(style -> "custom_street".equals(style.id)));
        assertTrue(loaded.getStyles().stream().anyMatch(style -> "mountain".equals(style.id)));
    }
}
