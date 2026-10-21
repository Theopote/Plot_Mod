package com.plot.plugin.earthwork.volume;
import com.plot.core.material.MaterialConversionModel;

/**
 * 土方算量报告：几何挖填、材料调配与世界方块修改数分离统计。
 */
public final class EarthworkVolumeReport {
    public static final EarthworkVolumeReport EMPTY = new EarthworkVolumeReport(
        0L, 0L, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0L, 0L);

    private final long geometricCutVolume;
    private final long geometricFillVolume;
    private final double reusableCutVolume;
    private final double compactedFillSupply;
    private final double compactedFillDemand;
    private final double importVolume;
    private final double exportVolume;
    private final long cutChangedBlocks;
    private final long fillChangedBlocks;
    private final long totalChangedBlocks;

    public EarthworkVolumeReport(
            long geometricCutVolume,
            long geometricFillVolume,
            double reusableCutVolume,
            double compactedFillSupply,
            double compactedFillDemand,
            double importVolume,
            double exportVolume,
            long cutChangedBlocks,
            long fillChangedBlocks,
            long totalChangedBlocks) {
        this.geometricCutVolume = Math.max(0L, geometricCutVolume);
        this.geometricFillVolume = Math.max(0L, geometricFillVolume);
        this.reusableCutVolume = Math.max(0.0, reusableCutVolume);
        this.compactedFillSupply = Math.max(0.0, compactedFillSupply);
        this.compactedFillDemand = Math.max(0.0, compactedFillDemand);
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
     * 由几何挖填量与实际方块变更数合成报告。
     * <p>
     * Minecraft 1:1：挖 1 ≈ 填 1；压实/可利用字段仅作兼容别名，等于几何量。
     * {@code materials} 参数保留签名兼容，不再参与主路径计算。
     */
    public static EarthworkVolumeReport fromMetrics(
            long geometricCutVolume,
            long geometricFillVolume,
            MaterialConversionModel materials,
            long cutChangedBlocks,
            long fillChangedBlocks) {
        long cut = Math.max(0L, geometricCutVolume);
        long fill = Math.max(0L, geometricFillVolume);
        double reusableCutVolume = cut;
        double compactedFillSupplyFromCut = cut;
        double compactedFillDemand = fill;
        double importVolume = Math.max(0.0, fill - cut);
        double exportVolume = Math.max(0.0, cut - fill);
        long totalChangedBlocks = cutChangedBlocks + fillChangedBlocks;
        return new EarthworkVolumeReport(
            cut,
            fill,
            reusableCutVolume,
            compactedFillSupplyFromCut,
            compactedFillDemand,
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

    /** 由可再利用挖方换算得到的压实填方供给量。 */
    public double compactedFillSupply() {
        return compactedFillSupply;
    }

    /** 填方压实方需求（几何填方量）。 */
    public double compactedFillDemand() {
        return compactedFillDemand;
    }

    /** 可供其它分区调配的压实填方余量。 */
    public double compactedFillSurplus() {
        return Math.max(0.0, compactedFillSupply - compactedFillDemand);
    }

    /** 需由其它分区或场外补充的压实填方缺量。 */
    public double compactedFillDeficit() {
        return Math.max(0.0, compactedFillDemand - compactedFillSupply);
    }

    /**
     * 将压实填方调配量按本报告的挖方供给比换算为几何挖方（自然方）。
     */
    public long geometricCutForCompactedTransfer(long compactedFill) {
        if (compactedFill <= 0L || compactedFillSupply <= 0.0 || geometricCutVolume <= 0L) {
            return 0L;
        }
        return Math.round(compactedFill * (double) geometricCutVolume / compactedFillSupply);
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

    /**
     * 保留几何/材料方量，只替换世界改方块计数（预览虚影、落地、报告共用同一份 placement）。
     */
    public EarthworkVolumeReport withChangedBlocks(long cutChangedBlocks, long fillChangedBlocks) {
        long cut = Math.max(0L, cutChangedBlocks);
        long fill = Math.max(0L, fillChangedBlocks);
        return new EarthworkVolumeReport(
            geometricCutVolume,
            geometricFillVolume,
            reusableCutVolume,
            compactedFillSupply,
            compactedFillDemand,
            importVolume,
            exportVolume,
            cut,
            fill,
            cut + fill);
    }
}
