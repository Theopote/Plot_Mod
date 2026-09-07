package com.plot.core.terrain;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalTreeClassifierTest {

    @Test
    void tr01ArtificialLogPillarWithoutLeavesIsNotNaturalTree() {
        Set<Long> logs = Set.of(
            BlockPos.asLong(0, 64, 0),
            BlockPos.asLong(0, 65, 0),
            BlockPos.asLong(0, 66, 0),
            BlockPos.asLong(0, 67, 0));
        Set<Long> terrain = Set.of(BlockPos.asLong(0, 63, 0));

        assertFalse(NaturalTreeClassifier.looksLikeNaturalTree(
            new BlockPos(0, 64, 0),
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> false,
            pos -> terrain.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()))));
    }

    @Test
    void tr02NaturalTreeWithLeavesAndTerrainIsRecognized() {
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
        assertTrue(NaturalTreeClassifier.looksLikeNaturalTree(
            seed,
            pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> leaves.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ())),
            pos -> terrain.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()))));
    }
}
