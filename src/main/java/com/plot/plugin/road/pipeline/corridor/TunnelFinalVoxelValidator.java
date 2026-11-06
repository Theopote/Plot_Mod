package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * 隧道最终体素校验：洞腔残留、灯具无衬砌安装面等专项指标。
 * 与 {@link RoadGenerationIntegrityValidator} 的开放走廊清障互补（后者跳过 TUNNEL mode）。
 */
public final class TunnelFinalVoxelValidator {
    private TunnelFinalVoxelValidator() {
    }

    public record Report(
            int unclearedTunnelCavityCount,
            int floatingTunnelLightCount,
            int missingTunnelLiningCount) {
        public static final Report ZERO = new Report(0, 0, 0);

        public boolean isClean() {
            return unclearedTunnelCavityCount == 0
                && floatingTunnelLightCount == 0
                && missingTunnelLiningCount == 0;
        }
    }

    public static Report validate(RoadGenerationResult result, TerrainSampler terrain) {
        if (result == null) {
            return Report.ZERO;
        }
        int uncleared = countUnclearedTunnelCavity(result, terrain);
        int floatingLights = countFloatingTunnelLights(result);
        return new Report(uncleared, floatingLights, 0);
    }

    public static void applyReportToResult(RoadGenerationResult result, Report report) {
        if (result == null || report == null) {
            return;
        }
        result.unclearedTunnelCavityCount = report.unclearedTunnelCavityCount();
        result.floatingTunnelLightCount = report.floatingTunnelLightCount();
        result.missingTunnelLiningCount = report.missingTunnelLiningCount();
    }

    /**
     * 计划为隧道净空 AIR，但最终仍非空气，且原始/当前采样仍见实体障碍。
     */
    static int countUnclearedTunnelCavity(RoadGenerationResult result, TerrainSampler terrain) {
        int count = 0;
        for (Map.Entry<BlockPos, ClearancePurpose> entry : result.placementClearancePurposes.entrySet()) {
            if (entry.getValue() != ClearancePurpose.TUNNEL_INTERIOR) {
                continue;
            }
            BlockPos pos = entry.getKey();
            BlockRecord record = result.placementRecords.get(pos);
            if (record != null && "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            RoadSolidLayer layer = result.placementLayers.get(pos);
            if (layer == RoadSolidLayer.TUNNEL) {
                Integer priority = result.placementMaterialPriorities.get(pos);
                // 合法灯具可占据洞腔格
                if (priority != null && priority >= RoadSolidMaterialPriority.DECORATIVE
                        && record != null
                        && record.newBlockId != null
                        && !"minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
            }
            if (terrain != null
                    && terrain.isChunkLoaded(pos.getX(), pos.getZ())
                    && isObstruction(terrain, pos.getX(), pos.getY(), pos.getZ())) {
                count++;
            } else if (record != null
                    && record.newBlockId != null
                    && !"minecraft:air".equals(record.newBlockId)
                    && layer != RoadSolidLayer.TUNNEL) {
                // 无地形上下文时：非隧道层实体占洞腔也计残留
                count++;
            }
        }
        return count;
    }

    /**
     * DECORATIVE 隧道灯具四周无 STRUCTURAL/ACCENT 衬砌邻格。
     */
    static int countFloatingTunnelLights(RoadGenerationResult result) {
        int count = 0;
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (result.placementLayers.get(pos) != RoadSolidLayer.TUNNEL) {
                continue;
            }
            Integer priority = result.placementMaterialPriorities.get(pos);
            if (priority == null || priority < RoadSolidMaterialPriority.DECORATIVE) {
                continue;
            }
            BlockRecord record = entry.getValue();
            if (record == null || record.newBlockId == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            boolean hasMount = false;
            for (int[] dir : dirs) {
                BlockPos neighbor = pos.add(dir[0], dir[1], dir[2]);
                if (isLiningSolid(result, neighbor)) {
                    hasMount = true;
                    break;
                }
            }
            if (!hasMount) {
                count++;
            }
        }
        return count;
    }

    private static boolean isLiningSolid(RoadGenerationResult result, BlockPos pos) {
        if (result.placementLayers.get(pos) != RoadSolidLayer.TUNNEL) {
            return false;
        }
        BlockRecord record = result.placementRecords.get(pos);
        if (record == null || record.newBlockId == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        Integer priority = result.placementMaterialPriorities.get(pos);
        return priority == null || priority < RoadSolidMaterialPriority.DECORATIVE;
    }

    private static boolean isObstruction(TerrainSampler terrain, int x, int y, int z) {
        return terrain.isConstructionVolumeObstruction(x, y, z)
            || terrain.isSolidBlock(x, y, z)
            || terrain.isRoadClearableDecoration(x, y, z);
    }
}
