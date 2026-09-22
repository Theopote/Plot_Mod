package com.plot.plugin.building.ui;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 体量预览高度：优先生成结果体素范围，否则 footprint 规格。 */
public final class BuildingMassingPreviewHeights {
    private BuildingMassingPreviewHeights() {
    }

    public static Map<String, Double> resolve(BuildingUiContext ctx, List<BuildingFootprint> targets) {
        Map<String, Double> heights = new HashMap<>();
        DistrictGenerationResult district = ctx.lastDistrictResult();
        if (district != null && district.buildingsAttempted() > 0) {
            for (DistrictGenerationResult.BuildingOutcome outcome : district.outcomes()) {
                if (!outcome.success() || outcome.result() == null) {
                    continue;
                }
                double height = heightFromPlacement(outcome.result().placementRecords);
                if (height > 0.0) {
                    heights.put(outcome.buildingId(), height);
                }
            }
            return heights;
        }

        BuildingGenerationResult single = ctx.lastGenerationResult();
        if (single != null && targets.size() == 1) {
            double height = heightFromPlacement(single.placementRecords);
            if (height > 0.0) {
                heights.put(targets.getFirst().getId(), height);
            }
        }
        return heights;
    }

    public static double forBuilding(BuildingFootprint building, Map<String, Double> previewHeights) {
        if (building == null) {
            return 0.0;
        }
        Double preview = previewHeights != null ? previewHeights.get(building.getId()) : null;
        if (preview != null && preview > 0.0) {
            return preview;
        }
        return building.getFloors() * building.getFloorHeight();
    }

    public static HeightRange range(List<BuildingFootprint> buildings, Map<String, Double> previewHeights) {
        double min = Double.POSITIVE_INFINITY;
        double max = 0.0;
        for (BuildingFootprint building : buildings) {
            if (building == null) {
                continue;
            }
            double height = forBuilding(building, previewHeights);
            min = Math.min(min, height);
            max = Math.max(max, height);
        }
        if (min == Double.POSITIVE_INFINITY) {
            min = 0.0;
        }
        return new HeightRange(min, max);
    }

    private static double heightFromPlacement(Map<BlockPos, BlockRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0.0;
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos pos : records.keySet()) {
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }
        if (minY == Integer.MAX_VALUE) {
            return 0.0;
        }
        return maxY - minY + 1;
    }

    public record HeightRange(double min, double max) {
    }
}
