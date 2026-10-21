package com.plot.plugin.earthwork.voxel;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkPlacementMergeTest {

    @Test
    void structureOverridesFillAtSamePosition() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        BlockPos pos = new BlockPos(3, 64, 7);

        assertTrue(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:air", "minecraft:dirt",
            EarthworkGenerationResult.ChangeType.FILL));
        assertTrue(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:air", "minecraft:stone_bricks",
            EarthworkGenerationResult.ChangeType.STRUCTURE));

        assertEquals(1, result.placementRecords.size());
        assertEquals(EarthworkGenerationResult.ChangeType.STRUCTURE, result.changeTypes.get(pos));
        BlockRecord record = result.placementRecords.get(pos);
        assertEquals("minecraft:air", record.previousBlockId);
        assertEquals("minecraft:stone_bricks", record.newBlockId);
        assertEquals(result.placementRecords.keySet(), result.changeTypes.keySet());
    }

    @Test
    void fillDoesNotOverrideStructure() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        BlockPos pos = new BlockPos(1, 70, 1);

        assertTrue(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:grass_block", "minecraft:stone_bricks",
            EarthworkGenerationResult.ChangeType.STRUCTURE));
        assertFalse(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:grass_block", "minecraft:dirt",
            EarthworkGenerationResult.ChangeType.FILL));

        assertEquals(EarthworkGenerationResult.ChangeType.STRUCTURE, result.changeTypes.get(pos));
        assertEquals("minecraft:stone_bricks", result.placementRecords.get(pos).newBlockId);
    }

    @Test
    void equalPriorityKeepsFirstWriter() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        BlockPos pos = new BlockPos(0, 65, 0);

        assertTrue(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:stone", "minecraft:air",
            EarthworkGenerationResult.ChangeType.CUT));
        assertFalse(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:stone", "minecraft:dirt",
            EarthworkGenerationResult.ChangeType.FILL));

        assertEquals(EarthworkGenerationResult.ChangeType.CUT, result.changeTypes.get(pos));
        assertEquals("minecraft:air", result.placementRecords.get(pos).newBlockId);
    }

    @Test
    void noOpWhenTargetMatchesWorld() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        BlockPos pos = new BlockPos(2, 60, 2);

        assertFalse(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:dirt", "minecraft:dirt",
            EarthworkGenerationResult.ChangeType.FILL));
        assertTrue(result.placementRecords.isEmpty());
        assertTrue(result.changeTypes.isEmpty());
    }
}
