package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import com.plot.plugin.powerline.ui.PowerLineStyleCardRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PL-PRESET-S6 P2：高塔画廊卡片在体素预览路径下仍应产生有效布局。 */
class TallTowerCardLayoutTest {

    private static final float GALLERY_INNER_WIDTH = PowerLineStyleCardRenderer.CARD_WIDTH - 4f;
    private static final float GALLERY_INNER_HEIGHT = 98f;

    @Test
    void wideTransmissionTowersProduceVoxelLayoutInGalleryBounds() {
        for (PowerLineStylePreset preset : List.of(
            PowerLineStylePresetCatalog.monsterPylon(),
            PowerLineStylePresetCatalog.megaLattice(),
            PowerLineStylePresetCatalog.industrialPortal(),
            PowerLineStylePresetCatalog.heavyDoubleCircuit(),
            PowerLineStylePresetCatalog.tripleArmTower(),
            PowerLineStylePresetCatalog.classicLattice())) {
            PoleDesign design = PowerLineStylePreviewBinding.previewDesign(preset);
            if (design == null || !design.hasTowerStructure()) {
                continue;
            }
            PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(design);
            PoleVoxelElevationRenderer.ElevationLayout layout = PoleVoxelElevationRenderer.computeLayout(
                model,
                PoleVoxelElevationRenderer.ElevationView.FRONT,
                0f,
                0f,
                GALLERY_INNER_WIDTH,
                GALLERY_INNER_HEIGHT);
            assertNotNull(layout, preset.getId());
            assertTrue(layout.blockSize() > 0f, preset.getId());
            assertTrue(layout.effectiveRows() > 0, preset.getId());
            float drawH = layout.blockSize() * layout.effectiveRows();
            assertTrue(drawH > GALLERY_INNER_HEIGHT * 0.25f, preset.getId() + " draw height too small");
        }
    }
}
