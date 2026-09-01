package com.plot.plugin.earthwork;

/**
 * 土方算量报告：几何挖填、材料调配与世界方块修改数分离统计。
 */
public final class EarthworkVolumeReport {
    public static final EarthworkVolumeReport EMPTY = new EarthworkVolumeReport(
        0L, 0L, 0.0, 0.0, 0.0, 0.0, 0L, 0L, 0L);

    private final long geometricCutVolume;
    private final long geometricFillVolume;
    private final double reusableCutVolume;
    private final double requiredFillMaterial;
    private final double importVolume;
    private final double exportVolume;
    private final long cutChangedBlocks;
    private final long fillChangedBlocks;
    private final long totalChangedBlocks;

    public EarthworkVolumeReport(
            long geometricCutVolume,
            long geometricFillVolume,
            double reusableCutVolume,
            double requiredFillMaterial,
            double importVolume,
            double exportVolume,
            long cutChangedBlocks,
            long fillChangedBlocks,
            long totalChangedBlocks) {
        this.geometricCutVolume = Math.max(0L, geometricCutVolume);
        this.geometricFillVolume = Math.max(0L, geometricFillVolume);
        this.reusableCutVolume = Math.max(0.0, reusableCutVolume);
        this.requiredFillMaterial = Math.max(0.0, requiredFillMaterial);
        this.importVolume = Math.max(0.0, importVolume);
        this.exportVolume = Math.max(0.0, exportVolume);
        this.cutChangedBlocks = Math.max(0L, cutChangedBlocks);
        this.fillChangedBlocks = Math.max(0L, fillChangedBlocks);
        this.totalChangedBlocks = Math.max(0L, totalChangedBlocks);
    }

    public static EarthworkVolumeReport empty() {
        return EMPTY;
    }

    /**
     * 由几何挖填量、压实系数与实际方块变更数合成完整报告。
     * 材料调配语义与 {@link EarthworkBalanceUtils} 平衡求解一致：
     * requiredFillMaterial = geometricFill × fillFactor，
     * 场内再利用 = min(挖方, 填方需求)，超出部分外运，不足部分外借。
     */
    public static EarthworkVolumeReport fromMetrics(
            long geometricCutVolume,
            long geometricFillVolume,
            float fillFactor,
            long cutChangedBlocks,
            long fillChangedBlocks) {
        double safeFillFactor = Math.max(1.0, fillFactor);
        double requiredFillMaterial = Math.round(geometricFillVolume * safeFillFactor);
        double reusableCutVolume = Math.min(geometricCutVolume, requiredFillMaterial);
        double exportVolume = Math.max(0.0, geometricCutVolume - requiredFillMaterial);
        double importVolume = Math.max(0.0, requiredFillMaterial - geometricCutVolume);
        long totalChangedBlocks = cutChangedBlocks + fillChangedBlocks;
        return new EarthworkVolumeReport(
            geometricCutVolume,
            geometricFillVolume,
            reusableCutVolume,
            requiredFillMaterial,
            importVolume,
            exportVolume,
            cutChangedBlocks,
            fillChangedBlocks,
            totalChangedBlocks);
    }

    public boolean hasGeometricVolume() {
        return geometricCutVolume > 0L || geometricFillVolume > 0L;
    }

    public long geometricCutVolume() {
        return geometricCutVolume;
    }

    public long geometricFillVolume() {
        return geometricFillVolume;
    }

    public double reusableCutVolume() {
        return reusableCutVolume;
    }

    public double requiredFillMaterial() {
        return requiredFillMaterial;
    }

    public double importVolume() {
        return importVolume;
    }

    public double exportVolume() {
        return exportVolume;
    }

    public long cutChangedBlocks() {
        return cutChangedBlocks;
    }

    public long fillChangedBlocks() {
        return fillChangedBlocks;
    }

    public long totalChangedBlocks() {
        return totalChangedBlocks;
    }
}
