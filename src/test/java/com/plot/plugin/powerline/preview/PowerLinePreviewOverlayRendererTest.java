package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLinePreviewOverlayRendererTest {

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
