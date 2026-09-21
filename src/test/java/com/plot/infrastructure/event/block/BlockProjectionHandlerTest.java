package com.plot.infrastructure.event.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockProjectionHandlerTest {

    @Test
    void quoteSetBlockArgumentWrapsBlockStateSyntax() {
        assertEquals(
            "\"minecraft:chain[axis=x]\"",
            BlockProjectionHandler.quoteSetBlockArgument("minecraft:chain[axis=x]"));
        assertEquals(
            "minecraft:iron_block",
            BlockProjectionHandler.quoteSetBlockArgument("minecraft:iron_block"));
    }
}
