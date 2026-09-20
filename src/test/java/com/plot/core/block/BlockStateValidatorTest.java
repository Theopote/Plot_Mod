package com.plot.core.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateValidatorTest {

    @Test
    void acceptsValidIronBarsHorizontalConnections() {
        assertTrue(BlockStateValidator.isValid(
            BlockSpec.parse("minecraft:iron_bars[east=true,north=true]")));
    }

    @Test
    void rejectsIronBarsVerticalConnections() {
        assertFalse(BlockStateValidator.isValid(
            BlockSpec.parse("minecraft:iron_bars[up=true]")));
    }

    @Test
    void acceptsPlainIronBars() {
        assertTrue(BlockStateValidator.isValid(BlockSpec.parse("minecraft:iron_bars")));
    }
}
