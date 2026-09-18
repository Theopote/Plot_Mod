package com.plot.plugin.pattern.model;

import com.plot.plugin.pattern.model.ImagePatternConfig.MaterialMatchMode;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInPatternPresetsTest {

    private static final Set<String> VANILLA_BLOCK_IDS = loadVanillaBlockIds();

    @Test
    void allBuiltInPresetMaterialsAreValidBlocks() {
        for (PatternPreset preset : BuiltInPatternPresets.all()) {
            for (String blockId : materialBlockIds(preset)) {
                assertFalse(
                    PatternBlockIdValidator.isKnownInvalid(blockId),
                    () -> preset.getId() + " uses known-invalid block id: " + blockId);
                assertTrue(
                    PatternBlockIdValidator.looksLikeBlockId(blockId),
                    () -> preset.getId() + " uses malformed block id: " + blockId);
                assertTrue(
                    VANILLA_BLOCK_IDS.contains(blockId),
                    () -> preset.getId() + " uses unregistered block id: " + blockId);
            }
        }
    }

    private static List<String> materialBlockIds(PatternPreset preset) {
        List<String> blockIds = new ArrayList<>();
        if (preset.getSource() == PatternSource.PROCEDURAL) {
            blockIds.addAll(preset.getProceduralConfig().getMaterials());
            return blockIds;
        }
        ImagePatternConfig imageConfig = preset.getImageConfig();
        if (imageConfig != null && imageConfig.getMaterialMatchMode() == MaterialMatchMode.CUSTOM) {
            blockIds.addAll(imageConfig.getPaletteBlocks());
        }
        return blockIds;
    }

    private static Set<String> loadVanillaBlockIds() {
        Set<String> ids = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            BuiltInPatternPresetsTest.class.getResourceAsStream("/pattern/vanilla_block_ids.txt"),
            StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    ids.add(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Missing pattern/vanilla_block_ids.txt test resource", e);
        }
        return Set.copyOf(ids);
    }
}
