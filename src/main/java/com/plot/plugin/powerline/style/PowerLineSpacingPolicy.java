package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLineUiPresets;

/** 线路杆塔间距：风格 profile + 相对密度卡片 + 玩家微调。 */
public final class PowerLineSpacingPolicy {
    private static final double DENSITY_MATCH_TOLERANCE = 4.0;
    private static final PoleSpacingProfile FALLBACK = PoleSpacingProfile.streetWood();

    private PowerLineSpacingPolicy() {
    }

    public static PoleSpacingProfile profileFor(PowerLineFootprint line) {
        if (line == null) {
            return FALLBACK;
        }
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.activePreset(line);
        if (preset != null) {
            return preset.getSpacingProfile();
        }
        PowerLineStylePreset detected = PowerLineStylePresetCatalog.detect(line);
        if (detected != null) {
            return detected.getSpacingProfile();
        }
        return FALLBACK;
    }

    public static double sliderMax(PowerLineFootprint line) {
        return profileFor(line).recommendedMax();
    }

    public static PowerLineUiPresets.SpacingDensity detectDensity(PowerLineFootprint line) {
        if (line == null) {
            return PowerLineUiPresets.SpacingDensity.NORMAL;
        }
        PoleSpacingProfile profile = profileFor(line);
        if (!spacingClose(line.getMinPoleSpacing(), profile.recommendedMin(), DENSITY_MATCH_TOLERANCE)) {
            return null;
        }
        double max = line.getMaxPoleSpacing();
        PowerLineUiPresets.SpacingDensity closest = PowerLineUiPresets.SpacingDensity.NORMAL;
        double best = Math.abs(max - profile.preferred());
        for (PowerLineUiPresets.SpacingDensity density : PowerLineUiPresets.SpacingDensity.values()) {
            double target = profile.maxSpacingFor(density);
            double diff = Math.abs(max - target);
            if (diff < best) {
                best = diff;
                closest = density;
            }
        }
        return best <= DENSITY_MATCH_TOLERANCE ? closest : null;
    }

    public static void applyDensity(
            PowerLineFootprint line,
            PowerLineUiPresets.SpacingDensity density) {
        if (line == null || density == null) {
            return;
        }
        PoleSpacingProfile profile = profileFor(line);
        line.setMinPoleSpacing(profile.recommendedMin());
        line.setMaxPoleSpacing(profile.maxSpacingFor(density));
        line.setSpacingCustomized(false);
    }

    public static void applyStyleDefaultSpacing(PowerLineFootprint line, PoleSpacingProfile profile) {
        if (line == null || profile == null) {
            return;
        }
        line.setMinPoleSpacing(profile.recommendedMin());
        line.setMaxPoleSpacing(profile.preferred());
        line.setSpacingCustomized(false);
    }

    public static boolean differsFromStyleRecommendation(PowerLineFootprint line, PowerLineStylePreset preset) {
        if (line == null || preset == null) {
            return false;
        }
        PoleSpacingProfile profile = preset.getSpacingProfile();
        return !spacingClose(line.getMinPoleSpacing(), profile.recommendedMin(), DENSITY_MATCH_TOLERANCE)
            || !spacingClose(line.getMaxPoleSpacing(), profile.preferred(), DENSITY_MATCH_TOLERANCE);
    }

    /** Route 布局密度偏好；未识别时默认为均衡（Balanced）。 */
    public static PowerLineUiPresets.SpacingDensity effectiveDensity(PowerLineFootprint line) {
        PowerLineUiPresets.SpacingDensity detected = detectDensity(line);
        return detected != null ? detected : PowerLineUiPresets.SpacingDensity.NORMAL;
    }

    /** 给定密度 + 当前风格 profile 的推荐最大档距（格）。 */
    public static double spacingForDensity(PowerLineFootprint line, PowerLineUiPresets.SpacingDensity density) {
        PoleSpacingProfile profile = profileFor(line);
        PowerLineUiPresets.SpacingDensity resolved = density != null
            ? density
            : PowerLineUiPresets.SpacingDensity.NORMAL;
        return profile.maxSpacingFor(resolved);
    }

    private static boolean spacingClose(double actual, double expected, double tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }
}
