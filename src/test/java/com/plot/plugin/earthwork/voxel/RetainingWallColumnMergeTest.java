package com.plot.plugin.earthwork.voxel;

import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.PlacementMeta;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainingWallColumnMergeTest {

    private static final long KEY = ((long) 5 << 32) | (7 & 0xFFFFFFFFL);

    @Test
    void discontinuousIntervalsPreserveGap() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        Map<Long, RetainingWallGenerator.ColumnPlan> plans = new HashMap<>();

        // 下段 71–73（棕），上段 77–79（蓝）——中间 74–76 不得填充
        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, KEY, 5, 7, 71, 73,
            "minecraft:brown_terracotta", PlacementMeta.ofZone("lower"));
        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, KEY, 5, 7, 77, 79,
            "minecraft:blue_concrete", PlacementMeta.ofZone("upper"));

        assertEquals(6, result.placementRecords.size());
        for (int y = 71; y <= 73; y++) {
            BlockPos pos = new BlockPos(5, y, 7);
            assertEquals("minecraft:brown_terracotta", result.placementRecords.get(pos).newBlockId);
            assertEquals("lower", result.placementMeta.get(pos).zoneId());
        }
        for (int y = 74; y <= 76; y++) {
            assertNull(result.placementRecords.get(new BlockPos(5, y, 7)),
                "gap Y=" + y + " must stay empty");
        }
        for (int y = 77; y <= 79; y++) {
            BlockPos pos = new BlockPos(5, y, 7);
            assertEquals("minecraft:blue_concrete", result.placementRecords.get(pos).newBlockId);
            assertEquals("upper", result.placementMeta.get(pos).zoneId());
        }
        assertEquals(6, plans.get(KEY).occupied.size());
    }

    @Test
    void overlappingHeightsKeepFirstWriterMaterialAndMeta() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        Map<Long, RetainingWallGenerator.ColumnPlan> plans = new HashMap<>();

        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, KEY, 5, 7, 60, 65,
            "minecraft:stone_bricks", PlacementMeta.ofZone("zone-a"));
        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, KEY, 5, 7, 63, 68,
            "minecraft:cobblestone", PlacementMeta.ofZone("zone-b"));

        assertEquals(9, result.placementRecords.size());
        for (int y = 60; y <= 65; y++) {
            BlockPos pos = new BlockPos(5, y, 7);
            assertEquals("minecraft:stone_bricks", result.placementRecords.get(pos).newBlockId);
            assertEquals("zone-a", result.placementMeta.get(pos).zoneId());
        }
        for (int y = 66; y <= 68; y++) {
            BlockPos pos = new BlockPos(5, y, 7);
            assertEquals("minecraft:cobblestone", result.placementRecords.get(pos).newBlockId);
            assertEquals("zone-b", result.placementMeta.get(pos).zoneId());
        }
    }

    @Test
    void sharedEndpointKeepsFirstWriterAtSharedY() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        Map<Long, RetainingWallGenerator.ColumnPlan> plans = new HashMap<>();

        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, KEY, 5, 7, 60, 62,
            "minecraft:stone_bricks", PlacementMeta.ofZone("a"));
        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, KEY, 5, 7, 62, 65,
            "minecraft:cobblestone", PlacementMeta.ofZone("b"));

        assertEquals(6, result.placementRecords.size());
        assertEquals("minecraft:stone_bricks",
            result.placementRecords.get(new BlockPos(5, 62, 7)).newBlockId);
        assertEquals("a", result.placementMeta.get(new BlockPos(5, 62, 7)).zoneId());
        assertEquals("minecraft:cobblestone",
            result.placementRecords.get(new BlockPos(5, 63, 7)).newBlockId);
    }

    @Test
    void inputOrderReverseStillPreservesDiscontinuousGap() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        Map<Long, RetainingWallGenerator.ColumnPlan> plans = new HashMap<>();

        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, KEY, 5, 7, 77, 79,
            "minecraft:blue_concrete", PlacementMeta.ofZone("upper"));
        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, KEY, 5, 7, 71, 73,
            "minecraft:brown_terracotta", PlacementMeta.ofZone("lower"));

        assertEquals(6, result.placementRecords.size());
        assertNull(result.placementRecords.get(new BlockPos(5, 75, 7)));
        assertEquals("minecraft:blue_concrete",
            result.placementRecords.get(new BlockPos(5, 78, 7)).newBlockId);
        assertEquals("minecraft:brown_terracotta",
            result.placementRecords.get(new BlockPos(5, 72, 7)).newBlockId);
    }

    @Test
    void structureSameYConflictKeepsFirst() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        BlockPos pos = new BlockPos(1, 64, 1);

        assertTrue(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:air", "minecraft:stone_bricks",
            EarthworkGenerationResult.ChangeType.STRUCTURE, PlacementMeta.ofZone("a")));
        assertFalse(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:air", "minecraft:cobblestone",
            EarthworkGenerationResult.ChangeType.STRUCTURE, PlacementMeta.ofZone("b")));

        assertEquals(1, result.placementRecords.size());
        assertEquals("minecraft:stone_bricks", result.placementRecords.get(pos).newBlockId);
        assertEquals(result.placementRecords.keySet(), result.changeTypes.keySet());
        assertEquals(result.placementRecords.keySet(), result.placementMeta.keySet());
    }
}
