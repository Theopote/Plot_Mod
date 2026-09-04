package com.plot.plugin.building.golden;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 {@link BuildingGenerationResult} 提取 Golden Test 统计量。
 */
public final class GoldenBuildingMetricsCollector {
    private GoldenBuildingMetricsCollector() {
    }

    public static GoldenBuildingMetrics collect(
            BuildingGenerationContext context,
            BuildingGenerationResult result) {
        if (context == null || result == null || result.placementRecords.isEmpty()) {
            return GoldenBuildingMetrics.empty();
        }

        String wallId = normalize(context.getDefinition().envelope().wallMaterial().getPrimaryMaterial());
        String floorId = normalize(context.getDefinition().envelope().floorMaterial().getPrimaryMaterial());
        String roofId = normalize(context.getRoofBlockId());
        String foundationId = normalize(context.getFoundationFillBlockId());

        int wall = 0;
        int floor = 0;
        int roof = 0;
        int foundation = 0;
        int opening = 0;
        int other = 0;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Map.Entry<net.minecraft.util.math.BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
            BlockRecord record = entry.getValue();
            net.minecraft.util.math.BlockPos pos = entry.getKey();
            String blockId = normalize(record.newBlockId);

            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());

            if (isAir(blockId)) {
                opening++;
            } else if (blockId.equals(wallId)) {
                wall++;
            } else if (blockId.equals(floorId)) {
                floor++;
            } else if (blockId.equals(roofId)) {
                roof++;
            } else if (blockId.equals(foundationId)) {
                foundation++;
            } else {
                other++;
            }
        }

        return new GoldenBuildingMetrics(
            result.placementRecords.size(),
            wall,
            floor,
            roof,
            foundation,
            opening,
            other,
            result.cutVolume,
            result.fillVolume,
            result.placementRecords.isEmpty() ? 0 : minX,
            result.placementRecords.isEmpty() ? 0 : maxX,
            result.placementRecords.isEmpty() ? 0 : minY,
            result.placementRecords.isEmpty() ? 0 : maxY,
            result.placementRecords.isEmpty() ? 0 : minZ,
            result.placementRecords.isEmpty() ? 0 : maxZ,
            result.effectiveRoofType != null
                ? result.effectiveRoofType.name()
                : BuildingFootprint.RoofType.FLAT.name(),
            new ArrayList<>(result.warnings));
    }

    private static boolean isAir(String blockId) {
        return blockId == null
            || blockId.isBlank()
            || "minecraft:air".equals(blockId)
            || "minecraft:cave_air".equals(blockId)
            || "minecraft:void_air".equals(blockId);
    }

    private static String normalize(String blockId) {
        return blockId != null ? blockId.trim() : "";
    }
}
