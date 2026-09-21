package com.plot.plugin.powerline.model;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PowerLineSagUtils;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.style.PowerLineStyleDefinition;
import com.plot.plugin.powerline.style.PowerLineStyleParametricCatalog;
import com.plot.plugin.powerline.style.StyleOverrides;
import com.plot.plugin.powerline.style.TowerMaterialApplyMode;

import java.util.Objects;

/**
 * 线路风格实例状态：base preset、参数化塔配置、风格偏离项与 resolve 层。
 * <p>
 * 生效材质、塔型、垂度与参数化塔配置由 {@code preset + overrides → resolve} 得出，不再在 Footprint 上重复存储。
 */
public final class PowerLineStyleState {
    private static final double SAG_TOLERANCE = 0.01;
    private static final double MAX_SAG_TOLERANCE = 0.5;
    private static final double DEFAULT_SAG_RATIO = 0.15;
    /** {@link StyleOverrides#getMaxSagDepth()} 为 {@code -1} 时表示不限制单跨下垂深度。 */
    private static final double UNLIMITED_MAX_SAG_SENTINEL = -1.0;

    private String presetId;
    private PowerLineStyleDefinition appliedDefinition;
    private StyleOverrides overrides = new StyleOverrides();

    public String presetId() {
        return presetId;
    }

