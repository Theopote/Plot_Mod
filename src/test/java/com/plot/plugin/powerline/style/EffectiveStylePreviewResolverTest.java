package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.family.TowerFamily;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectiveStylePreviewResolverTest {

    @Test
    void usesUserDesignGeometryInsteadOfBasePresetDefault() {
        PowerLineStylePreset base = PowerLineStylePresetCatalog.classicWood();
        PoleDesign tall = new PoleDesign("user/tall_wood", "Tall Wood");
        tall.setLayers(PoleDesignCatalog.simpleWoodPole().getLayers());
        tall.setAttachments(PoleDesignCatalog.simpleWoodPole().getAttachments());
        PowerLineQuickTunePolicy.applyPoleHeightBand(
            tall,
            base,
            PowerLineQuickTunePolicy.PoleHeightBand.TALL);

        PowerLineDesignProject designs = new PowerLineDesignProject();
        designs.addDesign(tall);
        PoleDesignResolver resolver = new PoleDesignResolver(designs);

        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        base.apply(line);
        line.setPoleDesignId(tall.getId());

        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(line, base, resolver);

        assertNotNull(preview);
        assertEquals(tall.totalHeight(), preview.previewDesign().totalHeight());
        assertNotEquals(
            PoleDesignCatalog.simpleWoodPole().totalHeight(),
            preview.previewDesign().totalHeight());
    }

    @Test
    void appliesPoleMaterialOverrideToColumnLayers() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePreset base = PowerLineStylePresetCatalog.classicWood();
        base.apply(line);
        line.setPoleMaterial(MaterialMix.single("minecraft:dark_oak_log"));

        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(
            line,
            base,
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertNotNull(preview);
        boolean hasDarkOakColumn = preview.previewDesign().getLayers().stream()
            .anyMatch(layer -> layer.getShape() == PoleLayer.Shape.COLUMN
                && "minecraft:dark_oak_log".equals(layer.getMaterial().getPrimaryMaterial()));
        assertTrue(hasDarkOakColumn);
        assertEquals("minecraft:dark_oak_log", preview.poleMaterial().getPrimaryMaterial());
    }

    @Test
    void prefersTowerFamilyOverFallbackPoleDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        line.setTowerFamilyId(TowerFamily.STANDARD_LATTICE_3_PHASE_ID);
        line.setPoleDesignId(PoleDesignCatalog.SIMPLE_WOOD_POLE_ID);
        line.setParametricTowerConfig(null);

        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(
            line,
            PowerLineStylePresetCatalog.classicLattice(),
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertNotNull(preview);
        assertEquals(TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID, preview.previewDesign().getId());
        assertTrue(preview.previewDesign().hasTowerStructure());
    }

    @Test
    void preservesWireMaterialsInPreviewSnapshot() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePreset base = PowerLineStylePresetCatalog.classicWood();
        base.apply(line);
        line.setWireMaterial(MaterialMix.single("minecraft:copper_block"));
        line.setTopWireMaterial(MaterialMix.single("minecraft:gold_block"));

        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(
            line,
            base,
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertNotNull(preview);
        assertEquals("minecraft:copper_block", preview.wireMaterial().getPrimaryMaterial());
        assertEquals("minecraft:gold_block", preview.topWireMaterial().getPrimaryMaterial());
    }

    @Test
    void smartTowersPreviewUsesGradedSuspensionRepresentative() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        PowerLineStylePreset base = PowerLineStylePresetCatalog.smartTowers();
        base.apply(line);

        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(
            line,
            base,
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertNotNull(preview);
        assertEquals(TowerFamilyDesignPresets.LATTICE_SUSPENSION_SMALL_ID, preview.previewDesign().getId());
        double compiledClassicHeight = PowerLineStyleParametricCatalog.compileRepresentative(
            line.getParametricTowerConfig()).getTowerStructure().maxHeight();
        assertTrue(
            preview.previewDesign().getTowerStructure().maxHeight() < compiledClassicHeight - 1.0,
            "preview should show graded small representative, not line-level classic compile");
    }

    @Test
    void smartTowersPreviewReflectsParametricHeightTune() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        PowerLineStylePreset base = PowerLineStylePresetCatalog.smartTowers();
        base.apply(line);
        double baselineHeight = EffectiveStylePreviewResolver.resolve(
            line,
            base,
            new PoleDesignResolver(new PowerLineDesignProject())).previewDesign().getTowerStructure().maxHeight();

        PowerLineQuickTunePolicy.applyParametricPoleHeightBand(
            line,
            base,
            PowerLineQuickTunePolicy.PoleHeightBand.TALL);

        EffectiveStylePreview tuned = EffectiveStylePreviewResolver.resolve(
            line,
            base,
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertTrue(tuned.previewDesign().getTowerStructure().maxHeight() > baselineHeight + 1.0);
    }

    @Test
    void classicLatticePreviewUsesFamilySuspensionNotBlindParametricCompile() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineStylePreset base = PowerLineStylePresetCatalog.classicLattice();
        base.apply(line);

        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(
            line,
            base,
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertNotNull(preview);
        assertEquals(TowerFamilyDesignPresets.LATTICE_SUSPENSION_ID, preview.previewDesign().getId());
        TowerGeneratorConfig config = line.getParametricTowerConfig();
        assertNotNull(config);
        assertEquals(
            preview.previewDesign().getTowerStructure().maxHeight(),
            TowerFamilyDesignPresets.latticeSuspension().getTowerStructure().maxHeight(),
            0.01);
    }

    @Test
    void synthesizesLegacyPreviewWhenNoDesign() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        line.setPoleHeight(14.0);
        line.setPoleMaterial(MaterialMix.single("minecraft:spruce_log"));

        EffectiveStylePreview preview = EffectiveStylePreviewResolver.resolve(
            line,
            null,
            new PoleDesignResolver(new PowerLineDesignProject()));

        assertNotNull(preview);
        assertEquals(14, preview.previewDesign().totalHeight());
    }
}
