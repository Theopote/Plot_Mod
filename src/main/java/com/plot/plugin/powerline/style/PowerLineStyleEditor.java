package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PowerLineSagUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;

import java.util.Objects;

/** Base Preset + Overrides 状态机（footprint 存生效值，overrides 记录偏离项）。 */
public final class PowerLineStyleEditor {
    private static final double SAG_TOLERANCE = 0.01;
    private static final double SPACING_TOLERANCE = 4.0;
    private static final double MAX_SAG_TOLERANCE = 0.5;

    private PowerLineStyleEditor() {
    }

    public static PowerLineStylePreset basePreset(PowerLineFootprint line) {
        if (line == null || line.getStylePackId() == null || line.getStylePackId().isBlank()) {
            return null;
        }
        return PowerLineStylePresetCatalog.find(line.getStylePackId());
    }

    /** 风格卡片：切换到新的 base preset（保留 spacingCustomized）。 */
    public static void selectPreset(PowerLineFootprint line, PowerLineStylePreset preset) {
        if (line == null || preset == null) {
            return;
        }
        line.clearStyleOverrides();
        preset.apply(line);
        syncOverridesFromFootprint(line);
    }

    /** 重置为 base preset 全部默认值（含间距）。 */
    public static void resetToBasePreset(PowerLineFootprint line) {
        PowerLineStylePreset preset = basePreset(line);
        if (preset == null) {
            return;
        }
        line.clearStyleOverrides();
        line.setSpacingCustomized(false);
        preset.apply(line);
        syncOverridesFromFootprint(line);
    }

    public static void afterStyleEdit(PowerLineFootprint line) {
        syncOverridesFromFootprint(line);
    }

    public static void afterSpacingEdit(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        line.setSpacingCustomized(true);
        syncSpacingOverrides(line);
    }

    public static void afterSpacingAdopted(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        line.setSpacingCustomized(false);
        syncSpacingOverrides(line);
    }

    public static boolean isModified(PowerLineFootprint line) {
        if (line == null) {
            return false;
        }
        if (line.isSpacingCustomized()) {
            return true;
        }
        return !line.getStyleOverrides().isEmpty();
    }

    public static int modifiedSettingCount(PowerLineFootprint line) {
        if (line == null) {
            return 0;
        }
        int count = line.getStyleOverrides().overrideCount();
        if (line.isSpacingCustomized()) {
            count++;
        }
        return count;
    }

    public static void syncOverridesFromFootprint(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        PowerLineStylePreset preset = basePreset(line);
        StyleOverrides overrides = line.getStyleOverrides();
        if (preset == null) {
            overrides.clear();
            return;
        }
        overrides.setSagRatio(overrideSag(line.getSagRatio(), preset));
        overrides.setMaxSagDepth(overrideMaxSagDepth(line));
        overrides.setWireMaterial(overrideMaterial(line.getWireMaterial(), preset.getWireMaterial()));
        overrides.setPoleMaterial(overrideMaterial(line.getPoleMaterial(), preset.getPoleMaterial()));
        overrides.setGroundWireMaterial(overrideMaterial(line.getGroundWireMaterial(), preset.getGroundWireMaterial()));
        overrides.setPoleDesignId(overrideId(line.getPoleDesignId(), preset.getPoleDesignId()));
        overrides.setTowerFamilyId(overrideId(line.getTowerFamilyId(), preset.getTowerFamilyId()));
        syncSpacingOverrides(line);
        if (overrides.isEmpty() && !line.isSpacingCustomized()) {
            overrides.clear();
        }
    }

    private static void syncSpacingOverrides(PowerLineFootprint line) {
        PowerLineStylePreset preset = basePreset(line);
        StyleOverrides overrides = line.getStyleOverrides();
        if (preset == null || !line.isSpacingCustomized()) {
            overrides.setPreferredSpacing(null);
            overrides.setRecommendedMinSpacing(null);
            return;
        }
        PoleSpacingProfile profile = preset.getSpacingProfile();
        overrides.setPreferredSpacing(overrideSpacing(line.getMaxPoleSpacing(), profile.preferred()));
        overrides.setRecommendedMinSpacing(overrideSpacing(line.getMinPoleSpacing(), profile.recommendedMin()));
    }

    private static Double overrideSag(double actual, PowerLineStylePreset preset) {
        double expected = preset.getSagPreset().ratio();
        return Math.abs(actual - expected) <= SAG_TOLERANCE ? null : actual;
    }

    private static Double overrideMaxSagDepth(PowerLineFootprint line) {
        if (line.isMaxSagDepthUnlimited()) {
            return -1.0;
        }
        double expected = PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;
        return Math.abs(line.getMaxSagDepth() - expected) <= MAX_SAG_TOLERANCE ? null : line.getMaxSagDepth();
    }

    private static MaterialMix overrideMaterial(MaterialMix actual, MaterialMix expected) {
        if (actual == null || expected == null) {
            return null;
        }
        return Objects.equals(actual.getPrimaryMaterial(), expected.getPrimaryMaterial()) ? null : actual.copy();
    }

    private static String overrideId(String actual, String expected) {
        String normalizedActual = actual == null || actual.isBlank() ? null : actual;
        String normalizedExpected = expected == null || expected.isBlank() ? null : expected;
        return Objects.equals(normalizedActual, normalizedExpected) ? null : normalizedActual;
    }

    private static Double overrideSpacing(double actual, double expected) {
        return Math.abs(actual - expected) <= SPACING_TOLERANCE ? null : actual;
    }
}
