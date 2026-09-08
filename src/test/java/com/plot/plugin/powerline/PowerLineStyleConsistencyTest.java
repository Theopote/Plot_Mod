package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamilyCatalog;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 风格一致性自动化验收（缩略图绑定 / apply 往返 / 生成冒烟）。
 * <p>
 * 人工 in-game 对照见 {@code docs/plugins/电力线路_风格一致性验收.md}。
 */
class PowerLineStyleConsistencyTest {

    static Stream<PowerLineStylePreset> allPresets() {
        return PowerLineStylePresetCatalog.defaultPresets().stream();
    }

    @ParameterizedTest
    @MethodSource("allPresets")
    void applyThenMatchesBundle(PowerLineStylePreset preset) {
        PowerLineFootprint line = sampleLine();
        preset.apply(line);

        assertEquals(preset.getId(), line.getStylePackId());
        assertTrue(preset.matchesBundle(line), preset.getId() + " should match after apply()");
        PowerLineStylePreset active = PowerLineStylePresetCatalog.activePreset(line);
        assertNotNull(active, preset.getId() + " should be active preset");
        assertEquals(preset.getId(), active.getId());
    }

    @ParameterizedTest
    @MethodSource("allPresets")
    void poleOrFamilyReferenceExists(PowerLineStylePreset preset) {
        if (preset.getTowerFamilyId() != null && !preset.getTowerFamilyId().isBlank()) {
            assertNotNull(
                TowerFamilyCatalog.findBuiltin(preset.getTowerFamilyId()),
                preset.getId() + " tower family");
            return;
        }
        assertNotNull(
            PoleDesignCatalog.findBuiltin(preset.getPoleDesignId()),
            preset.getId() + " pole design");
    }

    @ParameterizedTest
    @MethodSource("allPresets")
    void previewBindingAlignsWithApply(PowerLineStylePreset preset) {
        assertTrue(
            PowerLineStylePreviewBinding.previewAlignsWithApply(preset),
            preset.getId() + " thumbnail primary design should align with apply()");
    }

    @ParameterizedTest
    @MethodSource("allPresets")
    void sagPresetApplied(PowerLineStylePreset preset) {
        PowerLineFootprint line = sampleLine();
        preset.apply(line);

        assertEquals(
            preset.getSagPreset().ratio(),
            line.getSagRatio(),
            0.01,
            preset.getId() + " sag ratio");
    }

    @ParameterizedTest
    @MethodSource("allPresets")
    void generationSmokeProducesBlocksAndWireMaterial(PowerLineStylePreset preset) {
        PowerLineFootprint line = sampleLine();
        preset.apply(line);
        line.setMaxPoleSpacing(50.0);

        PowerLineGenerationResult result = PowerLineGeneratorWireTest.generate(line);

        assertTrue(result.poleCount >= 2, preset.getId() + " pole count");
        assertTrue(result.blockCount() > 0, preset.getId() + " block count");
        String wireBlock = preset.getWireMaterial().getPrimaryMaterial();
        assertTrue(
            result.placementRecords.values().stream().anyMatch(r -> wireBlock.equals(r.newBlockId)),
            preset.getId() + " should place wire material " + wireBlock);
        assertFalse(
            result.warnings.stream().anyMatch(w -> w.contains("not_found")),
            preset.getId() + " should not warn about missing designs");
    }

    @ParameterizedTest
    @MethodSource("allPresets")
    void labelAndDescriptionKeysExistInLangFiles(PowerLineStylePreset preset) throws IOException {
        assertLangKeyPresent("en_us.json", preset.getLabelKey());
        assertLangKeyPresent("en_us.json", preset.getDescriptionKey());
        assertLangKeyPresent("zh_cn.json", preset.getLabelKey());
        assertLangKeyPresent("zh_cn.json", preset.getDescriptionKey());
    }

    private static void assertLangKeyPresent(String langFile, String key) throws IOException {
        Path path = Path.of("src/main/resources/assets/plot/lang", langFile);
        String json = Files.readString(path);
        assertTrue(json.contains("\"" + key + "\""), key + " missing in " + langFile);
    }

    private static PowerLineFootprint sampleLine() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
    }
}
