package com.plot.plugin.building.generation;

import com.plot.plugin.building.BuildingFootprintValidator;
import com.plot.plugin.building.model.BuildingFootprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;

/**
 * 多栋建筑 fail-soft 编排：单栋异常/空结果/invalid 轮廓记为 skipped，其余继续。
 * <p>
 * <strong>产品约束（不可改为 abort-all）</strong>：100 栋中 3 栋 invalid → 97 generated + 3 reported。
 * 单栋 {@link BuildingGenerateFn#generate} 抛出的异常不得中断片区循环；仅在外层 infrastructure 故障时整片失败。
 */
public final class DistrictMassingGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/DistrictMassing");

    @FunctionalInterface
    public interface BuildingGenerateFn {
        BuildingGenerationResult generate(BuildingFootprint footprint) throws Exception;
    }

    private DistrictMassingGenerator() {
    }

    public static DistrictGenerationResult generate(
            Collection<BuildingFootprint> buildings,
            BuildingGenerateFn generateFn) {
        Objects.requireNonNull(generateFn, "generateFn");
        DistrictGenerationResult district = new DistrictGenerationResult();
        if (buildings == null || buildings.isEmpty()) {
            return district;
        }

        for (BuildingFootprint building : buildings) {
            if (building == null) {
                continue;
            }
            BuildingFootprintValidator.Result validation =
                BuildingFootprintValidator.validate(building.getOuterPoints());
            if (!validation.valid()) {
                district.addSkipped(
                    building,
                    DistrictGenerationResult.SkipReason.INVALID,
                    validation.reason() != null ? validation.reason().name() : null);
                continue;
            }
            try {
                BuildingGenerationResult result = generateFn.generate(building);
                if (result != null && result.skippedDueToSiteAnalysis) {
                    district.addSkipped(
                        building,
                        DistrictGenerationResult.SkipReason.SITE_ANALYSIS_FAILED,
                        null);
                    continue;
                }
                if (result == null || result.placementRecords.isEmpty()) {
                    district.addSkipped(
                        building,
                        DistrictGenerationResult.SkipReason.EMPTY,
                        null);
                    continue;
                }
                district.addSuccess(building, result);
            } catch (Exception e) {
                LOGGER.warn(
                    "District massing skipped building {} ({}): {}",
                    building.getId(),
                    building.getName(),
                    e.getMessage());
                district.addSkipped(
                    building,
                    DistrictGenerationResult.SkipReason.ERROR,
                    e.getMessage());
            }
        }
        district.finalizeOverlaps();
        return district;
    }
}
