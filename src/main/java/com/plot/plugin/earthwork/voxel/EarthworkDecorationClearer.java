package com.plot.plugin.earthwork.voxel;

import com.plot.core.terrain.EngineeringTerrainSampler;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.building.generation.siteprep.NaturalDecorationCleaner;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.PlacementMeta;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

/**
 * 清理土方范围内工程地面以上的树木、草花、树叶等自然附着物。
 * <p>
 * 不计入挖填方量（与道路 {@code clearRoadDecorations}、建筑 {@link NaturalDecorationCleaner} 同语义）。
 * 原木仅在判定为自然树时清理，避免误删玩家木梁。
 */
public final class EarthworkDecorationClearer {
    private EarthworkDecorationClearer() {
    }

    /**
     * 清理单列 {@code groundY+1 … rawSurfaceY} 上的可清除附着物；自然树做有限 BFS。
     *
     * @param cleared 跨列去重（树冠可能跨格）
     */
    public static void clearColumn(
            EarthworkVoxelizer voxelizer,
            World world,
            TerrainSnapshot.Column column,
            EarthworkGenerationResult result,
            PlacementMeta meta,
            Set<Long> cleared) {
        if (voxelizer == null || world == null || column == null || result == null || !column.chunkLoaded()) {
            return;
        }
        Set<Long> visited = cleared != null ? cleared : new HashSet<>();
        PlacementMeta safeMeta = meta != null ? meta : PlacementMeta.UNASSIGNED;
        EngineeringTerrainService terrain = EngineeringTerrainService.of(world);
        int worldX = column.worldX();
        int worldZ = column.worldZ();
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return;
        }

        int groundY = column.groundY();
        int rawY = EngineeringTerrainSampler.sampleRawSurface(world, worldX, worldZ);
        if (rawY < groundY + 1) {
            return;
        }

        for (int y = groundY + 1; y <= rawY; y++) {
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            long key = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
            if (visited.contains(key)) {
                continue;
            }
            if (!terrain.isClearableNaturalDecoration(worldX, y, worldZ)) {
                continue;
            }

            if (NaturalDecorationCleaner.isLog(world, pos)) {
                NaturalDecorationCleaner.TreeClearResult tree =
                    NaturalDecorationCleaner.collectNaturalTreeBlocks(world, pos);
                for (BlockPos treePos : tree.blocks()) {
                    long treeKey = BlockPos.asLong(treePos.getX(), treePos.getY(), treePos.getZ());
                    if (!visited.add(treeKey)) {
                        continue;
                    }
                    voxelizer.recordBlock(
                        result,
                        world,
                        treePos,
                        EarthworkGeometryUtils.EXCAVATION_BLOCK_ID,
                        EarthworkGenerationResult.ChangeType.CLEARANCE,
                        safeMeta);
                }
            } else {
                visited.add(key);
                voxelizer.recordBlock(
                    result,
                    world,
                    pos,
                    EarthworkGeometryUtils.EXCAVATION_BLOCK_ID,
                    EarthworkGenerationResult.ChangeType.CLEARANCE,
                    safeMeta);
            }
        }
    }

    public static Set<Long> newClearedSet() {
        return new HashSet<>();
    }
}
