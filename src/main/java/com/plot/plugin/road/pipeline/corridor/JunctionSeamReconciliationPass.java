package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 最终体素局部接缝修整：在道路/路口合并之后、世界写入之前，
 * 仅修正授权范围内的标高冲突、圆角叠层残留与洞腔内残留侧墙；不重算几何，不补边坡。
 */
public final class JunctionSeamReconciliationPass {
    /** 圆角抬高时，向下剥离进口路面/衬砌/装饰环的最大深度（格）。 */
    static final int UNDER_PAVEMENT_STRIP_DEPTH = 8;

    private JunctionSeamReconciliationPass() {
    }

    public static void apply(
            RoadGenerationResult aggregate,
            Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults) {
        if (aggregate == null || junctionResults == null || junctionResults.isEmpty()) {
            return;
        }
        // 一次扫描建列索引，避免每个路口列都扫整网 O(C×B)。
        Map<ColumnKey, List<BlockPos>> solidsByColumn = indexSolidPlacements(aggregate);
        for (RoadJunctionGenerator.JunctionBlocks blocks : junctionResults.values()) {
            if (blocks == null) {
                continue;
            }
            reconcileJunction(aggregate, blocks, solidsByColumn);
        }
    }

    /**
     * 合并结果中非空气放置按世界列归桶；接缝修整只遍历相关列。
     */
    public static Map<ColumnKey, List<BlockPos>> indexSolidPlacements(RoadGenerationResult aggregate) {
        Map<ColumnKey, List<BlockPos>> byColumn = new HashMap<>();
        if (aggregate == null || aggregate.placementRecords == null) {
            return byColumn;
        }
        for (Map.Entry<BlockPos, BlockRecord> entry : aggregate.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockRecord record = entry.getValue();
            if (pos == null || record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            ColumnKey key = new ColumnKey(pos.getX(), pos.getZ());
            byColumn.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pos);
        }
        return byColumn;
    }

    private static void reconcileJunction(
            RoadGenerationResult aggregate,
            RoadJunctionGenerator.JunctionBlocks blocks,
            Map<ColumnKey, List<BlockPos>> solidsByColumn) {
        JunctionElevationField field = blocks.elevationField();
        JunctionValidationManifest manifest = blocks.validationManifest();
        if (field == null || field.targetYByColumn().isEmpty()) {
            return;
        }
        Set<BlockPos> protectedBlocks = manifest != null && manifest.expectedProtectedBlocks() != null
            ? new HashSet<>(manifest.expectedProtectedBlocks())
            : Set.of();
        Integer elevatedDeckY = manifest != null ? manifest.elevatedDeckY() : null;
        Map<ColumnKey, List<BlockPos>> index = solidsByColumn != null ? solidsByColumn : Map.of();

        for (Map.Entry<ColumnKey, Integer> entry : field.targetYByColumn().entrySet()) {
            ColumnKey column = entry.getKey();
            Integer targetY = entry.getValue();
            if (column == null || targetY == null) {
                continue;
            }
            JunctionWorldFootprint.ElevationRole role = field.role(column);
            reconcileColumn(
                aggregate, column, targetY, elevatedDeckY, protectedBlocks, role,
                index.getOrDefault(column, List.of()));
        }
    }

