package com.plot.plugin.road;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;

/**
 * 道路插件参数合法范围与 clamp 工具（UI 与模型共用）。
 */
public final class RoadParameterLimits {
    /** 单车道最小可行驶宽为 1；总宽由车道推导，允许窄到 1。 */
    public static final int MIN_CARRIAGEWAY_WIDTH = 1;
    public static final int MAX_CARRIAGEWAY_WIDTH = 24;
    public static final int MIN_LANE_COUNT = 1;
    public static final int MAX_LANE_COUNT = 4;
    /** 单车道行驶宽度（格）；总宽由车道×宽 + 分隔占格推导。 */
    public static final int MIN_LANE_WIDTH_BLOCKS = 1;
    public static final int MAX_LANE_WIDTH_BLOCKS = 6;
    public static final int DEFAULT_LANE_WIDTH_BLOCKS = 3;
    public static final int MIN_STRIP_WIDTH = 1;
    public static final int MAX_STRIP_WIDTH = 3;
    /** 路表中央隔离带高度（路面以上格数）。 */
    public static final int MIN_MEDIAN_HEIGHT_BLOCKS = 1;
    public static final int MAX_MEDIAN_HEIGHT_BLOCKS = 3;
    public static final int DEFAULT_MEDIAN_HEIGHT_BLOCKS = 1;
    public static final String DEFAULT_MEDIAN_MATERIAL = "minecraft:oak_leaves";
    /** 行车道宽 ≤ 此值时桥墩仅允许单柱。 */
    public static final int MAX_CARRIAGEWAY_WIDTH_FOR_DOUBLE_PIER = 2;
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
    public static final double MIN_BRIDGE_PIER_SPACING_BLOCKS = 6.0;
    public static final double MAX_BRIDGE_PIER_SPACING_BLOCKS = 40.0;
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

    /** 单车道行驶宽度上限（与总宽解耦）。 */
    public static int maxPerLaneWidth(int carriagewayWidth, int laneCount) {
        return MAX_LANE_WIDTH_BLOCKS;
    }

    public static int clampLaneTravelWidth(int laneWidth) {
        return Math.clamp(laneWidth, MIN_LANE_WIDTH_BLOCKS, MAX_LANE_WIDTH_BLOCKS);
    }

