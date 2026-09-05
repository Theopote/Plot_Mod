package com.plot.plugin.building.generation;

import com.plot.core.command.commands.BuildingGenerateCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * 片区落地完成后的只读报告（预览清除后仍保留，供 Generate Tab 展示）。
 */
public final class DistrictBuildReport {

    public record SkipItem(String buildingId, String buildingName, String reasonKey, String errorDetail) {
    }

    private final int buildingsGenerated;
    private final int buildingsSkipped;
    private final int buildingsAttempted;
    private final double totalArea;
    private final double totalVolume;
    private final int plannedBlocks;
    private final int placedBlocks;
    private final int failedBlocks;
    private final boolean cancelled;
    private final boolean placementFullSuccess;
    private final List<SkipItem> skipped;
    private final List<String> warnings;
    private final int overlappingPairCount;
    private final int conflictingBlockCount;
    private final int waterSiteCount;
    private final int partialWaterSiteCount;
    private final int steepSiteCount;
    private final int structureConflictBuildingCount;
    private final int heavyEarthworkSiteCount;

    public DistrictBuildReport(
            int buildingsGenerated,
            int buildingsSkipped,
            int buildingsAttempted,
            double totalArea,
            double totalVolume,
            int plannedBlocks,
            int placedBlocks,
            int failedBlocks,
            boolean cancelled,
            boolean placementFullSuccess,
            List<SkipItem> skipped,
            List<String> warnings,
            int overlappingPairCount,
            int conflictingBlockCount) {
        this(
            buildingsGenerated,
            buildingsSkipped,
            buildingsAttempted,
            totalArea,
            totalVolume,
            plannedBlocks,
            placedBlocks,
            failedBlocks,
            cancelled,
            placementFullSuccess,
            skipped,
            warnings,
            overlappingPairCount,
            conflictingBlockCount,
            0,
            0,
            0,
            0,
            0);
    }

    public DistrictBuildReport(
            int buildingsGenerated,
            int buildingsSkipped,
            int buildingsAttempted,
            double totalArea,
            double totalVolume,
            int plannedBlocks,
            int placedBlocks,
            int failedBlocks,
            boolean cancelled,
            boolean placementFullSuccess,
            List<SkipItem> skipped,
            List<String> warnings,
            int overlappingPairCount,
            int conflictingBlockCount,
            int waterSiteCount,
            int partialWaterSiteCount,
            int steepSiteCount,
            int structureConflictBuildingCount,
            int heavyEarthworkSiteCount) {
        this.buildingsGenerated = buildingsGenerated;
        this.buildingsSkipped = buildingsSkipped;
        this.buildingsAttempted = buildingsAttempted;
        this.totalArea = totalArea;
        this.totalVolume = totalVolume;
        this.plannedBlocks = plannedBlocks;
        this.placedBlocks = placedBlocks;
        this.failedBlocks = failedBlocks;
        this.cancelled = cancelled;
        this.placementFullSuccess = placementFullSuccess;
        this.skipped = List.copyOf(skipped != null ? skipped : List.of());
        this.warnings = List.copyOf(warnings != null ? warnings : List.of());
        this.overlappingPairCount = Math.max(0, overlappingPairCount);
        this.conflictingBlockCount = Math.max(0, conflictingBlockCount);
        this.waterSiteCount = Math.max(0, waterSiteCount);
        this.partialWaterSiteCount = Math.max(0, partialWaterSiteCount);
        this.steepSiteCount = Math.max(0, steepSiteCount);
        this.structureConflictBuildingCount = Math.max(0, structureConflictBuildingCount);
        this.heavyEarthworkSiteCount = Math.max(0, heavyEarthworkSiteCount);
    }

    public static DistrictBuildReport from(
            DistrictGenerationResult district,
            BuildingGenerateCommand.ExecutionResult placement) {
        List<SkipItem> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int overlapPairs = 0;
        int conflictBlocks = 0;
        int waterSites = 0;
        int partialWater = 0;
        int steep = 0;
        int structureConflicts = 0;
        int heavyEarthwork = 0;
        if (district != null) {
            for (DistrictGenerationResult.BuildingOutcome outcome : district.skippedOutcomes()) {
                skipped.add(new SkipItem(
                    outcome.buildingId(),
                    outcome.buildingName(),
                    outcome.skipReason() != null ? outcome.skipReason().i18nKey() : "",
                    outcome.errorDetail()));
            }
            warnings.addAll(district.warnings());
            overlapPairs = district.overlappingBuildingPairs().size();
            conflictBlocks = district.conflictingBlockCount();
            waterSites = district.waterSiteCount();
            partialWater = district.partialWaterSiteCount();
            steep = district.steepSiteCount();
            structureConflicts = district.structureConflictBuildingCount();
            heavyEarthwork = district.heavyEarthworkSiteCount();
        }

        int planned = district != null ? district.totalBlocks() : 0;
        int placed = placement != null ? placement.success() : 0;
        int failed = placement != null ? placement.failed() : 0;
        boolean cancelled = placement != null && placement.cancelled();
        boolean fullSuccess = placement != null && placement.isFullSuccess();

        return new DistrictBuildReport(
            district != null ? district.buildingsGenerated() : 0,
            district != null ? district.buildingsSkipped() : 0,
            district != null ? district.buildingsAttempted() : 0,
            district != null ? district.totalArea() : 0.0,
            district != null ? district.totalVolume() : 0.0,
            planned,
            placed,
            failed,
            cancelled,
            fullSuccess,
            skipped,
            warnings,
            overlapPairs,
            conflictBlocks,
            waterSites,
            partialWater,
            steep,
            structureConflicts,
            heavyEarthwork);
    }

    public int buildingsGenerated() {
        return buildingsGenerated;
    }

    public int buildingsSkipped() {
        return buildingsSkipped;
    }

    public int buildingsAttempted() {
        return buildingsAttempted;
    }

    public double totalArea() {
        return totalArea;
    }

    public double totalVolume() {
        return totalVolume;
    }

    public int plannedBlocks() {
        return plannedBlocks;
    }

    public int placedBlocks() {
        return placedBlocks;
    }

    public int failedBlocks() {
        return failedBlocks;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public boolean placementFullSuccess() {
        return placementFullSuccess;
    }

    public List<SkipItem> skipped() {
        return skipped;
    }

    public List<String> warnings() {
        return warnings;
    }

    public int overlappingPairCount() {
        return overlappingPairCount;
    }

    public int conflictingBlockCount() {
        return conflictingBlockCount;
    }

    public int waterSiteCount() {
        return waterSiteCount;
    }

    public int partialWaterSiteCount() {
        return partialWaterSiteCount;
    }

    public int steepSiteCount() {
        return steepSiteCount;
    }

    public int structureConflictBuildingCount() {
        return structureConflictBuildingCount;
    }

    public int heavyEarthworkSiteCount() {
        return heavyEarthworkSiteCount;
    }

    public boolean hasSiteConditionSummary() {
        return waterSiteCount > 0
            || partialWaterSiteCount > 0
            || steepSiteCount > 0
            || structureConflictBuildingCount > 0
            || heavyEarthworkSiteCount > 0;
    }

    public boolean isDistrict() {
        return buildingsAttempted > 1;
    }
}
