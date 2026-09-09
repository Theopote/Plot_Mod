package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineStyleDefinitionTest {

    @Test
    void presetExposesDefinitionBundle() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.japaneseStreet();
        PowerLineStyleDefinition definition = preset.getDefinition();

        assertNotNull(definition);
        assertEquals(preset.getWireMaterial(), definition.getWireMaterial());
        assertEquals(preset.getSpacingProfile(), definition.getSpacingProfile());
        assertEquals(preset.getPreviewKind(), definition.getPreviewKind());
    }

    @Test
    void definitionApplyMatchesPresetApply() {
        PowerLineFootprint fromPreset = line();
        PowerLineFootprint fromDefinition = line();

        PowerLineStylePreset preset = PowerLineStylePresetCatalog.classicWood();
        preset.apply(fromPreset);
        preset.getDefinition().applyTo(fromDefinition, preset.getId());

        assertEquals(fromPreset.getStylePackId(), fromDefinition.getStylePackId());
        assertEquals(fromPreset.getPoleDesignId(), fromDefinition.getPoleDesignId());
        assertEquals(fromPreset.getWireMaterial().getPrimaryMaterial(), fromDefinition.getWireMaterial().getPrimaryMaterial());
        assertTrue(preset.getDefinition().matches(fromDefinition));
    }

    @Test
    void instanceTracksBasePresetAndOverrides() {
        PowerLineFootprint line = line();
        PowerLineStylePresetCatalog.japaneseStreet().apply(line);
        line.setWireMaterial(MaterialMix.single("minecraft:chain"));
        PowerLineStyleEditor.afterStyleEdit(line);

        PowerLineStyleInstance instance = line.styleInstance();
        assertNotNull(instance);
        assertEquals(PowerLineStylePreset.JAPANESE_STREET_ID, instance.basePresetId());
        assertNotNull(instance.definition());
        assertTrue(instance.isModified());
        assertFalse(instance.matchesBaseDefinition());
        assertNotNull(instance.overrides().getWireMaterial());
    }

    @Test
    void instanceMatchesWhenUnmodified() {
        PowerLineFootprint line = line();
        PowerLineStylePresetCatalog.classicWood().apply(line);
        PowerLineStyleEditor.afterStyleEdit(line);

        PowerLineStyleInstance instance = line.styleInstance();
        assertFalse(instance.isModified());
        assertTrue(instance.matchesBaseDefinition());
    }

    private static PowerLineFootprint line() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
    }
}
