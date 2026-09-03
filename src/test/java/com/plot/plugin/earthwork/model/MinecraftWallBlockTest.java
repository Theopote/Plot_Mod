package com.plot.plugin.earthwork.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftWallBlockTest {

    @Test
    void defaultAndKnownIds() {
        assertEquals("minecraft:stone_bricks", MinecraftWallBlock.DEFAULT_BLOCK_ID);
        assertEquals(MinecraftWallBlock.COBBLESTONE, MinecraftWallBlock.fromBlockId("minecraft:cobblestone"));
        assertEquals(MinecraftWallBlock.DEEPSLATE_BRICKS, MinecraftWallBlock.fromBlockId("minecraft:deepslate_bricks"));
        assertEquals(MinecraftWallBlock.STONE_BRICKS, MinecraftWallBlock.fromBlockId("minecraft:oak_planks"));
        assertEquals(MinecraftWallBlock.STONE_BRICKS, MinecraftWallBlock.fromBlockId(null));
    }
}
