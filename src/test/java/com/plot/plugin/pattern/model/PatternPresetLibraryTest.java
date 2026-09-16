package com.plot.plugin.pattern.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPresetLibraryTest {

    @TempDir
    Path tempDir;

    @Test
    void alwaysProvidesBuiltInPresets() {
        PatternPresetLibrary library = new PatternPresetLibrary(tempDir);
        assertEquals(9, library.getBuiltInPresets().size());
        assertTrue(library.getBuiltInPresets().stream()
            .allMatch(PatternPreset::isBuiltIn));
        assertNotNull(library.getPreset("builtin:checkerboard_classic"));
    }

    @Test
    void userPresetsPersistSeparatelyFromBuiltIns() {
        PatternPresetLibrary library = new PatternPresetLibrary(tempDir);
        PatternPreset user = new PatternPreset("My preset", new ProceduralPatternConfig());
        library.addPreset(user);

        assertEquals(1, library.getUserPresets().size());
        assertEquals(9, library.getBuiltInPresets().size());

        PatternPresetLibrary reloaded = new PatternPresetLibrary(tempDir);
        assertEquals(1, reloaded.getUserPresets().size());
        assertEquals("My preset", reloaded.getUserPresets().getFirst().getName());
        assertEquals(9, reloaded.getBuiltInPresets().size());
    }

    @Test
    void userPresetsFileDoesNotContainBuiltIns() throws IOException {
        PatternPresetLibrary library = new PatternPresetLibrary(tempDir);
        library.addPreset(new PatternPreset("Saved", new ProceduralPatternConfig()));

        String json = Files.readString(tempDir.resolve("presets/user_presets.json"));
        Gson gson = new GsonBuilder().create();
        List<PatternPreset> saved = gson.fromJson(json, new TypeToken<List<PatternPreset>>() {}.getType());
        assertEquals(1, saved.size());
        assertFalse(saved.getFirst().isBuiltIn());
    }

    @Test
    void migratesLegacyMixedPresetFile() throws IOException {
        PatternPreset legacyUser = new PatternPreset("Legacy", new ProceduralPatternConfig());
        PatternPreset legacyBuiltin = new PatternPreset("Old builtin", new ProceduralPatternConfig());
        legacyBuiltin.setBuiltIn(true);
        legacyBuiltin.setId("old-builtin-id");

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.createDirectories(tempDir.resolve("presets"));
        Files.writeString(
            tempDir.resolve("presets/pattern_presets.json"),
            gson.toJson(List.of(legacyUser, legacyBuiltin)));

        PatternPresetLibrary library = new PatternPresetLibrary(tempDir);
        assertEquals(1, library.getUserPresets().size());
        assertEquals("Legacy", library.getUserPresets().getFirst().getName());
        assertEquals(9, library.getBuiltInPresets().size());
        assertTrue(Files.exists(tempDir.resolve("presets/user_presets.json")));
    }
}
