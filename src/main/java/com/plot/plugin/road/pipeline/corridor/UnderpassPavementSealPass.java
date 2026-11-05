package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.NavigableSet;

/**
 * 立交下层路面封口：上层桥面投影下的下层柱若缺失路面记录，用邻近下层路面材质补齐，
 * 消除交叉「十」字形原地形空洞。
 */
public final class UnderpassPavementSealPass {
    private static final String FALLBACK_PAVEMENT = "minecraft:white_concrete";

    private UnderpassPavementSealPass() {
    }

    public static void apply(RoadGenerationResult result) {
        if (result == null || result.openRoadClearanceTops.isEmpty()) {
            return;
        }
        Map<ColumnKey, NavigableSet<Integer>> pavementYsByColumn =
            RoadSurfacePlacementPass.buildPavementYIndex(result);
        for (Map.Entry<BlockPos, Integer> entry : result.openRoadClearanceTops.entrySet()) {
            BlockPos surface = entry.getKey();
            if (surface == null) {
                continue;
            }
            Integer elevatedPavementY = RoadSurfacePlacementPass.lowestProtectedPavementAbove(
                pavementYsByColumn, surface.getX(), surface.getY(), surface.getZ());
            if (elevatedPavementY == null) {
                continue;
            }
            String material = resolvePavementMaterial(result, surface);
            sealSurfaceIfNeeded(result, surface, material);
            // 交叉「十」字臂：同标高邻格若也有上层路面投影但缺下层铺装，一并封口。
            int[][] deltas = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2}
            };
            for (int[] d : deltas) {
                int nx = surface.getX() + d[0];
                int nz = surface.getZ() + d[1];
                if (RoadSurfacePlacementPass.lowestProtectedPavementAbove(
                        pavementYsByColumn, nx, surface.getY(), nz) == null) {
                    continue;
                }
                BlockPos neighbor = new BlockPos(nx, surface.getY(), nz);
                sealSurfaceIfNeeded(result, neighbor, material);
            }
        }
    }

    private static void sealSurfaceIfNeeded(
            RoadGenerationResult result,
            BlockPos surface,
            String material) {
        var record = result.placementRecords.get(surface);
        var layer = result.placementLayers.get(surface);
        if (isPresentPavement(record, layer)) {
            return;
        }
        String blockId = material != null && !material.isBlank() ? material : FALLBACK_PAVEMENT;
        String previous = record != null && record.previousBlockId != null
            ? record.previousBlockId
            : "minecraft:air";
        result.recordPlacementOverride(surface, previous, blockId);
        result.placementLayers.put(surface, RoadSolidLayer.ROAD);
        result.placementMaterialPriorities.put(surface, RoadSolidMaterialPriority.DEFAULT);
        result.roadBlocks.add(surface);
    }

    private static boolean isPresentPavement(
            com.plot.core.command.BlockRecord record,
            RoadSolidLayer layer) {
        if (record == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            || layer == RoadSolidLayer.MARKING;
    }

    private static String resolvePavementMaterial(RoadGenerationResult result, BlockPos surface) {
        int[][] deltas = {
            {0, 0},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        String anyPavement = null;
        for (int[] d : deltas) {
            BlockPos neighbor = new BlockPos(
                surface.getX() + d[0], surface.getY(), surface.getZ() + d[1]);
            var record = result.placementRecords.get(neighbor);
            var layer = result.placementLayers.get(neighbor);
            if (!isPresentPavement(record, layer) || record.newBlockId == null) {
                continue;
            }
            if (layer == RoadSolidLayer.ROAD) {
                return record.newBlockId;
            }
            if (anyPavement == null) {
                anyPavement = record.newBlockId;
            }
        }
        return anyPavement;
    }
}
