package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import org.junit.jupiter.api.Test;

class MinecraftBracedPoleSymmetryTest {

    @Test
    void bracedWoodPolePreviewIsFrontMirrorSymmetric() {
        assertFullySymmetric(PoleDesignCatalog.minecraftBracedWoodPole());
    }

    @Test
    void doubleDeckBracedPolePreviewIsFrontMirrorSymmetric() {
        assertFullySymmetric(PoleDesignCatalog.minecraftDoubleDeckBracedPole());
    }

    @Test
    void utilityEquipmentPoleBracesAreFrontMirrorSymmetric() {
        PoleDesign design = PoleDesignCatalog.minecraftUtilityEquipmentPole();
        PresetPreviewTestSupport.assertStructuralFrontMirrorSymmetric(PoleVoxelizer.voxelize(design), design);
    }

    private static void assertFullySymmetric(PoleDesign design) {
        PresetPreviewTestSupport.assertFrontMirrorSymmetric(PoleVoxelizer.voxelize(design));
    }
}
