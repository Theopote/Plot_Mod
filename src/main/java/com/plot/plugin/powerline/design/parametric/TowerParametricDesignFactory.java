package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.PoleDesign;

/** 参数化塔型编译入口（与 {@code TowerStructurePresets} 并行存在，不替换旧预设）。 */
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

    public static TowerConstraintResult resolveSmallLattice(TowerParameterSet parameters) {
        return resolveSmallLattice(parameters, null);
    }

    public static TowerConstraintResult resolveSmallLattice(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.smallLattice(), parameters, envelope);
    }

    public static TowerConstraintResult resolveTripleArm(TowerParameterSet parameters) {
        return resolveTripleArm(parameters, null);
    }

    public static TowerConstraintResult resolveTripleArm(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.tripleArm(), parameters, envelope);
    }

    public static TowerConstraintResult resolveCup(TowerParameterSet parameters) {
        return resolveCup(parameters, null);
    }

    public static TowerConstraintResult resolveCup(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.cup(), parameters, envelope);
    }

    public static TowerConstraintResult resolveHeavy(TowerParameterSet parameters) {
        return resolveHeavy(parameters, null);
    }

    public static TowerConstraintResult resolveHeavy(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.heavy(), parameters, envelope);
    }

    public static TowerConstraintResult resolveMega(TowerParameterSet parameters) {
        return resolveMega(parameters, null);
    }

    public static TowerConstraintResult resolveMega(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.mega(), parameters, envelope);
    }

    public static TowerConstraintResult resolvePortal(TowerParameterSet parameters) {
        return resolvePortal(parameters, null);
    }

    public static TowerConstraintResult resolvePortal(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.portal(), parameters, envelope);
    }

    public static TowerConstraintResult resolveDrum(TowerParameterSet parameters) {
        return resolveDrum(parameters, null);
    }

    public static TowerConstraintResult resolveDrum(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.drum(), parameters, envelope);
    }

    public static TowerConstraintResult resolveUhv(TowerParameterSet parameters) {
        return resolveUhv(parameters, null);
    }

    public static TowerConstraintResult resolveUhv(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.uhv(), parameters, envelope);
    }

    public static TowerConstraintResult resolveSteampunk(TowerParameterSet parameters) {
        return resolveSteampunk(parameters, null);
    }

    public static TowerConstraintResult resolveSteampunk(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.steampunk(), parameters, envelope);
    }

    public static TowerConstraintResult resolveModernHvGlass(TowerParameterSet parameters) {
        return resolveModernHvGlass(parameters, null);
    }

    public static TowerConstraintResult resolveModernHvGlass(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return resolveProfile(TowerParameterProfiles.modernHvGlass(), parameters, envelope);
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
        return compileProfile(TowerParameterProfiles.classicDoubleArm(), parameters, envelope);
    }

    public static PoleDesign compileSmallLattice(TowerParameterSet parameters) {
        return compileSmallLattice(parameters, null);
    }

    public static PoleDesign compileSmallLattice(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.smallLattice(), parameters, envelope);
    }

    public static PoleDesign compileTripleArm(TowerParameterSet parameters) {
        return compileTripleArm(parameters, null);
    }

    public static PoleDesign compileTripleArm(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.tripleArm(), parameters, envelope);
    }

    public static PoleDesign compileCup(TowerParameterSet parameters) {
        return compileCup(parameters, null);
    }

    public static PoleDesign compileCup(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.cup(), parameters, envelope);
    }

    public static PoleDesign compileHeavy(TowerParameterSet parameters) {
        return compileHeavy(parameters, null);
    }

    public static PoleDesign compileHeavy(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.heavy(), parameters, envelope);
    }

    public static PoleDesign compileMega(TowerParameterSet parameters) {
        return compileMega(parameters, null);
    }

    public static PoleDesign compileMega(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.mega(), parameters, envelope);
    }

    public static PoleDesign compilePortal(TowerParameterSet parameters) {
        return compilePortal(parameters, null);
    }

    public static PoleDesign compilePortal(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.portal(), parameters, envelope);
    }

    public static PoleDesign compileDrum(TowerParameterSet parameters) {
        return compileDrum(parameters, null);
    }

    public static PoleDesign compileDrum(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.drum(), parameters, envelope);
    }

    public static PoleDesign compileUhv(TowerParameterSet parameters) {
        return compileUhv(parameters, null);
    }

    public static PoleDesign compileUhv(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.uhv(), parameters, envelope);
    }

    public static PoleDesign compileSteampunk(TowerParameterSet parameters) {
        return compileSteampunk(parameters, null);
    }

    public static PoleDesign compileSteampunk(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.steampunk(), parameters, envelope);
    }

    public static PoleDesign compileModernHvGlass(TowerParameterSet parameters) {
        return compileModernHvGlass(parameters, null);
    }

    public static PoleDesign compileModernHvGlass(
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        return compileProfile(TowerParameterProfiles.modernHvGlass(), parameters, envelope);
    }

    public static PoleDesign compileProfile(
            TowerParameterProfile profile,
            TowerParameterSet parameters,
            TowerBuildEnvelope envelope) {
        TowerConstraintResult result = resolveProfile(profile, parameters, envelope);
        if (result.hasErrors()) {
            String codes = result.issues().stream()
                .filter(issue -> issue.severity() == ConstraintSeverity.ERROR)
                .map(ConstraintIssue::code)
                .reduce((a, b) -> a + ", " + b)
                .orElse("UNKNOWN");
            throw new IllegalStateException("Cannot compile parametric tower: " + codes);
        }
        return TowerStructureCompiler.compile(profile, result.resolved());
    }
}
