package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import com.plot.plugin.powerline.style.PreviewOverlay;
import com.plot.plugin.powerline.style.StyleCardPreviewBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** PL-PRESET-S6：身份特征不应被 overlay 重复绘制。 */
class PreviewIdentityDuplicationTest {

    static Stream<PowerLineStylePreset> allPresets() {
        return PowerLineStylePresetCatalog.defaultPresets().stream();
    }

    @Test
    void wastelandWindDoesNotStackWindRotorOverlayOnVoxelRotor() {
        PowerLineStylePreset wind = PowerLineStylePresetCatalog.wastelandWind();
        assertEquals(PreviewOverlay.DECORATIVE_CONDUCTORS, PowerLineStylePreviewBinding.previewOverlay(wind));

        StyleCardPreviewBinding gallery = PowerLineStylePreviewBinding.cardPreviewBinding(wind);
        StyleCardPreviewBinding effective = PowerLineStylePreviewBinding.bindingForDesign(
            PowerLineStylePreviewBinding.previewDesign(wind),
            wind);
        assertNotEquals(PreviewOverlay.WIND_ROTOR, gallery.overlay());
        assertNotEquals(PreviewOverlay.WIND_ROTOR, effective.overlay());
    }

    @Test
    void wastelandWindPreviewVoxelIncludesFourWayRotorBlades() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(PoleDesignCatalog.wastelandWindTurbine());
        long bladeBlocks = model.voxels().stream()
            .filter(voxel -> "minecraft:orange_terracotta".equals(voxel.blockId()))
            .count();
        org.junit.jupiter.api.Assertions.assertTrue(
            bladeBlocks >= 16,
            "preview voxel model should include four-way rotor without overlay duplication");
    }

    @ParameterizedTest
    @MethodSource("allPresets")
    void noPresetGalleryUsesDeprecatedWindRotorOverlay(PowerLineStylePreset preset) {
        assertNotEquals(
            PreviewOverlay.WIND_ROTOR,
            PowerLineStylePreviewBinding.previewOverlay(preset),
            preset.getId() + " should not use duplicate WIND_ROTOR overlay");
    }
}
