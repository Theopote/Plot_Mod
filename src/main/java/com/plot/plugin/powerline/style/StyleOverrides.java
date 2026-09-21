package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;

import java.util.Objects;

/**
 * 相对 base {@link PowerLineStyleDefinition} 的用户覆盖；
 * {@code null} 字段表示沿用预设默认值。
 * <p>
 * 与 {@link PowerLineStyleInstance#basePresetId()} 组成线路上的风格实例。
 */
public class StyleOverrides {
    private Double sagRatio;
    private Double maxSagDepth;
    private MaterialMix wireMaterial;
    private MaterialMix poleMaterial;
    private MaterialMix braceMaterial;
    private MaterialMix armChordMaterial;
    private MaterialMix armBraceMaterial;
    private TowerMaterialApplyMode towerMaterialApplyMode;
    private MaterialMix topWireMaterial;
    private Double preferredSpacing;
    private String poleDesignId;
    private String towerFamilyId;
    private TowerGeneratorConfig parametricTowerConfig;
    private Boolean parametricSuppressed;

    public Double getSagRatio() {
        return sagRatio;
    }

    public void setSagRatio(Double sagRatio) {
        this.sagRatio = sagRatio;
    }

    public Double getMaxSagDepth() {
        return maxSagDepth;
    }

    public void setMaxSagDepth(Double maxSagDepth) {
        this.maxSagDepth = maxSagDepth;
    }

    public MaterialMix getWireMaterial() {
        return wireMaterial;
    }

    public void setWireMaterial(MaterialMix wireMaterial) {
        this.wireMaterial = wireMaterial != null ? wireMaterial.copy() : null;
    }

    public MaterialMix getPoleMaterial() {
        return poleMaterial;
    }

    public void setPoleMaterial(MaterialMix poleMaterial) {
        this.poleMaterial = poleMaterial != null ? poleMaterial.copy() : null;
    }

    public MaterialMix getBraceMaterial() {
        return braceMaterial;
    }

    public void setBraceMaterial(MaterialMix braceMaterial) {
        this.braceMaterial = braceMaterial != null ? braceMaterial.copy() : null;
    }

    public MaterialMix getArmChordMaterial() {
        return armChordMaterial;
    }

    public void setArmChordMaterial(MaterialMix armChordMaterial) {
        this.armChordMaterial = armChordMaterial != null ? armChordMaterial.copy() : null;
    }

    public MaterialMix getArmBraceMaterial() {
        return armBraceMaterial;
    }

    public void setArmBraceMaterial(MaterialMix armBraceMaterial) {
        this.armBraceMaterial = armBraceMaterial != null ? armBraceMaterial.copy() : null;
    }

    public TowerMaterialApplyMode getTowerMaterialApplyMode() {
        return towerMaterialApplyMode != null ? towerMaterialApplyMode : TowerMaterialApplyMode.LEGS_ONLY;
    }

    public void setTowerMaterialApplyMode(TowerMaterialApplyMode towerMaterialApplyMode) {
        this.towerMaterialApplyMode = towerMaterialApplyMode;
    }

    public MaterialMix getTopWireMaterial() {
        return topWireMaterial;
    }

    public void setTopWireMaterial(MaterialMix topWireMaterial) {
        this.topWireMaterial = topWireMaterial != null ? topWireMaterial.copy() : null;
    }

    public Double getPreferredSpacing() {
        return preferredSpacing;
    }

    public void setPreferredSpacing(Double preferredSpacing) {
        this.preferredSpacing = preferredSpacing;
    }

    public String getPoleDesignId() {
        return poleDesignId;
    }

    public void setPoleDesignId(String poleDesignId) {
        this.poleDesignId = blankToNull(poleDesignId);
    }

    public String getTowerFamilyId() {
        return towerFamilyId;
    }

    public void setTowerFamilyId(String towerFamilyId) {
        this.towerFamilyId = blankToNull(towerFamilyId);
    }

    public TowerGeneratorConfig getParametricTowerConfig() {
        return parametricTowerConfig != null ? parametricTowerConfig.copy() : null;
    }

    public void setParametricTowerConfig(TowerGeneratorConfig parametricTowerConfig) {
        this.parametricTowerConfig = parametricTowerConfig != null ? parametricTowerConfig.copy() : null;
    }

    public boolean isParametricSuppressed() {
        return Boolean.TRUE.equals(parametricSuppressed);
    }

    public void setParametricSuppressed(boolean parametricSuppressed) {
        this.parametricSuppressed = parametricSuppressed ? Boolean.TRUE : null;
    }

    public boolean isEmpty() {
        return sagRatio == null
            && maxSagDepth == null
            && wireMaterial == null
            && poleMaterial == null
            && braceMaterial == null
            && armChordMaterial == null
            && armBraceMaterial == null
            && towerMaterialApplyMode == null
            && topWireMaterial == null
            && preferredSpacing == null
            && poleDesignId == null
            && towerFamilyId == null
            && parametricTowerConfig == null
            && !isParametricSuppressed();
    }

