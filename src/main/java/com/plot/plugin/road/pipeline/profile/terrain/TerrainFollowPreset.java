package com.plot.plugin.road.pipeline.profile.terrain;

/** 地形跟随强度：控制纵断面趋势滤波的窗口大小（与 maxSlope 解耦）。 */
public enum TerrainFollowPreset {
    /** median ~15 m, moving average ~40 m, 5 relaxation passes, strong grade smoothing */
    GENTLE(15.0, 40.0, 50.0, 16.0, 0.70, 5, 0.35, 0.55, 3, 2.5, 55.0, 4.0, 0.10),
    /** median ~10 m, moving average ~30 m, 4 relaxation passes */
    STANDARD(10.0, 30.0, 35.0, 12.0, 0.50, 4, 0.50, 0.40, 2, 4.0, 45.0, 3.0, 0.15),
    /** median ~5 m, moving average ~20 m, 3 relaxation passes, lighter grade smoothing */
    TIGHT(5.0, 20.0, 25.0, 8.0, 0.25, 3, 0.65, 0.25, 1, 6.0, 30.0, 2.0, 0.25);

    private final double medianWindowMeters;
    private final double movingAverageWindowMeters;
    /** Minimum horizontal length to spread raw terrain steps in the trend line. */
    private final double stepTransitionMeters;
    /** Minimum horizontal length to spread design grade changes at crests / sags. */
    private final double minGradeTransitionMeters;
    /** Blend toward fillFactor-based cut/fill balance offset on the design profile. */
    private final double cutFillBalanceWeight;
    private final int relaxationIterations;
    private final double trendBlendWeight;
    private final double gradeChangeSmoothingWeight;
    private final int gradeChangeSmoothingIterations;
    /** Hard cap on adjacent-segment grade delta (percent points); {@code 0} disables. */
    private final double maxGradeChangePercent;
    /** Maximum horizontal span for short oscillation merge clusters. */
    private final double maxOscillationSpanMeters;
    /** Maximum peak-to-trough amplitude (blocks) eligible for oscillation merge. */
    private final double maxOscillationAmplitudeBlocks;
    /** Minimum relative score improvement required to accept a merge. */
    private final double minOscillationMergeBenefit;

    TerrainFollowPreset(
            double medianWindowMeters,
            double movingAverageWindowMeters,
            double stepTransitionMeters,
            double minGradeTransitionMeters,
            double cutFillBalanceWeight,
            int relaxationIterations,
            double trendBlendWeight,
            double gradeChangeSmoothingWeight,
            int gradeChangeSmoothingIterations,
            double maxGradeChangePercent,
            double maxOscillationSpanMeters,
            double maxOscillationAmplitudeBlocks,
            double minOscillationMergeBenefit) {
        this.medianWindowMeters = medianWindowMeters;
        this.movingAverageWindowMeters = movingAverageWindowMeters;
        this.stepTransitionMeters = stepTransitionMeters;
        this.minGradeTransitionMeters = minGradeTransitionMeters;
        this.cutFillBalanceWeight = cutFillBalanceWeight;
        this.relaxationIterations = relaxationIterations;
        this.trendBlendWeight = trendBlendWeight;
        this.gradeChangeSmoothingWeight = gradeChangeSmoothingWeight;
        this.gradeChangeSmoothingIterations = gradeChangeSmoothingIterations;
        this.maxGradeChangePercent = maxGradeChangePercent;
        this.maxOscillationSpanMeters = maxOscillationSpanMeters;
        this.maxOscillationAmplitudeBlocks = maxOscillationAmplitudeBlocks;
        this.minOscillationMergeBenefit = minOscillationMergeBenefit;
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

    /** 0 disables; 1 applies the full fillFactor balance offset to the design profile. */
    public double cutFillBalanceWeight() {
        return cutFillBalanceWeight;
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

    public double maxOscillationSpanMeters() {
        return maxOscillationSpanMeters;
    }

    public double maxOscillationAmplitudeBlocks() {
        return maxOscillationAmplitudeBlocks;
    }

    public double minOscillationMergeBenefit() {
        return minOscillationMergeBenefit;
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
