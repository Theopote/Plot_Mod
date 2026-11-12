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
 * <p>
 * 不得因 placementRecords 缺少脚下实体就下移设计路面标高——天然石头可能从未写入计划。
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
        List<BlockPos> roadAtColumn = new ArrayList<>();
        List<BlockPos> tunnelSolids = new ArrayList<>();
        List<BlockPos> plazaFamilyBelow = new ArrayList<>();
        List<BlockPos> solids = columnSolids != null ? columnSolids : List.of();
        // 剥离前快照合法路面材料：strip 可能把唯一 donor 清成 air。
        String savedPavementMaterial = null;
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
                if (savedPavementMaterial == null && isUsablePavementMaterial(record.newBlockId)) {
                    savedPavementMaterial = record.newBlockId;
                }
            } else if (layer == RoadSolidLayer.TUNNEL) {
                tunnelSolids.add(pos);
            }
            if (pos.getY() < targetY
                    && pos.getY() >= targetY - UNDER_PAVEMENT_STRIP_DEPTH
                    && isStackedLeftoverLayer(layer)) {
                plazaFamilyBelow.add(pos);
            }
        }

        // 核区/圆角/接缝：高出设计路面的进口残留必须剥掉，否则缓坡一侧和广场内会出现零星突起。
        stripPavementAboveTarget(
            aggregate, targetY, elevatedDeckY, protectedBlocks, solids);

        if (role == JunctionWorldFootprint.ElevationRole.ROAD_CORE) {
            return;
        }

        boolean hasTargetRoad = hasPavementSolid(aggregate, column, targetY);
        if (!hasTargetRoad
                && (role == JunctionWorldFootprint.ElevationRole.FILLET
                || role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION)) {
            String material = resolvePavementRestoreMaterial(
                savedPavementMaterial, aggregate, roadAtColumn);
            if (material != null) {
                BlockPos target = new BlockPos(column.worldX(), targetY, column.worldZ());
                if (!protectedBlocks.contains(target)
                        && (elevatedDeckY == null || targetY < elevatedDeckY - 1)) {
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
        // 合法 expectedTunnelLining / 受保护方块不得当残留剥离。
        if (role == JunctionWorldFootprint.ElevationRole.FILLET
                || role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
            for (BlockPos pos : plazaFamilyBelow) {
                if (isAuthorizedTunnelStructure(aggregate, protectedBlocks, pos)) {
                    continue;
                }
                clearLeftover(aggregate, pos);
            }
            for (BlockPos pos : roadAtColumn) {
                if (pos.getY() < targetY && pos.getY() >= targetY - UNDER_PAVEMENT_STRIP_DEPTH) {
                    clearLeftover(aggregate, pos);
                }
            }
            for (BlockPos pos : tunnelSolids) {
                if (pos.getY() < targetY && pos.getY() >= targetY - UNDER_PAVEMENT_STRIP_DEPTH) {
                    if (isAuthorizedTunnelStructure(aggregate, protectedBlocks, pos)) {
                        continue;
                    }
                    clearLeftover(aggregate, pos);
                }
            }
        }

        // 圆角/接缝列：仅清紧贴路面上的残留侧墙；禁止清到洞顶高度（避免路面局部下沉时顺带掏掉顶衬）。
        if (role == JunctionWorldFootprint.ElevationRole.FILLET
                || role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
            int cavityCeiling = targetY + 2;
            for (BlockPos pos : tunnelSolids) {
                if (pos.getY() <= targetY || pos.getY() > cavityCeiling) {
                    continue;
                }
                if (protectedBlocks.contains(pos)
                        || isAuthorizedTunnelStructure(aggregate, protectedBlocks, pos)) {
                    continue;
                }
                clearLeftover(aggregate, pos);
            }
        }
    }

    /**
     * 接缝回填材料：优先剥离前快照；否则扫剩余 ROAD 记录；绝不以 air 回填。
     */
    public static String resolvePavementRestoreMaterial(
            String savedPavementMaterial,
            RoadGenerationResult aggregate,
            List<BlockPos> roadAtColumn) {
        if (isUsablePavementMaterial(savedPavementMaterial)) {
            return savedPavementMaterial;
        }
        if (aggregate == null || roadAtColumn == null) {
            return null;
        }
        for (BlockPos pos : roadAtColumn) {
            BlockRecord record = aggregate.placementRecords.get(pos);
            if (record != null && isUsablePavementMaterial(record.newBlockId)) {
                return record.newBlockId;
            }
        }
        return null;
    }

    private static boolean isUsablePavementMaterial(String blockId) {
        return blockId != null
            && !blockId.isBlank()
            && !"minecraft:air".equals(blockId);
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

    static boolean isAuthorizedTunnelStructure(
            RoadGenerationResult aggregate,
            Set<BlockPos> protectedBlocks,
            BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (protectedBlocks != null && protectedBlocks.contains(pos)) {
            return true;
        }
        return aggregate != null
            && aggregate.expectedTunnelLining != null
            && aggregate.expectedTunnelLining.contains(pos);
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
            if (protectedBlocks.contains(pos)
                    || isAuthorizedTunnelStructure(aggregate, protectedBlocks, pos)) {
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
