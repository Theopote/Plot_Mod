package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.StyleCategory;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PowerLineStyleCardRendererTest {

    @Test
    void catalogContainsTwentyThreeStylePresets() {
        assertEquals(23, PowerLineStylePresetCatalog.defaultPresets().size());
        assertEquals(4, PowerLineStylePresetCatalog.galleryCategories().size());
        assertEquals(10, PowerLineStylePresetCatalog.utilityPresets().size());
        assertEquals(6, PowerLineStylePresetCatalog.transmissionPresets().size());
        assertEquals(4, PowerLineStylePresetCatalog.industrialPresets().size());
        assertEquals(3, PowerLineStylePresetCatalog.fantasyPresets().size());
        assertEquals(
            10,
            PowerLineStylePresetCatalog.presetsByCategory(StyleCategory.UTILITY).size());
        assertEquals(
            6,
            PowerLineStylePresetCatalog.presetsByCategory(StyleCategory.TRANSMISSION).size());
        assertEquals(
            4,
            PowerLineStylePresetCatalog.presetsByCategory(StyleCategory.INDUSTRIAL).size());
        assertEquals(
            3,
            PowerLineStylePresetCatalog.presetsByCategory(StyleCategory.FANTASY).size());
    }

    @Test
    void applyClassicWoodSetsPoleDesignAndPackId() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePreset pack = PowerLineStylePresetCatalog.classicWood();
        pack.apply(line);
        assertEquals(PowerLineStylePreset.RUSTIC_WOOD_ID, line.getStylePresetId());
        assertEquals(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID, line.getPoleDesignId());
        assertNull(line.getTowerFamilyId());
    }

    @Test
    void applyDoubleWoodSetsDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.doubleWood().apply(line);
        assertEquals(PoleDesignCatalog.DOUBLE_WOOD_POLE_ID, line.getPoleDesignId());
    }

    @Test
    void applyHeavyLatticeSetsTowerFamily() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.heavyLattice().apply(line);
        assertEquals(TowerFamily.HEAVY_TRANSMISSION_ID, line.getTowerFamilyId());
        assertNull(line.getPoleDesignId());
    }

    @Test
    void applyClassicLatticeSetsTowerFamily() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.classicLattice().apply(line);
        assertEquals(TowerFamily.STANDARD_LATTICE_3_PHASE_ID, line.getTowerFamilyId());
        assertNull(line.getPoleDesignId());
    }

    @Test
    void applyJapaneseStreetSetsDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.japaneseStreet().apply(line);
        assertEquals(PoleDesignCatalog.JAPANESE_STREET_POLE_ID, line.getPoleDesignId());
    }

    @Test
    void descriptionKeyFollowsLabelConvention() {
        PowerLineStylePreset pack = PowerLineStylePresetCatalog.steampunkBrass();
        assertEquals(pack.getLabelKey() + ".desc", pack.getDescriptionKey());
        assertEquals("plugin.powerline.style.pack.steampunk_brass.desc", pack.getDescriptionKey());
    }

    @Test
    void applyWastelandWindSetsDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePresetCatalog.wastelandWind().apply(line);
        assertEquals(PoleDesignCatalog.WASTELAND_WIND_TURBINE_ID, line.getPoleDesignId());
    }
}