    private static void reconcileColumn(
            RoadGenerationResult aggregate,
            ColumnKey column,
            int targetY,
            Integer elevatedDeckY,
            Set<BlockPos> protectedBlocks,
            JunctionWorldFootprint.ElevationRole role,
            List<BlockPos> columnSolids) {
        List<BlockPos> solids = columnSolids != null ? columnSolids : List.of();
        int workingTarget = targetY;
        // 圆角/接缝：先处理「路面悬空一格」——标高偏高时下层已被掏空，破坏突起后正下方是空气。
        if (role == JunctionWorldFootprint.ElevationRole.FILLET
                || role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
            workingTarget = settleMisalignedFilletPavement(
                aggregate, column, workingTarget, elevatedDeckY, protectedBlocks, solids);
        }

        List<BlockPos> roadAtColumn = new ArrayList<>();
        List<BlockPos> tunnelSolids = new ArrayList<>();
        List<BlockPos> plazaFamilyBelow = new ArrayList<>();
        for (BlockPos pos : solids) {
            if (pos == null) {
                continue;
            }
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            if (elevatedDeckY != null && pos.getY() >= elevatedDeckY - 1) {
                continue;
            }
            if (protectedBlocks.contains(pos)) {
                continue;
            }
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (layer == RoadSolidLayer.ROAD) {
                roadAtColumn.add(pos);
            } else if (layer == RoadSolidLayer.TUNNEL) {
                tunnelSolids.add(pos);
            }
            if (pos.getY() < workingTarget
                    && pos.getY() >= workingTarget - UNDER_PAVEMENT_STRIP_DEPTH
                    && isStackedLeftoverLayer(layer)) {
                plazaFamilyBelow.add(pos);
            }
        }

        // 核区/圆角/接缝：高出设计路面的进口残留必须剥掉，否则缓坡一侧和广场内会出现零星突起。
        stripPavementAboveTarget(
            aggregate, workingTarget, elevatedDeckY, protectedBlocks, solids);

        if (role == JunctionWorldFootprint.ElevationRole.ROAD_CORE) {
            return;
        }

        boolean hasTargetRoad = hasPavementSolid(aggregate, column, workingTarget);
        if (!hasTargetRoad && !roadAtColumn.isEmpty()
                && (role == JunctionWorldFootprint.ElevationRole.FILLET
                || role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION)) {
            // 接缝高度不一致：在目标 Y 恢复一层路面（沿用既有路面材料）。
            BlockPos donor = null;
            for (BlockPos pos : roadAtColumn) {
                BlockRecord record = aggregate.placementRecords.get(pos);
                if (record != null && !"minecraft:air".equals(record.newBlockId)) {
                    donor = pos;
                    break;
                }
            }
            if (donor != null) {
                BlockRecord donorRecord = aggregate.placementRecords.get(donor);
                String material = donorRecord != null ? donorRecord.newBlockId : "minecraft:stone";
                BlockPos target = new BlockPos(column.worldX(), workingTarget, column.worldZ());
                if (!protectedBlocks.contains(target)
                        && (elevatedDeckY == null || workingTarget < elevatedDeckY - 1)) {
                    String previous = aggregate.placementRecords.containsKey(target)
                        ? aggregate.placementRecords.get(target).previousBlockId
                        : "minecraft:air";
                    aggregate.recordPlacementOverride(target, previous, material);
                    aggregate.placementLayers.put(target, RoadSolidLayer.ROAD);
                    aggregate.placementMaterialPriorities.put(
                        target, RoadSolidMaterialPriority.DEFAULT);
                }
            }
        }

        // 圆角叠层：进口路缘 / 衬砌 / 装饰环落在设计路面之下时清掉，避免破坏顶面后露出下层突起。
        // 紧贴路面下一格的硬质路面若是唯一承托则不再剥（已由 settle 回落处理）。
        if (role == JunctionWorldFootprint.ElevationRole.FILLET
                || role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
            for (BlockPos pos : plazaFamilyBelow) {
                if (pos.getY() == workingTarget - 1 && isStackedPavementLayer(
                        aggregate.placementLayers.get(pos))
                        && !hasNonAirSolid(aggregate, column, workingTarget)) {
                    continue;
                }
                clearLeftover(aggregate, pos);
            }
            for (BlockPos pos : roadAtColumn) {
                if (pos.getY() < workingTarget && pos.getY() >= workingTarget - UNDER_PAVEMENT_STRIP_DEPTH) {
                    BlockRecord record = aggregate.placementRecords.get(pos);
                    if (record == null || "minecraft:air".equals(record.newBlockId)) {
                        continue;
                    }
                    if (pos.getY() == workingTarget - 1
                            && !hasNonAirSolid(aggregate, column, workingTarget)) {
                        continue;
                    }
                    clearLeftover(aggregate, pos);
                }
            }
            for (BlockPos pos : tunnelSolids) {
                if (pos.getY() < workingTarget && pos.getY() >= workingTarget - UNDER_PAVEMENT_STRIP_DEPTH) {
                    clearLeftover(aggregate, pos);
                }
            }
        }

        // 圆角/接缝列：仅清紧贴路面上的残留侧墙；禁止清到洞顶高度（避免路面局部下沉时顺带掏掉顶衬）。
        if (role == JunctionWorldFootprint.ElevationRole.FILLET
                || role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
            int cavityCeiling = workingTarget + 2;
            for (BlockPos pos : tunnelSolids) {
                if (pos.getY() <= workingTarget || pos.getY() > cavityCeiling) {
                    continue;
                }
                if (protectedBlocks.contains(pos)) {
                    continue;
                }
                clearLeftover(aggregate, pos);
            }
        }
    }

    /**
     * 圆角/接缝错位回落（最多一格）：
     * <ul>
     *   <li>路面仅在 Y+1、Y 为空 → 落到 Y</li>
     *   <li>路面在 Y、Y-1 为空 → 落到 Y-1（设计目标偏高，随后 under-strip 会掏空正下方）</li>
     * </ul>
     * @return 修正后的工作目标标高
     */
    static int settleMisalignedFilletPavement(
            RoadGenerationResult aggregate,
            ColumnKey column,
            int targetY,
            Integer elevatedDeckY,
            Set<BlockPos> protectedBlocks,
            List<BlockPos> columnSolids) {
        if (aggregate == null || column == null) {
            return targetY;
        }
        if (elevatedDeckY != null && targetY >= elevatedDeckY - 1) {
            return targetY;
        }
        // 先收高一格的悬空突起到设计目标。
        if (hasPavementSolid(aggregate, column, targetY + 1)
                && !hasNonAirSolid(aggregate, column, targetY)) {
            movePavementDown(
                aggregate, column, targetY + 1, targetY, elevatedDeckY, protectedBlocks);
        }
        // 设计目标本身偏高一格：路面脚下是空的，回落并下调工作目标，避免剥掉真正承托。
        if (hasPavementSolid(aggregate, column, targetY)
                && !hasNonAirSolid(aggregate, column, targetY - 1)) {
            movePavementDown(
                aggregate, column, targetY, targetY - 1, elevatedDeckY, protectedBlocks);
            return targetY - 1;
        }
        return targetY;
    }

