package com.plot.plugin.road;

import com.plot.core.command.commands.GenerateRoadCommand;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadGeneratorPlacementRecordTest {

    @Test
    void recordPlacementIfAbsentKeepsFirstClaimAtSamePosition() {
        RoadGenerator.RoadGenerationResult result = new RoadGenerator.RoadGenerationResult(0);
        BlockPos overlap = new BlockPos(3, 64, 7);

        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:grass_block", "minecraft:stone");
        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:dirt", "minecraft:gravel");

        assertEquals(1, result.placementRecords.size());
        GenerateRoadCommand.BlockRecord record = result.placementRecords.get(overlap);
        assertEquals("minecraft:grass_block", record.previousBlockId);
        assertEquals("minecraft:stone", record.newBlockId);
    }

    @Test
    void junctionRoadThenSidewalkOverlapKeepsRoadMaterial() {
        RoadGenerator.RoadGenerationResult result = new RoadGenerator.RoadGenerationResult(0);
        BlockPos overlap = new BlockPos(12, 65, 18);

        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:grass_block", "minecraft:stone");
        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:grass_block", "minecraft:oak_planks");

        assertEquals(1, result.placementRecords.size());
        assertEquals("minecraft:stone", result.placementRecords.get(overlap).newBlockId);
    }
}
