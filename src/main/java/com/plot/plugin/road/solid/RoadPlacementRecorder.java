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

    /**
     * 按图层固定优先级写入：更高优先级层可覆盖较低优先级层，与 flush 批次无关。
     * 同层优先级时使用 materialPriority，再使用 newBlockId 字典序作为最终 tie-break。
     */
    public static void recordByLayerPriority(
            Map<BlockPos, BlockRecord> placementRecords,
            Map<BlockPos, RoadSolidLayer> placementLayers,
            Map<BlockPos, Integer> placementMaterialPriorities,
            BlockPos pos,
            String previousBlockId,
            String newBlockId,
            RoadSolidLayer layer,
            int materialPriority) {
        if (placementRecords == null || placementLayers == null || placementMaterialPriorities == null
                || pos == null || newBlockId == null || layer == null) {
            return;
        }
        RoadSolidLayer existingLayer = placementLayers.get(pos);
        if (existingLayer != null) {
            int layerCompare = RoadSolidLayerPriority.compare(layer, existingLayer);
            if (layerCompare < 0) {
                return;
            }
            if (layerCompare == 0) {
                int existingMaterialPriority = placementMaterialPriorities.getOrDefault(
                    pos, RoadSolidMaterialPriority.DEFAULT);
                if (materialPriority < existingMaterialPriority) {
                    return;
                }
                if (materialPriority == existingMaterialPriority) {
                    BlockRecord existingRecord = placementRecords.get(pos);
                    String existingBlockId = existingRecord != null ? existingRecord.newBlockId : null;
                    if (existingBlockId != null && newBlockId.compareTo(existingBlockId) <= 0) {
                        return;
                    }
                }
            }
        }
        placementLayers.put(pos, layer);
        placementMaterialPriorities.put(pos, materialPriority);
        if (existingLayer == null) {
            recordIfAbsent(placementRecords, pos, previousBlockId, newBlockId);
        } else {
            recordOverride(placementRecords, pos, previousBlockId, newBlockId);
        }
    }
}
