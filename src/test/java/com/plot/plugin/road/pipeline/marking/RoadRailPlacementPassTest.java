package com.plot.plugin.road.pipeline.marking;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadRailPlacementPassTest {

    @Test
    void applyRewritesStraightRunWithEastWestShape() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        result.placementRecords.put(
            new BlockPos(0, 64, 0),
            new BlockRecord(new BlockPos(0, 64, 0), "minecraft:air", "minecraft:rail"));
        result.placementRecords.put(
            new BlockPos(1, 64, 0),
            new BlockRecord(new BlockPos(1, 64, 0), "minecraft:air", "minecraft:rail"));
        result.placementRecords.put(
            new BlockPos(2, 64, 0),
            new BlockRecord(new BlockPos(2, 64, 0), "minecraft:air", "minecraft:rail"));

        RoadRailPlacementPass.apply(result);

        assertTrue(result.placementRecords.get(new BlockPos(1, 64, 0)).newBlockId.contains("east_west"));
        assertTrue(result.placementRecords.get(new BlockPos(0, 64, 0)).newBlockId.contains("east_west"));
    }

    @Test
    void applyRewritesAscendingWhenNeighborIsHigher() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        result.placementRecords.put(
            new BlockPos(0, 64, 0),
            new BlockRecord(new BlockPos(0, 64, 0), "minecraft:air", "minecraft:rail"));
        result.placementRecords.put(
            new BlockPos(1, 65, 0),
            new BlockRecord(new BlockPos(1, 65, 0), "minecraft:air", "minecraft:rail"));

        RoadRailPlacementPass.apply(result);

        String low = result.placementRecords.get(new BlockPos(0, 64, 0)).newBlockId;
        assertTrue(low.contains("ascending_east"), "got " + low);
    }

    @Test
    void nonRailPlacementsUntouched() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(0, 64, 0);
        result.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));
        RoadRailPlacementPass.apply(result);
        assertEquals("minecraft:stone", result.placementRecords.get(pos).newBlockId);
    }
}