    public int overrideCount() {
        int count = 0;
        if (sagRatio != null) count++;
        if (maxSagDepth != null) count++;
        if (wireMaterial != null) count++;
        if (poleMaterial != null) count++;
        if (braceMaterial != null) count++;
        if (armChordMaterial != null) count++;
        if (armBraceMaterial != null) count++;
        if (towerMaterialApplyMode != null && towerMaterialApplyMode != TowerMaterialApplyMode.LEGS_ONLY) count++;
        if (topWireMaterial != null) count++;
        if (preferredSpacing != null) count++;
        if (poleDesignId != null) count++;
        if (towerFamilyId != null) count++;
        if (parametricTowerConfig != null) count++;
        if (isParametricSuppressed()) count++;
        return count;
    }

    public void clearMaterialAndTower() {
        wireMaterial = null;
        poleMaterial = null;
        braceMaterial = null;
        armChordMaterial = null;
        armBraceMaterial = null;
        towerMaterialApplyMode = null;
        topWireMaterial = null;
        poleDesignId = null;
        towerFamilyId = null;
    }

    public int materialAndTowerFingerprint() {
        int hash = materialFingerprint(wireMaterial);
        hash = 31 * hash + materialFingerprint(poleMaterial);
        hash = 31 * hash + materialFingerprint(braceMaterial);
        hash = 31 * hash + materialFingerprint(armChordMaterial);
        hash = 31 * hash + materialFingerprint(armBraceMaterial);
        hash = 31 * hash + Objects.hashCode(towerMaterialApplyMode);
        hash = 31 * hash + materialFingerprint(topWireMaterial);
        hash = 31 * hash + Objects.hashCode(poleDesignId);
        hash = 31 * hash + Objects.hashCode(towerFamilyId);
        return hash;
    }

    public void clearParametric() {
        parametricTowerConfig = null;
        parametricSuppressed = null;
    }

    public int styleValueFingerprint() {
        int hash = materialAndTowerFingerprint();
        hash = 31 * hash + Objects.hashCode(sagRatio);
        hash = 31 * hash + Objects.hashCode(maxSagDepth);
        hash = 31 * hash + parametricOverrideFingerprint();
        return hash;
    }

    private int parametricOverrideFingerprint() {
        int hash = Objects.hashCode(parametricSuppressed);
        if (parametricTowerConfig == null || !parametricTowerConfig.isParametric()) {
            return hash;
        }
        TowerParameterSet parameters = parametricTowerConfig.parameters();
        hash = 31 * hash + Objects.hashCode(parametricTowerConfig.profileId());
        hash = 31 * hash + Double.hashCode(parameters.height());
        hash = 31 * hash + Double.hashCode(parameters.baseWidth());
        hash = 31 * hash + Double.hashCode(parameters.armSpan());
        hash = 31 * hash + Double.hashCode(parameters.depthScale());
        hash = 31 * hash + Double.hashCode(parameters.waistRatio());
        hash = 31 * hash + Objects.hashCode(parameters.density());
        hash = 31 * hash + Objects.hashCode(parameters.armLevelScales());
        return hash;
    }

    public void clear() {
        sagRatio = null;
        maxSagDepth = null;
        wireMaterial = null;
        poleMaterial = null;
        braceMaterial = null;
        armChordMaterial = null;
        armBraceMaterial = null;
        towerMaterialApplyMode = null;
        topWireMaterial = null;
        preferredSpacing = null;
        poleDesignId = null;
        towerFamilyId = null;
        parametricTowerConfig = null;
        parametricSuppressed = null;
    }

    private static int materialFingerprint(MaterialMix mix) {
        if (mix == null) {
            return 0;
        }
        int hash = Objects.hashCode(mix.getPrimaryMaterial());
        hash = 31 * hash + Objects.hashCode(mix.getAccentMaterial());
        hash = 31 * hash + Float.hashCode(mix.getAccentRatio());
        return hash;
    }

    public StyleOverrides copy() {
        StyleOverrides copy = new StyleOverrides();
        copy.sagRatio = sagRatio;
        copy.maxSagDepth = maxSagDepth;
        copy.wireMaterial = wireMaterial != null ? wireMaterial.copy() : null;
        copy.poleMaterial = poleMaterial != null ? poleMaterial.copy() : null;
        copy.braceMaterial = braceMaterial != null ? braceMaterial.copy() : null;
        copy.armChordMaterial = armChordMaterial != null ? armChordMaterial.copy() : null;
        copy.armBraceMaterial = armBraceMaterial != null ? armBraceMaterial.copy() : null;
        copy.towerMaterialApplyMode = towerMaterialApplyMode;
        copy.topWireMaterial = topWireMaterial != null ? topWireMaterial.copy() : null;
        copy.preferredSpacing = preferredSpacing;
        copy.poleDesignId = poleDesignId;
        copy.towerFamilyId = towerFamilyId;
        copy.parametricTowerConfig = parametricTowerConfig != null ? parametricTowerConfig.copy() : null;
        copy.parametricSuppressed = parametricSuppressed;
        return copy;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
