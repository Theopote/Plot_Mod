package com.plot.plugin.pattern.image;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockColorMatcherTest {

    @Test
    void nearestBlockUsesCatalogColors() {
        BlockColorMatcher matcher = new BlockColorMatcher(List.of(
            "minecraft:quartz_block",
            "minecraft:blackstone",
            "minecraft:orange_terracotta"
        ));

        assertEquals("minecraft:quartz_block", matcher.nearestBlock(236, 239, 241));
        assertEquals("minecraft:blackstone", matcher.nearestBlock(20, 20, 20));
        assertEquals("minecraft:orange_terracotta", matcher.nearestBlock(164, 87, 41));
    }

    @Test
    void emptyPaletteFallsBackToStone() {
        BlockColorMatcher matcher = new BlockColorMatcher(List.of());
        assertEquals("minecraft:stone", matcher.nearestBlock(128, 128, 128));
        assertEquals(0, matcher.paletteSize());
    }
}
