package com.plot.plugin.building.generation.siteprep;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalDecorationCleanerTest {

    @Test
    void c04TreeFloodFillCollectsConnectedLogsAndLeaves() {
        Set<Long> logs = Set.of(
            BlockPos.asLong(0, 64, 0),
            BlockPos.asLong(0, 65, 0),
            BlockPos.asLong(0, 66, 0));
        Set<Long> leaves = Set.of(
            BlockPos.asLong(1, 66, 0),
            BlockPos.asLong(-1, 66, 0),
            BlockPos.asLong(0, 66, 1));

        NaturalDecorationCleaner.TreeClearResult result = NaturalDecorationCleaner.collectTreeBlocks(
            new BlockPos(0, 64, 0),
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> leaves.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())));

        assertFalse(result.hitLimit());
        assertEquals(6, result.blocks().size());
        assertTrue(result.blocks().contains(new BlockPos(1, 66, 0)));
    }

    @Test
    void c04TreeFloodFillStopsAtLimit() {
        Set<Long> logs = new HashSet<>();
        for (int x = 0; x <= NaturalDecorationCleaner.MAX_TREE_CLEAR_RADIUS; x++) {
            for (int z = 0; z <= NaturalDecorationCleaner.MAX_TREE_CLEAR_RADIUS; z++) {
                for (int y = 64; y <= 64 + NaturalDecorationCleaner.MAX_VERTICAL_RANGE; y++) {
                    logs.add(BlockPos.asLong(x, y, z));
                }
            }
        }

        NaturalDecorationCleaner.TreeClearResult result = NaturalDecorationCleaner.collectTreeBlocks(
            new BlockPos(0, 64, 0),
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> false);

        assertTrue(result.hitLimit());
        assertEquals(NaturalDecorationCleaner.MAX_TREE_CLEAR_BLOCKS, result.blocks().size());
    }

    @Test
    void c06OutsideRadiusNotCollected() {
        Set<Long> logs = Set.of(
            BlockPos.asLong(0, 64, 0),
            BlockPos.asLong(NaturalDecorationCleaner.MAX_TREE_CLEAR_RADIUS + 2, 64, 0));

        NaturalDecorationCleaner.TreeClearResult result = NaturalDecorationCleaner.collectTreeBlocks(
            new BlockPos(0, 64, 0),
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> false);

        assertEquals(1, result.blocks().size());
        assertEquals(new BlockPos(0, 64, 0), result.blocks().getFirst());
    }

    @Test
    void c07ArtificialLogPillarWithoutLeavesIsNotNaturalTree() {
        Set<Long> logs = Set.of(
            BlockPos.asLong(0, 64, 0),
            BlockPos.asLong(0, 65, 0),
            BlockPos.asLong(0, 66, 0),
            BlockPos.asLong(0, 67, 0));
        Set<Long> terrain = Set.of(BlockPos.asLong(0, 63, 0));

        boolean natural = NaturalDecorationCleaner.looksLikeNaturalTree(
            new BlockPos(0, 64, 0),
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> false,
            pos -> terrain.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())));

        assertFalse(natural, "player log pillar without leaves must not be cleared as a tree");
    }

    @Test
    void c08NaturalTreeWithLeavesAndTerrainIsCleared() {
        Set<Long> logs = Set.of(
            BlockPos.asLong(0, 64, 0),
            BlockPos.asLong(0, 65, 0),
            BlockPos.asLong(0, 66, 0));
        Set<Long> leaves = Set.of(
            BlockPos.asLong(1, 66, 0),
            BlockPos.asLong(-1, 66, 0),
            BlockPos.asLong(0, 66, 1),
            BlockPos.asLong(0, 66, -1));
        Set<Long> terrain = Set.of(BlockPos.asLong(0, 63, 0));

        BlockPos seed = new BlockPos(0, 64, 0);
        assertTrue(NaturalDecorationCleaner.looksLikeNaturalTree(
            seed,
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> leaves.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> terrain.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()))));

        NaturalDecorationCleaner.TreeClearResult cleared = NaturalDecorationCleaner.collectTreeBlocks(
            seed,
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> leaves.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())));

        assertEquals(7, cleared.blocks().size());
        assertTrue(cleared.blocks().contains(new BlockPos(1, 66, 0)));
    }
}
