package com.plot.plugin.powerline;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerMemberVoxelRasterizerTest {

    @Test
    void thicknessTwoExpandsSymmetricallyAroundVerticalMember() {
        Set<BlockPos> blocks = TowerMemberVoxelRasterizer.rasterizeMember(
            0.0, 0.0, 0.0,
            0.0, 4.0, 0.0,
            2);

        assertTrue(blocks.contains(new BlockPos(0, 0, 0)));
        assertTrue(blocks.contains(new BlockPos(1, 0, 0)));
        assertTrue(blocks.contains(new BlockPos(-1, 0, 0)));
        assertTrue(blocks.contains(new BlockPos(0, 0, 1)));
        assertTrue(blocks.contains(new BlockPos(0, 0, -1)));
    }
}
