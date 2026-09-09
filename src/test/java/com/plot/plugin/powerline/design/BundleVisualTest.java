package com.plot.plugin.powerline.design;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleVisualTest {

    @Test
    void singleLeavesCenterUntouched() {
        BlockPos center = new BlockPos(10, 64, 20);
        Set<BlockPos> blocks = BundleVisual.SINGLE.expand(center, 1, 0, 0);
        assertEquals(Set.of(center), blocks);
    }

    @Test
    void twinExpandsAlongHorizontalPerpendicularForXSpan() {
        BlockPos center = new BlockPos(10, 64, 20);
        Set<BlockPos> blocks = BundleVisual.TWIN.expand(center, 10, 0, 0);
        assertEquals(2, blocks.size());
        assertTrue(blocks.contains(center));
        assertTrue(blocks.contains(center.add(0, 0, 1)));
    }

    @Test
    void quadExpandsToFourBlocksForHorizontalSpan() {
        BlockPos center = new BlockPos(10, 64, 20);
        Set<BlockPos> blocks = BundleVisual.QUAD.expand(center, 10, 0, 0);
        assertEquals(4, blocks.size());
        assertTrue(blocks.contains(center));
        assertTrue(blocks.contains(center.add(0, 0, 1)));
        assertTrue(blocks.contains(center.add(0, 1, 0)));
        assertTrue(blocks.contains(center.add(0, 1, 1)));
    }

    @Test
    void expandAllAppliesToEveryCenter() {
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = new BlockPos(1, 64, 0);
        Set<BlockPos> blocks = BundleVisual.TWIN.expandAll(List.of(a, b), 1, 0, 0);
        assertEquals(4, blocks.size());
    }

    @Test
    void forSubconductorCountMapsTwinAndQuad() {
        assertEquals(BundleVisual.SINGLE, BundleVisual.forSubconductorCount(1));
        assertEquals(BundleVisual.TWIN, BundleVisual.forSubconductorCount(2));
        assertEquals(BundleVisual.TWIN, BundleVisual.forSubconductorCount(3));
        assertEquals(BundleVisual.QUAD, BundleVisual.forSubconductorCount(4));
    }
}
