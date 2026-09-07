package com.plot.plugin.building.generation.siteprep;

import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.core.terrain.NaturalTreeClassifier;
import com.plot.core.terrain.TerrainBlockReaders;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 有限范围清理自然附着物（含树木 flood-fill）。
 * <p>
 * 不删除 {@code OTHER_SOLID}；原木仅在 {@link #looksLikeNaturalTree} 为真时才做树清理，
 * 避免误删玩家木梁 / 木柱建筑。
 */
public final class NaturalDecorationCleaner {
    public static final int MAX_TREE_CLEAR_BLOCKS = 256;
    public static final int MAX_TREE_CLEAR_RADIUS = 6;
    public static final int MAX_VERTICAL_RANGE = 16;

    /** @see NaturalTreeClassifier#TREE_LEAF_SEARCH_RADIUS */
    public static final int TREE_LEAF_SEARCH_RADIUS = NaturalTreeClassifier.TREE_LEAF_SEARCH_RADIUS;
    /** @see NaturalTreeClassifier#MIN_NEARBY_LEAVES */
    public static final int MIN_NEARBY_LEAVES = NaturalTreeClassifier.MIN_NEARBY_LEAVES;
    /** @see NaturalTreeClassifier#TRUNK_BASE_SEARCH_DEPTH */
    public static final int TRUNK_BASE_SEARCH_DEPTH = NaturalTreeClassifier.TRUNK_BASE_SEARCH_DEPTH;

    private NaturalDecorationCleaner() {
    }

    /**
     * 保守自然树启发式：附近有足够树叶，且树干底部接近工程地面。
     * 无树叶的原木柱 / 木梁 → false（应按人工构筑处理）。
     */
    public static boolean looksLikeNaturalTree(World world, BlockPos seedLog) {
        return NaturalTreeClassifier.looksLikeNaturalTree(world, seedLog);
    }

    /**
     * 纯函数路径（测试友好）。
     */
    public static boolean looksLikeNaturalTree(
            BlockPos seedLog,
            Predicate<BlockPos> isLog,
            Predicate<BlockPos> isLeaf,
            Predicate<BlockPos> isTerrain) {
        return NaturalTreeClassifier.looksLikeNaturalTree(seedLog, isLog, isLeaf, isTerrain);
    }

    /**
     * 测试友好：对种子 log 做 BFS，只访问 logs/leaves。
     *
     * @return 应清理的方块；若触达上限则 {@code hitLimit=true}
     */
    public static TreeClearResult collectTreeBlocks(
            BlockPos seed,
            Predicate<BlockPos> isLog,
            Predicate<BlockPos> isLeaf) {
        if (seed == null || isLog == null || isLeaf == null || !isLog.test(seed)) {
            return TreeClearResult.empty();
        }

        List<BlockPos> collected = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(pack(seed));
        boolean hitLimit = false;

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            collected.add(current);
            if (collected.size() >= MAX_TREE_CLEAR_BLOCKS) {
                hitLimit = true;
                break;
            }

            for (BlockPos neighbor : neighbors(current)) {
                if (!withinTreeBounds(seed, neighbor)) {
                    continue;
                }
                long key = pack(neighbor);
                if (!visited.add(key)) {
                    continue;
                }
                if (isLog.test(neighbor) || isLeaf.test(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return new TreeClearResult(List.copyOf(collected), hitLimit);
    }

    /**
     * 世界路径：仅当看起来像自然树时才收集；否则返回 empty。
     */
    public static TreeClearResult collectNaturalTreeBlocks(World world, BlockPos seed) {
        if (world == null || seed == null || !looksLikeNaturalTree(world, seed)) {
            return TreeClearResult.empty();
        }
        return collectTreeBlocks(
            seed,
            pos -> isLog(world, pos),
            pos -> isLeaf(world, pos));
    }

    /**
     * 世界路径：从 seed 收集可清树木方块（调用方需先自行判定自然树）。
     */
    public static TreeClearResult collectTreeBlocks(World world, BlockPos seed) {
        if (world == null || seed == null) {
            return TreeClearResult.empty();
        }
        return collectTreeBlocks(
            seed,
            pos -> isLog(world, pos),
            pos -> isLeaf(world, pos));
    }

    public static boolean isNaturalDecoration(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return EngineeringTerrainService.of(world)
            .isClearableNaturalDecoration(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isLog(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return NaturalTreeClassifier.isLog(TerrainBlockReaders.of(world), pos);
    }

    public static boolean isLeaf(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return NaturalTreeClassifier.isLeaf(TerrainBlockReaders.of(world), pos);
    }

    private static boolean withinTreeBounds(BlockPos seed, BlockPos pos) {
        int dx = Math.abs(pos.getX() - seed.getX());
        int dz = Math.abs(pos.getZ() - seed.getZ());
        int dy = Math.abs(pos.getY() - seed.getY());
        return dx <= MAX_TREE_CLEAR_RADIUS
            && dz <= MAX_TREE_CLEAR_RADIUS
            && dy <= MAX_VERTICAL_RANGE;
    }

    private static List<BlockPos> neighbors(BlockPos pos) {
        return List.of(
            pos.north(), pos.south(), pos.east(), pos.west(),
            pos.up(), pos.down(),
            pos.north().up(), pos.south().up(), pos.east().up(), pos.west().up(),
            pos.north().down(), pos.south().down(), pos.east().down(), pos.west().down()
        );
    }

    private static long pack(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
    }

    public record TreeClearResult(List<BlockPos> blocks, boolean hitLimit) {
        public TreeClearResult {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }

        public static TreeClearResult empty() {
            return new TreeClearResult(List.of(), false);
        }
    }
}
