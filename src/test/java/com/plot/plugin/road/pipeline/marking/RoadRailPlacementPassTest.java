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
    void applyPreservesPoweredPropertyWhenRewritingShape() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        result.placementRecords.put(
            new BlockPos(0, 64, 0),
            new BlockRecord(
                new BlockPos(0, 64, 0),
                "minecraft:air",
                "minecraft:powered_rail[powered=true,shape=north_south]"));
        result.placementRecords.put(
            new BlockPos(1, 64, 0),
            new BlockRecord(
                new BlockPos(1, 64, 0),
                "minecraft:air",
                "minecraft:powered_rail[powered=true]"));

        RoadRailPlacementPass.apply(result);

        String left = result.placementRecords.get(new BlockPos(0, 64, 0)).newBlockId;
        assertTrue(left.contains("powered=true"), "got " + left);
        assertTrue(left.contains("east_west"), "got " + left);
    }

    @Test
    void teeJunctionKeepsThroughAxisDespiteSideNeighbor() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        // 南北贯通 + 东支：中心应南北
        result.placementRecords.put(
            new BlockPos(0, 64, -1), new BlockRecord(new BlockPos(0, 64, -1), "minecraft:air", "minecraft:rail"));
        result.placementRecords.put(
            new BlockPos(0, 64, 0), new BlockRecord(new BlockPos(0, 64, 0), "minecraft:air", "minecraft:rail"));
        result.placementRecords.put(
            new BlockPos(0, 64, 1), new BlockRecord(new BlockPos(0, 64, 1), "minecraft:air", "minecraft:rail"));
        result.placementRecords.put(
            new BlockPos(1, 64, 0), new BlockRecord(new BlockPos(1, 64, 0), "minecraft:air", "minecraft:rail"));

        RoadRailPlacementPass.apply(result);

        String center = result.placementRecords.get(new BlockPos(0, 64, 0)).newBlockId;
        assertTrue(center.contains("north_south"), "got " + center);
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
