package com.plot.plugin.powerline.model;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.style.PowerLineStyleDefinition;
import com.plot.plugin.powerline.style.StyleOverrides;

import java.util.Objects;

/**
 * 线路风格实例状态：base preset、参数化塔配置、材质/塔型偏离项与 resolve 层。
 * <p>
 * 生效材质与塔型由 {@code preset + overrides → resolve} 得出，不再在 Footprint 上重复存储。
 */
public final class PowerLineStyleState {
    private String presetId;
    private TowerGeneratorConfig parametricTowerConfig;
    private PowerLineStyleDefinition appliedDefinition;
    private StyleOverrides overrides = new StyleOverrides();

    public String presetId() {
        return presetId;
    }

    public void setPresetId(String presetId) {
        this.presetId = presetId != null && presetId.isBlank() ? null : presetId;
    }

    public TowerGeneratorConfig parametricConfig() {
        return parametricTowerConfig != null ? parametricTowerConfig.copy() : null;
    }

    public void setParametricConfig(TowerGeneratorConfig parametricTowerConfig) {
        this.parametricTowerConfig = parametricTowerConfig != null ? parametricTowerConfig.copy() : null;
    }

    public boolean hasParametricConfig() {
        return parametricTowerConfig != null && parametricTowerConfig.isParametric();
    }

    public void setAppliedDefinition(PowerLineStyleDefinition appliedDefinition) {
        this.appliedDefinition = appliedDefinition;
    }

    public PowerLineStyleDefinition appliedDefinition() {
        return appliedDefinition;
    }

    public PowerLineStyleDefinition resolveDefinition() {
        if (appliedDefinition != null) {
            return appliedDefinition;
        }
        return com.plot.plugin.powerline.style.PowerLineStylePresetCatalog.definitionForPresetId(presetId);
    }

    public StyleOverrides overrides() {
        if (overrides == null) {
            overrides = new StyleOverrides();
        }
        return overrides;
    }

    public void clearOverrides() {
        overrides().clear();
    }

