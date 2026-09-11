package com.plot.plugin.powerline.design.parametric;

/**
 * 世界建造包络。{@code worldTopExclusiveY} 为 Minecraft dimension 上界（exclusive）。
 */
public record TowerBuildEnvelope(
        int worldBottomY,
        int worldTopExclusiveY,
        double groundY,
        int topSafetyMargin) {

    public static final int DEFAULT_TOP_SAFETY_MARGIN = 4;

    public TowerBuildEnvelope {
        if (topSafetyMargin < 0) {
            throw new IllegalArgumentException("topSafetyMargin must be non-negative");
        }
    }

    public static TowerBuildEnvelope overworldDefault(double groundY) {
        return new TowerBuildEnvelope(-64, 320, groundY, DEFAULT_TOP_SAFETY_MARGIN);
    }

    public int maxUsableWorldY() {
        return worldTopExclusiveY - 1 - topSafetyMargin;
    }

    public double availableLocalHeight() {
        return maxUsableWorldY() - groundY;
    }
}
