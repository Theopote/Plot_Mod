package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.ui.PowerLineUiPresets;

/**
 * 风格预设的杆塔间距推荐元数据（Minecraft 装饰尺度，非工程规范）。
 *
 * @param recommendedMin 美观建议下限（Route 滑块起点 / 密度卡片共用）
 * @param preferred      「自然」档推荐最大档距
 * @param recommendedMax 疏松上限与高级滑块上限
 */
public record PoleSpacingProfile(double recommendedMin, double preferred, double recommendedMax) {

    public static PoleSpacingProfile streetWood() {
        return new PoleSpacingProfile(15, 30, 45);
    }

    /** 密集：介于建议最小与推荐跨度之间（Wood → 20，Heavy Lattice → 80）。 */
    public double denseMaxSpacing() {
        double range = preferred - recommendedMin;
        double factor = range >= 50.0 ? 1.0 / 6.0 : 1.0 / 3.0;
        return recommendedMin + range * factor;
    }

    /** 稀疏：介于推荐与建议最大之间（Wood → 40，Heavy Lattice → 180）。 */
    public double sparseMaxSpacing() {
        double range = recommendedMax - preferred;
        double factor = range >= 50.0 ? 5.0 / 7.0 : 2.0 / 3.0;
        return preferred + range * factor;
    }

    public double maxSpacingFor(PowerLineUiPresets.SpacingDensity density) {
        if (density == null) {
            return preferred;
        }
        return switch (density) {
            case DENSE -> denseMaxSpacing();
            case NORMAL -> preferred;
            case SPARSE -> sparseMaxSpacing();
        };
    }
}
