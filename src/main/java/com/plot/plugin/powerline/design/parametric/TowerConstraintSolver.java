package com.plot.plugin.powerline.design.parametric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 对解析后的连续几何施加视觉/世界约束。 */
public final class TowerConstraintSolver {
    static final double MIN_BAY_HEIGHT = 3.0;
    static final double MAX_TAPER_SLOPE = 0.9;
    static final double MIN_ARM_VERTICAL_SPACING_FRACTION = 0.06;
    static final double MIN_ARM_VERTICAL_SPACING_ABSOLUTE = 3.0;
    static final double MAX_ARM_VERTICAL_SPACING_CAP = 8.0;

    public static final String CODE_WORLD_HEIGHT_EXCEEDED = "WORLD_HEIGHT_EXCEEDED";
    public static final String CODE_BAY_HEIGHT_TOO_SMALL = "BAY_HEIGHT_TOO_SMALL";
    public static final String CODE_ARM_VERTICAL_SPACING = "ARM_VERTICAL_SPACING";
    public static final String CODE_TAPER_TOO_STEEP = "TAPER_TOO_STEEP";

    private TowerConstraintSolver() {
    }

    public static TowerConstraintResult solve(
            ResolvedTowerParameters resolved,
            TowerParameterProfile profile) {
        return solve(resolved, profile, null);
    }

    public static TowerConstraintResult solve(
            ResolvedTowerParameters resolved,
            TowerParameterProfile profile,
            TowerBuildEnvelope envelope) {
        if (resolved == null || profile == null) {
            throw new IllegalArgumentException("resolved parameters and profile are required");
        }

        List<ConstraintIssue> issues = new ArrayList<>();
        List<ConstraintAdjustment> adjustments = new ArrayList<>(resolved.adjustments());

        List<ResolvedTowerStation> stations = resolved.stations().stream()
            .sorted(Comparator.comparingDouble(ResolvedTowerStation::height))
            .toList();
        for (int i = 1; i < stations.size(); i++) {
            double gap = stations.get(i).height() - stations.get(i - 1).height();
            if (gap < MIN_BAY_HEIGHT) {
                issues.add(new ConstraintIssue(
                    ConstraintSeverity.WARNING,
                    CODE_BAY_HEIGHT_TOO_SMALL,
                    "bay height " + gap + " < " + MIN_BAY_HEIGHT));
            }
            double heightDelta = gap;
            if (heightDelta > 0.0) {
                double slope = Math.abs(stations.get(i).halfWidth() - stations.get(i - 1).halfWidth()) / heightDelta;
                if (slope > MAX_TAPER_SLOPE) {
                    issues.add(new ConstraintIssue(
                        ConstraintSeverity.WARNING,
                        CODE_TAPER_TOO_STEEP,
                        "taper slope " + slope + " exceeds " + MAX_TAPER_SLOPE));
                }
            }
        }

        List<ResolvedTowerArm> arms = resolved.arms().stream()
            .sorted(Comparator.comparingDouble(ResolvedTowerArm::baseHeight))
            .toList();
        if (arms.size() >= 2) {
            double requiredSpacing = Math.min(
                MAX_ARM_VERTICAL_SPACING_CAP,
                Math.max(MIN_ARM_VERTICAL_SPACING_ABSOLUTE, resolved.height() * MIN_ARM_VERTICAL_SPACING_FRACTION));
            for (int i = 1; i < arms.size(); i++) {
                double spacing = arms.get(i).baseHeight() - arms.get(i - 1).baseHeight();
                if (spacing < requiredSpacing) {
                    issues.add(new ConstraintIssue(
                        ConstraintSeverity.ERROR,
                        CODE_ARM_VERTICAL_SPACING,
                        "arm spacing " + spacing + " < " + requiredSpacing));
                }
            }
        }

        if (envelope != null) {
            double requiredTop = resolved.requiredTopHeight();
            double available = envelope.availableLocalHeight();
            if (requiredTop > available) {
                issues.add(new ConstraintIssue(
                    ConstraintSeverity.ERROR,
                    CODE_WORLD_HEIGHT_EXCEEDED,
                    "required top " + requiredTop + " exceeds available " + available));
            }
        }

        return new TowerConstraintResult(resolved, adjustments, issues);
    }
}
