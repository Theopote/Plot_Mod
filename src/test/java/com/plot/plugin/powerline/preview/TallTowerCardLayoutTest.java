package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PowerLineStylePreviewBinding;
import com.plot.plugin.powerline.ui.PowerLineStyleCardRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** PL-PRESET-S6 P2：高塔画廊卡片应优先填满高度，避免宽臂塔缩成矮剪影。 */
class TallTowerCardLayoutTest {

    private static final float GALLERY_INNER_WIDTH = PowerLineStyleCardRenderer.CARD_WIDTH - 4f;
    /** {@link com.plot.plugin.powerline.ui.PowerLineStyleCardRenderer} 预览区上下各 2px 内边距。 */
    private static final float GALLERY_INNER_HEIGHT = 98f;

    @Test
    void wideTransmissionTowersFillCardHeightWithThumbnailFit() {
        float innerW = GALLERY_INNER_WIDTH;
        float innerH = GALLERY_INNER_HEIGHT;
        float minFill = StylePreviewLayout.cardThumbnailMinHeightFill();
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
            TowerStructuralElevationRenderer.StructuralLayout balanced =
                TowerStructuralElevationRenderer.computeLayout(
                    design,
                    TowerStructuralElevationRenderer.StructuralView.FRONT,
                    0f,
                    0f,
                    innerW,
                    innerH,
                    TowerStructuralElevationRenderer.LayoutFit.BALANCED);
            TowerStructuralElevationRenderer.StructuralLayout thumbnail =
                TowerStructuralElevationRenderer.computeLayout(
                    design,
                    TowerStructuralElevationRenderer.StructuralView.FRONT,
                    0f,
                    0f,
                    innerW,
                    innerH,
                    TowerStructuralElevationRenderer.LayoutFit.CARD_THUMBNAIL);
            assertTrue(balanced != null && thumbnail != null, preset.getId());
            double modelHeight = design.getTowerStructure().maxHeight();
            float availH = innerH - 8f; // CARD_PADDING * 2
            float balancedFill = (float) (modelHeight * balanced.scale()) / availH;
            float thumbnailFill = (float) (modelHeight * thumbnail.scale()) / availH;
            assertTrue(
                thumbnailFill >= minFill,
                preset.getId() + " thumbnail fill " + thumbnailFill + " < " + minFill);
            assertTrue(
                thumbnail.scale() >= balanced.scale(),
                preset.getId() + " thumbnail should not shrink below balanced scale");
        }
    }

    @Test
    void galleryBoundsSelectThumbnailFit() {
        float innerW = GALLERY_INNER_WIDTH;
        float innerH = GALLERY_INNER_HEIGHT;
        assertTrue(
            StylePreviewLayout.fitForBounds(0f, 0f, innerW, innerH)
                == TowerStructuralElevationRenderer.LayoutFit.CARD_THUMBNAIL);
        assertTrue(
            StylePreviewLayout.fitForBounds(0f, 0f, 280f, 140f)
                == TowerStructuralElevationRenderer.LayoutFit.BALANCED);
    }
}
