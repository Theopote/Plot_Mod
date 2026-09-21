package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TowerMaterialOverrideSupportTest {

    @Test
    void legsOnlyAppliesPoleMaterialToPrimaryOnly() {
        PowerLineFootprint line = smartTowersLine();
        line.setTowerMaterialApplyMode(TowerMaterialApplyMode.LEGS_ONLY);
        line.setPoleMaterial(MaterialMix.single("minecraft:copper_block"));

        PoleDesign effective = applySmartTowers(line);

        assertEquals(
            "minecraft:copper_block",
            effective.getTowerStructure().getPrimaryMaterial().getPrimaryMaterial());
        assertEquals(
            "minecraft:iron_bars",
            effective.getTowerStructure().getBraceMaterial().getPrimaryMaterial());
    }

    @Test
    void syncAllAppliesPoleMaterialAcrossTowerMembers() {
        PowerLineFootprint line = smartTowersLine();
        line.setTowerMaterialApplyMode(TowerMaterialApplyMode.SYNC_ALL);
        line.setPoleMaterial(MaterialMix.single("minecraft:copper_block"));

        PoleDesign effective = applySmartTowers(line);

        assertEquals(
            "minecraft:copper_block",
            effective.getTowerStructure().getPrimaryMaterial().getPrimaryMaterial());
        assertEquals(
            "minecraft:copper_block",
            effective.getTowerStructure().getBraceMaterial().getPrimaryMaterial());
        assertNotNull(effective.getTowerStructure().getArms().getFirst().getMaterial());
        assertEquals(
            "minecraft:copper_block",
            effective.getTowerStructure().getArms().getFirst().getMaterial().getPrimaryMaterial());
        assertEquals(
            "minecraft:copper_block",
            effective.getTowerStructure().getArms().getFirst().getBraceMaterial().getPrimaryMaterial());
    }

    @Test
    void componentOverridesApplyWhenNotSyncingAll() {
        PowerLineFootprint line = smartTowersLine();
        line.setTowerMaterialApplyMode(TowerMaterialApplyMode.LEGS_ONLY);
        line.setBraceMaterialOverride(MaterialMix.single("minecraft:gold_block"));
        line.setArmChordMaterialOverride(MaterialMix.single("minecraft:sea_lantern"));
        line.setArmBraceMaterialOverride(MaterialMix.single("minecraft:chain"));

        PoleDesign effective = applySmartTowers(line);

        assertEquals(
            "minecraft:gold_block",
            effective.getTowerStructure().getBraceMaterial().getPrimaryMaterial());
        assertEquals(
            "minecraft:sea_lantern",
            effective.getTowerStructure().getArms().getFirst().getMaterial().getPrimaryMaterial());
        assertEquals(
            "minecraft:chain",
            effective.getTowerStructure().getArms().getFirst().getBraceMaterial().getPrimaryMaterial());
    }

    @Test
    void syncAllPreservesSteampunkComponentMaterials() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new com.plot.api.geometry.Vec2d(0, 0), new com.plot.api.geometry.Vec2d(80, 0)));
        PowerLineStylePresetCatalog.steampunkBrass().apply(line);
        line.setTowerMaterialApplyMode(TowerMaterialApplyMode.SYNC_ALL);

        PoleDesign catalog = PoleDesignCatalog.steampunkBrassTower();
        PoleDesign applied = ParametricStyleTowerApplicator.apply(
            catalog,
            line.getParametricTowerConfig(),
            null,
            line);
        PoleDesign effective = EffectivePoleDesignResolver.applyLineOverrides(applied, line);

        assertEquals(
            "minecraft:copper_block",
            effective.getTowerStructure().getPrimaryMaterial().getPrimaryMaterial());
        assertEquals(
            "minecraft:chain",
            effective.getTowerStructure().getBraceMaterial().getPrimaryMaterial());
        TowerArm upperRod = effective.getTowerStructure().getArms().stream()
            .filter(arm -> "arm_rod".equals(arm.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals("minecraft:lightning_rod", upperRod.getMaterial().getPrimaryMaterial());
        assertEquals(
            "minecraft:gold_block",
            effective.getTowerStructure().getArms().stream()
                .filter(arm -> "arm_brass".equals(arm.getId()))
                .findFirst()
                .orElseThrow()
                .getMaterial()
                .getPrimaryMaterial());
    }

    @Test
    void compiledArmsUseSeparateChordAndBraceMaterials() {
        PoleDesign compiled = PowerLineStyleParametricCatalog.compileRepresentative(
            TowerGeneratorConfig.parametricSteampunk(
                com.plot.plugin.powerline.design.parametric.TowerParameterSet.steampunkDefaults()));
        assertNotNull(compiled);
        TowerArm firstArm = compiled.getTowerStructure().getArms().getFirst();
        assertEquals("minecraft:gold_block", firstArm.getMaterial().getPrimaryMaterial());
        assertEquals("minecraft:chain", firstArm.getBraceMaterial().getPrimaryMaterial());
    }

    private static PowerLineFootprint smartTowersLine() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        line.setMaxPoleSpacing(100.0);
        PowerLineStylePresetCatalog.smartTowers().apply(line);
        return line;
    }

    private static PoleDesign applySmartTowers(PowerLineFootprint line) {
        PoleDesign assigned = TowerFamilyDesignPresets.latticeSuspensionTall();
        PoleDesign applied = ParametricStyleTowerApplicator.apply(
            assigned,
            line.getParametricTowerConfig(),
            null,
            line);
        return EffectivePoleDesignResolver.applyLineOverrides(applied, line);
    }
}
