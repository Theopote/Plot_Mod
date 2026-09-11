package com.plot.plugin.powerline.design.parametric;

/** Profile 与世界包络交集后的有效高度范围。 */
public final class TowerParametricHeightLimits {
    public record EffectiveHeightRange(double min, double max, Double worldLimitedMax) {
    }

    private TowerParametricHeightLimits() {
    }

    public static EffectiveHeightRange heightRange(
            TowerParameterProfile profile,
            TowerParameterSet template,
            TowerBuildEnvelope envelope) {
        double min = profile.heightRange().min();
        double max = profile.heightRange().max();
        Double worldLimited = null;
        if (envelope != null && template != null) {
            worldLimited = maxAllowedHeight(profile, template, envelope);
            max = Math.min(max, worldLimited);
        }
        if (max < min) {
            max = min;
        }
        return new EffectiveHeightRange(min, max, worldLimited);
    }

    public static double maxAllowedHeight(
            TowerParameterProfile profile,
            TowerParameterSet template,
            TowerBuildEnvelope envelope) {
        if (profile == null || template == null || envelope == null) {
            return profile != null ? profile.heightRange().max() : Double.MAX_VALUE;
        }
        double profileMax = profile.heightRange().max();
        double profileMin = profile.heightRange().min();
        for (double height = profileMax; height >= profileMin; height -= 1.0) {
            TowerParameterSet candidate = new TowerParameterSet(
                height,
                template.baseWidth(),
                template.armSpan(),
                template.depthScale(),
                template.waistRatio(),
                template.density());
            TowerConstraintResult result = TowerParametricDesignFactory.resolveProfile(
                profile,
                candidate,
                envelope);
            boolean worldExceeded = result.issues().stream()
                .anyMatch(issue -> TowerConstraintSolver.CODE_WORLD_HEIGHT_EXCEEDED.equals(issue.code()));
            if (!worldExceeded) {
                return height;
            }
        }
        return profileMin;
    }
}
