package com.plot.plugin.road;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadDimensionUtilsTest {

    @Test
    void metersToBlocksRoundsToNearestInteger() {
        assertEquals(5, RoadDimensionUtils.metersToBlocks(4.6));
        assertEquals(5, RoadDimensionUtils.metersToBlocks(5.4));
        assertEquals(1, RoadDimensionUtils.metersToBlocks(0.2));
    }

    @Test
    void lateralOffsetsCoverExactBlockCount() {
        assertEquals(-2, RoadDimensionUtils.minLateralOffset(5));
        assertEquals(2, RoadDimensionUtils.maxLateralOffset(5));
        assertEquals(2.5, RoadDimensionUtils.halfExtentFromCenter(5));

        assertEquals(-2, RoadDimensionUtils.minLateralOffset(6));
        assertEquals(3, RoadDimensionUtils.maxLateralOffset(6));
        assertEquals(3.5, RoadDimensionUtils.halfExtentFromCenter(6));
    }
}
