package com.plot.plugin.powerline.style;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
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
