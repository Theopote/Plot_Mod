package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;

/**
 * Classic Double Arm 参数化编译入口（与 {@code TowerStructurePresets} 并行存在，不替换旧预设）。
 */
public final class TowerParametricDesignFactory {
    private TowerParametricDesignFactory() {
    }

    public static TowerConstraintResult resolveClassic(TowerParameterSet parameters) {
        return resolveClassic(parameters, null);
    }

    public static TowerConstraintResult resolveClassic(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.classicDoubleArm(), parameters, envelope);
    }

    public static TowerConstraintResult resolveProfile(
            TowerParameterProfile profile,
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        ResolvedTowerParameters resolved = TowerParameterResolver.resolve(profile, parameters);
        return TowerConstraintSolver.solve(resolved, profile, envelope);
    }

    public static PoleDesign compileClassicDoubleArm(TowerParameterSet parameters) {
        return compileClassicDoubleArm(parameters, null);
    }

    public static PoleDesign compileClassicDoubleArm(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        TowerConstraintResult result = resolveClassic(parameters, envelope);
        if (result.hasErrors()) {
            String codes = result.issues().stream()
                .filter(issue -> issue.severity() == ConstraintSeverity.ERROR)
                .map(ConstraintIssue::code)
                .reduce((a, b) -> a + ", " + b)
                .orElse("UNKNOWN");
            throw new IllegalStateException("Cannot compile parametric tower: " + codes);
        }
        return TowerStructureCompiler.compile(TowerParameterProfiles.classicDoubleArm(), result.resolved());
    }
}
