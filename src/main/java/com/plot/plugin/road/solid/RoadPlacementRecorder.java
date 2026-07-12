package com.plot.plugin.road.solid;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * 道路方块放置记录：边生成用 first-claim，路口材质覆盖用 override。
 */
public final class RoadPlacementRecorder {
    private RoadPlacementRecorder() {
    }

    /**
     * 同一位置仅保留首次写入（边 solids 落地）。
     */
    public static void recordIfAbsent(
            Map<BlockPos, BlockRecord> placementRecords,
            BlockPos pos,
            String previousBlockId,
            String newBlockId) {
        if (placementRecords == null || pos == null || newBlockId == null) {
            return;
        }
        if (!placementRecords.containsKey(pos)) {
            placementRecords.put(pos, new BlockRecord(pos, previousBlockId, newBlockId));
        }
    }

    /**
     * 覆盖目标材质，但保留首次记录的 previousBlockId（路口材质覆盖边生成）。
     */
    public static void recordOverride(
            Map<BlockPos, BlockRecord> placementRecords,
            BlockPos pos,
            String previousBlockId,
            String newBlockId) {
        if (placementRecords == null || pos == null || newBlockId == null) {
            return;
        }
        String originalPrevious = placementRecords.containsKey(pos)
            ? placementRecords.get(pos).previousBlockId
            : previousBlockId;
        placementRecords.put(pos, new BlockRecord(pos, originalPrevious, newBlockId));
    }
}
