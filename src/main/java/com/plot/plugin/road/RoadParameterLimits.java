package com.plot.plugin.road;

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
    public static final int ELEVATION_MIN = -64;
    public static final int ELEVATION_MAX = 320;
    public static final int MIN_CROSSING_CLEARANCE = 1;
    public static final int MAX_CROSSING_CLEARANCE = 10;
    public static final int STREETLIGHT_DISABLED = 0;
    public static final int MIN_STREETLIGHT_SPACING = 8;
    public static final int MAX_STREETLIGHT_SPACING = 50;
    public static final double MIN_CONTINUOUS_SLOPE_LENGTH = 5.0;
    public static final double MAX_CONTINUOUS_SLOPE_LENGTH = 100.0;
    public static final double MIN_RELAXED_SLOPE_LENGTH = 1.0;
    public static final double MAX_RELAXED_SLOPE_LENGTH = 30.0;

    private RoadParameterLimits() {
    }

    public static int clampCarriagewayWidth(int width) {
        return Math.max(MIN_CARRIAGEWAY_WIDTH, Math.min(MAX_CARRIAGEWAY_WIDTH, width));
    }

    public static int clampLaneCount(int laneCount) {
        return Math.max(MIN_LANE_COUNT, Math.min(MAX_LANE_COUNT, laneCount));
    }

    public static int maxPerLaneWidth(int carriagewayWidth, int laneCount) {
        int lanes = Math.max(MIN_LANE_COUNT, clampLaneCount(laneCount));
        return Math.max(MIN_STRIP_WIDTH, clampCarriagewayWidth(carriagewayWidth) / lanes);
    }

    public static int clampLaneWidth(int laneWidth, int carriagewayWidth, int laneCount) {
        return Math.max(MIN_STRIP_WIDTH, Math.min(maxPerLaneWidth(carriagewayWidth, laneCount), laneWidth));
    }

    public static int clampStripWidth(int width) {
        return Math.max(MIN_STRIP_WIDTH, Math.min(MAX_STRIP_WIDTH, width));
    }

    public static int clampShoulderWidth(int width) {
        return Math.max(0, Math.min(MAX_STRIP_WIDTH, width));
    }

    public static double clampElevation(double elevation) {
        return Math.max(ELEVATION_MIN, Math.min(ELEVATION_MAX, elevation));
    }

    public static Double clampManualElevation(Double elevation) {
        if (elevation == null) {
            return null;
        }
        return clampElevation(elevation);
    }

    public static double clampCrossingClearance(double clearance) {
        return Math.max(MIN_CROSSING_CLEARANCE, Math.min(MAX_CROSSING_CLEARANCE, clearance));
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
        return Math.max(MIN_STREETLIGHT_SPACING, Math.min(MAX_STREETLIGHT_SPACING, spacing));
    }

    public static double clampMaxContinuousSlopeLength(double length) {
        return Math.max(MIN_CONTINUOUS_SLOPE_LENGTH, Math.min(MAX_CONTINUOUS_SLOPE_LENGTH, length));
    }

    public static double clampRelaxedSlopeLength(double length, double maxContinuousLength) {
        double continuous = clampMaxContinuousSlopeLength(maxContinuousLength);
        double upper = Math.min(MAX_RELAXED_SLOPE_LENGTH, continuous);
        return Math.max(MIN_RELAXED_SLOPE_LENGTH, Math.min(upper, length));
    }
}
