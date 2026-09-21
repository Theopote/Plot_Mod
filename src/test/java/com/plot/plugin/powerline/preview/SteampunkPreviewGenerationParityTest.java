package com.plot.plugin.powerline.preview;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.EffectiveStylePreviewResolver;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.plot.plugin.powerline.preview.PresetPreviewTestSupport.assertSignaturesClose;
import static com.plot.plugin.powerline.preview.PresetPreviewTestSupport.signature;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteampunkPreviewGenerationParityTest {

    @Test
    void catalogDesignMatchesGalleryPreview() {
        PoleDesign catalog = PoleDesignCatalog.steampunkBrassTower();
        PoleDesign gallery = com.plot.plugin.powerline.style.PowerLineStylePreviewBinding
            .previewDesign(PowerLineStylePresetCatalog.steampunkBrass());

        assertTrue(catalog.hasTowerStructure());
        assertTrue(gallery.hasTowerStructure());
        assertSignaturesClose("steampunk catalog vs gallery", signature(catalog), signature(gallery));
    }

    @Test
    void generatorApplicatorMatchesEffectivePreview() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.steampunkBrass();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        preset.apply(line);

        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());
        PoleDesign effective = EffectiveStylePreviewResolver.resolve(line, preset, resolver).previewDesign();
        PoleDesign catalog = resolver.find(PoleDesignCatalog.STEAMPUNK_BRASS_TOWER_ID);
        PoleDesign applied = ParametricStyleTowerApplicator.apply(
            catalog,
            line.getParametricTowerConfig(),
            null,
            line);

        assertNotNull(effective);
        assertNotNull(applied);
        assertSignaturesClose("steampunk effective vs generator", signature(applied), signature(effective));
    }
}
