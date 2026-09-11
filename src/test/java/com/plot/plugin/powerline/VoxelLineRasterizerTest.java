package com.plot.plugin.powerline;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelLineRasterizerTest {

    @Test
    void diagonalCubeUsesInterleavedAxesNotAxisOrderSegments() {
        List<BlockPos> blocks = VoxelLineRasterizer.rasterizeBlockLine3D(
            new BlockPos(0, 0, 0),
            new BlockPos(3, 3, 3));

        assertEquals(new BlockPos(0, 0, 0), blocks.getFirst());
        assertEquals(new BlockPos(3, 3, 3), blocks.getLast());
        assertSixConnected(blocks);
        assertFalse(hasLongSingleAxisRun(blocks, 3), "should not walk X then Y then Z");
        assertTrue(blocks.size() >= 7, "6-connected diagonal needs more than 4 cells");
    }

    @Test
    void pureAxisLineRemainsStraight() {
        List<BlockPos> blocks = VoxelLineRasterizer.rasterizeBlockLine3D(
            new BlockPos(0, 0, 0),
            new BlockPos(10, 0, 0));

        assertEquals(11, blocks.size());
        for (int x = 0; x <= 10; x++) {
            assertTrue(blocks.contains(new BlockPos(x, 0, 0)));
        }
        assertSixConnected(blocks);
    }

    @Test
    void slopedLineDoesNotUseLongAxisOrderSegments() {
        List<BlockPos> blocks = VoxelLineRasterizer.rasterizeBlockLine3D(
            new BlockPos(0, 0, 0),
            new BlockPos(10, 10, 2));

        assertEquals(new BlockPos(0, 0, 0), blocks.getFirst());
        assertEquals(new BlockPos(10, 10, 2), blocks.getLast());
        assertSixConnected(blocks);
        assertFalse(hasLongSingleAxisRun(blocks, 4));
        assertTrue(usesMultipleAxesEarly(blocks));
    }

    @Test
    void reversedEndpointsVisitSameVoxels() {
        BlockPos from = new BlockPos(2, 4, 1);
        BlockPos to = new BlockPos(9, 7, 5);
        Set<BlockPos> forward = new HashSet<>(VoxelLineRasterizer.rasterizeBlockLine3D(from, to));
        Set<BlockPos> backward = new HashSet<>(VoxelLineRasterizer.rasterizeBlockLine3D(to, from));
        assertEquals(forward, backward);
    }

    @Test
    void identicalEndpointsReturnSingleton() {
        BlockPos pos = new BlockPos(4, 8, 2);
        List<BlockPos> blocks = VoxelLineRasterizer.rasterizeBlockLine3D(pos, pos);
        assertEquals(List.of(pos), blocks);
    }

    private static void assertSixConnected(List<BlockPos> blocks) {
        assertFalse(blocks.isEmpty());
        for (int i = 1; i < blocks.size(); i++) {
            BlockPos previous = blocks.get(i - 1);
            BlockPos current = blocks.get(i);
            int stepDistance = Math.abs(previous.getX() - current.getX())
                + Math.abs(previous.getY() - current.getY())
                + Math.abs(previous.getZ() - current.getZ());
            assertEquals(1, stepDistance, "gap between " + previous + " and " + current);
        }
    }

    private static boolean hasLongSingleAxisRun(List<BlockPos> blocks, int maxRun) {
        if (blocks.size() < 2) {
            return false;
        }
        int run = 1;
        Integer axis = null;
        for (int i = 1; i < blocks.size(); i++) {
            BlockPos previous = blocks.get(i - 1);
            BlockPos current = blocks.get(i);
            int dx = current.getX() - previous.getX();
            int dy = current.getY() - previous.getY();
            int dz = current.getZ() - previous.getZ();
            int changedAxes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
            if (changedAxes != 1) {
                return true;
            }
            int currentAxis = dx != 0 ? 0 : (dy != 0 ? 1 : 2);
            if (axis != null && axis == currentAxis) {
                run++;
                if (run > maxRun) {
                    return true;
                }
            } else {
                axis = currentAxis;
                run = 1;
            }
        }
        return false;
    }

    private static boolean usesMultipleAxesEarly(List<BlockPos> blocks) {
        Set<String> axes = new HashSet<>();
        int limit = Math.min(6, blocks.size() - 1);
        for (int i = 1; i <= limit; i++) {
            BlockPos previous = blocks.get(i - 1);
            BlockPos current = blocks.get(i);
            if (current.getX() != previous.getX()) {
                axes.add("x");
            }
            if (current.getY() != previous.getY()) {
                axes.add("y");
            }
            if (current.getZ() != previous.getZ()) {
                axes.add("z");
            }
        }
        return axes.size() >= 2;
    }
}
