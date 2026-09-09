package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 常识性线路检查阈值（跟随玩家 Route/Style 间距，而非工程规范 profile）。 */
public record ValidationLimits(
        double maximumSpan,
        double minimumSpan,
        double minimumGroundClearance,
        double wireOverlapThreshold,
        double suspensionAngleWarning) {

    private static final double DEFAULT_OVERLAP_THRESHOLD = 0.25;

    public static ValidationLimits fromFootprint(PowerLineFootprint footprint, EngineeringRuleProfile profile) {
        EngineeringRuleProfile safeProfile = profile != null
            ? profile
            : com.plot.plugin.powerline.engineering.EngineeringRuleProfileCatalog.genericPlanning();
        double configuredMax = footprint != null ? footprint.getMaxPoleSpacing() : 0.0;
        double configuredMin = footprint != null ? footprint.getMinPoleSpacing() : 0.0;
        double maxSpan = configuredMax > 0.0
            ? configuredMax
            : safeProfile.getSpan().getMaximumSpan();
        double minSpan = configuredMin > 0.0
            ? Math.min(configuredMin, safeProfile.getSpan().getMinimumSpan())
            : safeProfile.getSpan().getMinimumSpan();
        return new ValidationLimits(
            maxSpan,
            minSpan,
            safeProfile.getClearance().getMinimumGroundClearance(),
            DEFAULT_OVERLAP_THRESHOLD,
            safeProfile.getAngle().getSuspensionMaxAngle());
    }
}
