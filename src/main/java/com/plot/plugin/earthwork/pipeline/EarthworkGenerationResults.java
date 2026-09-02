package com.plot.plugin.earthwork.pipeline;

import com.plot.plugin.earthwork.EarthworkGenerator;

/**
 * {@link EarthworkGenerator.EarthworkGenerationResult} 复制工具。
 */
public final class EarthworkGenerationResults {

    private EarthworkGenerationResults() {
    }

    public static void copyInto(
            EarthworkGenerator.EarthworkGenerationResult target,
            EarthworkGenerator.EarthworkGenerationResult source) {
        target.existingTerrainSnapshot = source.existingTerrainSnapshot;
        target.placementRecords.putAll(source.placementRecords);
        target.changeTypes.putAll(source.changeTypes);
        target.gridSamples.addAll(source.gridSamples);
        target.volumeReport = source.volumeReport;
        target.siteVolumeReport = source.siteVolumeReport;
        target.projectReport = source.projectReport;
        target.resolvedElevation = source.resolvedElevation;
        target.resolvedElevationMin = source.resolvedElevationMin;
        target.resolvedElevationMax = source.resolvedElevationMax;
        target.slopedSurface = source.slopedSurface;
        target.calculationCellCount = source.calculationCellCount;
        target.warnings.addAll(source.warnings);
    }
}
