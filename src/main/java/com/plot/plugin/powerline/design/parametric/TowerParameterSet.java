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

    public static TowerParameterSet smallLatticeDefaults() {
        return new TowerParameterSet(24.0, 9.0, 16.0, 1.0, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet tripleArmDefaults() {
        return new TowerParameterSet(50.0, 16.0, 29.0, 1.0, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet cupDefaults() {
        return new TowerParameterSet(40.0, 10.0, 32.0, 1.0, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet heavyDefaults() {
        return new TowerParameterSet(32.0, 11.0, 24.0, 1.0, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet megaDefaults() {
        return new TowerParameterSet(60.0, 18.0, 30.0, 1.0, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet portalDefaults() {
        return new TowerParameterSet(42.0, 21.0, 32.0, 1.0, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet drumDefaults() {
        return new TowerParameterSet(58.0, 17.0, 28.0, 1.0, StructureDensity.MEDIUM);
    }

    public static TowerParameterSet uhvDefaults() {
        return new TowerParameterSet(80.0, 28.0, 52.0, 1.0, StructureDensity.MEDIUM);
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
