package com.plot.plugin.powerline.preview;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.EffectivePoleDesignResolver;
import com.plot.plugin.powerline.style.EffectiveStylePreviewResolver;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import com.plot.plugin.powerline.style.TowerMaterialApplyMode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.plot.plugin.powerline.preview.PresetPreviewTestSupport.assertSignaturesClose;
import static com.plot.plugin.powerline.preview.PresetPreviewTestSupport.signature;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void galleryVoxelsMatchBuiltTowerEvenWithSyncAllMaterialMode() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.steampunkBrass();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        preset.apply(line);
        line.setTowerMaterialApplyMode(TowerMaterialApplyMode.SYNC_ALL);

        PoleDesign gallery = PowerLineStylePreviewBinding.previewDesign(preset);
        PoleDesignResolver resolver = new PoleDesignResolver(new PowerLineDesignProject());
        PoleDesign catalog = resolver.find(PoleDesignCatalog.STEAMPUNK_BRASS_TOWER_ID);
        PoleDesign built = EffectivePoleDesignResolver.applyLineOverrides(
            ParametricStyleTowerApplicator.apply(
                catalog,
                line.getParametricTowerConfig(),
                null,
                line),
            line);

        assertEquals(voxelShape(gallery), voxelShape(built), "gallery and built steampunk voxels should match");
    }

    private static Map<String, Long> voxelShape(PoleDesign design) {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(design, "steampunk_parity");
        Map<String, Long> counts = new HashMap<>();
        for (PreviewVoxel voxel : model.voxels()) {
            counts.merge(voxel.blockId(), 1L, Long::sum);
        }
        return counts;
    }
}
