package com.plot.plugin.earthwork.voxel;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import net.minecraft.util.math.BlockPos;

/**
 * 统一放置记录合并规则：同位置冲突按施工来源优先级决定，而不是依赖生成步骤的偶然顺序。
 * <p>
 * 优先级：{@code STRUCTURE}（挡土墙）&gt; {@code CUT}/{@code FILL}（整平/边坡）。
 * 高优先级可覆盖低优先级；同级保留先写入者，保证 {@code placementRecords} 与 {@code changeTypes} 一一对应。
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

    /**
     * @param worldPrevious 当前世界中的方块 ID（预览阶段尚未落地）
     * @return {@code true} 若写入或覆盖了该位置的放置记录
     */
    public static boolean recordOrReplace(
            EarthworkGenerationResult result,
            BlockPos pos,
            String worldPrevious,
            String newBlockId,
            EarthworkGenerationResult.ChangeType changeType) {
        if (result == null || pos == null || changeType == null) {
            return false;
        }
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            EarthworkGenerationResult.ChangeType existingType = result.changeTypes.get(pos);
            if (priority(changeType) <= priority(existingType)) {
                return false;
            }
            String previous = existing.previousBlockId;
            if (!EarthworkVoxelizer.shouldApplyBlockChange(previous, newBlockId)) {
                // 覆盖后与现状相同 → 删除该位置记录（无净变更）
                result.placementRecords.remove(pos);
                result.changeTypes.remove(pos);
                return true;
            }
            result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
            result.changeTypes.put(pos, changeType);
            return true;
        }
        if (!EarthworkVoxelizer.shouldApplyBlockChange(worldPrevious, newBlockId)) {
            return false;
        }
        result.placementRecords.put(pos, new BlockRecord(pos, worldPrevious, newBlockId));
        result.changeTypes.put(pos, changeType);
        return true;
    }
}
