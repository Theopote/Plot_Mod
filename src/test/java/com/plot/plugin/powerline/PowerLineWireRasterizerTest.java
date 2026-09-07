package com.plot.plugin.powerline;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineWireRasterizerTest {

    @Test
    void sampleCountUsesSegmentCountPlusOne() {
        assertEquals(21, PowerLineWireRasterizer.computeWireSampleCount(20.0, 1));
        assertEquals(2, PowerLineWireRasterizer.computeWireSampleCount(0.5, 1));
        assertEquals(11, PowerLineWireRasterizer.computeWireSampleCount(10.0, 1));
    }

    @Test
    void rasterizeHorizontalSpanCoversEveryCell() {
        List<BlockPos> blocks = PowerLineWireRasterizer.rasterizeLine3D(0, 64, 0, 19, 64, 0);
        Set<Integer> xs = new HashSet<>();
        for (BlockPos block : blocks) {
            xs.add(block.getX());
            assertEquals(64, block.getY());
            assertEquals(0, block.getZ());
        }
        for (int x = 0; x <= 19; x++) {
            assertTrue(xs.contains(x), "missing wire block at x=" + x);
        }
    }

    @Test
    void rasterizeDiagonalPathIsContinuous() {
        List<BlockPos> blocks = PowerLineWireRasterizer.rasterizeLine3D(0, 0, 0, 10, 5, 10);
        assertTrue(blocks.size() > 1);
        for (int i = 1; i < blocks.size(); i++) {
            BlockPos previous = blocks.get(i - 1);
            BlockPos current = blocks.get(i);
            int stepDistance = Math.abs(previous.getX() - current.getX())
                + Math.abs(previous.getY() - current.getY())
                + Math.abs(previous.getZ() - current.getZ());
            assertEquals(1, stepDistance, "gap between " + previous + " and " + current);
        }
    }

    @Test
    void rasterizeSlopedSpanDoesNotSkipVerticalSteps() {
        List<BlockPos> blocks = PowerLineWireRasterizer.rasterizeLine3D(0, 60, 0, 0, 80, 0);
        Set<Integer> ys = new HashSet<>();
        for (BlockPos block : blocks) {
            ys.add(block.getY());
        }
        for (int y = 60; y <= 80; y++) {
            assertTrue(ys.contains(y), "missing wire block at y=" + y);
        }
    }
}
