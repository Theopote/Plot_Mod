package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.model.PowerLineFootprint;

/**
 * 线路上的风格实例：Base Preset + User Overrides。
 * <p>
 * footprint 存生效值；{@link StyleOverrides} 记录相对 base {@link PowerLineStyleDefinition} 的偏离项
 * （{@code null} 字段 = 沿用预设默认）。
 */
public final class PowerLineStyleInstance {
    private final PowerLineFootprint footprint;

    private PowerLineStyleInstance(PowerLineFootprint footprint) {
        this.footprint = footprint;
    }

    public static PowerLineStyleInstance of(PowerLineFootprint footprint) {
        return footprint == null ? null : new PowerLineStyleInstance(footprint);
    }

    public PowerLineFootprint footprint() {
        return footprint;
    }

    public String basePresetId() {
        return footprint.getStylePresetId();
    }

    public StyleOverrides overrides() {
        return footprint.getStyleOverrides();
    }

    public PowerLineStylePreset basePreset() {
        return PowerLineStylePresetCatalog.find(basePresetId());
    }

    public PowerLineStyleDefinition definition() {
        PowerLineStylePreset preset = basePreset();
        return preset != null ? preset.getDefinition() : null;
    }

    public boolean hasBasePreset() {
        return basePreset() != null;
    }

    /** 是否存在相对 base 定义的偏离（含 Route 间距自定义）。 */
    public boolean isModified() {
        if (footprint.isSpacingCustomized()) {
            return true;
        }
        return !overrides().isEmpty();
    }

    /** 生效值是否与 base 定义默认 bundle 完全一致。 */
    public boolean matchesBaseDefinition() {
        PowerLineStyleDefinition definition = definition();
        if (definition == null) {
            return false;
        }
        if (footprint.isSpacingCustomized()) {
            return false;
        }
        return definition.matches(footprint);
    }

    public int modifiedSettingCount() {
        int count = overrides().overrideCount();
        if (footprint.isSpacingCustomized()) {
            count++;
        }
        return count;
    }
}
