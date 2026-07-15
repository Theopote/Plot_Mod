package com.plot.core.material;

/**
 * 主材质 + 可选点缀材质按比例混合的配置。
 */
public class MaterialMix {
    private String primaryMaterial;
    private String accentMaterial;
    private float accentRatio;

    public MaterialMix() {
    }

    public MaterialMix(String primaryMaterial, String accentMaterial, float accentRatio) {
        this.primaryMaterial = primaryMaterial;
        this.accentMaterial = accentMaterial;
        setAccentRatio(accentRatio);
    }

    public static MaterialMix single(String materialId) {
        return new MaterialMix(materialId, null, 0f);
    }

    public boolean hasAccent() {
        return accentMaterial != null && !accentMaterial.isBlank() && accentRatio > 0f;
    }

    public String getPrimaryMaterial() {
        return primaryMaterial;
    }

    public void setPrimaryMaterial(String primaryMaterial) {
        this.primaryMaterial = primaryMaterial;
    }

    public String getAccentMaterial() {
        return accentMaterial;
    }

    public void setAccentMaterial(String accentMaterial) {
        this.accentMaterial = accentMaterial;
    }

    public float getAccentRatio() {
        return accentRatio;
    }

    public void setAccentRatio(float accentRatio) {
        this.accentRatio = Math.max(0f, Math.min(0.5f, accentRatio));
    }

    public MaterialMix copy() {
        return new MaterialMix(primaryMaterial, accentMaterial, accentRatio);
    }

    public static MaterialMix orDefault(MaterialMix mix, MaterialMix fallback) {
        if (mix != null && mix.getPrimaryMaterial() != null && !mix.getPrimaryMaterial().isBlank()) {
            return mix;
        }
        return fallback;
    }
}
