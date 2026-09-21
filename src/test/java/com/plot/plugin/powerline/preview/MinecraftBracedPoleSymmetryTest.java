package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import org.junit.jupiter.api.Test;

class MinecraftBracedPoleSymmetryTest {

    @Test
    void bracedWoodPolePreviewIsFrontMirrorSymmetric() {
        assertSymmetric(PoleDesignCatalog.minecraftBracedWoodPole());
    }

    @Test
    void doubleDeckBracedPolePreviewIsFrontMirrorSymmetric() {
        assertSymmetric(PoleDesignCatalog.minecraftDoubleDeckBracedPole());
    }

    private static void assertSymmetric(PoleDesign design) {
        PresetPreviewTestSupport.assertFrontMirrorSymmetric(PoleVoxelizer.voxelize(design));
    }
}
