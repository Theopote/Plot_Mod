package com.plot.infrastructure.event.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockProjectionHandlerTest {

    @Test
    void setBlockCommandUsesUnquotedBlockStateSyntax() {
        String command = String.format(
            "setblock %d %d %d %s",
            0, 64, 0,
            "minecraft:iron_chain[axis=x]");
        assertTrue(command.contains("minecraft:iron_chain[axis=x]"));
        assertTrue(!command.contains("\""));
    }
}
