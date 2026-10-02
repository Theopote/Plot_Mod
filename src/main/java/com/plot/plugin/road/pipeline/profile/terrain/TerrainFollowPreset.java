package com.plot.plugin.road.pipeline.profile.terrain;

/** 地形跟随强度：控制纵断面趋势滤波的窗口大小（与 maxSlope 解耦）。 */
public enum TerrainFollowPreset {
    /** median ~15 m, moving average ~40 m, 5 relaxation passes, strong grade smoothing */
    GENTLE(15.0, 40.0, 50.0, 16.0, 5, 0.35, 0.55, 3, 2.5),
    /** median ~10 m, moving average ~30 m, 4 relaxation passes */
    STANDARD(10.0, 30.0, 35.0, 12.0, 4, 0.50, 0.40, 2, 4.0),
    /** median ~5 m, moving average ~20 m, 3 relaxation passes, lighter grade smoothing */
    TIGHT(5.0, 20.0, 25.0, 8.0, 3, 0.65, 0.25, 1, 6.0);

    private final double medianWindowMeters;
    private final double movingAverageWindowMeters;
    /** Minimum horizontal length to spread raw terrain steps in the trend line. */
    private final double stepTransitionMeters;
    /** Minimum horizontal length to spread design grade changes at crests / sags. */
    private final double minGradeTransitionMeters;
    private final int relaxationIterations;
    private final double trendBlendWeight;
    private final double gradeChangeSmoothingWeight;
    private final int gradeChangeSmoothingIterations;
    /** Hard cap on adjacent-segment grade delta (percent points); {@code 0} disables. */
    private final double maxGradeChangePercent;

    TerrainFollowPreset(
            double medianWindowMeters,
            double movingAverageWindowMeters,
            double stepTransitionMeters,
            double minGradeTransitionMeters,
            int relaxationIterations,
            double trendBlendWeight,
            double gradeChangeSmoothingWeight,
            int gradeChangeSmoothingIterations,
            double maxGradeChangePercent) {
        this.medianWindowMeters = medianWindowMeters;
        this.movingAverageWindowMeters = movingAverageWindowMeters;
        this.stepTransitionMeters = stepTransitionMeters;
        this.minGradeTransitionMeters = minGradeTransitionMeters;
        this.relaxationIterations = relaxationIterations;
        this.trendBlendWeight = trendBlendWeight;
        this.gradeChangeSmoothingWeight = gradeChangeSmoothingWeight;
        this.gradeChangeSmoothingIterations = gradeChangeSmoothingIterations;
        this.maxGradeChangePercent = maxGradeChangePercent;
    }

    public double medianWindowMeters() {
        return medianWindowMeters;
    }

    public double movingAverageWindowMeters() {
        return movingAverageWindowMeters;
    }

    public double stepTransitionMeters() {
        return stepTransitionMeters;
    }

    public double minGradeTransitionMeters() {
        return minGradeTransitionMeters;
    }

    public int relaxationIterations() {
        return relaxationIterations;
    }

    public double trendBlendWeight() {
        return trendBlendWeight;
    }

    /** Blend toward equal-grade vertices; higher values yield longer, steadier slopes. */
    public double gradeChangeSmoothingWeight() {
        return gradeChangeSmoothingWeight;
    }

    public int gradeChangeSmoothingIterations() {
        return gradeChangeSmoothingIterations;
    }

    public double maxGradeChangePercent() {
        return maxGradeChangePercent;
    }

    public static TerrainFollowPreset fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
