package com.plot.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.adopt.EarthworkAdoptDefaults;
import com.plot.plugin.earthwork.model.GradingRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkConfigTest {

    @Test
    void inventorySeparatesPluginPreferenceAdoptDefaultAndRuntimeLegacy() {
        assertEquals(EarthworkConfigInventory.FieldKind.PLUGIN_PREFERENCE,
            EarthworkConfigInventory.WORK_MODE.kind());
        assertEquals(EarthworkConfigInventory.FieldKind.ADOPT_DEFAULT,
            EarthworkConfigInventory.AUTO_BALANCE.kind());
        assertEquals(EarthworkConfigInventory.FieldKind.RUNTIME_LEGACY,
            EarthworkConfigInventory.CUT_VOLUME.kind());
        assertEquals(9, EarthworkConfigInventory.allFields().length);
    }

    @Test
    void normalizeAfterLoadStripsRuntimeLegacyFields(@TempDir Path tempDir) throws Exception {
        Path configDir = tempDir.resolve("plugins");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("earthwork_balance.json");
        String legacyJson = """
            {
              "autoBalance": true,
              "targetElevation": 12.0,
              "cutVolume": 100.0,
              "fillVolume": 200.0
            }
            """;
        Files.writeString(configFile, legacyJson, StandardCharsets.UTF_8);

        EarthworkConfig config = new Gson().fromJson(legacyJson, EarthworkConfig.class);
        config.normalizeAfterLoad();

        assertEquals(0.0f, config.getTargetElevation());
        assertEquals(0.0f, config.getCutVolume());
        assertEquals(0.0f, config.getFillVolume());
        assertTrue(config.isAdoptDefaultAutoBalance());
    }

    @Test
    void gsonSerializationOmitsTransientRuntimeFields() {
        EarthworkConfig config = new EarthworkConfig("earthwork_balance");
        config.setCutVolume(999f);
        config.setFillVolume(888f);
        config.setTargetElevation(12f);
        config.setAdoptDefaultAutoBalance(true);

        String json = new GsonBuilder().setPrettyPrinting().create().toJson(config);
        assertFalse(json.contains("cutVolume"));
        assertFalse(json.contains("fillVolume"));
        assertFalse(json.contains("targetElevation"));
        assertTrue(json.contains("autoBalance"));
    }

    @Test
    void adoptDefaultsUsesTerrainFallbackWithoutWorld() {
        GradingRegion region = new GradingRegion(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        EarthworkConfig config = new EarthworkConfig("earthwork_balance");
        config.setAdoptDefaultAutoBalance(false);

        EarthworkAdoptDefaults.applyToNewRegion(region, config, null, null);

        assertFalse(region.isAutoBalance());
        assertEquals(64, region.getManualTargetElevation());
    }
}
