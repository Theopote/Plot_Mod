package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadMaterialUtilsTest {

    @Test
    void normalizeBlockIds() {
        assertEquals("minecraft:stone", RoadMaterialUtils.normalizeStoredMaterial("minecraft:stone"));
        assertEquals("minecraft:stone", RoadMaterialUtils.resolveBlockId("minecraft:stone"));
    }

    @Test
    void blankMaterialStaysNullForStorage() {
        assertNull(RoadMaterialUtils.normalizeStoredMaterial(null));
        assertNull(RoadMaterialUtils.normalizeStoredMaterial(""));
    }

    @Test
    void unknownMaterialFallsBackForGeneration() {
        assertEquals("minecraft:stone", RoadMaterialUtils.resolveBlockId("not_a_block_id"));
        assertNull(RoadMaterialUtils.normalizeStoredMaterial("material.plot.concrete"));
    }
}
