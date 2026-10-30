package com.plot.plugin.road.pipeline.marking;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadRailConnectivityValidatorTest {

    @Test
    void connectedRunWithSupportsHasNoIssues() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        place(result, 0, 64, 0, "minecraft:oak_planks");
        place(result, 1, 64, 0, "minecraft:oak_planks");
        place(result, 2, 64, 0, "minecraft:oak_planks");
        place(result, 0, 65, 0, "minecraft:rail");
        place(result, 1, 65, 0, "minecraft:rail");
        place(result, 2, 65, 0, "minecraft:rail");
        RoadRailPlacementPass.apply(result);

        var report = RoadRailConnectivityValidator.validate(result);
        assertEquals(3, report.railCount());
        assertEquals(0, report.orphanRails());
        assertEquals(0, report.shapeDiscontinuities());
        assertEquals(0, report.unsupportedRails());
        assertEquals(0, report.supportUnknownRails());
        assertFalse(report.hasIssues());
    }

    @Test
    void isolatedRailAmongOthersCountsAsOrphan() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        place(result, 0, 64, 0, "minecraft:oak_planks");
        place(result, 5, 64, 0, "minecraft:oak_planks");
        place(result, 0, 65, 0, "minecraft:rail");
        place(result, 5, 65, 0, "minecraft:rail");
        RoadRailPlacementPass.apply(result);

        var report = RoadRailConnectivityValidator.validate(result);
        assertEquals(2, report.orphanRails());
        assertTrue(report.hasIssues());
    }

    @Test
    void adjacentButWrongShapeCountsDiscontinuity() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        place(result, 0, 64, 0, "minecraft:oak_planks");
        place(result, 1, 64, 0, "minecraft:oak_planks");
        // 强制南北 shape 却东西相邻 → 轨向不连续
        place(result, 0, 65, 0, "minecraft:rail[shape=north_south]");
        place(result, 1, 65, 0, "minecraft:rail[shape=north_south]");

        var report = RoadRailConnectivityValidator.validate(result);
        assertEquals(0, report.orphanRails());
        assertTrue(report.shapeDiscontinuities() >= 1, "got " + report.shapeDiscontinuities());
        assertTrue(report.hasIssues());
    }

    @Test
    void missingSupportInRecordsWithAirIsConfirmed() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        place(result, 0, 64, 0, "minecraft:air");
        place(result, 1, 64, 0, "minecraft:air");
        place(result, 0, 65, 0, "minecraft:rail");
        place(result, 1, 65, 0, "minecraft:rail");
        RoadRailPlacementPass.apply(result);

        var report = RoadRailConnectivityValidator.validate(result);
        assertEquals(2, report.unsupportedRails());
        assertEquals(0, report.supportUnknownRails());
        assertTrue(report.hasIssues());
    }

    @Test
    void missingSupportCellNotInRecordsIsUnknown() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        place(result, 0, 65, 0, "minecraft:rail");
        place(result, 1, 65, 0, "minecraft:rail");
        RoadRailPlacementPass.apply(result);

        var report = RoadRailConnectivityValidator.validate(result);
        assertEquals(0, report.unsupportedRails());
        assertEquals(2, report.supportUnknownRails());
        // 支撑未知不单独构成 hasIssues（需读世界才能确认）
        assertFalse(report.hasIssues());
    }

    private static void place(RoadGenerationResult result, int x, int y, int z, String blockId) {
        BlockPos pos = new BlockPos(x, y, z);
        result.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", blockId));
    }
}
