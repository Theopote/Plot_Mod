package com.plot.plugin.earthwork.voxel;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.PlacementMeta;
import net.minecraft.util.math.BlockPos;

/**
 * 统一放置记录合并规则：同位置冲突按施工来源优先级决定，而不是依赖生成步骤的偶然顺序。
 * <p>
 * 优先级：{@code STRUCTURE}（挡土墙）&gt; {@code CUT}/{@code FILL}（整平/边坡）。
 * <p>
 * <b>同级冲突设计约束：</b>CUT 与 FILL 同级时保留先写入者。设计地形合成阶段每个
 * 网格单元只归属一个 Zone，体素阶段不应出现同格双 Zone 挖填；若仍发生，视为
 * 前置合成前提被破坏，静默丢弃后续写入以保持 {@code placementRecords}↔{@code changeTypes}
 * 一一对应，并由测试锁定该行为。
 */
public final class EarthworkPlacementMerge {
    private EarthworkPlacementMerge() {
    }

    public static int priority(EarthworkGenerationResult.ChangeType type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case STRUCTURE -> 2;
            case CUT, FILL -> 1;
        };
    }

    public static boolean recordOrReplace(
            EarthworkGenerationResult result,
            BlockPos pos,
            String worldPrevious,
            String newBlockId,
            EarthworkGenerationResult.ChangeType changeType) {
        return recordOrReplace(result, pos, worldPrevious, newBlockId, changeType, PlacementMeta.UNASSIGNED);
    }

    /**
     * @param worldPrevious 当前世界中的方块 ID（预览阶段尚未落地）
     * @param meta          施工归属（Zone ID）；覆盖时采用写入方归属
     * @return {@code true} 若写入或覆盖了该位置的放置记录
     */
    public static boolean recordOrReplace(
            EarthworkGenerationResult result,
            BlockPos pos,
            String worldPrevious,
            String newBlockId,
            EarthworkGenerationResult.ChangeType changeType,
            PlacementMeta meta) {
        if (result == null || pos == null || changeType == null) {
            return false;
        }
        PlacementMeta safeMeta = meta != null ? meta : PlacementMeta.UNASSIGNED;
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            EarthworkGenerationResult.ChangeType existingType = result.changeTypes.get(pos);
            if (priority(changeType) <= priority(existingType)) {
                return false;
            }
            String previous = existing.previousBlockId;
            if (!EarthworkVoxelizer.shouldApplyBlockChange(previous, newBlockId)) {
                result.placementRecords.remove(pos);
                result.changeTypes.remove(pos);
                result.placementMeta.remove(pos);
                return true;
            }
            result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
            result.changeTypes.put(pos, changeType);
            result.placementMeta.put(pos, safeMeta);
            return true;
        }
        if (!EarthworkVoxelizer.shouldApplyBlockChange(worldPrevious, newBlockId)) {
            return false;
        }
        result.placementRecords.put(pos, new BlockRecord(pos, worldPrevious, newBlockId));
        result.changeTypes.put(pos, changeType);
        result.placementMeta.put(pos, safeMeta);
        return true;
    }
}
