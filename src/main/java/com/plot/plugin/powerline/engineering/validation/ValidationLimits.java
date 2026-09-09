package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 常识性线路检查阈值（跟随玩家 Route/Style 间距）。 */
public record ValidationLimits(
        double maximumSpan,
        double minimumSpan,
        double minimumGroundClearance,
        double wireOverlapThreshold,
        double suspensionAngleWarning) {

    /** 装饰性碰地阈值：约 1～1.5 格即视为穿山，不再用 6 格专业净空。 */
    public static final double DEFAULT_MIN_GROUND_CLEARANCE = 1.5;
    public static final double DEFAULT_MAX_SPAN = 40.0;
    /** 硬下限：杆距几乎叠在一起。 */
    public static final double ABSOLUTE_MIN_VISUAL_SPAN = 5.0;
    /** @deprecated 使用 {@link #ABSOLUTE_MIN_VISUAL_SPAN} 或线路 {@code minPoleSpacing} */
    @Deprecated
    public static final double DEFAULT_MIN_SPAN = ABSOLUTE_MIN_VISUAL_SPAN;
    public static final double DEFAULT_SUSPENSION_ANGLE = 5.0;
    public static final double DEFAULT_OVERLAP_THRESHOLD = 0.25;
    public static final double TOWER_PREFERRED_HEIGHT_MARGIN = 2.0;

    public static ValidationLimits fromFootprint(PowerLineFootprint footprint) {
        double configuredMax = footprint != null ? footprint.getMaxPoleSpacing() : 0.0;
        double configuredMin = footprint != null ? footprint.getMinPoleSpacing() : 0.0;
        double maxSpan = configuredMax > 0.0 ? configuredMax : DEFAULT_MAX_SPAN;
        double minSpan = configuredMin > 0.0 ? configuredMin : ABSOLUTE_MIN_VISUAL_SPAN;
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
