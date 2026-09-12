package com.plot.plugin.powerline.design.parametric;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerSilhouette;
import com.plot.plugin.powerline.style.PowerLineStyleParametricCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerParametricDecorativeProfilesTest {

    @Test
    void steampunkProfileUsesCopperAndGoldMaterials() {
        PoleDesign compiled = TowerParametricDesignFactory.compileSteampunk(TowerParameterSet.steampunkDefaults());

        assertEquals(TowerSilhouette.DOUBLE_ARM, compiled.getTowerStructure().getSilhouette());
        assertEquals(2, compiled.getTowerStructure().getArms().size());
        assertMaterial("minecraft:copper_block", compiled.getTowerStructure().getPrimaryMaterial());
        assertMaterial("minecraft:cut_copper", compiled.getTowerStructure().getBraceMaterial());
        assertMaterial("minecraft:gold_block", compiled.getTowerStructure().getArms().getFirst().getMaterial());
        assertEquals(28.0, compiled.getTowerStructure().maxHeight(), 0.5);
    }

    @Test
    void modernHvGlassProfileUsesIronAndSeaLanternArm() {
        PoleDesign compiled = TowerParametricDesignFactory.compileModernHvGlass(TowerParameterSet.modernHvGlassDefaults());

        assertEquals(TowerSilhouette.TAPERED_LATTICE, compiled.getTowerStructure().getSilhouette());
        assertEquals(1, compiled.getTowerStructure().getArms().size());
        assertMaterial("minecraft:iron_block", compiled.getTowerStructure().getPrimaryMaterial());
        assertMaterial("minecraft:iron_bars", compiled.getTowerStructure().getBraceMaterial());
        assertMaterial("minecraft:sea_lantern", compiled.getTowerStructure().getArms().getFirst().getMaterial());
        assertEquals(34.0, compiled.getTowerStructure().maxHeight(), 0.5);
    }

    @Test
    void catalogMapsDecorativePoleDesignsToProfiles() {
        assertEquals(
            TowerParameterProfiles.STEAMPUNK_ID,
            PowerLineStyleParametricCatalog.forPoleDesignId(PoleDesignCatalog.STEAMPUNK_BRASS_TOWER_ID).profileId());
        assertEquals(
            TowerParameterProfiles.MODERN_HV_GLASS_ID,
            PowerLineStyleParametricCatalog.forPoleDesignId(PoleDesignCatalog.MODERN_HV_GLASS_TOWER_ID).profileId());
    }

    @Test
    void editorEnableSteampunkProducesConfig() {
        PoleDesign design = new PoleDesign("steampunk", "Steampunk");
        TowerParametricEditor.enableParametricSteampunk(design, TowerParameterSet.steampunkDefaults());

        assertTrue(design.isParametricMode());
        assertEquals(TowerParameterProfiles.STEAMPUNK_ID, design.getGeneratorConfig().profileId());
        assertNotNull(design.getTowerStructure());
        assertTrue(design.getTowerStructure().maxHeight() > 20.0);
    }

    @Test
    void upperSteampunkArmHasWiderReachThanLowerArm() {
        PoleDesign compiled = TowerParametricDesignFactory.compileSteampunk(TowerParameterSet.steampunkDefaults());
        TowerArm lower = compiled.getTowerStructure().getArms().stream()
            .filter(arm -> "arm_brass".equals(arm.getId()))
            .findFirst()
            .orElseThrow();
        TowerArm upper = compiled.getTowerStructure().getArms().stream()
            .filter(arm -> "arm_rod".equals(arm.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(upper.getLateralReach() >= lower.getLateralReach());
    }

    private static void assertMaterial(String expected, MaterialMix actual) {
        assertNotNull(actual);
        assertEquals(expected, actual.getPrimaryMaterial());
    }
}
