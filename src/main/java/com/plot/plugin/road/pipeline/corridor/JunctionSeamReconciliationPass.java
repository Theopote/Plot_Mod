package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.JunctionBlocks;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.corridor.ColumnDesignElevationPlanner.ColumnElevationPlan;
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
 * 重叠列设计 Y 由 {@link ColumnDesignElevationPlanner} 在合并前一次算定；本 Pass 只消费，
 * 清理路面时同步撤销 {@code expectedRoadSurfaces}，避免封口回铺。
 */
public final class JunctionSeamReconciliationPass {
    /** 圆角抬高时，向下剥离进口路面/衬砌/装饰环的最大深度（格）。 */
    static final int UNDER_PAVEMENT_STRIP_DEPTH = 8;

    private JunctionSeamReconciliationPass() {
    }

    public static void apply(
            RoadGenerationResult aggregate,
            Map<String, JunctionBlocks> junctionResults) {
        apply(aggregate, junctionResults, (RoadNetwork) null);
    }

    public static void apply(
            RoadGenerationResult aggregate,
            Map<String, JunctionBlocks> junctionResults,
            RoadNetwork network) {
        if (aggregate == null || junctionResults == null || junctionResults.isEmpty()) {
            return;
        }
        Map<ColumnKey, List<BlockPos>> solidsByColumn = indexSolidPlacements(aggregate);
        ColumnElevationPlan plan = ColumnDesignElevationPlanner.plan(junctionResults, network);
        apply(aggregate, junctionResults, plan, solidsByColumn);
    }

    /**
     * 使用已算定的列设计标高规划（assemble 前一次规划时可注入，避免接缝内再裁决）。
     */
    public static void apply(
            RoadGenerationResult aggregate,
            Map<String, JunctionBlocks> junctionResults,
            ColumnElevationPlan plan) {
        if (aggregate == null || junctionResults == null || junctionResults.isEmpty()) {
            return;
        }
        apply(aggregate, junctionResults, plan, indexSolidPlacements(aggregate));
    }

    private static void apply(
            RoadGenerationResult aggregate,
            Map<String, JunctionBlocks> junctionResults,
            ColumnElevationPlan plan,
            Map<ColumnKey, List<BlockPos>> solidsByColumn) {
        ColumnElevationPlan safePlan = plan != null ? plan : ColumnElevationPlan.empty();
        Map<ColumnKey, List<BlockPos>> expectedByColumn = indexExpectedSurfacesByColumn(aggregate);
        for (Map.Entry<String, JunctionBlocks> entry : junctionResults.entrySet()) {
            JunctionBlocks blocks = entry.getValue();
            if (blocks == null) {
                continue;
            }
            reconcileJunction(
                aggregate,
                entry.getKey(),
                blocks,
                solidsByColumn,
                expectedByColumn,
                safePlan);
        }
    }

    /** @deprecated 保留测试兼容；新代码请用 {@link ColumnDesignElevationPlanner#plan}. */
    @Deprecated
    static ColumnElevationPlan resolveColumnElevationPlan(
            Map<String, JunctionBlocks> junctionResults,
            RoadNetwork network) {
        return ColumnDesignElevationPlanner.plan(junctionResults, network);
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

    /**
     * 设计期望路面按世界列归桶；接缝撤销只访问当前列，避免 O(C×S) 全表扫描。
     */
    public static Map<ColumnKey, List<BlockPos>> indexExpectedSurfacesByColumn(
            RoadGenerationResult aggregate) {
        Map<ColumnKey, List<BlockPos>> byColumn = new HashMap<>();
        if (aggregate == null || aggregate.expectedRoadSurfaces == null
                || aggregate.expectedRoadSurfaces.isEmpty()) {
            return byColumn;
        }
        for (BlockPos pos : aggregate.expectedRoadSurfaces.keySet()) {
            if (pos == null) {
                continue;
            }
            ColumnKey key = new ColumnKey(pos.getX(), pos.getZ());
            byColumn.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pos);
        }
        return byColumn;
    }

    private static void reconcileJunction(
            RoadGenerationResult aggregate,
            String nodeId,
            JunctionBlocks blocks,
            Map<ColumnKey, List<BlockPos>> solidsByColumn,
            Map<ColumnKey, List<BlockPos>> expectedByColumn,
            ColumnElevationPlan plan) {
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
        Map<ColumnKey, List<BlockPos>> expectedIndex =
            expectedByColumn != null ? expectedByColumn : Map.of();
        ColumnElevationPlan safePlan = plan != null ? plan : ColumnElevationPlan.empty();

        for (Map.Entry<ColumnKey, Integer> entry : field.targetYByColumn().entrySet()) {
            ColumnKey column = entry.getKey();
            Integer targetY = entry.getValue();
            if (column == null || targetY == null) {
                continue;
            }
            int designY = safePlan.designY(nodeId, column, targetY);
            Set<Integer> preservedYs = safePlan.preservedYs(column);
            JunctionWorldFootprint.ElevationRole role = field.role(column);
            reconcileColumn(
                aggregate, column, designY, elevatedDeckY, protectedBlocks, preservedYs, role,
                index, expectedIndex);
        }
    }

    private static void reconcileColumn(
            RoadGenerationResult aggregate,
            ColumnKey column,
            int targetY,
            Integer elevatedDeckY,
            Set<BlockPos> protectedBlocks,
            Set<Integer> preservedYs,
            JunctionWorldFootprint.ElevationRole role,
            Map<ColumnKey, List<BlockPos>> solidsByColumn,
            Map<ColumnKey, List<BlockPos>> expectedByColumn) {
        List<BlockPos> roadAtColumn = new ArrayList<>();
        List<BlockPos> tunnelSolids = new ArrayList<>();
        List<BlockPos> plazaFamilyBelow = new ArrayList<>();
        List<BlockPos> solids = solidsByColumn != null
            ? solidsByColumn.getOrDefault(column, List.of())
            : List.of();
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
            if (preservedYs != null && preservedYs.contains(pos.getY()) && pos.getY() != targetY) {
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
                    && isStackedLeftoverLayer(layer)
                    && (preservedYs == null || !preservedYs.contains(pos.getY()))) {
                plazaFamilyBelow.add(pos);
            }
        }

