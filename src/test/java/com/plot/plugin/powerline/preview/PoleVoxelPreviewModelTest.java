package com.plot.plugin.powerline.preview;

import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.family.TowerFamilyDesignPresets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoleVoxelPreviewModelTest {

    @Test
    void frontElevationUsesNearestVoxelNotFarFace() {
        PoleVoxelPreviewModel model = new PoleVoxelPreviewModel(List.of(
            new PreviewVoxel(0, 4, 0, "minecraft:oak_fence"),
            new PreviewVoxel(0, 4, 6, "minecraft:iron_block")));

        assertEquals("minecraft:oak_fence", model.blockAtFront(0, 4));
    }

    @Test
    void sideElevationUsesNearestVoxelNotFarFace() {
        PoleVoxelPreviewModel model = new PoleVoxelPreviewModel(List.of(
            new PreviewVoxel(0, 4, 0, "minecraft:oak_fence"),
            new PreviewVoxel(6, 4, 0, "minecraft:iron_block")));

        assertEquals("minecraft:oak_fence", model.blockAtSide(0, 4));
    }

    @Test
    void frontElevationShowsStructureBehindOuterFace() {
        PoleVoxelPreviewModel model = new PoleVoxelPreviewModel(List.of(
            new PreviewVoxel(0, 2, 0, "minecraft:oak_fence"),
            new PreviewVoxel(1, 2, 3, "minecraft:iron_block")));

        assertEquals("minecraft:oak_fence", model.blockAtFront(0, 2));
        assertEquals("minecraft:iron_block", model.blockAtFront(1, 2));
    }

    @Test
    void woodPoleFrontShowsShaftAndCrossarm() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(PoleDesignCatalog.simpleWoodPole());
        int shaftHits = 0;
        int armHits = 0;
        for (int y = model.minY(); y <= model.maxY(); y++) {
            int rowHits = 0;
            for (int x = model.minX(); x <= model.maxX(); x++) {
                if (model.blockAtFront(x, y) != null) {
                    rowHits++;
                }
            }
            if (rowHits >= 3) {
                armHits++;
            } else if (rowHits >= 1) {
                shaftHits++;
            }
        }
        assertTrue(shaftHits >= 6, "front view must show the pole shaft");
        assertTrue(armHits >= 1, "front view must show the crossarm span");
    }

    @Test
    void woodPoleSideShowsFullShaftNotOnlyArmTip() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(PoleDesignCatalog.simpleWoodPole());
        int rowsWithBlocks = 0;
        for (int y = model.minY(); y <= model.maxY(); y++) {
            boolean hit = false;
            for (int z = model.minZ(); z <= model.maxZ(); z++) {
                if (model.blockAtSide(z, y) != null) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                rowsWithBlocks++;
            }
        }
        assertEquals(model.heightY(), rowsWithBlocks, "side view must include every shaft row, not only the arm tip");
    }

    @Test
    void latticeFrontIncludesArmsBehindFrontLegs() {
        PoleVoxelPreviewModel model = PoleVoxelizer.voxelize(TowerFamilyDesignPresets.monsterPylonSuspension());
        int occupied = 0;
        int behindOuterFace = 0;
        for (int y = model.minY(); y <= model.maxY(); y++) {
            for (int x = model.minX(); x <= model.maxX(); x++) {
                if (model.blockAtFront(x, y) == null) {
                    continue;
                }
                occupied++;
                boolean onOuterFace = false;
                for (PreviewVoxel voxel : model.voxels()) {
                    if (voxel.x() == x && voxel.y() == y && voxel.z() == model.minZ()) {
                        onOuterFace = true;
                        break;
                    }
                }
                if (!onOuterFace) {
                    behindOuterFace++;
                }
            }
        }
        assertTrue(occupied > 0);
        assertTrue(behindOuterFace > 0, "front elevation must include arms/bracing not on the front-leg plane");
    }
}