    public MaterialMix resolveWireMaterial(PowerLineStyleDefinition definition) {
        MaterialMix override = overrides().getWireMaterial();
        if (override != null) {
            return override.copy();
        }
        if (definition != null && definition.getWireMaterial() != null) {
            return definition.getWireMaterial().copy();
        }
        return MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL);
    }

    public MaterialMix resolvePoleMaterial(PowerLineStyleDefinition definition) {
        MaterialMix override = overrides().getPoleMaterial();
        if (override != null) {
            return override.copy();
        }
        if (definition != null && definition.getPoleMaterial() != null) {
            return definition.getPoleMaterial().copy();
        }
        return MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL);
    }

    public MaterialMix resolveTopWireMaterial(PowerLineStyleDefinition definition) {
        MaterialMix override = overrides().getTopWireMaterial();
        if (override != null) {
            return override.copy();
        }
        if (definition != null && definition.getTopWireMaterial() != null) {
            return definition.getTopWireMaterial().copy();
        }
        return MaterialMix.single("minecraft:chain");
    }

    public String resolveTowerFamilyId(PowerLineStyleDefinition definition) {
        String override = overrides().getTowerFamilyId();
        if (override != null) {
            return override;
        }
        PowerLineStyleDefinition resolvedDefinition = definition != null ? definition : appliedDefinition;
        return resolvedDefinition != null ? resolvedDefinition.getTowerFamilyId() : null;
    }

    public String resolvePoleDesignId(PowerLineStyleDefinition definition) {
        String override = overrides().getPoleDesignId();
        if (override != null) {
            return override;
        }
        PowerLineStyleDefinition resolvedDefinition = definition != null ? definition : appliedDefinition;
        return resolvedDefinition != null ? resolvedDefinition.getPoleDesignId() : null;
    }

    public void setWireMaterial(MaterialMix actual, PowerLineStyleDefinition definition) {
        MaterialMix normalized = actual != null
            ? actual.copy()
            : resolveWireMaterial(definition);
        overrides().setWireMaterial(overrideMaterial(
            normalized,
            definition != null ? definition.getWireMaterial() : null,
            MaterialMix.single(PowerLineFootprint.DEFAULT_WIRE_MATERIAL)));
    }

    public void setPoleMaterial(MaterialMix actual, PowerLineStyleDefinition definition) {
        MaterialMix normalized = actual != null
            ? actual.copy()
            : resolvePoleMaterial(definition);
        overrides().setPoleMaterial(overrideMaterial(
            normalized,
            definition != null ? definition.getPoleMaterial() : null,
            MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL)));
    }

    public void setTopWireMaterial(MaterialMix actual, PowerLineStyleDefinition definition) {
        MaterialMix normalized = actual != null
            ? actual.copy()
            : resolveTopWireMaterial(definition);
        overrides().setTopWireMaterial(overrideMaterial(
            normalized,
            definition != null ? definition.getTopWireMaterial() : null,
            MaterialMix.single("minecraft:chain")));
    }

    public void setTowerFamilyId(String actual, PowerLineStyleDefinition definition) {
        String normalized = blankToNull(actual);
        PowerLineStyleDefinition resolvedDefinition = definition != null ? definition : appliedDefinition;
        overrides().setTowerFamilyId(overrideId(
            normalized,
            resolvedDefinition != null ? resolvedDefinition.getTowerFamilyId() : null));
    }

    public void setPoleDesignId(String actual, PowerLineStyleDefinition definition) {
        String normalized = blankToNull(actual);
        PowerLineStyleDefinition resolvedDefinition = definition != null ? definition : appliedDefinition;
        overrides().setPoleDesignId(overrideId(
            normalized,
            resolvedDefinition != null ? resolvedDefinition.getPoleDesignId() : null));
    }

    public void clearMaterialAndTowerOverrides() {
        overrides().clearMaterialAndTower();
    }

    /** 风格层指纹（preset + 参数化塔 + 材质/塔型偏离）。 */
    public int generationFingerprint() {
        int hash = Objects.hashCode(presetId);
        hash = 31 * hash + parametricConfigFingerprint(parametricTowerConfig);
        hash = 31 * hash + overrides().materialAndTowerFingerprint();
        return hash;
    }

    private static int parametricConfigFingerprint(TowerGeneratorConfig config) {
        if (config == null || !config.isParametric()) {
            return 0;
        }
        TowerParameterSet parameters = config.parameters();
        int hash = Objects.hashCode(config.profileId());
        hash = 31 * hash + Double.hashCode(parameters.height());
        hash = 31 * hash + Double.hashCode(parameters.baseWidth());
        hash = 31 * hash + Double.hashCode(parameters.armSpan());
        hash = 31 * hash + Double.hashCode(parameters.depthScale());
        hash = 31 * hash + Double.hashCode(parameters.waistRatio());
        hash = 31 * hash + Objects.hashCode(parameters.density());
        hash = 31 * hash + Objects.hashCode(parameters.armLevelScales());
        return hash;
    }

    private static MaterialMix overrideMaterial(
            MaterialMix actual,
            MaterialMix expected,
            MaterialMix platformDefault) {
        if (actual == null) {
            return null;
        }
        MaterialMix baseline = expected != null ? expected : platformDefault;
        if (baseline == null) {
            return actual.copy();
        }
        if (Objects.equals(actual.getPrimaryMaterial(), baseline.getPrimaryMaterial())
                && Objects.equals(blankToNull(actual.getAccentMaterial()), blankToNull(baseline.getAccentMaterial()))
                && Float.compare(actual.getAccentRatio(), baseline.getAccentRatio()) == 0) {
            return null;
        }
        return actual.copy();
    }

    private static String overrideId(String actual, String expected) {
        String normalizedActual = blankToNull(actual);
        String normalizedExpected = blankToNull(expected);
        return Objects.equals(normalizedActual, normalizedExpected) ? null : normalizedActual;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
