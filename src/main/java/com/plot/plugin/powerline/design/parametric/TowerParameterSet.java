package com.plot.plugin.powerline.design.parametric;

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
        StructureDensity density) {

    public static TowerParameterSet classicDefaults() {
        return new TowerParameterSet(36.0, 13.0, 24.0, 1.0, StructureDensity.MEDIUM);
    }

    public TowerParameterSet {
        if (!Double.isFinite(height)
                || !Double.isFinite(baseWidth)
                || !Double.isFinite(armSpan)
                || !Double.isFinite(depthScale)
                || density == null) {
            throw new IllegalArgumentException("invalid tower parameter set");
        }
    }
}
