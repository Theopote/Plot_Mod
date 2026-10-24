package com.plot.plugin.earthwork.voxel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.PlacementMeta;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkDecorationClearerTest {

    @Test
    void clearancePriorityYieldsToCutAndFill() {
        assertTrue(EarthworkPlacementMerge.priority(EarthworkGenerationResult.ChangeType.CLEARANCE)
            < EarthworkPlacementMerge.priority(EarthworkGenerationResult.ChangeType.CUT));
        assertTrue(EarthworkPlacementMerge.priority(EarthworkGenerationResult.ChangeType.CUT)
            < EarthworkPlacementMerge.priority(EarthworkGenerationResult.ChangeType.STRUCTURE));
    }

    @Test
    void clearColumnRecordsAirAsClearanceWithoutWorldUsesSampler() {
        Map<BlockPos, String> blocks = new HashMap<>();
        BlockPos grass = new BlockPos(3, 65, 4);
        BlockPos leaf = new BlockPos(3, 66, 4);
        blocks.put(new BlockPos(3, 64, 4), "minecraft:grass_block");
        blocks.put(grass, "minecraft:short_grass");
        blocks.put(leaf, "minecraft:oak_leaves");

        EarthworkVoxelizer voxelizer = new EarthworkVoxelizer(pos -> blocks.getOrDefault(pos, "minecraft:air"));
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        TerrainSnapshot.Column column = new TerrainSnapshot.Column(
            new Vec2d(3.5, 4.5), 3, 4, 64, "minecraft:grass_block", true);

        // world=null → EngineeringTerrainService 不会采样装饰；只验证 API 在无世界时安全返回
        EarthworkDecorationClearer.clearColumn(
            voxelizer, null, column, result, PlacementMeta.ofZone("pad"), new HashSet<>());
        assertTrue(result.placementRecords.isEmpty());
    }

    @Test
    void syncDoesNotCountClearanceAsCutOrFill() {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        BlockPos pos = new BlockPos(1, 70, 1);
        EarthworkPlacementMerge.recordOrReplace(
            result,
            pos,
            "minecraft:oak_leaves",
            EarthworkGeometryUtils.EXCAVATION_BLOCK_ID,
            EarthworkGenerationResult.ChangeType.CLEARANCE,
            PlacementMeta.ofZone("pad"));
        result.syncChangedBlocksFromPlacements();
        assertEquals(0L, result.volumeReport.cutChangedBlocks());
        assertEquals(0L, result.volumeReport.fillChangedBlocks());
        assertEquals(1L, result.structureChangedBlocks);
    }
}
