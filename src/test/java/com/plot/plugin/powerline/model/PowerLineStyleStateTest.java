package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.style.PowerLineStyleDefinition;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineStyleStateTest {

    @Test
    void resolveMaterialsFromPresetDefinitionWhenUnmodified() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicWood());
        PowerLineStyleDefinition definition = line.styleInstance().definition();

        if (definition != null) {
            assertEquals(
                definition.getWireMaterial().getPrimaryMaterial(),
                line.getWireMaterial().getPrimaryMaterial());
        }
        assertNull(line.getStyleOverrides().getWireMaterial());
    }

    @Test
    void materialEditStoresOverrideAndResolvesEffectiveValue() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicWood());
        line.setWireMaterial(MaterialMix.single("minecraft:chain"));
        PowerLineStyleEditor.afterStyleEdit(line);

        assertEquals("minecraft:chain", line.getWireMaterial().getPrimaryMaterial());
        assertEquals("minecraft:chain", line.getStyleOverrides().getWireMaterial().getPrimaryMaterial());
    }

    @Test
    void towerFamilyWithoutPresetStoresOverride() {
        PowerLineFootprint line = line();
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);

        assertEquals(TowerFamily.STANDARD_LATTICE_3_PHASE_ID, line.getTowerFamilyId());
        assertEquals(
            TowerFamily.STANDARD_LATTICE_3_PHASE_ID,
            line.getStyleOverrides().getTowerFamilyId());
    }

    @Test
    void poleDesignFallbackCoexistsWithTowerFamily() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicLattice());
        line.setPoleDesignId("custom/pole");

        assertEquals("custom/pole", line.getPoleDesignId());
        assertEquals(
            PowerLineStylePresetCatalog.classicLattice().getTowerFamilyId(),
            line.getTowerFamilyId());
    }

    @Test
    void presetApplyClearsMaterialAndTowerOverrides() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicWood());
        line.setWireMaterial(MaterialMix.single("minecraft:chain"));
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicWood());

        assertNull(line.getStyleOverrides().getWireMaterial());
        assertTrue(line.styleInstance().matchesBaseDefinition());
    }

    @Test
    void resolveSagFromPresetDefinitionWhenUnmodified() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicWood());
        PowerLineStyleDefinition definition = line.styleInstance().definition();

        if (definition != null) {
            assertEquals(definition.getSagPreset().ratio(), line.getSagRatio(), 1e-6);
        }
        assertNull(line.getStyleOverrides().getSagRatio());
    }

    @Test
    void sagEditStoresOverrideAndResolvesEffectiveValue() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicWood());
        line.setSagRatio(0.35);
        PowerLineStyleEditor.afterStyleEdit(line);

        assertEquals(0.35, line.getSagRatio(), 1e-6);
        assertEquals(0.35, line.getStyleOverrides().getSagRatio(), 1e-6);
    }

    @Test
    void unlimitedMaxSagDepthStoresSentinelOverride() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicWood());
        line.setMaxSagDepth(0.0);

        assertTrue(line.isMaxSagDepthUnlimited());
        assertEquals(-1.0, line.getStyleOverrides().getMaxSagDepth(), 1e-6);
    }

    @Test
    void defaultSagAndMaxSagResolveWithoutOverrides() {
        PowerLineFootprint line = line();

        assertEquals(PowerLineFootprint.DEFAULT_SAG_RATIO, line.getSagRatio(), 1e-6);
        assertEquals(
            com.plot.plugin.powerline.PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH,
            line.getMaxSagDepth(),
            1e-6);
        assertTrue(line.getStyleOverrides().isEmpty());
    }

    @Test
    void resolveParametricFromPresetDefinitionWhenUnmodified() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicLattice());
        PowerLineStyleDefinition definition = line.styleInstance().definition();

        assertTrue(line.hasParametricTowerConfig());
        if (definition != null) {
            assertEquals(
                Objects.requireNonNull(definition.getParametricConfig()).profileId(),
                line.getParametricTowerConfig().profileId());
        }
        assertNull(line.getStyleOverrides().getParametricTowerConfig());
        assertFalse(line.getStyleOverrides().isParametricSuppressed());
    }

    @Test
    void parametricEditStoresOverrideAndResolvesEffectiveValue() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.tripleArmTower());
        TowerGeneratorConfig tuned = line.getParametricTowerConfig().withParameters(
            new com.plot.plugin.powerline.design.parametric.TowerParameterSet(
                52.0,
                16.0,
                29.0,
                1.1,
                0.95,
                java.util.List.of(1.05, 1.0),
                com.plot.plugin.powerline.design.parametric.TowerParameterSet.tripleArmDefaults().density()));
        line.setParametricTowerConfig(tuned);
        PowerLineStyleEditor.afterStyleEdit(line);

        assertEquals(52.0, line.getParametricTowerConfig().parameters().height(), 0.01);
        assertNotNull(line.getStyleOverrides().getParametricTowerConfig());
        assertEquals(52.0, line.getStyleOverrides().getParametricTowerConfig().parameters().height(), 0.01);
    }

    @Test
    void clearParametricConfigSuppressesPresetDefinition() {
        PowerLineFootprint line = line();
        PowerLineStyleEditor.selectPreset(line, PowerLineStylePresetCatalog.classicLattice());
        line.setParametricTowerConfig(null);

        assertFalse(line.hasParametricTowerConfig());
        assertTrue(line.getStyleOverrides().isParametricSuppressed());
    }

    private static PowerLineFootprint line() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
    }
}
