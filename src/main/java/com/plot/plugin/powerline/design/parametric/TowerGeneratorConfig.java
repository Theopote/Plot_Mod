package com.plot.plugin.powerline.design.parametric;

/** 杆塔参数生成配置（可选持久化于 {@link com.plot.plugin.powerline.design.PoleDesign}）。 */
public record TowerGeneratorConfig(
        String profileId,
        TowerGeneratorMode mode,
        TowerParameterSet parameters) {

    public TowerGeneratorConfig {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId is required");
        }
        if (mode == null) {
            mode = TowerGeneratorMode.PARAMETRIC;
        }
        if (parameters == null) {
            throw new IllegalArgumentException("parameters are required");
        }
    }

    public static TowerGeneratorConfig parametricClassic(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.CLASSIC_DOUBLE_ARM_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricClassicDefaults() {
        return parametricClassic(TowerParameterSet.classicDefaults());
    }

    public static TowerGeneratorConfig parametricSmallLattice(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.SMALL_LATTICE_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricSmallLatticeDefaults() {
        return parametricSmallLattice(TowerParameterSet.smallLatticeDefaults());
    }

    public static TowerGeneratorConfig parametricTripleArm(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.TRIPLE_ARM_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricTripleArmDefaults() {
        return parametricTripleArm(TowerParameterSet.tripleArmDefaults());
    }

    public static TowerGeneratorConfig parametricCup(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.CUP_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricCupDefaults() {
        return parametricCup(TowerParameterSet.cupDefaults());
    }

    public static TowerGeneratorConfig parametricHeavy(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.HEAVY_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricHeavyDefaults() {
        return parametricHeavy(TowerParameterSet.heavyDefaults());
    }

    public static TowerGeneratorConfig parametricMega(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.MEGA_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricMegaDefaults() {
        return parametricMega(TowerParameterSet.megaDefaults());
    }

    public static TowerGeneratorConfig parametricPortal(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.PORTAL_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricPortalDefaults() {
        return parametricPortal(TowerParameterSet.portalDefaults());
    }

    public static TowerGeneratorConfig parametricDrum(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.DRUM_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricDrumDefaults() {
        return parametricDrum(TowerParameterSet.drumDefaults());
    }

    public static TowerGeneratorConfig parametricUhv(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.UHV_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricUhvDefaults() {
        return parametricUhv(TowerParameterSet.uhvDefaults());
    }

    public static TowerGeneratorConfig parametricSteampunk(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.STEAMPUNK_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricSteampunkDefaults() {
        return parametricSteampunk(TowerParameterSet.steampunkDefaults());
    }

    public static TowerGeneratorConfig parametricModernHvGlass(TowerParameterSet parameters) {
        return new TowerGeneratorConfig(
            TowerParameterProfiles.MODERN_HV_GLASS_ID,
            TowerGeneratorMode.PARAMETRIC,
            parameters);
    }

    public static TowerGeneratorConfig parametricModernHvGlassDefaults() {
        return parametricModernHvGlass(TowerParameterSet.modernHvGlassDefaults());
    }

    public boolean isParametric() {
        return mode == TowerGeneratorMode.PARAMETRIC;
    }

    public TowerGeneratorConfig withParameters(TowerParameterSet next) {
        return new TowerGeneratorConfig(profileId, mode, next);
    }

    public TowerGeneratorConfig copy() {
        return new TowerGeneratorConfig(profileId, mode, parameters);
    }
}
