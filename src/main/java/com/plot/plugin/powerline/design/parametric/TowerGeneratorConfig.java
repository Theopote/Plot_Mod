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
