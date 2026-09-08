package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 玩家友好的线路参数预设（内部仍映射到 footprint 数值）。 */
public final class PowerLineUiPresets {
    public enum SpacingDensity {
        DENSE(12.0, 4.0),
        NORMAL(20.0, 6.0),
        SPARSE(30.0, 10.0);

        private final double maxSpacing;
        private final double minSpacing;

        SpacingDensity(double maxSpacing, double minSpacing) {
            this.maxSpacing = maxSpacing;
            this.minSpacing = minSpacing;
        }

        public double maxSpacing() {
            return maxSpacing;
        }

        public double minSpacing() {
            return minSpacing;
        }
    }

    public enum WireSag {
        STRAIGHT(0.03),
        LIGHT(0.08),
        NATURAL(0.15),
        LOOSE(0.22);

        private final double ratio;

        WireSag(double ratio) {
            this.ratio = ratio;
        }

        public double ratio() {
            return ratio;
        }
    }

    private PowerLineUiPresets() {
    }

    public static SpacingDensity detectSpacing(PowerLineFootprint line) {
        if (line == null) {
            return SpacingDensity.NORMAL;
        }
        double max = line.getMaxPoleSpacing();
        if (max <= 14.0) {
            return SpacingDensity.DENSE;
        }
        if (max >= 26.0) {
            return SpacingDensity.SPARSE;
        }
        return SpacingDensity.NORMAL;
    }

    public static WireSag detectSag(PowerLineFootprint line) {
        if (line == null) {
            return WireSag.NATURAL;
        }
        double ratio = line.getSagRatio();
        if (ratio <= 0.05) {
            return WireSag.STRAIGHT;
        }
        if (ratio <= 0.10) {
            return WireSag.LIGHT;
        }
        if (ratio <= 0.18) {
            return WireSag.NATURAL;
        }
        return WireSag.LOOSE;
    }

    public static void applySpacing(PowerLineFootprint line, SpacingDensity density) {
        line.setMaxPoleSpacing(density.maxSpacing());
        line.setMinPoleSpacing(density.minSpacing());
    }

    public static void applySag(PowerLineFootprint line, WireSag sag) {
        line.setSagRatio(sag.ratio());
    }
}