        stripPavementAboveTarget(
            aggregate, targetY, elevatedDeckY, protectedBlocks, preservedYs, solids);

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
                    rememberSolid(solidsByColumn, column, target);
                }
            }
        }

        if (role == JunctionWorldFootprint.ElevationRole.FILLET
                || role == JunctionWorldFootprint.ElevationRole.SEAM_TRANSITION) {
            for (BlockPos pos : plazaFamilyBelow) {
                if (isAuthorizedTunnelStructure(aggregate, protectedBlocks, pos)) {
                    continue;
                }
                clearLeftover(aggregate, pos);
            }
            for (BlockPos pos : roadAtColumn) {
                if (pos.getY() < targetY
                        && pos.getY() >= targetY - UNDER_PAVEMENT_STRIP_DEPTH
                        && (preservedYs == null || !preservedYs.contains(pos.getY()))) {
                    clearLeftover(aggregate, pos);
                }
            }
            for (BlockPos pos : tunnelSolids) {
                if (pos.getY() < targetY && pos.getY() >= targetY - UNDER_PAVEMENT_STRIP_DEPTH) {
                    if (isAuthorizedTunnelStructure(aggregate, protectedBlocks, pos)) {
                        continue;
                    }
                    if (preservedYs != null && preservedYs.contains(pos.getY())) {
                        continue;
                    }
                    clearLeftover(aggregate, pos);
                }
            }
        }

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

        // 设计 Y 已裁定后，作废同列非保留高度的期望；保护条件与实体剥离一致。
        revokeConflictingSurfaceExpectations(
            aggregate, column, targetY, preservedYs, elevatedDeckY, protectedBlocks, expectedByColumn);
    }

    public static void revokeConflictingSurfaceExpectations(
            RoadGenerationResult aggregate,
            ColumnKey column,
            int targetY,
            Set<Integer> preservedYs) {
        revokeConflictingSurfaceExpectations(
            aggregate, column, targetY, preservedYs, null, Set.of(),
            indexExpectedSurfacesByColumn(aggregate));
    }

    public static void revokeConflictingSurfaceExpectations(
            RoadGenerationResult aggregate,
            ColumnKey column,
            int targetY,
            Set<Integer> preservedYs,
            Map<ColumnKey, List<BlockPos>> expectedByColumn) {
        revokeConflictingSurfaceExpectations(
            aggregate, column, targetY, preservedYs, null, Set.of(), expectedByColumn);
    }

    /**
     * 与实体接缝剥离同一套保护：targetY / preservedYs / elevatedDeckY / protectedBlocks。
     */
    public static void revokeConflictingSurfaceExpectations(
            RoadGenerationResult aggregate,
            ColumnKey column,
            int targetY,
            Set<Integer> preservedYs,
            Integer elevatedDeckY,
            Set<BlockPos> protectedBlocks,
            Map<ColumnKey, List<BlockPos>> expectedByColumn) {
        if (aggregate == null || aggregate.expectedRoadSurfaces == null || column == null) {
            return;
        }
        List<BlockPos> columnExpectations = expectedByColumn != null
            ? expectedByColumn.get(column)
            : null;
        if (columnExpectations == null || columnExpectations.isEmpty()) {
            return;
        }
        Set<BlockPos> protectedSet = protectedBlocks != null ? protectedBlocks : Set.of();
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos pos : columnExpectations) {
            if (pos == null || pos.getY() == targetY) {
                continue;
            }
            if (preservedYs != null && preservedYs.contains(pos.getY())) {
                continue;
            }
            // 与 reconcileColumn 实体保护对齐：上层桥面/底板不得因下层接缝撤销期望。
            if (elevatedDeckY != null && pos.getY() >= elevatedDeckY - 1) {
                continue;
            }
            if (protectedSet.contains(pos) || protectedSet.contains(pos.toImmutable())) {
                continue;
            }
            stale.add(pos);
        }
        if (stale.isEmpty()) {
            return;
        }
        for (BlockPos pos : stale) {
            BlockPos key = pos.toImmutable();
            aggregate.revokeSurfaceExpectation(key);
            columnExpectations.remove(key);
        }
        if (columnExpectations.isEmpty()) {
            expectedByColumn.remove(column);
        }
    }

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
            Set<Integer> preservedYs,
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
            if (preservedYs != null && preservedYs.contains(pos.getY())) {
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
        aggregate.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.CLEARANCE);
        // 同步作废设计期望，避免 UnderpassPavementSealPass 把已清层再铺回来。
        aggregate.revokeSurfaceExpectation(pos);
        // 接缝清场空气即授权洞腔，清除残留 lining 期望。
        aggregate.adoptClearanceAsTunnelCavity(pos, ClearancePurpose.TUNNEL_INTERIOR);
    }

    private static void rememberSolid(
            Map<ColumnKey, List<BlockPos>> solidsByColumn,
            ColumnKey column,
            BlockPos pos) {
        if (solidsByColumn == null || column == null || pos == null) {
            return;
        }
        List<BlockPos> list = solidsByColumn.computeIfAbsent(column, ignored -> new ArrayList<>());
        if (!list.contains(pos)) {
            list.add(pos);
        }
    }

}
