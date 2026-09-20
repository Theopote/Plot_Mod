package com.plot.plugin.powerline.preview;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.EffectiveStylePreview;
import com.plot.plugin.powerline.style.EffectiveStylePreviewResolver;
import com.plot.plugin.powerline.style.ParametricStyleTowerApplicator;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.plot.plugin.powerline.preview.PresetPreviewTestSupport.assertSignaturesClose;
import static com.plot.plugin.powerline.preview.PresetPreviewTestSupport.signature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 分档格构族默认落地应与画廊代表塔参数/体素一致。 */
class GradedLatticePreviewGenerationParityTest {

    @Test
    void smartTowersDefaultGenerationMatchesGalleryRepresentative() {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.smartTowers();
        PoleDesign gallery = PowerLineStylePreviewBinding.previewDesign(preset);
        assertNotNull(gallery);

        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(80, 0)));
        line.setMaxPoleSpacing(100.0);
        preset.apply(line);

        PoleDesign assigned = TowerFamilyDesignPresets.latticeSuspensionTall();
        PoleDesign generated = ParametricStyleTowerApplicator.apply(
            assigned,
            line.getParametricTowerConfig(),
            null,
            line);
        assertNotNull(generated);

        TowerGeneratorConfig galleryConfig = gallery.getGeneratorConfig();
        TowerGeneratorConfig generatedConfig = generated.getGeneratorConfig();
        assertNotNull(galleryConfig);
        assertNotNull(generatedConfig);
        assertEquals(galleryConfig.profileId(), generatedConfig.profileId());
        assertParametersEqual(galleryConfig.parameters(), generatedConfig.parameters());

        assertSignaturesClose(
            "smart_towers default",
            signature(gallery),
            signature(generated));

        EffectiveStylePreview effective = EffectiveStylePreviewResolver.resolve(
            line,
            preset,
            new PoleDesignResolver(new PowerLineDesignProject()));
        assertNotNull(effective);
        assertSignaturesClose(
            "smart_towers effective",
            signature(gallery),
            signature(effective.previewDesign()));

        assertEquals(
            PoleVoxelizer.voxelize(gallery, "parity").voxels().size(),
            PoleVoxelizer.voxelize(generated, "parity").voxels().size());
    }

    private static void assertParametersEqual(TowerParameterSet expected, TowerParameterSet actual) {
        assertEquals(expected.height(), actual.height(), 0.05, "height");
        assertEquals(expected.baseWidth(), actual.baseWidth(), 0.05, "baseWidth");
        assertEquals(expected.armSpan(), actual.armSpan(), 0.05, "armSpan");
        assertEquals(expected.depthScale(), actual.depthScale(), 0.05, "depthScale");
        assertEquals(expected.waistRatio(), actual.waistRatio(), 0.05, "waistRatio");
        assertEquals(expected.density(), actual.density(), "density");
    }
}
