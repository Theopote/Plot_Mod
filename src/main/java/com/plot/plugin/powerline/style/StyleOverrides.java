package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;

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
    private MaterialMix topWireMaterial;
    private Double preferredSpacing;
    private Double recommendedMinSpacing;
    private String poleDesignId;
    private String towerFamilyId;
    private TowerGeneratorConfig parametricTowerConfig;

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

    public Double getRecommendedMinSpacing() {
        return recommendedMinSpacing;
    }

    public void setRecommendedMinSpacing(Double recommendedMinSpacing) {
        this.recommendedMinSpacing = recommendedMinSpacing;
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

    public boolean isEmpty() {
        return sagRatio == null
            && maxSagDepth == null
            && wireMaterial == null
            && poleMaterial == null
            && topWireMaterial == null
            && preferredSpacing == null
            && recommendedMinSpacing == null
            && poleDesignId == null
            && towerFamilyId == null
            && parametricTowerConfig == null;
    }

    public int overrideCount() {
        int count = 0;
        if (sagRatio != null) count++;
        if (maxSagDepth != null) count++;
        if (wireMaterial != null) count++;
        if (poleMaterial != null) count++;
        if (topWireMaterial != null) count++;
        if (preferredSpacing != null || recommendedMinSpacing != null) count++;
        if (poleDesignId != null) count++;
        if (towerFamilyId != null) count++;
        if (parametricTowerConfig != null) count++;
        return count;
    }

    public void clear() {
        sagRatio = null;
        maxSagDepth = null;
        wireMaterial = null;
        poleMaterial = null;
        topWireMaterial = null;
        preferredSpacing = null;
        recommendedMinSpacing = null;
        poleDesignId = null;
        towerFamilyId = null;
        parametricTowerConfig = null;
    }

    public StyleOverrides copy() {
        StyleOverrides copy = new StyleOverrides();
        copy.sagRatio = sagRatio;
        copy.maxSagDepth = maxSagDepth;
        copy.wireMaterial = wireMaterial != null ? wireMaterial.copy() : null;
        copy.poleMaterial = poleMaterial != null ? poleMaterial.copy() : null;
        copy.topWireMaterial = topWireMaterial != null ? topWireMaterial.copy() : null;
        copy.preferredSpacing = preferredSpacing;
        copy.recommendedMinSpacing = recommendedMinSpacing;
        copy.poleDesignId = poleDesignId;
        copy.towerFamilyId = towerFamilyId;
        copy.parametricTowerConfig = parametricTowerConfig != null ? parametricTowerConfig.copy() : null;
        return copy;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
