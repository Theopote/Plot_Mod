package com.plot.plugin.earthwork;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkGeneratorTest {

    @Test
    void skipsNoOpAirToAirChanges() {
        assertFalse(EarthworkGenerator.shouldApplyBlockChange("minecraft:air", "minecraft:air"));
    }

    @Test
    void skipsWhenTargetMatchesCurrentBlock() {
        assertFalse(EarthworkGenerator.shouldApplyBlockChange("minecraft:dirt", "minecraft:dirt"));
    }

    @Test
    void appliesCutWhenReplacingSolidWithAir() {
        assertTrue(EarthworkGenerator.shouldApplyBlockChange("minecraft:grass_block", "minecraft:air"));
    }

    @Test
    void appliesFillWhenReplacingAirWithMaterial() {
        assertTrue(EarthworkGenerator.shouldApplyBlockChange("minecraft:air", "minecraft:dirt"));
    }

    @Test
    void normalizesBlockIdsBeforeComparison() {
        assertFalse(EarthworkGenerator.shouldApplyBlockChange("Minecraft:Air", "minecraft:air"));
    }
}