    public void setPresetId(String presetId) {
        this.presetId = presetId != null && presetId.isBlank() ? null : presetId;
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

    public double resolveSagRatio(PowerLineStyleDefinition definition) {
        Double override = overrides().getSagRatio();
        if (override != null) {
            return override;
        }
        return expectedSagRatio(definition != null ? definition : appliedDefinition);
    }

    public double resolveMaxSagDepth(PowerLineStyleDefinition definition) {
        Double override = overrides().getMaxSagDepth();
        if (override != null) {
            if (override <= UNLIMITED_MAX_SAG_SENTINEL + 0.5) {
                return 0.0;
            }
            return override;
        }
        return expectedMaxSagDepth(definition != null ? definition : appliedDefinition);
    }

    public boolean isMaxSagDepthUnlimited(PowerLineStyleDefinition definition) {
        Double override = overrides().getMaxSagDepth();
        if (override != null) {
            return override <= UNLIMITED_MAX_SAG_SENTINEL + 0.5;
        }
        return false;
    }

    public TowerGeneratorConfig resolveParametricConfig(PowerLineStyleDefinition definition) {
        if (overrides().isParametricSuppressed()) {
            return null;
        }
        TowerGeneratorConfig override = overrides().getParametricTowerConfig();
        if (override != null) {
            return override.copy();
        }
        PowerLineStyleDefinition resolvedDefinition = definition != null ? definition : appliedDefinition;
        if (resolvedDefinition != null && resolvedDefinition.hasParametricConfig()) {
            return resolvedDefinition.getParametricConfig();
        }
        return null;
    }

    public boolean hasParametricConfig(PowerLineStyleDefinition definition) {
        TowerGeneratorConfig resolved = resolveParametricConfig(definition);
        return resolved != null && resolved.isParametric();
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

    public TowerMaterialApplyMode resolveTowerMaterialApplyMode() {
        return overrides().getTowerMaterialApplyMode();
    }

    public void setTowerMaterialApplyMode(TowerMaterialApplyMode mode) {
        TowerMaterialApplyMode normalized = mode != null ? mode : TowerMaterialApplyMode.LEGS_ONLY;
        overrides().setTowerMaterialApplyMode(
            normalized == TowerMaterialApplyMode.LEGS_ONLY ? null : normalized);
    }

    public void setBraceMaterialOverride(MaterialMix braceMaterial) {
        overrides().setBraceMaterial(braceMaterial != null ? braceMaterial.copy() : null);
    }

    public void setArmChordMaterialOverride(MaterialMix armChordMaterial) {
        overrides().setArmChordMaterial(armChordMaterial != null ? armChordMaterial.copy() : null);
    }

    public void setArmBraceMaterialOverride(MaterialMix armBraceMaterial) {
        overrides().setArmBraceMaterial(armBraceMaterial != null ? armBraceMaterial.copy() : null);
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

    public void setSagRatio(double actual, PowerLineStyleDefinition definition) {
        double normalized = Math.max(0.0, Math.min(1.0, actual));
        overrides().setSagRatio(overrideSag(
            normalized,
            definition != null ? definition : appliedDefinition));
    }

    public void setMaxSagDepth(double actual, PowerLineStyleDefinition definition) {
        PowerLineStyleDefinition resolvedDefinition = definition != null ? definition : appliedDefinition;
        if (actual <= 0.0) {
            overrides().setMaxSagDepth(overrideUnlimitedMaxSagDepth(resolvedDefinition));
            return;
        }
        double normalized = Math.max(1.0, Math.min(64.0, actual));
        overrides().setMaxSagDepth(overrideMaxSagDepth(normalized, resolvedDefinition));
    }

    public void setParametricTowerConfig(TowerGeneratorConfig actual, PowerLineStyleDefinition definition) {
        PowerLineStyleDefinition resolvedDefinition = definition != null ? definition : appliedDefinition;
        if (actual == null || !actual.isParametric()) {
            if (resolvedDefinition != null && resolvedDefinition.hasParametricConfig()) {
                overrides().setParametricSuppressed(true);
            } else {
                overrides().setParametricSuppressed(false);
            }
            overrides().setParametricTowerConfig(null);
            return;
        }
        overrides().setParametricSuppressed(false);
        TowerGeneratorConfig expected = resolvedDefinition != null
            ? resolvedDefinition.getParametricConfig()
            : null;
        overrides().setParametricTowerConfig(overrideParametric(actual, expected));
    }

    public void clearMaterialAndTowerOverrides() {
        overrides().clearMaterialAndTower();
    }

    public void clearSagOverrides() {
        overrides().setSagRatio(null);
        overrides().setMaxSagDepth(null);
    }

    public void clearParametricOverrides() {
        overrides().setParametricSuppressed(false);
        overrides().setParametricTowerConfig(null);
    }

    public void clearPresetStyleOverrides() {
        clearMaterialAndTowerOverrides();
        clearSagOverrides();
        clearParametricOverrides();
    }

    /** 风格层指纹（preset + 生效参数化塔 + 风格偏离项）。 */
    public int generationFingerprint() {
        int hash = Objects.hashCode(presetId);
        hash = 31 * hash + parametricConfigFingerprint(resolveParametricConfig(resolveDefinition()));
        hash = 31 * hash + overrides().styleValueFingerprint();
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

    private static double expectedSagRatio(PowerLineStyleDefinition definition) {
        if (definition != null) {
            return definition.getSagPreset().ratio();
        }
        return DEFAULT_SAG_RATIO;
    }

    private static double expectedMaxSagDepth(PowerLineStyleDefinition definition) {
        if (definition != null) {
            return definition.getMaxSagDepth();
        }
        return PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;
    }

    private static Double overrideSag(double actual, PowerLineStyleDefinition definition) {
        double expected = expectedSagRatio(definition);
        return Math.abs(actual - expected) <= SAG_TOLERANCE ? null : actual;
    }

    private static Double overrideMaxSagDepth(double actual, PowerLineStyleDefinition definition) {
        double expected = expectedMaxSagDepth(definition);
        return Math.abs(actual - expected) <= MAX_SAG_TOLERANCE ? null : actual;
    }

    private static Double overrideUnlimitedMaxSagDepth(PowerLineStyleDefinition definition) {
        return UNLIMITED_MAX_SAG_SENTINEL;
    }

    private static TowerGeneratorConfig overrideParametric(
            TowerGeneratorConfig actual,
            TowerGeneratorConfig expected) {
        if (PowerLineStyleParametricCatalog.parametersMatch(expected, actual)) {
            return null;
        }
        return actual.copy();
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
