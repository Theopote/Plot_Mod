package com.plot.plugin.road;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;

/**
 * 道路插件参数合法范围与 clamp 工具（UI 与模型共用）。
 */
public final class RoadParameterLimits {
    public static final int MIN_CARRIAGEWAY_WIDTH = 3;
    public static final int MAX_CARRIAGEWAY_WIDTH = 24;
    public static final int MIN_LANE_COUNT = 1;
    public static final int MAX_LANE_COUNT = 4;
    public static final int MIN_STRIP_WIDTH = 1;
    public static final int MAX_STRIP_WIDTH = 3;
    /** Inclusive minimum placeable block Y for default overworld fallback. */
    public static final int ELEVATION_MIN = TerrainSampler.DEFAULT_WORLD_BOTTOM_Y;
    /** Inclusive maximum placeable block Y for default overworld fallback. */
    public static final int ELEVATION_MAX = TerrainSampler.DEFAULT_WORLD_TOP_EXCLUSIVE_Y - 1;
    public static final int MIN_CROSSING_CLEARANCE = 1;
    /** 与隧道净高上限对齐，立交净空可高于下穿道路隧道净高。 */
    public static final int MAX_CROSSING_CLEARANCE = 12;
    public static final int STREETLIGHT_DISABLED = 0;
    public static final int MIN_STREETLIGHT_SPACING = 8;
    public static final int MAX_STREETLIGHT_SPACING = 50;
    public static final int MIN_BRIDGE_THRESHOLD = 1;
    public static final int MAX_BRIDGE_THRESHOLD = 20;
    /** 与 {@link com.plot.plugin.road.terrain.RoadTerrainStyle#BALANCED} 风格表一致。 */
    public static final int DEFAULT_BRIDGE_THRESHOLD = 3;
    public static final int MIN_TUNNEL_THRESHOLD = 1;
    public static final int MAX_TUNNEL_THRESHOLD = 30;
    public static final double MIN_PATH_SAMPLE_DISTANCE = 0.5;
    public static final double MAX_PATH_SAMPLE_DISTANCE = 5.0;
    /** 边坡水平开挖/填筑最大跑距（格）：路径面板滑动条。 */
    public static final int MIN_SLOPE_BATTER_RUN_BLOCKS = 5;
    public static final int MAX_SLOPE_BATTER_RUN_BLOCKS = 50;
    public static final int DEFAULT_SLOPE_BATTER_RUN_BLOCKS = 32;
    /** 桥墩间距（世界格）。 */
    public static final double MIN_BRIDGE_PIER_SPACING_BLOCKS = 3.0;
    public static final double MAX_BRIDGE_PIER_SPACING_BLOCKS = 24.0;
    public static final double DEFAULT_BRIDGE_PIER_SPACING_BLOCKS = 6.0;
    /** 桥墩柱截面横向直径（格）；与行车道宽同奇偶，绝对范围 [1, 4]。 */
    public static final int MIN_BRIDGE_PIER_DIAMETER_BLOCKS = 1;
    public static final int MAX_BRIDGE_PIER_DIAMETER_BLOCKS = 4;
    public static final int DEFAULT_BRIDGE_PIER_DIAMETER_BLOCKS = 1;
    /** 双柱：中心线到柱心的默认偏移占位；实际解析时按直径与空隙重算。 */
    public static final int DEFAULT_BRIDGE_PIER_OFFSET_BLOCKS = 1;
    /** 桥梁护栏高度（桥面以上格数）。 */
    public static final int MIN_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS = 1;
    public static final int MAX_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS = 4;
    public static final int DEFAULT_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS = 1;

    private RoadParameterLimits() {
    }

    public static int clampCarriagewayWidth(int width) {
        return Math.clamp(width, MIN_CARRIAGEWAY_WIDTH, MAX_CARRIAGEWAY_WIDTH);
    }

    public static int clampLaneCount(int laneCount) {
        return Math.clamp(laneCount, MIN_LANE_COUNT, MAX_LANE_COUNT);
    }

    public static int maxPerLaneWidth(int carriagewayWidth, int laneCount) {
        int lanes = Math.max(MIN_LANE_COUNT, clampLaneCount(laneCount));
        return Math.max(MIN_STRIP_WIDTH, clampCarriagewayWidth(carriagewayWidth) / lanes);
    }

