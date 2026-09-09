package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineSagUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.style.PowerLineSpacingPolicy;

/** 玩家友好的线路参数预设（内部仍映射到 footprint 数值）。 */
public final class PowerLineUiPresets {
    /** 预设卡片上限（Loose）。 */
    public static final double PRESET_SAG_MAX_RATIO = 0.22;
    /** Advanced 滑块上限。 */
    public static final double ADVANCED_SAG_MAX_RATIO = 0.35;
    /** 最大下垂深度滑块上限（格）。 */
    public static final float ADVANCED_MAX_SAG_DEPTH_MAX = 64f;
    private static final double SAG_MATCH_TOLERANCE = 0.01;

    /** 相对当前风格性格的疏松 / 标准 / 紧凑档距。 */
    public enum SpacingDensity {
        DENSE,
        NORMAL,
        SPARSE
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
        return PowerLineSpacingPolicy.detectDensity(line);
    }

    public static WireSag detectSag(PowerLineFootprint line) {
        if (line == null) {
            return WireSag.NATURAL;
        }
        double ratio = line.getSagRatio();
        for (WireSag sag : WireSag.values()) {
            if (Math.abs(ratio - sag.ratio()) <= SAG_MATCH_TOLERANCE) {
                return sag;
            }
        }
        return null;
    }

    public static boolean isPresetSag(PowerLineFootprint line) {
        return detectSag(line) != null;
    }

    public static void applySpacing(PowerLineFootprint line, SpacingDensity density) {
        PowerLineSpacingPolicy.applyDensity(line, density);
    }

    public static void applySag(PowerLineFootprint line, WireSag sag) {
        if (line == null || sag == null) {
            return;
        }
        line.setSagRatio(Math.min(sag.ratio(), PRESET_SAG_MAX_RATIO));
    }

    public static void applyAdvancedSag(PowerLineFootprint line, double ratio) {
        if (line == null) {
            return;
        }
        double clamped = Math.max(0.0, Math.min(ratio, ADVANCED_SAG_MAX_RATIO));
        line.setSagRatio(clamped);
    }

    public static void applyMaxSagDepth(PowerLineFootprint line, float depthBlocks, boolean unlimited) {
        if (line == null) {
            return;
        }
        if (unlimited) {
            line.setMaxSagDepth(0.0);
            return;
        }
        line.setMaxSagDepth(Math.max(1.0, Math.min(ADVANCED_MAX_SAG_DEPTH_MAX, depthBlocks)));
    }

    public static float displayMaxSagDepth(PowerLineFootprint line) {
        if (line == null || line.isMaxSagDepthUnlimited()) {
            return (float) PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;
        }
        return (float) line.getMaxSagDepth();
    }
}
