package com.plot.plugin.road;

import com.plot.plugin.road.model.section.BridgePierStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadParameterLimitsPierTest {

    @Test
    void diameterMatchesCarriagewayParityAndHalfWidthCap() {
        assertEquals(1, RoadParameterLimits.clampBridgePierDiameterBlocks(5, 2));
        assertEquals(1, RoadParameterLimits.clampBridgePierDiameterBlocks(5, 1));
        assertEquals(3, RoadParameterLimits.clampBridgePierDiameterBlocks(7, 3));
        assertEquals(2, RoadParameterLimits.clampBridgePierDiameterBlocks(6, 1));
        assertEquals(2, RoadParameterLimits.clampBridgePierDiameterBlocks(6, 3));
        assertArrayEquals(new int[] {2}, RoadParameterLimits.validBridgePierDiameters(6));
        assertArrayEquals(new int[] {1, 3}, RoadParameterLimits.validBridgePierDiameters(7));
        assertArrayEquals(new int[] {2, 4}, RoadParameterLimits.validBridgePierDiameters(8));
        // 绝对上限 4：更宽的路也不能超过 4
        assertEquals(4, RoadParameterLimits.clampBridgePierDiameterBlocks(12, 6));
        assertEquals(4, RoadParameterLimits.clampBridgePierDiameterStored(9));
        assertArrayEquals(new int[] {2, 4}, RoadParameterLimits.validBridgePierDiameters(12));
        assertArrayEquals(new int[] {1, 3}, RoadParameterLimits.validBridgePierDiameters(11));
        // 双柱在 W=8 时直径 4 放不下最小空隙，应收束到 2
        assertArrayEquals(
            new int[] {2},
            RoadParameterLimits.validBridgePierDiameters(8, BridgePierStyle.DOUBLE));
        assertEquals(
            2,
            RoadParameterLimits.clampBridgePierDiameterBlocks(8, 4, BridgePierStyle.DOUBLE));
    }

    @Test
    void doubleGapMatchesCarriagewayParity() {
        for (int width : new int[] {5, 6, 7, 8, 9}) {
            int diameter = RoadParameterLimits.clampBridgePierDiameterBlocks(
                width, width / 2, BridgePierStyle.DOUBLE);
            int minOffset = RoadParameterLimits.minDoublePierOffsetBlocks(width, diameter);
            int gap = RoadParameterLimits.doublePierGapBlocks(minOffset, diameter);
            assertEquals(width & 1, gap & 1, "gap parity for width " + width);
            assertTrue(gap >= ((width & 1) == 1 ? 1 : 2));
        }
    }

    @Test
    void doubleOffsetKeepsGapAtLeastMinimum() {
        int diameter = RoadParameterLimits.clampBridgePierDiameterBlocks(
            9, 3, BridgePierStyle.DOUBLE);
        int minOffset = RoadParameterLimits.minDoublePierOffsetBlocks(9, diameter);
        assertEquals(2, minOffset);
        assertEquals(1, RoadParameterLimits.doublePierGapBlocks(minOffset, diameter));

        int clampedLow = RoadParameterLimits.clampBridgePierOffsetBlocks(9, diameter, 1);
        assertTrue(clampedLow >= minOffset);
        assertTrue(RoadParameterLimits.doublePierGapBlocks(clampedLow, diameter) >= 1);
    }

    @Test
    void legacyPierStyleIdsMigrate() {
        assertEquals(BridgePierStyle.SINGLE, BridgePierStyle.fromId("AUTO"));
        assertEquals(BridgePierStyle.DOUBLE, BridgePierStyle.fromId("MULTI"));
        assertEquals(BridgePierStyle.SINGLE, BridgePierStyle.fromStoredOrDefault(null));
        assertEquals(BridgePierStyle.DOUBLE, BridgePierStyle.fromStoredOrDefault("multi"));
    }

    @Test
    void pierFootprintSnapsToCarriagewayGrid() {
        for (int width : new int[] {5, 6, 7, 8, 9, 10}) {
            int roadMin = RoadDimensionUtils.minLateralOffset(width);
            int roadMax = RoadDimensionUtils.maxLateralOffset(width);
            int singleDiameter = RoadParameterLimits.clampBridgePierDiameterBlocks(
                width, width / 2, BridgePierStyle.SINGLE);
            for (int absolute : RoadDimensionUtils.pierFootprintLaterals(0, singleDiameter)) {
                assertTrue(absolute >= roadMin && absolute <= roadMax,
                    "single pier lateral " + absolute + " outside width " + width);
            }
            int doubleDiameter = RoadParameterLimits.clampBridgePierDiameterBlocks(
                width, width / 2, BridgePierStyle.DOUBLE);
            int[] offsets = RoadParameterLimits.validBridgePierOffsets(width, doubleDiameter);
            for (int offset : offsets) {
                for (int sign : new int[] {-1, 1}) {
                    for (int absolute : RoadDimensionUtils.pierFootprintLaterals(
                            sign * offset, doubleDiameter)) {
                        assertTrue(absolute >= roadMin && absolute <= roadMax,
                            "double pier lateral " + absolute + " outside width " + width
                                + " offset " + offset);
                    }
                }
            }
        }
        // 偶数路宽单柱直径 2：占据中心两侧整数格，相对左缘为整格索引。
        assertArrayEquals(new int[] {0, 1}, RoadDimensionUtils.pierFootprintLaterals(0, 2));
        int roadMin6 = RoadDimensionUtils.minLateralOffset(6);
        assertEquals(-2, roadMin6);
        assertEquals(2, 0 - roadMin6);
        assertEquals(3, 1 - roadMin6);
    }
}
