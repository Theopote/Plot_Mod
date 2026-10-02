package com.plot.plugin.road.profile;

/**
 * 纵断面质量摘要：基于建造台阶与地形的估算指标（预览级，非最终土方结算）。
 */
public record RoadProfileQualityMetrics(
        int cutBlockColumns,
        int fillBlockColumns,
        double maxBuildGradePercent,
        double maxGradeChangePercent,
        double longestConstantGradeRun,
        int longestFlatRun,
        int stepCount,
        int abnormalStepCount,
        double maxDesignBuildDeviation,
        double cumulativeGradeError) {

    private static final RoadProfileQualityMetrics EMPTY = new RoadProfileQualityMetrics(
        0, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0.0, 0.0);

    public static RoadProfileQualityMetrics empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public int earthworkBlockColumns() {
        return cutBlockColumns + fillBlockColumns;
    }

    public boolean exceedsMaxGrade(double maxGradePercent) {
        return maxGradePercent > 0.0 && maxBuildGradePercent > maxGradePercent + 1e-6;
    }

    public boolean exceedsMaxGradeChange(double limitPercent) {
        return limitPercent > 0.0 && maxGradeChangePercent > limitPercent + 1e-6;
    }

    public boolean hasAbnormalSteps() {
        return abnormalStepCount > 0;
    }

    public boolean hasDesignBuildMismatch() {
        return maxDesignBuildDeviation > 1.0;
    }
}