    /** @deprecated 使用 {@link #clampLaneTravelWidth(int)}；保留重载以免旧调用断裂。 */
    @Deprecated
    public static int clampLaneWidth(int laneWidth, int carriagewayWidth, int laneCount) {
        return clampLaneTravelWidth(laneWidth);
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

    /** 行车道过窄时双柱无法布置。 */
    public static boolean allowsDoublePier(int carriagewayWidth) {
        return Math.max(1, carriagewayWidth) > MAX_CARRIAGEWAY_WIDTH_FOR_DOUBLE_PIER;
    }

    /**
     * 单柱最大直径：默认 ≤⌊W/2⌋，且不超过路宽与绝对上限。
     * 偶数路宽必须能取到偶数直径才能居中；W=2 时 ⌊W/2⌋=1 会破坏奇偶，抬到 2。
     */
    public static int maxSinglePierDiameterBlocks(int carriagewayWidth) {
        int width = Math.max(1, carriagewayWidth);
        int byHalf = Math.clamp(width / 2, MIN_BRIDGE_PIER_DIAMETER_BLOCKS, MAX_BRIDGE_PIER_DIAMETER_BLOCKS);
        if ((width & 1) == 0) {
            byHalf = Math.max(byHalf, Math.min(2, width));
        }
        return Math.min(byHalf, Math.min(width, MAX_BRIDGE_PIER_DIAMETER_BLOCKS));
    }

    /**
     * 中央隔离带宽度：与单柱直径同一套奇偶/上限（落在路表中心，不占行车道宽）。
     */
    public static int clampMedianWidthBlocks(int carriagewayWidth, int medianWidth) {
        return clampBridgePierDiameterBlocks(carriagewayWidth, medianWidth, BridgePierStyle.SINGLE);
    }

    public static int[] validMedianWidths(int carriagewayWidth) {
        return validBridgePierDiameters(carriagewayWidth, BridgePierStyle.SINGLE);
    }

    public static int clampMedianHeightBlocks(int height) {
        return Math.clamp(height, MIN_MEDIAN_HEIGHT_BLOCKS, MAX_MEDIAN_HEIGHT_BLOCKS);
    }

    /** 与行车道同奇偶的最小单柱直径（奇数路宽→1，偶数路宽→2）。 */
    public static int minBridgePierDiameterBlocks(int carriagewayWidth) {
        int width = Math.max(1, carriagewayWidth);
        return (width & 1) == 1 ? 1 : Math.min(2, width);
    }

    /** 将上限收束到与路宽同奇偶（不小于最小直径时）。 */
    private static int parityAlignedMaxDiameter(int width, int rawMax) {
        int min = minBridgePierDiameterBlocks(width);
        int max = Math.max(min, rawMax);
        if ((max & 1) != (width & 1) && max > min) {
            max--;
        }
        return max;
    }

    /** 将整数收束到与 {@code paritySource} 同奇偶，落在 [min, max] 内。 */
    private static int snapToParity(int value, int paritySource, int min, int max) {
        int want = paritySource & 1;
        int v = Math.clamp(value, min, max);
        if ((v & 1) == want) {
            return v;
        }
        if (v - 1 >= min && ((v - 1) & 1) == want) {
            return v - 1;
        }
        if (v + 1 <= max && ((v + 1) & 1) == want) {
            return v + 1;
        }
        return (min & 1) == want ? min : Math.min(max, min + 1);
    }

    /**
     * 钳制柱直径：单柱与路宽同奇偶，落在
     * [{@link #minBridgePierDiameterBlocks}, {@link #maxSinglePierDiameterBlocks}]。
     */
    public static int clampBridgePierDiameterBlocks(int carriagewayWidth, int diameter) {
        return clampBridgePierDiameterBlocks(carriagewayWidth, diameter, BridgePierStyle.SINGLE);
    }

    /**
     * 钳制柱直径。双柱不跟路宽奇偶同步，需配合
     * {@link #clampBridgePierDiameterBlocks(int, int, BridgePierStyle, int)} 传入偏移。
     */
    public static int clampBridgePierDiameterBlocks(
            int carriagewayWidth,
            int diameter,
            BridgePierStyle style) {
        if (style == BridgePierStyle.DOUBLE) {
            int offset = minDoublePierOffsetBlocks(carriagewayWidth);
            return clampBridgePierDiameterBlocks(carriagewayWidth, diameter, style, offset);
        }
        int width = Math.max(1, carriagewayWidth);
        int min = minBridgePierDiameterBlocks(width);
        int max = parityAlignedMaxDiameter(width, maxSinglePierDiameterBlocks(width));
        if (max < min) {
            return min;
        }
        return snapToParity(diameter, width, min, max);
    }

    /**
     * 双柱直径：范围 {@code 1..min(4, ⌊(W − 柱间空隙) / 2⌋)}，不与路宽奇偶同步。
     * {@code offsetBlocks} 语义为两柱之间的空隙格数（UI「双柱偏移」）。
     */
    public static int clampBridgePierDiameterBlocks(
            int carriagewayWidth,
            int diameter,
            BridgePierStyle style,
            int offsetBlocks) {
        if (style != BridgePierStyle.DOUBLE) {
            return clampBridgePierDiameterBlocks(carriagewayWidth, diameter, BridgePierStyle.SINGLE);
        }
        int width = Math.max(1, carriagewayWidth);
        int gap = clampBridgePierOffsetBlocks(width, offsetBlocks);
        int max = maxDoublePierDiameterBlocks(width, gap);
        return Math.clamp(diameter, MIN_BRIDGE_PIER_DIAMETER_BLOCKS, max);
    }

    /**
     * 双柱最大直径：{@code ⌊(行车道宽 − 柱间空隙) / 2⌋}，再夹到绝对上限 4，
     * 并保证左右柱足迹不越出行车道。
     */
    public static int maxDoublePierDiameterBlocks(int carriagewayWidth, int gapBlocks) {
        int width = Math.max(1, carriagewayWidth);
        int gap = clampBridgePierOffsetBlocks(width, gapBlocks);
        int byFormula = Math.max(1, (width - gap) / 2);
        int max = Math.min(MAX_BRIDGE_PIER_DIAMETER_BLOCKS, byFormula);
        while (max > MIN_BRIDGE_PIER_DIAMETER_BLOCKS
                && !RoadDimensionUtils.doublePierFitsCarriageway(width, gap, max)) {
            max--;
        }
        return Math.max(MIN_BRIDGE_PIER_DIAMETER_BLOCKS, max);
    }

    /** 双柱直径依赖柱间空隙；未指定时按最小合法空隙估算上限。 */
    public static int maxDoublePierDiameterBlocks(int carriagewayWidth) {
        return maxDoublePierDiameterBlocks(
            carriagewayWidth, minDoublePierOffsetBlocks(carriagewayWidth));
    }

    /** 合法单柱直径序列（同奇偶，步长 2）。 */
    public static int[] validBridgePierDiameters(int carriagewayWidth) {
        return validBridgePierDiameters(carriagewayWidth, BridgePierStyle.SINGLE);
    }

    public static int[] validBridgePierDiameters(int carriagewayWidth, BridgePierStyle style) {
        if (style == BridgePierStyle.DOUBLE) {
            return validBridgePierDiameters(
                carriagewayWidth, style, minDoublePierOffsetBlocks(carriagewayWidth));
        }
        int min = minBridgePierDiameterBlocks(carriagewayWidth);
        int max = clampBridgePierDiameterBlocks(
            carriagewayWidth, maxSinglePierDiameterBlocks(carriagewayWidth), BridgePierStyle.SINGLE);
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

    /** 合法双柱直径序列：1..max，连续整数。 */
    public static int[] validBridgePierDiameters(
            int carriagewayWidth,
            BridgePierStyle style,
            int offsetBlocks) {
        if (style != BridgePierStyle.DOUBLE) {
            return validBridgePierDiameters(carriagewayWidth, BridgePierStyle.SINGLE);
        }
        int max = maxDoublePierDiameterBlocks(carriagewayWidth, offsetBlocks);
        int[] values = new int[max];
        for (int i = 0; i < max; i++) {
            values[i] = i + 1;
        }
        return values;
    }

    public static Integer clampBridgePierDiameterBlocksNullable(int carriagewayWidth, Integer diameter) {
        if (diameter == null) {
            return null;
        }
        return clampBridgePierDiameterBlocks(carriagewayWidth, diameter);
    }

    /**
     * 「双柱偏移」即柱间空隙格数；保留此方法供旧提示文案兼容，返回空隙本身。
     */
    public static int doublePierGapBlocks(int offsetBlocks, int diameterBlocks) {
        return Math.max(0, offsetBlocks);
    }

    /**
     * 双柱最小柱间空隙：与行车道宽同奇偶（奇数路宽→1，偶数→2），保证至少一格可见空隙。
     */
    public static int minDoublePierOffsetBlocks(int carriagewayWidth) {
        int width = Math.max(1, carriagewayWidth);
        return (width & 1) == 1 ? 1 : 2;
    }

    /** 兼容旧调用：空隙主约束与直径解耦，忽略 diameterBlocks。 */
    public static int minDoublePierOffsetBlocks(int carriagewayWidth, int diameterBlocks) {
        return minDoublePierOffsetBlocks(carriagewayWidth);
    }

    /**
     * 双柱最大柱间空隙：与路宽同奇偶；至少两侧各留 1 格柱径（{@code 2×1 + 空隙 ≤ W}）。
     */
    public static int maxDoublePierOffsetBlocks(int carriagewayWidth) {
        return maxDoublePierOffsetBlocks(carriagewayWidth, MIN_BRIDGE_PIER_DIAMETER_BLOCKS);
    }

    /** 在路宽奇偶约束上，再按给定直径收紧最大空隙。 */
    public static int maxDoublePierOffsetBlocks(int carriagewayWidth, int diameterBlocks) {
        int width = Math.max(1, carriagewayWidth);
        int min = minDoublePierOffsetBlocks(width);
        int diameter = Math.clamp(
            diameterBlocks, MIN_BRIDGE_PIER_DIAMETER_BLOCKS, MAX_BRIDGE_PIER_DIAMETER_BLOCKS);
        int max = width - 2 * diameter;
        max = snapToParity(max, width, min, Math.max(min, max));
        while (max > min && !RoadDimensionUtils.doublePierFitsCarriageway(width, max, diameter)) {
            max -= 2;
        }
        if (!RoadDimensionUtils.doublePierFitsCarriageway(width, max, diameter) && max >= min) {
            // 回退到能放下的最大同奇偶空隙
            for (int g = max; g >= min; g -= 2) {
                if (RoadDimensionUtils.doublePierFitsCarriageway(width, g, diameter)) {
                    return g;
                }
            }
            return min;
        }
        return Math.max(min, max);
    }

    /**
     * 钳制双柱空隙（UI「双柱偏移」）：与行车道宽同奇偶。
     */
    public static int clampBridgePierOffsetBlocks(int carriagewayWidth, int offsetBlocks) {
        int width = Math.max(1, carriagewayWidth);
        int min = minDoublePierOffsetBlocks(width);
        int max = maxDoublePierOffsetBlocks(width);
        return snapToParity(offsetBlocks, width, min, max);
    }

    /**
     * 钳制双柱空隙；若给出直径，额外保证该直径足迹不越界。
     */
    public static int clampBridgePierOffsetBlocks(
            int carriagewayWidth,
            int diameterBlocks,
            int offsetBlocks) {
        int width = Math.max(1, carriagewayWidth);
        int min = minDoublePierOffsetBlocks(width);
        int diameter = Math.clamp(
            diameterBlocks, MIN_BRIDGE_PIER_DIAMETER_BLOCKS, MAX_BRIDGE_PIER_DIAMETER_BLOCKS);
        int max = maxDoublePierOffsetBlocks(width, diameter);
        return snapToParity(offsetBlocks, width, min, max);
    }

    /** 合法双柱空隙序列（与路宽同奇偶，步长 2）。 */
    public static int[] validBridgePierOffsets(int carriagewayWidth) {
        int min = minDoublePierOffsetBlocks(carriagewayWidth);
        int max = maxDoublePierOffsetBlocks(carriagewayWidth);
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

    /** 合法双柱空隙；若给出直径则按足迹再收紧上限。 */
    public static int[] validBridgePierOffsets(int carriagewayWidth, int diameterBlocks) {
        int min = minDoublePierOffsetBlocks(carriagewayWidth);
        int max = maxDoublePierOffsetBlocks(carriagewayWidth, diameterBlocks);
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
