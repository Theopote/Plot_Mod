package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.voxel.EarthworkVoxelizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkVoxelizerTest {

    @Test
    void skipsNoOpAirToAirChanges() {
        assertFalse(EarthworkVoxelizer.shouldApplyBlockChange("minecraft:air", "minecraft:air"));
    }

    @Test
    void skipsWhenTargetMatchesCurrentBlock() {
        assertFalse(EarthworkVoxelizer.shouldApplyBlockChange("minecraft:dirt", "minecraft:dirt"));
    }

    @Test
    void appliesCutWhenReplacingSolidWithAir() {
        assertTrue(EarthworkVoxelizer.shouldApplyBlockChange("minecraft:grass_block", "minecraft:air"));
    }

    @Test
    void appliesFillWhenReplacingAirWithMaterial() {
        assertTrue(EarthworkVoxelizer.shouldApplyBlockChange("minecraft:air", "minecraft:dirt"));
    }

    @Test
    void normalizesBlockIdsBeforeComparison() {
        assertFalse(EarthworkVoxelizer.shouldApplyBlockChange("Minecraft:Air", "minecraft:air"));
    }
}
