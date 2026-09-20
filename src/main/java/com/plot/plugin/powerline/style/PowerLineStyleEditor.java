package com.plot.plugin.powerline.style;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;


/**
 * Base Preset + Overrides 状态机（偏离项存 StyleState，生效值经 resolve 层解析）。
 * <p>
 * 概念模型见 {@link PowerLineStyleDefinition}（定义）与 {@link PowerLineStyleInstance}（实例）。
 */
public final class PowerLineStyleEditor {
    private static final double SPACING_TOLERANCE = 4.0;

    private PowerLineStyleEditor() {
    }

    public static PowerLineStyleInstance instance(PowerLineFootprint line) {
        return PowerLineStyleInstance.of(line);
    }

    public static PowerLineStylePreset basePreset(PowerLineFootprint line) {
        PowerLineStyleInstance style = instance(line);
        return style != null ? style.basePreset() : null;
    }

    public static boolean isSpacingCustomized(PowerLineFootprint line) {
        return line != null && line.getStyleOverrides().getPreferredSpacing() != null;
    }

    /** 解析当前 base preset（内置目录 + 用户造型模板）。 */
    public static PowerLineStylePreset resolveBasePreset(
            PowerLineFootprint line,
            PowerLineDesignProject designProject) {
        PowerLineStylePreset builtin = basePreset(line);
        if (builtin != null) {
            return builtin;
        }
        return UserPoleDesignTemplateCatalog.resolvePreset(line, designProject);
    }

    /** 将线路切换到用户造型模板（清除私有 fork 与微调覆盖）。 */
    public static void selectUserTemplate(
            PowerLineFootprint line,
            PoleDesign design,
            PowerLineDesignProject designProject) {
        if (line == null || design == null) {
            return;
        }
        if (designProject != null) {
            LinePoleDesignOverrides.removeLineInstance(line, designProject);
        }
        line.clearStyleOverrides();
        UserPoleDesignTemplateCatalog.toPreset(design, line).apply(line);
        syncOverridesFromFootprint(line);
    }

    /** 风格卡片：切换到新的 base preset（保留间距 override）。 */
    public static void selectPreset(PowerLineFootprint line, PowerLineStylePreset preset) {
        if (line == null || preset == null) {
            return;
        }
        Double spacingOverride = isSpacingCustomized(line)
            ? line.getStyleOverrides().getPreferredSpacing()
            : null;
        line.clearStyleOverrides();
        preset.apply(line);
        if (spacingOverride != null) {
            line.setMaxPoleSpacing(spacingOverride);
            line.getStyleOverrides().setPreferredSpacing(spacingOverride);
        }
        syncOverridesFromFootprint(line);
    }

    /** 重置为 base preset 全部默认值（含间距）。 */
    public static void resetToBasePreset(PowerLineFootprint line) {
        PowerLineStylePreset preset = basePreset(line);
        if (preset == null) {
            return;
        }
        line.clearStyleOverrides();
        preset.apply(line);
        syncOverridesFromFootprint(line);
    }

    /** 重置为 base preset，并清除该线路的私有造型 fork。 */
    public static void resetToBasePreset(
            PowerLineFootprint line,
            PowerLineDesignProject designProject) {
        if (designProject != null) {
            LinePoleDesignOverrides.removeLineInstance(line, designProject);
        }
        resetToBasePreset(line);
    }

    public static void afterStyleEdit(PowerLineFootprint line) {
        syncOverridesFromFootprint(line);
    }

    /** 最大档距（style spacing）被用户修改后调用；不包含过近警告阈值。 */
    public static void afterSpacingEdit(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        line.getStyleOverrides().setPreferredSpacing(line.getMaxPoleSpacing());
        syncOverridesFromFootprint(line);
    }

    public static void afterSpacingAdopted(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        line.getStyleOverrides().setPreferredSpacing(null);
        syncSpacingOverrides(line);
        syncOverridesFromFootprint(line);
    }

    public static boolean isModified(PowerLineFootprint line) {
        PowerLineStyleInstance style = instance(line);
        return style != null && style.isModified();
    }

    public static int modifiedSettingCount(PowerLineFootprint line) {
        PowerLineStyleInstance style = instance(line);
        return style != null ? style.modifiedSettingCount() : 0;
    }

    public static void syncOverridesFromFootprint(PowerLineFootprint line) {
        if (line == null) {
            return;
        }
        PowerLineStylePreset preset = basePreset(line);
        StyleOverrides overrides = line.getStyleOverrides();
        if (preset == null) {
            syncSpacingOverrides(line);
            return;
        }
        PowerLineStyleDefinition definition = preset.getDefinition();
        overrides.setParametricTowerConfig(overrideParametric(
            line.getParametricTowerConfig(),
            definition.getParametricConfig()));
        syncSpacingOverrides(line);
        if (overrides.isEmpty()) {
            overrides.clear();
        }
    }

    private static void syncSpacingOverrides(PowerLineFootprint line) {
        StyleOverrides overrides = line.getStyleOverrides();
        if (overrides.getPreferredSpacing() != null) {
            overrides.setPreferredSpacing(line.getMaxPoleSpacing());
            return;
        }
        PowerLineStylePreset preset = basePreset(line);
        if (preset == null || PowerLineSpacingPolicy.detectDensity(line) != null) {
            return;
        }
        double preferred = preset.getSpacingProfile().preferred();
        if (Math.abs(line.getMaxPoleSpacing() - preferred) > SPACING_TOLERANCE) {
            overrides.setPreferredSpacing(line.getMaxPoleSpacing());
        }
    }

    private static TowerGeneratorConfig overrideParametric(
            TowerGeneratorConfig actual,
            TowerGeneratorConfig expected) {
        if (PowerLineStyleParametricCatalog.parametersMatch(expected, actual)) {
            return null;
        }
        return actual != null ? actual.copy() : null;
    }
}
