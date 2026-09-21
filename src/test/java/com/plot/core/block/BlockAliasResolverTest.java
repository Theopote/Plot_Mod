package com.plot.core.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockAliasResolverTest {

    @Test
    void resolvesChainBlockSpecToRuntimeId() {
        BlockSpec input = BlockSpec.with("minecraft:chain", "axis", "x");
        BlockSpec resolved = BlockAliasResolver.resolve(input);
        assertEquals(BlockAliasResolver.resolveChainBlockId(), resolved.blockId());
        assertEquals("x", resolved.property("axis"));
    }

    @Test
    void resolvesSetBlockArgumentPreservingProperties() {
        String resolved = BlockAliasResolver.resolveSetBlockArgument("minecraft:chain[axis=x]");
        assertTrue(resolved.startsWith(BlockAliasResolver.resolveChainBlockId() + "[axis=x]"));
    }

    @Test
    void leavesUnrelatedBlocksUnchanged() {
        BlockSpec input = BlockSpec.with("minecraft:iron_bars", "north", "true");
        assertEquals(input, BlockAliasResolver.resolve(input));
    }
}
