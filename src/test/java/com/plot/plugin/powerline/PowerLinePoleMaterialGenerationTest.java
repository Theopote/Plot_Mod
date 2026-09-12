package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.plot.plugin.powerline.PresetMinecraftRealizabilitySupport.assertQuickTunePoleMaterialInGeneration;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePoleMaterialGenerationTest {

    @Test
    void poleMaterialQuickTuneAffectsGeneratedWoodPoleBlocks() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.classicWood().apply(line);
        line.setPoleMaterial(MaterialMix.single("minecraft:dark_oak_log"));
        line.setMaxPoleSpacing(50.0);

        PowerLineGenerationResult result = PowerLineGeneratorWireTest.generate(line);
        assertQuickTunePoleMaterialInGeneration("classicWood", result, "minecraft:dark_oak_log");
    }

    @Test
    void allBuiltinPresetsAreCoveredByQuickTuneGeneratorMatrix() {
        assertTrue(
            PresetMinecraftRealizabilitySupport.allPresets().size() >= 25,
            "Quick Tune generator matrix should track all builtin presets");
    }
}
