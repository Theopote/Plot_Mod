package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleVoxelElevationRendererTest {

    private static final float CARD_PREVIEW_WIDTH = 116f;
    private static final float CARD_PREVIEW_HEIGHT = 92f;

    @Test
    void simpleWoodPoleUsesDetailedLodInStyleCardSize() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(PoleDesignCatalog.simpleWoodPole());
        PoleVoxelElevationRenderer.ElevationLayout layout = PoleVoxelElevationRenderer.computeLayout(
            model,
            PoleVoxelElevationRenderer.ElevationView.FRONT,
            0f,
            0f,
            CARD_PREVIEW_WIDTH,
            CARD_PREVIEW_HEIGHT);

        assertNotNull(layout);
        assertEquals(PoleVoxelElevationRenderer.PreviewLod.DETAILED, layout.lod());
        assertEquals(1, layout.groupSize());
        assertTrue(layout.blockSize() >= 2f);
    }

    @Test
    void monsterPylonUsesFilledLodInsteadOfFailing() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(TowerFamilyDesignPresets.monsterPylonSuspension());
        PoleVoxelElevationRenderer.ElevationLayout layout = PoleVoxelElevationRenderer.computeLayout(
            model,
            PoleVoxelElevationRenderer.ElevationView.FRONT,
            0f,
            0f,
            CARD_PREVIEW_WIDTH,
            CARD_PREVIEW_HEIGHT);

        assertNotNull(layout);
        assertEquals(PoleVoxelElevationRenderer.PreviewLod.FILLED, layout.lod());
        assertEquals(1, layout.groupSize());
        assertTrue(layout.blockSize() >= 0.8f);
        assertTrue(layout.blockSize() < 2f);
    }

    @Test
    void extremelyTallTowerDownsamplesOccupancy() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(TowerFamilyDesignPresets.monsterPylonSuspension());
        PoleVoxelElevationRenderer.ElevationLayout layout = PoleVoxelElevationRenderer.computeLayout(
            model,
            PoleVoxelElevationRenderer.ElevationView.FRONT,
            0f,
            0f,
            48f,
            32f);

        assertNotNull(layout);
        assertTrue(layout.groupSize() > 1);
        assertEquals(PoleVoxelElevationRenderer.PreviewLod.DOWNSAMPLED, layout.lod());
        assertTrue(layout.blockSize() >= 0.8f);
    }
}
