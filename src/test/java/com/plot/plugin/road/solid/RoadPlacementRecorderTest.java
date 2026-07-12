package com.plot.plugin.road.solid;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadPlacementRecorderTest {

    @Test
    void recordIfAbsentKeepsFirstClaim() {
        Map<BlockPos, BlockRecord> records = new LinkedHashMap<>();
        BlockPos pos = new BlockPos(1, 64, 2);

        RoadPlacementRecorder.recordIfAbsent(records, pos, "minecraft:grass_block", "minecraft:stone");
        RoadPlacementRecorder.recordIfAbsent(records, pos, "minecraft:dirt", "minecraft:gravel");

        assertEquals(1, records.size());
        assertEquals("minecraft:grass_block", records.get(pos).previousBlockId);
        assertEquals("minecraft:stone", records.get(pos).newBlockId);
    }

    @Test
    void recordOverridePreservesOriginalPreviousBlockId() {
        Map<BlockPos, BlockRecord> records = new LinkedHashMap<>();
        BlockPos pos = new BlockPos(4, 65, 6);

        RoadPlacementRecorder.recordIfAbsent(records, pos, "minecraft:grass_block", "minecraft:stone");
        RoadPlacementRecorder.recordOverride(records, pos, "minecraft:dirt", "minecraft:black_concrete");

        assertEquals("minecraft:grass_block", records.get(pos).previousBlockId);
        assertEquals("minecraft:black_concrete", records.get(pos).newBlockId);
    }

    @Test
    void flushJunctionSolidsAppliesLayerMaterialOverrides() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        RoadSolidModel solids = new RoadSolidModel();
        solids.add(new com.plot.api.geometry.Vec2d(0, 0), 64, RoadSolidLayer.ROAD);
        solids.add(new com.plot.api.geometry.Vec2d(1, 0), 64, RoadSolidLayer.SIDEWALK);
        solids.add(new com.plot.api.geometry.Vec2d(2, 0), 64, RoadSolidLayer.MARKING);

        RoadVoxelRasterizer.flushJunctionSolids(
            result,
            solids,
            null,
            "minecraft:stone",
            "minecraft:oak_planks",
            "minecraft:white_concrete");

        assertEquals(2, result.roadBlocks.size());
        assertEquals(1, result.sidewalkBlocks.size());
        assertEquals(3, result.placementRecords.size());
        assertEquals("minecraft:stone", result.placementRecords.get(new BlockPos(0, 64, 0)).newBlockId);
        assertEquals("minecraft:oak_planks", result.placementRecords.get(new BlockPos(1, 64, 0)).newBlockId);
        assertEquals("minecraft:white_concrete", result.placementRecords.get(new BlockPos(2, 64, 0)).newBlockId);
    }
}
