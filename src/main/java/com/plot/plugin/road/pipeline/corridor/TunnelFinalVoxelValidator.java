package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
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
            // 广场面层 / 护栏等附属设施占据洞腔掩膜交叠格：设计顶替，不是未清洞腔。
            if (isAuthorizedCavityOccupant(record, layer)) {
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

    private static boolean isAuthorizedCavityOccupant(BlockRecord record, RoadSolidLayer layer) {
        if (record == null || record.newBlockId == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            || layer == RoadSolidLayer.MARKING
            || layer == RoadSolidLayer.BRIDGE
            || layer == RoadSolidLayer.GUARDRAIL
            || layer == RoadSolidLayer.STREETLIGHT;
    }

    static int countMissingTunnelLiningExact(RoadGenerationResult result) {
        Set<BlockPos> expected = result.expectedTunnelLining;
        if (expected == null || expected.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (BlockPos pos : expected) {
            if (isMissingLiningHole(result, pos)) {
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
            if (isMissingLiningHole(result, pos) && hasCardinalLining(result, pos)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 仅统计「空洞」：无落点或未授权空气。
     * <p>
     * 与 {@link JunctionProtectedOverburdenValidator#countMissingLiningInJunctionScope} 对齐——
     * 任意非空气实体（含广场 ROAD 顶替）不算衬砌空洞；授权洞腔 / TUNNEL 空气也不算。
     */
    private static boolean isMissingLiningHole(RoadGenerationResult result, BlockPos pos) {
        if (result == null || pos == null) {
            return false;
        }
        if (isLiningSolid(result, pos)) {
            return false;
        }
        if (isAuthorizedLiningAbsence(result, pos)) {
            return false;
        }
        BlockRecord record = result.placementRecords.get(pos);
        if (record != null
                && record.newBlockId != null
                && !"minecraft:air".equals(record.newBlockId)) {
            // 被其它实体占据（广场面层/底板等设计顶替），不是 lining 空洞。
            return false;
        }
        return true;
    }

    /**
     * 连通清障把走廊衬砌合法打开为洞腔后，{@code expectedTunnelLining} 可能仍残留；
     * 与 {@link JunctionProtectedOverburdenValidator#countMissingLiningInJunctionScope} 同口径，
     * 不得把授权洞腔空气当成衬砌丢失拦建造。
     */
    private static boolean isAuthorizedLiningAbsence(RoadGenerationResult result, BlockPos pos) {
        if (result == null || pos == null) {
            return false;
        }
        if (result.expectedTunnelCavity != null && result.expectedTunnelCavity.contains(pos)) {
            return true;
        }
        BlockRecord record = result.placementRecords.get(pos);
        if (record == null || !"minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        return result.placementLayers.get(pos) == RoadSolidLayer.TUNNEL;
    }

    /**
     * DECORATIVE 隧道灯具四周无合法安装面（衬砌 / 护栏 / 结构路面 / 桥面）。
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
                if (isLightMountSolid(result, neighbor)) {
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

    /** 灯具可挂接：隧道衬砌，或护栏/桥面/路面等结构实体。 */
    private static boolean isLightMountSolid(RoadGenerationResult result, BlockPos pos) {
        if (isLiningSolid(result, pos)) {
            return true;
        }
        if (result == null || pos == null) {
            return false;
        }
        BlockRecord record = result.placementRecords.get(pos);
        if (record == null || record.newBlockId == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        RoadSolidLayer layer = result.placementLayers.get(pos);
        return layer == RoadSolidLayer.GUARDRAIL
            || layer == RoadSolidLayer.BRIDGE
            || layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            || layer == RoadSolidLayer.RETAINING_WALL;
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
            || terrain.isRoadClearableDecoration(x, y, z)
            || terrain.isFluidBlock(x, y, z);
    }

    /** 诊断：未清洞腔样本（日志用）。 */
    public static List<String> sampleUnclearedCavity(RoadGenerationResult result, TerrainSampler terrain, int limit) {
        List<String> samples = new ArrayList<>();
        if (result == null || result.expectedTunnelCavity == null || limit <= 0) {
            return samples;
        }
        Set<BlockPos> authorizedLining = result.expectedTunnelLining;
        for (BlockPos pos : result.expectedTunnelCavity) {
            if (samples.size() >= limit) {
                break;
            }
            if (pos == null) {
                continue;
            }
            BlockRecord record = result.placementRecords.get(pos);
            if (record != null && "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            RoadSolidLayer layer = result.placementLayers.get(pos);
            if (layer == RoadSolidLayer.TUNNEL) {
                Integer priority = result.placementMaterialPriorities.get(pos);
                if (priority != null && priority >= RoadSolidMaterialPriority.DECORATIVE
                        && record != null
                        && record.newBlockId != null
                        && !"minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
            }
            if (isLiningSolid(result, pos)
                    && authorizedLining != null
                    && authorizedLining.contains(pos)) {
                continue;
            }
            if (isAuthorizedCavityOccupant(record, layer)) {
                continue;
            }
            boolean terrainHit = terrain != null
                && terrain.isChunkLoaded(pos.getX(), pos.getZ())
                && isObstruction(terrain, pos.getX(), pos.getY(), pos.getZ());
            boolean solidHit = record != null
                && record.newBlockId != null
                && !"minecraft:air".equals(record.newBlockId);
            if (!terrainHit && !solidHit) {
                continue;
            }
            samples.add("cavity@" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + " layer=" + layer
                + " block=" + (record != null ? record.newBlockId : "null")
                + (terrainHit ? " terrain" : ""));
        }
        return samples;
    }
}
