package com.plot.plugin.building.generation;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * BuildingBlockWriter 覆盖语义单元测试。
 */
class BuildingBlockWriterTest {

    @Test
    void laterWriteOverridesNewBlockIdButKeepsFirstPrevious() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BlockPos pos = new BlockPos(1, 70, 1);

        BuildingBlockWriter.recordBlockWithPrevious(
            result, pos, "minecraft:grass_block", "minecraft:stone_bricks");
        BuildingBlockWriter.recordBlockWithPrevious(
            result, pos, "minecraft:dirt", "minecraft:air");

        BlockRecord record = result.placementRecords.get(pos);
        assertNotNull(record);
        assertEquals("minecraft:grass_block", record.previousBlockId);
        assertEquals("minecraft:air", record.newBlockId);
        assertEquals(1, result.placementRecords.size());
    }

    @Test
    void airCanOverrideWall() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BlockPos pos = new BlockPos(0, 64, 0);

        BuildingBlockWriter.recordBlockWithPrevious(
            result, pos, "minecraft:stone", "minecraft:cobblestone");
        BuildingBlockWriter.recordBlockWithPrevious(
            result, pos, "ignored", "minecraft:air");

        assertEquals("minecraft:air", result.placementRecords.get(pos).newBlockId);
        assertEquals("minecraft:stone", result.placementRecords.get(pos).previousBlockId);
    }

    @Test
    void roofCanOverrideTopFloor() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BlockPos pos = new BlockPos(4, 80, 4);

        BuildingBlockWriter.recordBlockWithPrevious(
            result, pos, "minecraft:air", "minecraft:oak_planks");
        BuildingBlockWriter.recordBlockWithPrevious(
            result, pos, "minecraft:air", "minecraft:dark_oak_planks");

        assertEquals("minecraft:dark_oak_planks", result.placementRecords.get(pos).newBlockId);
        assertEquals("minecraft:air", result.placementRecords.get(pos).previousBlockId);
    }

    @Test
    void nullInputsAreIgnored() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BlockPos pos = new BlockPos(1, 1, 1);

        BuildingBlockWriter.recordBlockWithPrevious(null, pos, "a", "b");
        BuildingBlockWriter.recordBlockWithPrevious(result, null, "a", "b");
        BuildingBlockWriter.recordBlockWithPrevious(result, pos, "a", null);

        assertEquals(0, result.placementRecords.size());
    }

    @Test
    void nullProjectionHandlerFallsBackToAirPrevious() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BlockPos pos = new BlockPos(1, 1, 1);

        BuildingBlockWriter.recordBlock(result, pos, "minecraft:stone", null);

        assertEquals(1, result.placementRecords.size());
        assertEquals("minecraft:air", result.placementRecords.get(pos).previousBlockId);
        assertEquals("minecraft:stone", result.placementRecords.get(pos).newBlockId);
    }

    @Test
    void sameMapEntryIsReplacedNotDuplicated() {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BlockPos pos = new BlockPos(9, 9, 9);

        BuildingBlockWriter.recordBlockWithPrevious(result, pos, "prev", "first");
        BlockRecord first = result.placementRecords.get(pos);
        BuildingBlockWriter.recordBlockWithPrevious(result, pos, "other", "second");
        BlockRecord second = result.placementRecords.get(pos);

        assertEquals(1, result.placementRecords.size());
        assertSame(result.placementRecords.get(pos), second);
        assertEquals("prev", first.previousBlockId);
        assertEquals("prev", second.previousBlockId);
        assertEquals("second", second.newBlockId);
    }
}
