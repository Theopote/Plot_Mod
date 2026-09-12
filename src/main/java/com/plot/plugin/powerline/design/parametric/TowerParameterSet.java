package com.plot.plugin.powerline.design.parametric;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户主控参数。
 * <p>
 * {@code baseWidth} 与 {@code armSpan} 均为<strong>总宽</strong>（full width），
 * 编译器负责转换为 halfWidth / lateralReach。
 */
public record TowerParameterSet(
        double height,
        double baseWidth,
        double armSpan,
        double depthScale,
        double waistRatio,
        List<Double> armLevelScales,
        StructureDensity density) {

    public static final double DEFAULT_WAIST_RATIO = 1.0;
    public static final double DEFAULT_ARM_LEVEL_SCALE = 1.0;

    public static TowerParameterSet classicDefaults() {
        return new TowerParameterSet(36.0, 13.0, 24.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet smallLatticeDefaults() {
        return new TowerParameterSet(24.0, 9.0, 16.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet tripleArmDefaults() {
        return new TowerParameterSet(50.0, 16.0, 29.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet cupDefaults() {
        return new TowerParameterSet(40.0, 10.0, 32.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet heavyDefaults() {
        return new TowerParameterSet(32.0, 11.0, 24.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet megaDefaults() {
        return new TowerParameterSet(60.0, 18.0, 30.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet portalDefaults() {
        return new TowerParameterSet(42.0, 21.0, 32.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet drumDefaults() {
        return new TowerParameterSet(58.0, 17.0, 28.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet uhvDefaults() {
        return new TowerParameterSet(80.0, 28.0, 52.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet steampunkDefaults() {
        return new TowerParameterSet(28.0, 8.0, 14.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet modernHvGlassDefaults() {
        return new TowerParameterSet(34.0, 10.0, 18.0, 1.0, DEFAULT_WAIST_RATIO, null, StructureDensity.MEDIUM);
    }

    public TowerParameterSet {
        if (!Double.isFinite(height)
                || !Double.isFinite(baseWidth)
                || !Double.isFinite(armSpan)
                || !Double.isFinite(depthScale)
                || !Double.isFinite(waistRatio)
                || density == null) {
            throw new IllegalArgumentException("invalid tower parameter set");
        }
        if (armLevelScales != null) {
            armLevelScales = List.copyOf(armLevelScales);
            for (Double scale : armLevelScales) {
                if (scale == null || !Double.isFinite(scale)) {
                    throw new IllegalArgumentException("invalid arm level scale");
                }
            }
        }
    }

    public double armLevelScaleAt(int index) {
        if (armLevelScales == null || index < 0 || index >= armLevelScales.size()) {
            return DEFAULT_ARM_LEVEL_SCALE;
        }
        return armLevelScales.get(index);
    }

    public List<Double> armLevelScalesForProfile(TowerParameterProfile profile) {
        int count = profile.armTemplates().size();
        List<Double> resolved = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            resolved.add(armLevelScaleAt(i));
        }
        return resolved;
    }

    public TowerParameterSet withArmLevelScale(TowerParameterProfile profile, int index, double scale) {
        List<Double> scales = new ArrayList<>(armLevelScalesForProfile(profile));
        scales.set(index, scale);
        return withArmLevelScales(scales);
    }

    public TowerParameterSet withArmLevelScales(List<Double> scales) {
        return new TowerParameterSet(height, baseWidth, armSpan, depthScale, waistRatio, scales, density);
    }
}
