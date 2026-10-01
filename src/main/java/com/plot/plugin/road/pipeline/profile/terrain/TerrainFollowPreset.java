package com.plot.plugin.road.pipeline.profile.terrain;

/** 地形跟随强度：控制纵断面趋势滤波的窗口大小（与 maxSlope 解耦）。 */
public enum TerrainFollowPreset {
    /** median ~15 m, moving average ~40 m, 5 relaxation passes */
    GENTLE(15.0, 40.0, 5, 0.35),
    /** median ~10 m, moving average ~30 m, 4 relaxation passes */
    STANDARD(10.0, 30.0, 4, 0.50),
    /** median ~5 m, moving average ~20 m, 3 relaxation passes */
    TIGHT(5.0, 20.0, 3, 0.65);

    private final double medianWindowMeters;
    private final double movingAverageWindowMeters;
    private final int relaxationIterations;
    private final double trendBlendWeight;

    TerrainFollowPreset(
            double medianWindowMeters,
            double movingAverageWindowMeters,
            int relaxationIterations,
            double trendBlendWeight) {
        this.medianWindowMeters = medianWindowMeters;
        this.movingAverageWindowMeters = movingAverageWindowMeters;
        this.relaxationIterations = relaxationIterations;
        this.trendBlendWeight = trendBlendWeight;
    }

    public double medianWindowMeters() {
        return medianWindowMeters;
    }

    public double movingAverageWindowMeters() {
        return movingAverageWindowMeters;
    }

    public int relaxationIterations() {
        return relaxationIterations;
    }

    public double trendBlendWeight() {
        return trendBlendWeight;
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
