package com.plot.core.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlockStateSanitizerTest {

    @Test
    void stripsInvalidIronBarsVerticalConnections() {
        BlockSpec sanitized = BlockStateSanitizer.sanitize(
            BlockSpec.parse("minecraft:iron_bars[north=true,up=true]"));
        assertEquals("minecraft:iron_bars", sanitized.blockId());
        assertEquals("true", sanitized.property("north"));
        assertNull(sanitized.property("up"));
    }

    @Test
    void verticalIronBarsHasNoProperties() {
        BlockSpec sanitized = BlockStateSanitizer.sanitize(
            BlockSpec.parse("minecraft:iron_bars[up=true]"));
        assertEquals("minecraft:iron_bars", sanitized.toSetBlockArgument());
        assertFalse(sanitized.hasProperties());
    }

    @Test
    void preservesHorizontalIronBarsConnectionsWithoutRegistry() {
        String argument = "minecraft:iron_bars[east=true,north=true]";
        assertEquals(argument, BlockStateSanitizer.sanitizeSetBlockArgument(argument));
    }

    @Test
    void unknownBlockPassesThroughWhenRegistryUnavailable() {
        String argument = "minecraft:plot_test_block[custom=value]";
        assertEquals(argument, BlockStateSanitizer.sanitizeSetBlockArgument(argument));
    }
}
