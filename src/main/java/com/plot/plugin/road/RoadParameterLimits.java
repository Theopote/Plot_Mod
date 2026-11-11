package com.plot.plugin.road;

import com.plot.core.terrain.TerrainSampler;
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
    public static final int MIN_TUNNEL_THRESHOLD = 1;
    public static final int MAX_TUNNEL_THRESHOLD = 30;
    public static final double MIN_PATH_SAMPLE_DISTANCE = 0.5;
    public static final double MAX_PATH_SAMPLE_DISTANCE = 5.0;
    /** 边坡水平开挖/填筑最大跑距（格）：路径面板滑动条。 */
    public static final int MIN_SLOPE_BATTER_RUN_BLOCKS = 5;
    public static final int MAX_SLOPE_BATTER_RUN_BLOCKS = 50;
    public static final int DEFAULT_SLOPE_BATTER_RUN_BLOCKS = 32;

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

}
