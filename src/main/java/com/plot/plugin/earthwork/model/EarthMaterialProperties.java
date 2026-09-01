package com.plot.plugin.earthwork.model;

/**
 * 土方材料换算参数：定义挖方自然方如何转化为可用填方压实方。
 * <p>
 * 平衡目标：{@code geometricCut × reusableRatio × cutToCompactedFillRatio ≈ geometricFill}。
 */
public final class EarthMaterialProperties {
    public static final float DEFAULT_REUSABLE_RATIO = 0.90f;
    public static final float DEFAULT_CUT_TO_COMPACTED_FILL_RATIO = 0.92f;
    public static final EarthMaterialProperties DEFAULT = new EarthMaterialProperties(
        DEFAULT_REUSABLE_RATIO,
        DEFAULT_CUT_TO_COMPACTED_FILL_RATIO);

    private final float reusableRatio;
    private final float cutToCompactedFillRatio;

    public EarthMaterialProperties(float reusableRatio, float cutToCompactedFillRatio) {
        this.reusableRatio = clampRatio(reusableRatio);
        this.cutToCompactedFillRatio = clampRatio(cutToCompactedFillRatio);
    }

    /**
     * 场内可再利用挖方占几何挖方的比例（自然方 / 自然方）。
     */
    public float reusableRatio() {
        return reusableRatio;
    }

    /**
     * 每单位可再利用挖方（自然方）可形成的填方压实方量。
     */
    public float cutToCompactedFillRatio() {
        return cutToCompactedFillRatio;
    }

    /**
     * 综合换算：几何挖方自然方 → 可形成的填方压实方。
     */
    public double effectiveCutToCompactedFillRatio() {
        return reusableRatio * cutToCompactedFillRatio;
    }

    /**
     * 由旧版 {@code fillFactor}（填方松散系数）迁移。
     * 旧平衡式 {@code cut = fill × fillFactor} 等价于 {@code reusableRatio=1, cutToCompactedFillRatio=1/fillFactor}。
     */
    public static EarthMaterialProperties fromLegacyFillFactor(float fillFactor) {
        float safeFillFactor = Math.max(1.0f, Math.min(2.0f, fillFactor));
        return new EarthMaterialProperties(1.0f, 1.0f / safeFillFactor);
    }

    public EarthMaterialProperties withReusableRatio(float reusableRatio) {
        return new EarthMaterialProperties(reusableRatio, cutToCompactedFillRatio);
    }

    public EarthMaterialProperties withCutToCompactedFillRatio(float cutToCompactedFillRatio) {
        return new EarthMaterialProperties(reusableRatio, cutToCompactedFillRatio);
    }

    private static float clampRatio(float value) {
        return Math.max(0.01f, Math.min(1.0f, value));
    }
}
