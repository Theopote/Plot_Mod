package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectivePoleDesignResolverTest {

    @Test
    void applyLineOverridesReplacesColumnAndTowerPrimaryMaterial() {
        PoleDesign source = PoleDesignCatalog.simpleWoodPole();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        line.setPoleMaterial(MaterialMix.single("minecraft:dark_oak_log"));

        PoleDesign effective = EffectivePoleDesignResolver.applyLineOverrides(source, line);

        assertTrue(effective.getLayers().stream()
            .filter(layer -> layer.getShape() == PoleLayer.Shape.COLUMN)
            .allMatch(layer -> "minecraft:dark_oak_log".equals(layer.getMaterial().getPrimaryMaterial())));
    }

    @Test
    void modernHvGlassStyleApplicatorAdoptsCompiledAttachmentsWithInsulators() {
        PoleDesign legacy = PoleDesignCatalog.modernHvGlassTower();
        assertTrue(legacy.getAttachments().stream().allMatch(attachment -> attachment.getInsulatorLength() <= 0));

        TowerGeneratorConfig config = PowerLineStylePresetCatalog.modernHvGlass()
            .getDefinition()
            .getParametricConfig();
        PoleDesign applied = ParametricStyleTowerApplicator.apply(legacy, config);

        assertTrue(
            applied.getAttachments().stream().anyMatch(attachment -> attachment.getInsulatorLength() > 0),
            "first parametric apply should adopt profile attachments with insulator length");
    }

    @Test
    void compactLatticeGalleryUsesParametricRepresentative() {
        PoleDesign gallery = PowerLineStylePreviewBinding.previewDesign(
            PowerLineStylePresetCatalog.compactLattice());
        PoleDesign legacy = PoleDesignCatalog.latticeSteelTower();

        assertTrue(gallery.hasTowerStructure(), "gallery should show parametric tower structure");
        assertFalse(legacy.hasTowerStructure(), "legacy catalog pole is layer-only");
    }
}
