package com.plot.plugin.building.generation;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 片区（多栋）生成/预览汇总：fail-soft，单栋失败不阻断其余。
 * <p>
 * Phase C 预览与 Phase D 落地共用此结构。
 */
public final class DistrictGenerationResult {

    public enum SkipReason {
        EMPTY("plugin.building.district_skip_empty"),
        ERROR("plugin.building.district_skip_error"),
        INVALID("plugin.building.district_skip_invalid");

        private final String i18nKey;

        SkipReason(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String i18nKey() {
            return i18nKey;
        }
    }

    public record BuildingOutcome(
            String buildingId,
            String buildingName,
            boolean success,
            SkipReason skipReason,
            String errorDetail,
            BuildingGenerationResult result) {

        public static BuildingOutcome ok(
                BuildingFootprint building,
                BuildingGenerationResult result) {
            return new BuildingOutcome(
                building.getId(),
                building.getName(),
                true,
                null,
                null,
                result);
        }

        public static BuildingOutcome skipped(
                BuildingFootprint building,
                SkipReason reason,
                String errorDetail) {
            return new BuildingOutcome(
                building.getId(),
                building.getName(),
                false,
                reason,
                errorDetail,
                null);
        }
    }

    private final List<BuildingOutcome> outcomes = new ArrayList<>();
    private final Map<BlockPos, BlockRecord> mergedPlacementRecords = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private int buildingsGenerated;
    private int buildingsSkipped;
    private int totalBlocks;
    private int totalCutVolume;
    private int totalFillVolume;
    private double totalArea;
    private double totalVolume;

    public List<BuildingOutcome> outcomes() {
        return List.copyOf(outcomes);
    }

    public Map<BlockPos, BlockRecord> mergedPlacementRecords() {
        return mergedPlacementRecords;
    }

    public int buildingsGenerated() {
        return buildingsGenerated;
    }

    public int buildingsSkipped() {
        return buildingsSkipped;
    }

    public int buildingsAttempted() {
        return buildingsGenerated + buildingsSkipped;
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public int totalCutVolume() {
        return totalCutVolume;
    }

    public int totalFillVolume() {
        return totalFillVolume;
    }

    /** 成功建筑占地面积之和（m²） */
    public double totalArea() {
        return totalArea;
    }

    /** 成功建筑体量估算：Σ(面积 × 层数 × 层高) */
    public double totalVolume() {
        return totalVolume;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public boolean hasPlacements() {
        return !mergedPlacementRecords.isEmpty();
    }

    public List<BuildingOutcome> skippedOutcomes() {
        List<BuildingOutcome> skipped = new ArrayList<>();
        for (BuildingOutcome outcome : outcomes) {
            if (!outcome.success()) {
                skipped.add(outcome);
            }
        }
        return skipped;
    }

    void addSuccess(BuildingFootprint building, BuildingGenerationResult result) {
        outcomes.add(BuildingOutcome.ok(building, result));
        buildingsGenerated++;
        totalCutVolume += result.cutVolume;
        totalFillVolume += result.fillVolume;
        double area = building.computeArea();
        totalArea += area;
        totalVolume += area * building.getFloors() * building.getFloorHeight();
        if (result.warnings != null) {
            for (String warning : result.warnings) {
                if (warning != null && !warning.isBlank() && !warnings.contains(warning)) {
                    warnings.add(warning);
                }
            }
        }
        // 后写覆盖先写（同格冲突时以后栋为准）
        for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
            mergedPlacementRecords.put(entry.getKey(), entry.getValue());
        }
        totalBlocks = mergedPlacementRecords.size();
    }

    void addSkipped(BuildingFootprint building, SkipReason reason, String errorDetail) {
        outcomes.add(BuildingOutcome.skipped(building, reason, errorDetail));
        buildingsSkipped++;
        if (buildingsAttempted() > 1 && !warnings.contains("plugin.building.warn.district_partial")) {
            warnings.add("plugin.building.warn.district_partial");
        }
    }

    /**
     * 供现有单结果 UI / 落地路径消费的合并视图。
     */
    public BuildingGenerationResult toMergedResult() {
        BuildingGenerationResult merged = new BuildingGenerationResult();
        merged.placementRecords.putAll(mergedPlacementRecords);
        merged.cutVolume = totalCutVolume;
        merged.fillVolume = totalFillVolume;
        merged.blockCount = totalBlocks;
        if (buildingsGenerated == 1) {
            for (BuildingOutcome outcome : outcomes) {
                if (outcome.success() && outcome.result() != null) {
                    merged.effectiveRoofType = outcome.result().effectiveRoofType;
                    merged.warnings.addAll(outcome.result().warnings);
                    break;
                }
            }
        }
        for (String warning : warnings) {
            if (!merged.warnings.contains(warning)) {
                merged.warnings.add(warning);
            }
        }
        return merged;
    }
}
