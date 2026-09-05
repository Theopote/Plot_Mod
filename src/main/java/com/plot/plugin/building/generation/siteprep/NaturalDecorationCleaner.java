package com.plot.plugin.building.generation.siteprep;

import com.plot.core.terrain.EngineeringTerrainService;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
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
 * 不删除 {@code OTHER_SOLID}；树清理有块数/半径/高度上限。
 */
public final class NaturalDecorationCleaner {
    public static final int MAX_TREE_CLEAR_BLOCKS = 256;
    public static final int MAX_TREE_CLEAR_RADIUS = 6;
    public static final int MAX_VERTICAL_RANGE = 16;

    private NaturalDecorationCleaner() {
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
     * 世界路径：从 seed 收集可清树木方块。
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
        try {
            BlockState state = world.getBlockState(pos);
            return state.isIn(BlockTags.LOGS);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isLeaf(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        try {
            BlockState state = world.getBlockState(pos);
            return state.isIn(BlockTags.LEAVES);
        } catch (Exception e) {
            return false;
        }
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
