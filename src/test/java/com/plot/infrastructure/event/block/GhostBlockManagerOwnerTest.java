package com.plot.infrastructure.event.block;

import com.plot.api.world.GhostBlockOwners;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostBlockManagerOwnerTest {
    private GhostBlockManager manager;

    @BeforeEach
    void setUp() {
        manager = GhostBlockManager.getInstance();
        manager.clearAllGhostBlocks();
    }

    @Test
    void clearGhostBlocksOnlyRemovesMatchingOwner() {
        manager.addGhostBlock(GhostBlockOwners.PATTERN, new BlockPos(1, 64, 2), "minecraft:white_wool");
        manager.addGhostBlock(GhostBlockOwners.ROAD, new BlockPos(3, 64, 4), "minecraft:stone");

        manager.clearGhostBlocks(GhostBlockOwners.PATTERN);

        assertEquals(1, manager.getVisibleGhostBlockCount());
        assertEquals(1, manager.getVisibleGhostBlockCount(GhostBlockOwners.ROAD));
        assertEquals(0, manager.getVisibleGhostBlockCount(GhostBlockOwners.PATTERN));
    }

    @Test
    void replaceGhostBlocksReplacesOnlyOwnerPreview() {
        manager.addGhostBlock(GhostBlockOwners.BUILDING, new BlockPos(1, 64, 1), "minecraft:oak_planks");
        Map<BlockPos, String> patternBlocks = new LinkedHashMap<>();
        patternBlocks.put(new BlockPos(5, 64, 5), "minecraft:red_wool");
        patternBlocks.put(new BlockPos(6, 64, 6), "minecraft:blue_wool");

        manager.replaceGhostBlocks(GhostBlockOwners.PATTERN, patternBlocks);

        assertEquals(3, manager.getVisibleGhostBlockCount());
        assertEquals(1, manager.getVisibleGhostBlockCount(GhostBlockOwners.BUILDING));
        assertEquals(2, manager.getVisibleGhostBlockCount(GhostBlockOwners.PATTERN));

        manager.replaceGhostBlocks(GhostBlockOwners.PATTERN, Map.of());
        assertEquals(1, manager.getVisibleGhostBlockCount());
        assertEquals(1, manager.getVisibleGhostBlockCount(GhostBlockOwners.BUILDING));
    }
}