    public static int clampLaneWidth(int laneWidth, int carriagewayWidth, int laneCount) {
        return Math.max(MIN_STRIP_WIDTH, Math.min(maxPerLaneWidth(carriagewayWidth, laneCount), laneWidth));
    }

    public static int clampStripWidth(int width) {
        return Math.clamp(width, MIN_STRIP_WIDTH, MAX_STRIP_WIDTH);
    }

    public static int clampShoulderWidth(int width) {
        return Math.clamp(width, 0, MAX_STRIP_WIDTH);
    }

    public static double clampElevation(double elevation) {
        return elevationBounds().clamp(elevation);
    }

    public static RoadElevationBounds elevationBounds() {
        return RoadWorldElevationBounds.fallback();
    }

    public static Double clampManualElevation(Double elevation) {
        if (elevation == null) {
            return null;
        }
        return clampElevation(elevation);
    }

    public static double clampCrossingClearance(double clearance) {
        return Math.clamp(clearance, MIN_CROSSING_CLEARANCE, MAX_CROSSING_CLEARANCE);
    }

    public static Double clampCrossingClearanceNullable(Double clearance) {
        if (clearance == null) {
            return null;
        }
        return clampCrossingClearance(clearance);
    }

    public static float clampGradePercent(float percent) {
        return SlopeFormatUtils.clampGradePercent(percent);
    }

    public static Integer normalizeStreetlightSpacing(Integer spacing) {
        if (spacing == null || spacing <= STREETLIGHT_DISABLED) {
            return null;
        }
        return Math.clamp(spacing, MIN_STREETLIGHT_SPACING, MAX_STREETLIGHT_SPACING);
    }

    public static int clampSlopeBatterRunBlocks(int blocks) {
        return Math.clamp(blocks, MIN_SLOPE_BATTER_RUN_BLOCKS, MAX_SLOPE_BATTER_RUN_BLOCKS);
    }

    public static double clampBridgePierSpacingBlocks(double blocks) {
        return Math.clamp(blocks, MIN_BRIDGE_PIER_SPACING_BLOCKS, MAX_BRIDGE_PIER_SPACING_BLOCKS);
    }

    public static Double clampBridgePierSpacingBlocksNullable(Double blocks) {
        if (blocks == null) {
            return null;
        }
        return clampBridgePierSpacingBlocks(blocks);
    }

    public static int clampBridgeGuardrailHeightBlocks(int blocks) {
        return Math.clamp(blocks, MIN_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS, MAX_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS);
    }

    public static Integer clampBridgeGuardrailHeightBlocksNullable(Integer blocks) {
        if (blocks == null) {
            return null;
        }
        return clampBridgeGuardrailHeightBlocks(blocks);
    }

    public static int clampBridgeThreshold(int blocks) {
        return Math.clamp(blocks, MIN_BRIDGE_THRESHOLD, MAX_BRIDGE_THRESHOLD);
    }

    public static Integer clampBridgeThresholdNullable(Integer blocks) {
        if (blocks == null) {
            return null;
        }
        return clampBridgeThreshold(blocks);
    }

    /** 单柱最大直径：min(⌊行车道宽 / 2⌋, {@link #MAX_BRIDGE_PIER_DIAMETER_BLOCKS})，至少 1。 */
    public static int maxSinglePierDiameterBlocks(int carriagewayWidth) {
        int width = Math.max(1, carriagewayWidth);
        return Math.clamp(width / 2, MIN_BRIDGE_PIER_DIAMETER_BLOCKS, MAX_BRIDGE_PIER_DIAMETER_BLOCKS);
    }

    /** 与行车道同奇偶的最小直径；极窄偶数路宽无解时退回 1。 */
    public static int minBridgePierDiameterBlocks(int carriagewayWidth) {
        int width = Math.max(1, carriagewayWidth);
        int wantOdd = width & 1;
        if (wantOdd == 1) {
            return 1;
        }
        int max = parityAlignedMaxDiameter(width, maxSinglePierDiameterBlocks(width));
        return max >= 2 ? 2 : 1;
    }

    /** 将上限收束到与路宽同奇偶（不小于最小直径时）。 */
    private static int parityAlignedMaxDiameter(int width, int rawMax) {
        int min = (width & 1) == 1 ? 1 : (rawMax >= 2 ? 2 : 1);
        int max = Math.max(min, rawMax);
        if ((max & 1) != (width & 1) && max > min) {
            max--;
        }
        return max;
    }

