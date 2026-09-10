package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.structure.TowerStructurePresets;
import com.plot.plugin.powerline.preview.PoleVoxelElevationRenderer;
import com.plot.plugin.powerline.preview.PoleVoxelPreviewModel;
import com.plot.plugin.powerline.preview.PoleVoxelizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleDesignPreviewRendererTest {

    @Test
    void overlayMappingStaysInsideDesignerPane() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(PoleDesignCatalog.simpleWoodPole());
        float x0 = 0f;
        float y0 = 20f;
        float x1 = 200f;
        float y1 = 220f;
        PoleVoxelElevationRenderer.ElevationLayout layout = PoleVoxelElevationRenderer.computeLayout(
            model,
            PoleVoxelElevationRenderer.ElevationView.FRONT,
            x0,
            y0,
            x1,
            y1);
        assertNotNull(layout);

        float markerX = PoleVoxelElevationRenderer.mapHorizontalToScreen(
            layout,
            PoleVoxelElevationRenderer.ElevationView.FRONT,
            model,
            0);
        float markerY = PoleVoxelElevationRenderer.mapVerticalToScreen(layout, model, 2);
        assertTrue(markerX >= x0 && markerX <= x1);
        assertTrue(markerY >= y0 && markerY <= y1);
    }

    @Test
    void towerDesignProducesVoxelLayoutForDesignerPane() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(
            TowerStructurePresets.taperedLatticePoleDesign("tower", "Tower"));
        PoleVoxelElevationRenderer.ElevationLayout layout = PoleVoxelElevationRenderer.computeLayout(
            model,
            PoleVoxelElevationRenderer.ElevationView.SIDE,
            0f,
            20f,
            180f,
            220f);
        assertNotNull(layout);
        assertTrue(layout.blockSize() > 0f);
    }
}
