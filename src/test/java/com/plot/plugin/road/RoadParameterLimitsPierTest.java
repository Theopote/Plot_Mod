package com.plot.plugin.road;

import com.plot.plugin.road.model.section.BridgePierStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadParameterLimitsPierTest {

    @Test
    void singleDiameterMatchesCarriagewayParityAndCaps() {
        assertEquals(1, RoadParameterLimits.clampBridgePierDiameterBlocks(5, 2));
        assertEquals(3, RoadParameterLimits.clampBridgePierDiameterBlocks(7, 3));
        assertEquals(2, RoadParameterLimits.clampBridgePierDiameterBlocks(6, 1));
        assertArrayEquals(new int[] {2}, RoadParameterLimits.validBridgePierDiameters(6));
        assertArrayEquals(new int[] {1, 3}, RoadParameterLimits.validBridgePierDiameters(7));
        assertEquals(4, RoadParameterLimits.clampBridgePierDiameterBlocks(12, 6));
    }

    @Test
    void doubleOffsetIsVisibleGapWithWidthParity() {
        // 奇数路宽 → 空隙为奇数；偶数 → 偶数
        assertArrayEquals(new int[] {1, 3, 5}, RoadParameterLimits.validBridgePierOffsets(7));
        assertArrayEquals(new int[] {2, 4}, RoadParameterLimits.validBridgePierOffsets(6));
        assertEquals(1, RoadParameterLimits.clampBridgePierOffsetBlocks(9, 2));
        assertEquals(2, RoadParameterLimits.clampBridgePierOffsetBlocks(8, 3));
        for (int width : new int[] {5, 6, 7, 8, 9, 10}) {
            for (int gap : RoadParameterLimits.validBridgePierOffsets(width)) {
                assertEquals(width & 1, gap & 1, "gap parity for width " + width);
            }
        }
    }

    @Test
    void doubleDiameterRangeUsesGapNotCenterOffset() {
        // W=9, gap=1 → max = (9-1)/2 = 4
        assertEquals(4, RoadParameterLimits.maxDoublePierDiameterBlocks(9, 1));
        assertArrayEquals(
            new int[] {1, 2, 3, 4},
            RoadParameterLimits.validBridgePierDiameters(9, BridgePierStyle.DOUBLE, 1));
        // W=9, gap=3 → max = 3
        assertEquals(3, RoadParameterLimits.maxDoublePierDiameterBlocks(9, 3));
        // W=8, gap=2 → max = 3
        assertEquals(3, RoadParameterLimits.maxDoublePierDiameterBlocks(8, 2));
        assertEquals(
            3,
            RoadParameterLimits.clampBridgePierDiameterBlocks(8, 4, BridgePierStyle.DOUBLE, 2));
    }

    @Test
    void doublePierGapOneWithDiameterTwoLeavesOneBlockBetween() {
        // 用户反馈：偏移=1、直径=2 时应看到 1 格空隙，而不是粘成一团
        assertArrayEquals(
            new int[] {-2, -1, 1, 2},
            RoadDimensionUtils.doublePierFootprintLaterals(1, 2));
        // 偏移=3、直径=1：柱在 ±2，中间空隙恰为 3 格（-1,0,1）
        assertArrayEquals(
            new int[] {-2, 2},
            RoadDimensionUtils.doublePierFootprintLaterals(3, 1));
        int[] centers = RoadDimensionUtils.doublePierCenters(3, 1);
        assertArrayEquals(new int[] {-2, 2}, centers);
    }

    @Test
    void pierFootprintSnapsToCarriagewayGrid() {
        for (int width : new int[] {5, 6, 7, 8, 9, 10}) {
            int roadMin = RoadDimensionUtils.minLateralOffset(width);
            int roadMax = RoadDimensionUtils.maxLateralOffset(width);
            int singleDiameter = RoadParameterLimits.clampBridgePierDiameterBlocks(
                width, width / 2, BridgePierStyle.SINGLE);
            for (int absolute : RoadDimensionUtils.pierFootprintLaterals(0, singleDiameter)) {
                assertTrue(absolute >= roadMin && absolute <= roadMax);
            }
            for (int gap : RoadParameterLimits.validBridgePierOffsets(width)) {
                int diameter = RoadParameterLimits.clampBridgePierDiameterBlocks(
                    width, width / 2, BridgePierStyle.DOUBLE, gap);
                assertTrue(
                    RoadDimensionUtils.doublePierFitsCarriageway(width, gap, diameter),
                    "width=" + width + " gap=" + gap + " diameter=" + diameter);
            }
        }
    }

    @Test
    void legacyPierStyleIdsMigrate() {
        assertEquals(BridgePierStyle.SINGLE, BridgePierStyle.fromId("AUTO"));
        assertEquals(BridgePierStyle.DOUBLE, BridgePierStyle.fromId("MULTI"));
        assertEquals(BridgePierStyle.SINGLE, BridgePierStyle.fromStoredOrDefault(null));
        assertEquals(BridgePierStyle.DOUBLE, BridgePierStyle.fromStoredOrDefault("multi"));
    }
}
