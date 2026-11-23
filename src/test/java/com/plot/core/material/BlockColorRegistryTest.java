package com.plot.core.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockColorRegistryTest {
    @Test
    void knownLampMaterialsKeepHandTunedColors() {
        assertEquals(0xFF48494E, BlockColorRegistry.colorFor("minecraft:polished_deepslate"));
        assertEquals(0xFF7A7A7A, BlockColorRegistry.colorFor("minecraft:stone_bricks"));
        assertEquals(0xFF6B5037, BlockColorRegistry.colorFor("minecraft:spruce_fence"));
        assertTrue(BlockColorRegistry.hasExplicitColor("minecraft:polished_deepslate_wall"));
    }

    @Test
    void unknownIdWithoutRegistryStillReturnsOpaqueColor() {
        int color = BlockColorRegistry.colorFor("plot:unit_test_unknown_block_xyz");
        assertNotEquals(0, color);
        assertEquals(0xFF, (color >>> 24) & 0xFF);
    }

    @Test
    void blankIdFallsBackToNeutralGray() {
        assertEquals(0xFF808080, BlockColorRegistry.colorFor(null));
        assertEquals(0xFF808080, BlockColorRegistry.colorFor("  "));
    }
}
