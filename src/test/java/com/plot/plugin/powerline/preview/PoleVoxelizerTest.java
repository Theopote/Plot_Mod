package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleVoxelizerTest {

    @Test
    void simpleWoodPoleProducesVoxels() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(PoleDesignCatalog.simpleWoodPole());
        assertFalse(model.isEmpty());
        assertTrue(model.heightY() >= 3);
        assertTrue(model.widthX() >= 1);
    }

    @Test
    void japaneseStreetPoleHasCrossarmWidth() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(PoleDesignCatalog.japaneseStreetPole());
        assertFalse(model.isEmpty());
        assertTrue(model.widthZ() >= 3);
    }

    @Test
    void monsterPylonTowerStructureProducesVoxels() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(TowerFamilyDesignPresets.monsterPylonSuspension());
        assertFalse(model.isEmpty());
        assertTrue(model.heightY() >= 40);
        assertTrue(model.widthX() >= 4);
    }
}
