package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;

/**
 * 隧道最终体素校验：洞腔残留、灯具无衬砌安装面、衬砌缺失等专项指标。
 * 洞腔/衬砌期望来自 {@link RoadGenerationResult#expectedTunnelCavity} /
 * {@link RoadGenerationResult#expectedTunnelLining}，不得用最终 AIR 用途表反推。
 */
public final class TunnelFinalVoxelValidator {
    private TunnelFinalVoxelValidator() {
    }

    public record Report(
            int unclearedTunnelCavityCount,
            int floatingTunnelLightCount,
            /** 严格逐格：期望衬砌位无 STRUCTURAL/ACCENT 实体。 */
            int missingTunnelLiningCount,
            /**
             * 诊断用：期望位缺失但六邻有衬砌（采样偏移）。不得当作设计已满足。
             */
            int missingTunnelLiningToleratedCount) {
        public static final Report ZERO = new Report(0, 0, 0, 0);

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
        int missingExact = countMissingTunnelLiningExact(result);
        int missingTolerated = countMissingTunnelLiningTolerated(result);
        return new Report(uncleared, floatingLights, missingExact, missingTolerated);
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
     * 计划为隧道净空，但最终仍非空气，且原始/当前采样仍见实体障碍。
     */
    static int countUnclearedTunnelCavity(RoadGenerationResult result, TerrainSampler terrain) {
        Set<BlockPos> expected = result.expectedTunnelCavity;
        if (expected == null || expected.isEmpty()) {
            return 0;
        }
        Set<BlockPos> authorizedLining = result.expectedTunnelLining;
        int count = 0;
        for (BlockPos pos : expected) {
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
            // 仅当该格本身是计划衬砌时，才允许结构衬砌占洞腔（掩膜交叠授权）。
            if (isLiningSolid(result, pos)
                    && authorizedLining != null
                    && authorizedLining.contains(pos)) {
                continue;
            }
            if (terrain != null
                    && terrain.isChunkLoaded(pos.getX(), pos.getZ())
                    && isObstruction(terrain, pos.getX(), pos.getY(), pos.getZ())) {
                count++;
            } else if (record != null
                    && record.newBlockId != null
                    && !"minecraft:air".equals(record.newBlockId)
                    && layer != RoadSolidLayer.TUNNEL) {
                count++;
            } else if (record != null
                    && record.newBlockId != null
                    && !"minecraft:air".equals(record.newBlockId)
                    && layer == RoadSolidLayer.TUNNEL
                    && (result.placementMaterialPriorities.get(pos) == null
                        || result.placementMaterialPriorities.get(pos) < RoadSolidMaterialPriority.DECORATIVE)) {
                // 无地形时：非灯具隧道实体占洞腔（含未授权衬砌侵入）
                count++;
            }
        }
        return count;
    }

    /** 严格逐格：期望衬砌位必须有衬砌实体；邻格有衬砌不算满足。 */
    static int countMissingTunnelLining(RoadGenerationResult result) {
        return countMissingTunnelLiningExact(result);
    }

    static int countMissingTunnelLiningExact(RoadGenerationResult result) {
        Set<BlockPos> expected = result.expectedTunnelLining;
        if (expected == null || expected.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (BlockPos pos : expected) {
            if (!isLiningSolid(result, pos)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 期望位缺失、但六邻至少有一格衬砌——仅作采样偏移诊断，不写入验收主指标。
     */
    static int countMissingTunnelLiningTolerated(RoadGenerationResult result) {
        Set<BlockPos> expected = result.expectedTunnelLining;
        if (expected == null || expected.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (BlockPos pos : expected) {
            if (!isLiningSolid(result, pos) && hasCardinalLining(result, pos)) {
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

    private static boolean hasCardinalLining(RoadGenerationResult result, BlockPos pos) {
        return isLiningSolid(result, pos.add(1, 0, 0))
            || isLiningSolid(result, pos.add(-1, 0, 0))
            || isLiningSolid(result, pos.add(0, 1, 0))
            || isLiningSolid(result, pos.add(0, -1, 0))
            || isLiningSolid(result, pos.add(0, 0, 1))
            || isLiningSolid(result, pos.add(0, 0, -1));
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
