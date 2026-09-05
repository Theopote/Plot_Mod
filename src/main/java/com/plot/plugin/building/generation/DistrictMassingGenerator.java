package com.plot.plugin.building.generation;

import com.plot.plugin.building.model.BuildingFootprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;

/**
 * 多栋建筑 fail-soft 编排：单栋异常/空结果记为 skipped，其余继续。
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
            if (building.getOuterPoints() == null || building.getOuterPoints().size() < 3) {
                district.addSkipped(
                    building,
                    DistrictGenerationResult.SkipReason.INVALID,
                    null);
                continue;
            }
            try {
                BuildingGenerationResult result = generateFn.generate(building);
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
        return district;
    }
}
