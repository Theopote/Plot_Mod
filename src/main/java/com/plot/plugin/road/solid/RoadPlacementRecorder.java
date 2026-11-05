package com.plot.plugin.road.solid;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.pipeline.corridor.ClearancePurpose;
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
        recordByLayerPriority(
            placementRecords,
            placementLayers,
            placementMaterialPriorities,
            null,
            pos,
            previousBlockId,
            newBlockId,
            layer,
            materialPriority,
            null);
    }

    public static void recordByLayerPriority(
            Map<BlockPos, BlockRecord> placementRecords,
            Map<BlockPos, RoadSolidLayer> placementLayers,
            Map<BlockPos, Integer> placementMaterialPriorities,
            Map<BlockPos, ClearancePurpose> placementClearancePurposes,
            BlockPos pos,
            String previousBlockId,
            String newBlockId,
            RoadSolidLayer layer,
            int materialPriority,
            ClearancePurpose clearancePurpose) {
        if (placementRecords == null || placementLayers == null || placementMaterialPriorities == null
                || pos == null || newBlockId == null || layer == null) {
            return;
        }
        RoadSolidLayer existingLayer = placementLayers.get(pos);
        ClearancePurpose existingPurpose = placementClearancePurposes != null
            ? placementClearancePurposes.get(pos)
            : null;
        if (existingLayer != null) {
            int layerCompare = RoadSolidLayerPriority.compare(layer, existingLayer);
            BlockRecord existingRecord = placementRecords.get(pos);
            String existingBlockId = existingRecord != null ? existingRecord.newBlockId : null;
            // 截断挡墙仅可覆盖边坡开挖 AIR，不得侵入隧道/行车净空。
            if (layerCompare < 0) {
                if (!retainingWallOverridesClearanceAir(
                        layer, newBlockId, existingLayer, existingBlockId, existingPurpose)) {
                    return;
                }
            } else if (layerCompare > 0) {
                // 边坡开挖 AIR 不得抹掉挡墙；隧道/行车净空 AIR 可以。
                if (slopeExcavationAirMustNotEraseRetaining(
                        layer, newBlockId, existingLayer, clearancePurpose)) {
                    return;
                }
            } else {
                int existingMaterialPriority = placementMaterialPriorities.getOrDefault(
                    pos, RoadSolidMaterialPriority.DEFAULT);
                if (materialPriority < existingMaterialPriority) {
                    return;
                }
                if (materialPriority == existingMaterialPriority) {
                    if (existingBlockId != null && newBlockId.compareTo(existingBlockId) <= 0) {
                        return;
                    }
                }
            }
        }
        placementLayers.put(pos, layer);
        placementMaterialPriorities.put(pos, materialPriority);
        if (placementClearancePurposes != null) {
            if (isAirBlockId(newBlockId) && clearancePurpose != null) {
                placementClearancePurposes.put(
                    pos,
                    ClearancePurpose.preferProtected(existingPurpose, clearancePurpose));
            } else if (!isAirBlockId(newBlockId)) {
                placementClearancePurposes.remove(pos);
            }
        }
        // mergeFrom 可能只带了 placementRecords、没带 layer。路口清场已经通过了层/材质比较，
        // 必须覆盖已有记录，否则会留下「层是 TUNNEL、方块仍是衬砌」的堵塞柱。
        if (existingLayer == null && !placementRecords.containsKey(pos)) {
            recordIfAbsent(placementRecords, pos, previousBlockId, newBlockId);
        } else {
            recordOverride(placementRecords, pos, previousBlockId, newBlockId);
        }
    }

    /** 截断挡墙落地：仅覆盖边坡开挖 AIR。 */
    static boolean retainingWallOverridesClearanceAir(
            RoadSolidLayer incomingLayer,
            String incomingBlockId,
            RoadSolidLayer existingLayer,
            String existingBlockId,
            ClearancePurpose existingPurpose) {
        if (incomingLayer != RoadSolidLayer.RETAINING_WALL
                || isAirBlockId(incomingBlockId)
                || existingLayer != RoadSolidLayer.TUNNEL
                || !isAirBlockId(existingBlockId)) {
            return false;
        }
        return existingPurpose == null || existingPurpose.allowsRetainingWallOverride();
    }

    /** 边坡开挖 AIR（或未标注）不得抹掉已落盘挡墙。 */
    static boolean slopeExcavationAirMustNotEraseRetaining(
            RoadSolidLayer incomingLayer,
            String incomingBlockId,
            RoadSolidLayer existingLayer,
            ClearancePurpose incomingPurpose) {
        if (incomingLayer != RoadSolidLayer.TUNNEL
                || !isAirBlockId(incomingBlockId)
                || existingLayer != RoadSolidLayer.RETAINING_WALL) {
            return false;
        }
        return incomingPurpose == null || incomingPurpose.allowsRetainingWallOverride();
    }

    private static boolean isAirBlockId(String blockId) {
        return blockId == null || blockId.isBlank() || "minecraft:air".equals(blockId);
    }
}