    /**
     * 钳制柱直径：落在 [1, min(4, ⌊W/2⌋)]，并与行车道宽同奇偶（按单柱上限）。
     */
    public static int clampBridgePierDiameterBlocks(int carriagewayWidth, int diameter) {
        return clampBridgePierDiameterBlocks(carriagewayWidth, diameter, BridgePierStyle.SINGLE);
    }

    /**
     * 钳制柱直径。双柱时额外保证「最小空隙偏移 ≤ 外缘不越界的最大偏移」，
     * 避免直径过大导致双柱足迹半格错位或越出行车道。
     */
    public static int clampBridgePierDiameterBlocks(
            int carriagewayWidth,
            int diameter,
            BridgePierStyle style) {
        int width = Math.max(1, carriagewayWidth);
        int min = minBridgePierDiameterBlocks(width);
        int max = parityAlignedMaxDiameter(width, maxSinglePierDiameterBlocks(width));
        if (style == BridgePierStyle.DOUBLE) {
            max = maxDoublePierDiameterBlocks(width);
        }
        if (max < min) {
            return min;
        }
        int d = Math.clamp(diameter, min, max);
        int wantOdd = width & 1;
        if ((d & 1) == wantOdd) {
            return d;
        }
        if (d - 1 >= min && ((d - 1) & 1) == wantOdd) {
            return d - 1;
        }
        if (d + 1 <= max && ((d + 1) & 1) == wantOdd) {
            return d + 1;
        }
        return min;
    }

    /**
     * 双柱可用的最大直径：在同奇偶与半宽上限内，取仍能放下「两柱 + 最小空隙」的最大值。
     */
    public static int maxDoublePierDiameterBlocks(int carriagewayWidth) {
        int width = Math.max(1, carriagewayWidth);
        int min = minBridgePierDiameterBlocks(width);
        int max = parityAlignedMaxDiameter(width, maxSinglePierDiameterBlocks(width));
        for (int d = max; d >= min; d -= 2) {
            if (rawMaxDoublePierOffsetBlocks(width, d) >= minDoublePierOffsetForDiameter(width, d)) {
                return d;
            }
        }
        return min;
    }

    /** 合法直径序列（同奇偶，步长 2），供 UI 下拉/步进。 */
    public static int[] validBridgePierDiameters(int carriagewayWidth) {
        return validBridgePierDiameters(carriagewayWidth, BridgePierStyle.SINGLE);
    }

