package com.plot.core.terrain;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Predicate;

/**
 * 共享自然树启发式：Building / Road 等工程清理路径共用。
 * <p>
 * 保守策略：附近有足够树叶且树干底部接近工程地面 → 自然树；
 * 无树叶的原木柱 / 木梁 → 人工构筑，不可清理。
 */
public final class NaturalTreeClassifier {
    /** 判定自然树时，在种子周围搜索树叶的半径。 */
    public static final int TREE_LEAF_SEARCH_RADIUS = 3;
    /** 附近至少这么多树叶才认为是自然树（保守：宁可漏清树，也不删木建筑）。 */
    public static final int MIN_NEARBY_LEAVES = 3;
    /** 树干底部向下找工程地面的最大距离。 */
    public static final int TRUNK_BASE_SEARCH_DEPTH = 8;

    private NaturalTreeClassifier() {
    }

    public static boolean looksLikeNaturalTree(World world, BlockPos seedLog) {
        if (world == null || seedLog == null) {
            return false;
        }
        return looksLikeNaturalTree(TerrainBlockReaders.of(world), seedLog);
    }

    public static boolean looksLikeNaturalTree(TerrainBlockReader reader, BlockPos seedLog) {
        if (reader == null || seedLog == null || !isLog(reader, seedLog)) {
            return false;
        }
        return looksLikeNaturalTree(
            seedLog,
            pos -> isLog(reader, pos),
            pos -> isLeaf(reader, pos),
            pos -> isEngineeringTerrain(reader, pos));
    }

    /**
     * 纯函数路径（测试友好）。
     */
    public static boolean looksLikeNaturalTree(
            BlockPos seedLog,
            Predicate<BlockPos> isLog,
            Predicate<BlockPos> isLeaf,
            Predicate<BlockPos> isTerrain) {
        if (seedLog == null || isLog == null || isLeaf == null || isTerrain == null) {
            return false;
        }
        if (!isLog.test(seedLog)) {
            return false;
        }
        int leafCount = countNearby(seedLog, TREE_LEAF_SEARCH_RADIUS, 16, isLeaf);
        if (leafCount < MIN_NEARBY_LEAVES) {
            return false;
        }
        return hasTerrainNearTrunkBase(seedLog, isLog, isTerrain);
    }

    public static boolean isLog(TerrainBlockReader reader, BlockPos pos) {
        if (reader == null || pos == null) {
            return false;
        }
        try {
            BlockState state = reader.getBlockState(pos);
            return state != null && state.isIn(BlockTags.LOGS);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isLeaf(TerrainBlockReader reader, BlockPos pos) {
        if (reader == null || pos == null) {
            return false;
        }
        try {
            BlockState state = reader.getBlockState(pos);
            return state != null && state.isIn(BlockTags.LEAVES);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isEngineeringTerrain(TerrainBlockReader reader, BlockPos pos) {
        try {
            return EngineeringTerrainService.isEngineeringTerrain(reader.getBlockState(pos));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasTerrainNearTrunkBase(
            BlockPos seedLog,
            Predicate<BlockPos> isLog,
            Predicate<BlockPos> isTerrain) {
        BlockPos base = seedLog;
        for (int i = 0; i < TRUNK_BASE_SEARCH_DEPTH; i++) {
            BlockPos below = base.down();
            if (!isLog.test(below)) {
                break;
            }
            base = below;
        }
        for (int dy = 1; dy <= 2; dy++) {
            if (isTerrain.test(base.down(dy))) {
                return true;
            }
        }
        return false;
    }

    private static int countNearby(
            BlockPos center,
            int horizontalRadius,
            int verticalRange,
            Predicate<BlockPos> match) {
        int count = 0;
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (match.test(center.add(dx, dy, dz))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
