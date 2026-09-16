package com.plot.plugin.pattern;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternGenerationResultTest {

    @Test
    void mergeFromUsesFirstClaimForOverlappingPlacements() {
        PatternGenerationResult first = new PatternGenerationResult();
        first.placementRecords.put(
            new BlockPos(1, 64, 2),
            new BlockRecord(new BlockPos(1, 64, 2), "minecraft:grass_block", "minecraft:white_wool"));

        PatternGenerationResult second = new PatternGenerationResult();
        second.placementRecords.put(
            new BlockPos(1, 64, 2),
            new BlockRecord(new BlockPos(1, 64, 2), "minecraft:grass_block", "minecraft:black_wool"));
        second.placementRecords.put(
            new BlockPos(3, 64, 4),
            new BlockRecord(new BlockPos(3, 64, 4), "minecraft:grass_block", "minecraft:stone"));

        first.mergeFrom(second);

        assertEquals("minecraft:white_wool", first.placementRecords.get(new BlockPos(1, 64, 2)).newBlockId);
        assertEquals("minecraft:stone", first.placementRecords.get(new BlockPos(3, 64, 4)).newBlockId);
        assertEquals(1, first.getSkippedOverlapCount());
        assertEquals(2, first.getBlockCount());
    }

    @Test
    void exceedsFallbackBuildThresholdWhenTooManyFallbackCells() {
        PatternGenerationResult result = new PatternGenerationResult();
        result.placementRecords.put(
            new BlockPos(0, 64, 0),
            new BlockRecord(new BlockPos(0, 64, 0), "minecraft:grass_block", "minecraft:stone"));
        result.setFallbackElevationCount(10);
        assertTrue(result.exceedsFallbackBuildThreshold());
    }
}