    public static int[] validBridgePierDiameters(int carriagewayWidth, BridgePierStyle style) {
        int min = minBridgePierDiameterBlocks(carriagewayWidth);
        int max = clampBridgePierDiameterBlocks(
            carriagewayWidth, maxSinglePierDiameterBlocks(carriagewayWidth), style);
        if (max < min) {
            return new int[] {min};
        }
        int count = ((max - min) / 2) + 1;
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = min + i * 2;
        }
        return values;
    }

    public static Integer clampBridgePierDiameterBlocksNullable(int carriagewayWidth, Integer diameter) {
        if (diameter == null) {
            return null;
        }
        return clampBridgePierDiameterBlocks(carriagewayWidth, diameter);
    }

    /** 双柱空隙 = 2×偏移 − 直径。 */
    public static int doublePierGapBlocks(int offsetBlocks, int diameterBlocks) {
        return 2 * offsetBlocks - diameterBlocks;
    }

    /** 给定已对齐直径时，满足最小空隙的最小偏移（不回夹直径，避免递归）。 */
    private static int minDoublePierOffsetForDiameter(int width, int diameter) {
        int minGap = (width & 1) == 1 ? 1 : 2;
        return (diameter + minGap + 1) / 2;
    }

    /** 柱外缘不越界的最大偏移（不回夹直径）。 */
    private static int rawMaxDoublePierOffsetBlocks(int width, int diameter) {
        int roadMax = RoadDimensionUtils.maxLateralOffset(width);
        int pierMax = RoadDimensionUtils.maxLateralOffset(diameter);
        int roadMin = RoadDimensionUtils.minLateralOffset(width);
        int pierMin = RoadDimensionUtils.minLateralOffset(diameter);
        int byRight = roadMax - pierMax;
        int byLeft = pierMin - roadMin;
        return Math.min(byRight, byLeft);
    }

    /**
     * 满足空隙 ≥ 1、且空隙与路宽同奇偶的最小偏移。
     * 因空隙 = 2×偏移 − 直径，直径已与路宽同奇偶时，最小空隙为 1（奇数路宽）或 2（偶数路宽）。
     */
    public static int minDoublePierOffsetBlocks(int carriagewayWidth, int diameterBlocks) {
        int width = Math.max(1, carriagewayWidth);
        int diameter = clampBridgePierDiameterBlocks(width, diameterBlocks, BridgePierStyle.DOUBLE);
        return minDoublePierOffsetForDiameter(width, diameter);
    }

    /**
     * 双柱最大偏移：柱外缘不超出行车道。
     * {@code offset + maxLateralOffset(diameter) ≤ maxLateralOffset(width)}，
     * 且左侧对称约束一并计入。
     */
    public static int maxDoublePierOffsetBlocks(int carriagewayWidth, int diameterBlocks) {
        int width = Math.max(1, carriagewayWidth);
        int diameter = clampBridgePierDiameterBlocks(width, diameterBlocks, BridgePierStyle.DOUBLE);
        int min = minDoublePierOffsetForDiameter(width, diameter);
        int max = rawMaxDoublePierOffsetBlocks(width, diameter);
        return Math.max(min, max);
    }

    /**
     * 钳制双柱偏移：空隙与路宽同奇偶且 ≥ 最小空隙，柱足迹落在行车道内。
     * 若直径过大导致区间为空，回退到不越界的最大偏移（缩小空隙），避免半格/越界。
     */
    public static int clampBridgePierOffsetBlocks(
            int carriagewayWidth,
            int diameterBlocks,
            int offsetBlocks) {
        int width = Math.max(1, carriagewayWidth);
        int diameter = clampBridgePierDiameterBlocks(width, diameterBlocks, BridgePierStyle.DOUBLE);
        int min = minDoublePierOffsetForDiameter(width, diameter);
        int rawMax = rawMaxDoublePierOffsetBlocks(width, diameter);
        if (rawMax < min) {
            return Math.max(1, rawMax);
        }
        return Math.clamp(offsetBlocks, min, rawMax);
    }

    /** 合法双柱偏移序列，供 UI 下拉/步进。 */
    public static int[] validBridgePierOffsets(int carriagewayWidth, int diameterBlocks) {
        int width = Math.max(1, carriagewayWidth);
        int diameter = clampBridgePierDiameterBlocks(width, diameterBlocks, BridgePierStyle.DOUBLE);
        int min = minDoublePierOffsetForDiameter(width, diameter);
        int rawMax = rawMaxDoublePierOffsetBlocks(width, diameter);
        if (rawMax < min) {
            return new int[] {Math.max(1, rawMax)};
        }
        int[] values = new int[rawMax - min + 1];
        for (int i = 0; i < values.length; i++) {
            values[i] = min + i;
        }
        return values;
    }

    public static Integer clampBridgePierOffsetBlocksNullable(
            int carriagewayWidth,
            int diameterBlocks,
            Integer offsetBlocks) {
        if (offsetBlocks == null) {
            return null;
        }
        return clampBridgePierOffsetBlocks(carriagewayWidth, diameterBlocks, offsetBlocks);
    }

    /** 存储态直径粗钳制（尚不知路宽时）；最终以行车道宽再钳。 */
    public static int clampBridgePierDiameterStored(int diameter) {
        return Math.clamp(
            diameter,
            MIN_BRIDGE_PIER_DIAMETER_BLOCKS,
            MAX_BRIDGE_PIER_DIAMETER_BLOCKS);
    }

    public static Integer clampBridgePierDiameterStoredNullable(Integer diameter) {
        if (diameter == null) {
            return null;
        }
        return clampBridgePierDiameterStored(diameter);
    }

    public static int clampBridgePierOffsetStored(int offset) {
        return Math.clamp(offset, 1, MAX_CARRIAGEWAY_WIDTH);
    }

    public static Integer clampBridgePierOffsetStoredNullable(Integer offset) {
        if (offset == null) {
            return null;
        }
        return clampBridgePierOffsetStored(offset);
    }

}
