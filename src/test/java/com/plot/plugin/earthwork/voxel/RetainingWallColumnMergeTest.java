package com.plot.plugin.earthwork.voxel;

import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.PlacementMeta;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainingWallColumnMergeTest {

    @Test
    void sharedColumnMergesHeightRangeWithoutDroppingSecondWallExtent() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        Map<Long, RetainingWallGenerator.ColumnPlan> plans = new HashMap<>();
        long key = ((long) 5 << 32) | (7 & 0xFFFFFFFFL);

        // 第一段墙：Y 60–62，石砖
        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, key, 5, 7, 60, 62,
            "minecraft:stone_bricks", PlacementMeta.ofZone("zone-a"));
        // 第二段墙同列：Y 62–65，不同材质 → 高度并集，材质保留先到者
        RetainingWallGenerator.placeOrMergeColumn(
            result, null, plans, key, 5, 7, 62, 65,
            "minecraft:cobblestone", PlacementMeta.ofZone("zone-b"));

        RetainingWallGenerator.ColumnPlan plan = plans.get(key);
        assertEquals(60, plan.bottom);
        assertEquals(65, plan.top);
        assertEquals("minecraft:stone_bricks", plan.blockId);
        // 并集高度 60..65 共 6 层均有 STRUCTURE 记录，材质为先到者
        assertEquals(6, result.placementRecords.size());
        for (int y = 60; y <= 65; y++) {
            BlockPos pos = new BlockPos(5, y, 7);
            assertEquals("minecraft:stone_bricks", result.placementRecords.get(pos).newBlockId);
            assertEquals(EarthworkGenerationResult.ChangeType.STRUCTURE, result.changeTypes.get(pos));
        }
    }

    @Test
    void structureMergeKeepsFirstMaterialAndSingleChangeType() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        BlockPos pos = new BlockPos(1, 64, 1);

        assertTrue(EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:air", "minecraft:stone_bricks",
            EarthworkGenerationResult.ChangeType.STRUCTURE, PlacementMeta.ofZone("a")));
        assertTrue(!EarthworkPlacementMerge.recordOrReplace(
            result, pos, "minecraft:air", "minecraft:cobblestone",
            EarthworkGenerationResult.ChangeType.STRUCTURE, PlacementMeta.ofZone("b")));

        assertEquals(1, result.placementRecords.size());
        assertEquals("minecraft:stone_bricks", result.placementRecords.get(pos).newBlockId);
        assertEquals(result.placementRecords.keySet(), result.changeTypes.keySet());
        assertEquals(result.placementRecords.keySet(), result.placementMeta.keySet());
    }
}
