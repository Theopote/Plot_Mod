package com.plot.plugin.powerline.style;

import com.plot.core.material.MaterialMix;

/** 相对 Base Preset 的用户覆盖；{@code null} 字段表示沿用预设默认值。 */
public class StyleOverrides {
    private Double sagRatio;
    private Double maxSagDepth;
    private MaterialMix wireMaterial;
    private MaterialMix poleMaterial;
    private MaterialMix groundWireMaterial;
    private Double preferredSpacing;
    private Double recommendedMinSpacing;
    private String poleDesignId;
    private String towerFamilyId;

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

    public MaterialMix getGroundWireMaterial() {
        return groundWireMaterial;
    }

    public void setGroundWireMaterial(MaterialMix groundWireMaterial) {
        this.groundWireMaterial = groundWireMaterial != null ? groundWireMaterial.copy() : null;
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

    public boolean isEmpty() {
        return sagRatio == null
            && maxSagDepth == null
            && wireMaterial == null
            && poleMaterial == null
            && groundWireMaterial == null
            && preferredSpacing == null
            && recommendedMinSpacing == null
            && poleDesignId == null
            && towerFamilyId == null;
    }

    public int overrideCount() {
        int count = 0;
        if (sagRatio != null) count++;
        if (maxSagDepth != null) count++;
        if (wireMaterial != null) count++;
        if (poleMaterial != null) count++;
        if (groundWireMaterial != null) count++;
        if (preferredSpacing != null || recommendedMinSpacing != null) count++;
        if (poleDesignId != null) count++;
        if (towerFamilyId != null) count++;
        return count;
    }

    public void clear() {
        sagRatio = null;
        maxSagDepth = null;
        wireMaterial = null;
        poleMaterial = null;
        groundWireMaterial = null;
        preferredSpacing = null;
        recommendedMinSpacing = null;
        poleDesignId = null;
        towerFamilyId = null;
    }

    public StyleOverrides copy() {
        StyleOverrides copy = new StyleOverrides();
        copy.sagRatio = sagRatio;
        copy.maxSagDepth = maxSagDepth;
        copy.wireMaterial = wireMaterial != null ? wireMaterial.copy() : null;
        copy.poleMaterial = poleMaterial != null ? poleMaterial.copy() : null;
        copy.groundWireMaterial = groundWireMaterial != null ? groundWireMaterial.copy() : null;
        copy.preferredSpacing = preferredSpacing;
        copy.recommendedMinSpacing = recommendedMinSpacing;
        copy.poleDesignId = poleDesignId;
        copy.towerFamilyId = towerFamilyId;
        return copy;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
