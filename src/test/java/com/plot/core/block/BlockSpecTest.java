package com.plot.core.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockSpecTest {

    @Test
    void plainBlockRoundTrips() {
        BlockSpec spec = BlockSpec.of("minecraft:iron_block");
        assertEquals("minecraft:iron_block", spec.toSetBlockArgument());
        assertEquals(spec, BlockSpec.parse("minecraft:iron_block"));
    }

    @Test
    void parsesAndFormatsBlockState() {
        BlockSpec spec = BlockSpec.with("minecraft:lightning_rod", "facing", "east");
        assertEquals("minecraft:lightning_rod[facing=east]", spec.toSetBlockArgument());

        BlockSpec parsed = BlockSpec.parse("minecraft:lightning_rod[facing=east]");
        assertEquals("minecraft:lightning_rod", parsed.blockId());
        assertEquals("east", parsed.property("facing"));
        assertEquals(spec, parsed);
    }

    @Test
    void parsesChainAxis() {
        BlockSpec spec = BlockSpec.parse("minecraft:chain[axis=y]");
        assertEquals("minecraft:chain", spec.blockId());
        assertEquals("y", spec.property("axis"));
        assertTrue(spec.hasProperties());
    }

    @Test
    void blockRecordStoresFullArgument() {
        var record = new com.plot.core.command.BlockRecord(
            net.minecraft.util.math.BlockPos.ORIGIN,
            "minecraft:air",
            BlockSpec.with("minecraft:chain", "axis", "z"));
        assertEquals("minecraft:chain[axis=z]", record.newBlockId);
        assertEquals("z", record.newBlockSpec().property("axis"));
    }

    @Test
    void withPropertyBuildsMultiPropertySpec() {
        BlockSpec spec = BlockSpec.with("minecraft:iron_trapdoor", "facing", "north")
            .withProperty("half", "bottom")
            .withProperty("open", "false");
        assertEquals("north", spec.property("facing"));
        assertEquals("bottom", spec.property("half"));
        assertEquals(
            "minecraft:iron_trapdoor[facing=north,half=bottom,open=false]",
            spec.toSetBlockArgument());
        assertEquals(spec, BlockSpec.parse(spec.toSetBlockArgument()));
    }

    @Test
    void parseWithoutPropertiesHasEmptyMap() {
        BlockSpec spec = BlockSpec.parse("minecraft:oak_fence");
        assertFalse(spec.hasProperties());
        assertEquals("minecraft:oak_fence", spec.toSetBlockArgument());
    }
}
