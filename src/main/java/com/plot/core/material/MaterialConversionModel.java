package com.plot.core.material;

/**
 * 土方材料换算参数：定义挖方自然方如何转化为可用填方压实方。
 * <p>
 * 平衡目标：{@code geometricCut × reusableRatio × cutToCompactedFillRatio ≈ geometricFill}。
 */
public final class MaterialConversionModel {
    public static final float DEFAULT_REUSABLE_RATIO = 0.90f;
    public static final float DEFAULT_CUT_TO_COMPACTED_FILL_RATIO = 0.92f;
    public static final MaterialConversionModel DEFAULT = new MaterialConversionModel(
        DEFAULT_REUSABLE_RATIO,
        DEFAULT_CUT_TO_COMPACTED_FILL_RATIO);

    private final float reusableRatio;
    private final float cutToCompactedFillRatio;

    public MaterialConversionModel(float reusableRatio, float cutToCompactedFillRatio) {
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
    public static MaterialConversionModel fromLegacyFillFactor(float fillFactor) {
        float safeFillFactor = Math.max(1.0f, Math.min(2.0f, fillFactor));
        return new MaterialConversionModel(1.0f, 1.0f / safeFillFactor);
    }

    public MaterialConversionModel withReusableRatio(float reusableRatio) {
        return new MaterialConversionModel(reusableRatio, cutToCompactedFillRatio);
    }

    public MaterialConversionModel withCutToCompactedFillRatio(float cutToCompactedFillRatio) {
        return new MaterialConversionModel(reusableRatio, cutToCompactedFillRatio);
    }

    private static float clampRatio(float value) {
        return Math.max(0.01f, Math.min(1.0f, value));
    }
}
