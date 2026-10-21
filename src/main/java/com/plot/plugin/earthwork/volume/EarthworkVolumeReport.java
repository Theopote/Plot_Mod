package com.plot.plugin.earthwork.volume;

/**
 * Minecraft 土方算量：几何挖填与世界实际改方块数分离统计。
 * <p>
 * 计量规则为方块 1:1（挖 N 填 M → 净差 N−M），不模拟压实或材料利用率。
 */
public final class EarthworkVolumeReport {
    public static final EarthworkVolumeReport EMPTY = new EarthworkVolumeReport(0L, 0L, 0L, 0L);

    private final long geometricCutVolume;
    private final long geometricFillVolume;
    private final long cutChangedBlocks;
    private final long fillChangedBlocks;
    private final long totalChangedBlocks;

    public EarthworkVolumeReport(
            long geometricCutVolume,
            long geometricFillVolume,
            long cutChangedBlocks,
            long fillChangedBlocks) {
        this.geometricCutVolume = Math.max(0L, geometricCutVolume);
        this.geometricFillVolume = Math.max(0L, geometricFillVolume);
        this.cutChangedBlocks = Math.max(0L, cutChangedBlocks);
        this.fillChangedBlocks = Math.max(0L, fillChangedBlocks);
        this.totalChangedBlocks = this.cutChangedBlocks + this.fillChangedBlocks;
    }

    public static EarthworkVolumeReport empty() {
        return EMPTY;
    }

    public static EarthworkVolumeReport fromMetrics(
            long geometricCutVolume,
            long geometricFillVolume,
            long cutChangedBlocks,
            long fillChangedBlocks) {
        return new EarthworkVolumeReport(
            geometricCutVolume, geometricFillVolume, cutChangedBlocks, fillChangedBlocks);
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

    /** 几何净差：挖 − 填（正为多挖）。 */
    public long netGeometricVolume() {
        return geometricCutVolume - geometricFillVolume;
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

    /** 保留几何方量，只替换世界改方块计数。 */
    public EarthworkVolumeReport withChangedBlocks(long cutChangedBlocks, long fillChangedBlocks) {
        return new EarthworkVolumeReport(
            geometricCutVolume, geometricFillVolume, cutChangedBlocks, fillChangedBlocks);
    }
}
