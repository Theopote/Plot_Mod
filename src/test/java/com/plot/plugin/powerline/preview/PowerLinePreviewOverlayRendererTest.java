package com.plot.plugin.powerline.preview;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePreviewOverlayRendererTest {

    @Test
    void proportionalInsulatorLengthTracksBlockCount() {
        float shortLen = PowerLinePreviewOverlayRenderer.resolveInsulatorScreenLength(
            2,
            6f,
            PowerLinePreviewOverlayRenderer.PROPORTIONAL_INSULATOR_MAX_PX);
        float longLen = PowerLinePreviewOverlayRenderer.resolveInsulatorScreenLength(
            8,
            6f,
            PowerLinePreviewOverlayRenderer.PROPORTIONAL_INSULATOR_MAX_PX);
        assertTrue(longLen > shortLen);
        assertEquals(12f, shortLen, 0.01f);
        assertEquals(48f, longLen, 0.01f);
    }

    @Test
    void compactInsulatorLengthCapsForStyleCards() {
        float capped = PowerLinePreviewOverlayRenderer.resolveInsulatorScreenLength(
            8,
            6f,
            PowerLinePreviewOverlayRenderer.COMPACT_INSULATOR_MAX_PX);
        assertEquals(PowerLinePreviewOverlayRenderer.COMPACT_INSULATOR_MAX_PX, capped, 0.01f);
    }

    @Test
    void resolvesWireColorsFromMaterialMix() {
        int copper = BlockPreviewColors.previewColor(MaterialMix.single("minecraft:copper_block"), 0xFF90A4AE);
        int gold = BlockPreviewColors.previewColor(MaterialMix.single("minecraft:gold_block"), 0xFFECEFF1);
        assertEquals(0xFFE08A5A, copper);
        assertEquals(0xFFFFD54F, gold);
    }

    @Test
    void windHubSitsOnCrossarmNotCardTop() {
        PoleDesign design = PoleDesignCatalog.wastelandWindTurbine();
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(design);
        double hubY = PowerLinePreviewOverlayRenderer.windHubVoxelY(design, model);
        // 12 + 4 column layers, then 1-block crossarm at y=16
        assertEquals(16.0, hubY, 0.01);
        assertTrue(hubY > model.maxY() * 0.6, "hub should be near the top of the mast");
    }

    @Test
    void windHubScreenYTracksCrossarmNotCardFraction() {
        PoleDesign design = PoleDesignCatalog.wastelandWindTurbine();
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(design);
        float x0 = 0f;
        float y0 = 0f;
        float x1 = 200f;
        float y1 = 200f;
        PoleVoxelElevationRenderer.ElevationLayout layout = PoleVoxelElevationRenderer.computeLayout(
            model,
            PoleVoxelElevationRenderer.ElevationView.FRONT,
            x0,
            y0,
            x1,
            y1);
        double hubY = PowerLinePreviewOverlayRenderer.windHubVoxelY(design, model);
        float screenHub = PoleVoxelElevationRenderer.mapVerticalToScreen(layout, model, hubY);
        float screenCrossarm = PoleVoxelElevationRenderer.mapVerticalToScreen(layout, model, 16);
        float cardFraction = y0 + (y1 - y0) * 0.28f;
        assertEquals(screenCrossarm, screenHub, 0.01f);
        assertTrue(
            Math.abs(screenHub - cardFraction) > layout.blockSize(),
            "hub must not stay at the old 28% card fraction");
    }
}
