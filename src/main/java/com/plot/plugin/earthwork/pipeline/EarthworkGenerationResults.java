package com.plot.plugin.earthwork.pipeline;

import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;

/**
 * {@link EarthworkGenerationResult} 复制工具。
 */
public final class EarthworkGenerationResults {

    private EarthworkGenerationResults() {
    }

    public static void copyInto(
            EarthworkGenerationResult target,
            EarthworkGenerationResult source) {
        target.existingTerrainSnapshot = source.existingTerrainSnapshot;
        target.placementRecords.putAll(source.placementRecords);
        target.changeTypes.putAll(source.changeTypes);
        target.placementMeta.putAll(source.placementMeta);
        target.gridSamples.addAll(source.gridSamples);
        target.volumeReport = source.volumeReport;
        target.siteVolumeReport = source.siteVolumeReport;
        target.projectReport = source.projectReport;
        target.designTerrainGrid = source.designTerrainGrid;
        target.elevationVolumeCurve = source.elevationVolumeCurve;
        target.sectionProfile = source.sectionProfile;
        target.elevationVolumeCurveRequiresSlopeCoupledSolver =
            source.elevationVolumeCurveRequiresSlopeCoupledSolver;
        target.resolvedElevation = source.resolvedElevation;
        target.resolvedElevationMin = source.resolvedElevationMin;
        target.resolvedElevationMax = source.resolvedElevationMax;
        target.slopedSurface = source.slopedSurface;
        target.siteGeneration = source.siteGeneration;
        target.calculationCellCount = source.calculationCellCount;
        target.balanceResidualImbalance = source.balanceResidualImbalance;
        target.balanceStatus = source.balanceStatus;
        target.balanceSearchHalfRange = source.balanceSearchHalfRange;
        target.unassignedCutChangedBlocks = source.unassignedCutChangedBlocks;
        target.unassignedFillChangedBlocks = source.unassignedFillChangedBlocks;
        target.structureChangedBlocks = source.structureChangedBlocks;
        target.warnings.addAll(source.warnings);
    }
}
