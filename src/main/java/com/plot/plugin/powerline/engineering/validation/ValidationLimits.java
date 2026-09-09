package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 常识性线路检查阈值（跟随玩家 Route/Style 间距）。 */
public record ValidationLimits(
        double maximumSpan,
        double minimumSpan,
        double minimumGroundClearance,
        double wireOverlapThreshold,
        double suspensionAngleWarning) {

    public static final double DEFAULT_MIN_GROUND_CLEARANCE = 6.0;
    public static final double DEFAULT_MAX_SPAN = 40.0;
    public static final double DEFAULT_MIN_SPAN = 6.0;
    public static final double DEFAULT_SUSPENSION_ANGLE = 5.0;
    public static final double DEFAULT_OVERLAP_THRESHOLD = 0.25;
    public static final double TOWER_PREFERRED_HEIGHT_MARGIN = 2.0;

    public static ValidationLimits fromFootprint(PowerLineFootprint footprint) {
        double configuredMax = footprint != null ? footprint.getMaxPoleSpacing() : 0.0;
        double configuredMin = footprint != null ? footprint.getMinPoleSpacing() : 0.0;
        double maxSpan = configuredMax > 0.0 ? configuredMax : DEFAULT_MAX_SPAN;
        double minSpan = configuredMin > 0.0
            ? Math.min(configuredMin, DEFAULT_MIN_SPAN)
            : DEFAULT_MIN_SPAN;
        double suspensionAngle = footprint != null
            ? footprint.getCornerAngleThreshold()
            : DEFAULT_SUSPENSION_ANGLE;
        return new ValidationLimits(
            maxSpan,
            minSpan,
            DEFAULT_MIN_GROUND_CLEARANCE,
            DEFAULT_OVERLAP_THRESHOLD,
            suspensionAngle);
    }
}