    private static boolean hasPavementSolid(
            RoadGenerationResult aggregate, ColumnKey column, int y) {
        BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
        BlockRecord record = aggregate.placementRecords.get(pos);
        if (record == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        return isStackedPavementLayer(aggregate.placementLayers.get(pos));
    }

    private static boolean hasNonAirSolid(
            RoadGenerationResult aggregate, ColumnKey column, int y) {
        BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
        BlockRecord record = aggregate.placementRecords.get(pos);
        return record != null && record.newBlockId != null
            && !"minecraft:air".equals(record.newBlockId);
    }

    private static void movePavementDown(
            RoadGenerationResult aggregate,
            ColumnKey column,
            int fromY,
            int toY,
            Integer elevatedDeckY,
            Set<BlockPos> protectedBlocks) {
        if (toY >= fromY) {
            return;
        }
        BlockPos from = new BlockPos(column.worldX(), fromY, column.worldZ());
        BlockPos to = new BlockPos(column.worldX(), toY, column.worldZ());
        if (protectedBlocks != null && protectedBlocks.contains(from)) {
            return;
        }
        if (elevatedDeckY != null && (fromY >= elevatedDeckY - 1 || toY >= elevatedDeckY - 1)) {
            return;
        }
        BlockRecord donor = aggregate.placementRecords.get(from);
        if (donor == null || "minecraft:air".equals(donor.newBlockId)) {
            return;
        }
        RoadSolidLayer layer = aggregate.placementLayers.getOrDefault(from, RoadSolidLayer.ROAD);
        if (!isStackedPavementLayer(layer)) {
            return;
        }
        String previous = aggregate.placementRecords.containsKey(to)
            ? aggregate.placementRecords.get(to).previousBlockId
            : "minecraft:air";
        aggregate.recordPlacementOverride(to, previous, donor.newBlockId);
        aggregate.placementLayers.put(to, layer);
        Integer priority = aggregate.placementMaterialPriorities.get(from);
        aggregate.placementMaterialPriorities.put(
            to, priority != null ? priority : RoadSolidMaterialPriority.DEFAULT);
        clearLeftover(aggregate, from);
    }

    private static void stripPavementAboveTarget(
            RoadGenerationResult aggregate,
            int targetY,
            Integer elevatedDeckY,
            Set<BlockPos> protectedBlocks,
            List<BlockPos> columnSolids) {
        List<BlockPos> extra = new ArrayList<>();
        for (BlockPos pos : columnSolids) {
            if (pos == null) {
                continue;
            }
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            if (pos.getY() <= targetY || pos.getY() > targetY + UNDER_PAVEMENT_STRIP_DEPTH) {
                continue;
            }
            if (elevatedDeckY != null && pos.getY() >= elevatedDeckY - 1) {
                continue;
            }
            if (protectedBlocks.contains(pos)) {
                continue;
            }
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (isStackedPavementLayer(layer)) {
                extra.add(pos);
            }
        }
        for (BlockPos pos : extra) {
            clearLeftover(aggregate, pos);
        }
    }

    /** 可叠在圆角路面下的进口残留层：硬质路面族 + 隧道衬砌/装饰环（非空气）。 */
    static boolean isStackedLeftoverLayer(RoadSolidLayer layer) {
        if (layer == null) {
            return false;
        }
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            || layer == RoadSolidLayer.MARKING
            || layer == RoadSolidLayer.TUNNEL
            || layer == RoadSolidLayer.GUARDRAIL
            || layer == RoadSolidLayer.STREETLIGHT;
    }

    /** 高出设计路面的突起：只剥硬质路面族，不动合法洞顶衬砌。 */
    static boolean isStackedPavementLayer(RoadSolidLayer layer) {
        if (layer == null || layer == RoadSolidLayer.TUNNEL) {
            return false;
        }
        return isStackedLeftoverLayer(layer);
    }

    private static void clearLeftover(RoadGenerationResult aggregate, BlockPos pos) {
        BlockRecord existing = aggregate.placementRecords.get(pos);
        String previous = existing != null ? existing.previousBlockId : "minecraft:stone";
        aggregate.recordPlacementOverride(pos, previous, "minecraft:air");
        aggregate.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        aggregate.placementMaterialPriorities.put(pos, Integer.valueOf(RoadSolidMaterialPriority.CLEARANCE));
    }
}
