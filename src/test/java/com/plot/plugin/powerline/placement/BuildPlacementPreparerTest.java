package com.plot.plugin.powerline.placement;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BuildPlacementPreparerTest {

    @Test
    void prepareExecutionRecordsUsesCurrentWorldStateNotPreviewSnapshot() {
        BlockPos pos = new BlockPos(10, 64, 20);
        BlockRecord previewRecord = new BlockRecord(pos, "minecraft:grass_block", "minecraft:iron_bars");
        Map<BlockPos, String> world = new LinkedHashMap<>();
        world.put(pos, "minecraft:diamond_block");

        List<BlockRecord> executionRecords = BuildPlacementPreparer.prepareExecutionRecords(
            List.of(previewRecord),
            projectionFor(world));

        assertEquals(1, executionRecords.size());
        assertEquals("minecraft:diamond_block", executionRecords.getFirst().previousBlockId);
        assertNotEquals(previewRecord.previousBlockId, executionRecords.getFirst().previousBlockId);
        assertEquals("minecraft:iron_bars", executionRecords.getFirst().newBlockId);
    }

    private static IBlockProjectionService projectionFor(Map<BlockPos, String> world) {
        return new IBlockProjectionService() {
            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }

            @Override
            public String getBlockIdAt(BlockPos pos) {
                return world.getOrDefault(pos, "minecraft:air");
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                world.put(pos.toImmutable(), blockId);
                return true;
            }
        };
    }
}
