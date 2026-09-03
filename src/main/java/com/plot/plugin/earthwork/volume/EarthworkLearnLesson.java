package com.plot.plugin.earthwork.volume;

import com.plot.core.material.MaterialConversionModel;

/**
 * 学习模式讲解：先讲挖/填结果，再对比「1 挖 ≈ 1 填」和现实换算例子。
 * 不引入新的土方模型，只包装 {@link EarthworkVolumeReport} 与 {@link MaterialConversionModel#LEARNING}。
 */
public final class EarthworkLearnLesson {
    private EarthworkLearnLesson() {
    }

    public record ConversionStory(
            long dug,
            long fillNeeded,
            long leftoverIfOneToOne,
            long missingIfOneToOne,
            long realityReusable,
            long realityUsableFill,
            long realityExport,
            long realityImport,
            float reusablePercent,
            float compactedPercent) {
        public boolean realityDiffersFromOneToOne() {
            return leftoverIfOneToOne != realityExport || missingIfOneToOne != realityImport;
        }
    }

    public static ConversionStory from(EarthworkVolumeReport volumes) {
        EarthworkVolumeReport safe = volumes != null ? volumes : EarthworkVolumeReport.empty();
        return fromGeometry(safe.geometricCutVolume(), safe.geometricFillVolume());
    }

    public static ConversionStory fromGeometry(long dug, long fillNeeded) {
        long cut = Math.max(0L, dug);
        long fill = Math.max(0L, fillNeeded);
        EarthworkVolumeReport reality = EarthworkVolumeReport.fromMetrics(
            cut, fill, MaterialConversionModel.LEARNING, 0L, 0L);
        return new ConversionStory(
            cut,
            fill,
            Math.max(0L, cut - fill),
            Math.max(0L, fill - cut),
            Math.round(reality.reusableCutVolume()),
            Math.round(reality.compactedFillSupply()),
            Math.round(reality.exportVolume()),
            Math.round(reality.importVolume()),
            MaterialConversionModel.LEARNING_REUSABLE_RATIO * 100.0f,
            MaterialConversionModel.LEARNING_CUT_TO_COMPACTED_FILL_RATIO * 100.0f);
    }
}
