package com.plot.plugin.powerline.design.parametric;

/** JSON DTO for {@link TowerGeneratorConfig} (project / pole design persistence). */
public final class TowerGeneratorConfigData {
    public String profileId;
    public String mode;
    public double height;
    public double baseWidth;
    public double armSpan;
    public double depthScale;
    public double waistRatio;
    public double[] armLevelScales;
    public String density;

    public static TowerGeneratorConfigData from(TowerGeneratorConfig config) {
        if (config == null) {
            return null;
        }
        TowerGeneratorConfigData data = new TowerGeneratorConfigData();
        data.profileId = config.profileId();
        data.mode = config.mode().name();
        data.height = config.parameters().height();
        data.baseWidth = config.parameters().baseWidth();
        data.armSpan = config.parameters().armSpan();
        data.depthScale = config.parameters().depthScale();
        data.waistRatio = config.parameters().waistRatio();
        if (config.parameters().armLevelScales() != null && !config.parameters().armLevelScales().isEmpty()) {
            data.armLevelScales = config.parameters().armLevelScales().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
        }
        data.density = config.parameters().density().name();
        return data;
    }

    public TowerGeneratorConfig toConfig() {
        if (profileId == null || profileId.isBlank()) {
            return null;
        }
        TowerGeneratorMode parsedMode = TowerGeneratorMode.PARAMETRIC;
        if (mode != null && !mode.isBlank()) {
            try {
                parsedMode = TowerGeneratorMode.valueOf(mode.trim());
            } catch (IllegalArgumentException ignored) {
                parsedMode = TowerGeneratorMode.PARAMETRIC;
            }
        }
        StructureDensity parsedDensity = StructureDensity.MEDIUM;
        if (density != null && !density.isBlank()) {
            try {
                parsedDensity = StructureDensity.valueOf(density.trim());
            } catch (IllegalArgumentException ignored) {
                parsedDensity = StructureDensity.MEDIUM;
            }
        }
        double parsedWaistRatio = waistRatio > 0.0 ? waistRatio : TowerParameterSet.DEFAULT_WAIST_RATIO;
        java.util.List<Double> parsedArmLevelScales = null;
        if (armLevelScales != null && armLevelScales.length > 0) {
            parsedArmLevelScales = new java.util.ArrayList<>(armLevelScales.length);
            for (double scale : armLevelScales) {
                parsedArmLevelScales.add(scale);
            }
        }
        return new TowerGeneratorConfig(
            profileId,
            parsedMode,
            new TowerParameterSet(
                height,
                baseWidth,
                armSpan,
                depthScale,
                parsedWaistRatio,
                parsedArmLevelScales,
                parsedDensity));
    }
}
