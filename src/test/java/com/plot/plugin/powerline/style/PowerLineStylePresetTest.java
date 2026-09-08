package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLineUiPresets;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineStylePresetTest {

    @Test
    void classicWoodAppliesFullBundle() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setWireMaterial(MaterialMix.single("minecraft:chain"));

        PowerLineStylePreset preset = PowerLineStylePackCatalog.classicWood();
        preset.apply(line);

        assertEquals(PowerLineStylePreset.RUSTIC_WOOD_ID, line.getStylePackId());
        assertEquals(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID, line.getPoleDesignId());
        assertNull(line.getTowerFamilyId());
        assertEquals("minecraft:oak_fence", line.getPoleMaterial().getPrimaryMaterial());
        assertEquals("minecraft:iron_bars", line.getWireMaterial().getPrimaryMaterial());
        assertEquals(0.15, line.getSagRatio(), 0.001);
        assertEquals(1, preset.conductorCount());
        assertTrue(preset.matchesBundle(line));
    }

    @Test
    void partialMatchDoesNotCountAsBundle() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePackCatalog.classicWood().apply(line);
        line.setWireMaterial(MaterialMix.single("minecraft:chain"));

        assertFalse(PowerLineStylePackCatalog.classicWood().matchesBundle(line));
    }

    @Test
    void detectFindsPresetByStoredIdAndBundle() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePackCatalog.rustic().apply(line);

        PowerLineStylePreset detected = PowerLineStylePackCatalog.detect(line);
        assertEquals(PowerLineStylePreset.RUSTIC_ID, detected.getId());
        assertEquals(PowerLineStylePreset.ConductorLayout.SINGLE, detected.getConductorLayout());
    }

    @Test
    void latticePresetUsesThreePhaseConductors() {
        PowerLineStylePreset preset = PowerLineStylePackCatalog.classicLattice();
        assertEquals(3, preset.conductorCount());
        assertEquals(PowerLineStylePreset.ConductorLayout.THREE_PHASE_HORIZONTAL, preset.getConductorLayout());
    }

    @Test
    void woodPoleDesignHasExplicitConductorAttachments() {
        assertTrue(PoleDesignCatalog.simpleWoodPole().hasEnabledAttachments());
        assertEquals(
            1,
            PowerLineStylePreset.countConductors(PoleDesignCatalog.simpleWoodPole()));
        assertEquals(
            3,
            PowerLineStylePreset.countConductors(PoleDesignCatalog.doubleWoodPole()));
    }
}
