package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
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
 * 重叠路口标高：同层且拓扑相连才协商统一 Y；立交/大跨度或无连接足迹重叠则各用自身 Y，
 * 且不得剥掉对端声称的路面标高。清理路面时同步撤销 {@code expectedRoadSurfaces}，避免封口回铺。
 */
public final class JunctionSeamReconciliationPass {
    /** 圆角抬高时，向下剥离进口路面/衬砌/装饰环的最大深度（格）。 */
    static final int UNDER_PAVEMENT_STRIP_DEPTH = 8;

    private JunctionSeamReconciliationPass() {
    }

    public static void apply(
            RoadGenerationResult aggregate,
            Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults) {
        apply(aggregate, junctionResults, null);
    }

    public static void apply(
            RoadGenerationResult aggregate,
            Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults,
            RoadNetwork network) {
        if (aggregate == null || junctionResults == null || junctionResults.isEmpty()) {
            return;
        }
        Map<ColumnKey, List<BlockPos>> solidsByColumn = indexSolidPlacements(aggregate);
        ColumnElevationPlan plan = resolveColumnElevationPlan(junctionResults, network);
        for (Map.Entry<String, RoadJunctionGenerator.JunctionBlocks> entry : junctionResults.entrySet()) {
            RoadJunctionGenerator.JunctionBlocks blocks = entry.getValue();
            if (blocks == null) {
                continue;
            }
            reconcileJunction(
                aggregate,
                entry.getKey(),
                blocks,
                solidsByColumn,
                plan);
        }
    }

    /**
     * 按列解析各路口设计 Y，以及不得被其它路口剥离的对端路面 Y。
     */
    static ColumnElevationPlan resolveColumnElevationPlan(
            Map<String, RoadJunctionGenerator.JunctionBlocks> junctionResults,
            RoadNetwork network) {
        Map<ColumnKey, List<ColumnClaim>> claimsByColumn = new HashMap<>();
        if (junctionResults != null) {
            for (Map.Entry<String, RoadJunctionGenerator.JunctionBlocks> entry : junctionResults.entrySet()) {
                String nodeId = entry.getKey();
                RoadJunctionGenerator.JunctionBlocks blocks = entry.getValue();
                if (nodeId == null || blocks == null || blocks.elevationField() == null) {
                    continue;
                }
                JunctionValidationManifest manifest = blocks.validationManifest();
                int junctionY = manifest != null ? manifest.junctionY() : blocks.elevationField().junctionY();
                boolean gradeSeparated = isGradeSeparatedClaim(network, nodeId, manifest);
                for (Map.Entry<ColumnKey, Integer> target : blocks.elevationField().targetYByColumn().entrySet()) {
                    if (target.getKey() == null || target.getValue() == null) {
                        continue;
                    }
                    claimsByColumn
                        .computeIfAbsent(target.getKey(), ignored -> new ArrayList<>())
                        .add(new ColumnClaim(nodeId, target.getValue(), junctionY, gradeSeparated));
                }
            }
        }

        Map<String, Map<ColumnKey, Integer>> designYByJunction = new HashMap<>();
        Map<ColumnKey, Set<Integer>> preservedYsByColumn = new HashMap<>();

        for (Map.Entry<ColumnKey, List<ColumnClaim>> entry : claimsByColumn.entrySet()) {
            ColumnKey column = entry.getKey();
            List<ColumnClaim> claims = entry.getValue();
            if (claims.size() == 1) {
                ColumnClaim only = claims.getFirst();
                designYByJunction
                    .computeIfAbsent(only.nodeId(), ignored -> new HashMap<>())
                    .put(column, only.targetY());
                continue;
            }

            boolean keepBoth = false;
            for (ColumnClaim claim : claims) {
                if (claim.gradeSeparated()) {
                    keepBoth = true;
                    break;
                }
            }
            if (!keepBoth) {
                int minJy = Integer.MAX_VALUE;
                int maxJy = Integer.MIN_VALUE;
                for (ColumnClaim claim : claims) {
                    minJy = Math.min(minJy, claim.junctionY());
                    maxJy = Math.max(maxJy, claim.junctionY());
                }
                if (maxJy - minJy > JunctionFilletElevationLoft.SAME_LEVEL_Y_SPAN) {
                    keepBoth = true;
                }
            }

            if (keepBoth || network == null || !anyConnectedPair(claims, network)) {
                // 立交 / 大跨度 / 无拓扑连接：各用自身 Y，并保护对端声称高度。
                Set<Integer> preserved = preservedYsByColumn.computeIfAbsent(column, ignored -> new HashSet<>());
                for (ColumnClaim claim : claims) {
                    designYByJunction
                        .computeIfAbsent(claim.nodeId(), ignored -> new HashMap<>())
                        .put(column, claim.targetY());
                    preserved.add(claim.targetY());
                }
                continue;
            }

            // 同层且至少一对相连：在相连子图内协商 max；孤立声明者仍用自身 Y。
            List<Set<String>> components = connectedComponents(claims, network);
            Map<String, Integer> claimY = new HashMap<>();
            for (ColumnClaim claim : claims) {
                claimY.put(claim.nodeId(), claim.targetY());
            }
            Set<Integer> preserved = preservedYsByColumn.computeIfAbsent(column, ignored -> new HashSet<>());
            Set<String> negotiated = new HashSet<>();
            for (Set<String> component : components) {
                if (component.size() >= 2) {
                    int negotiatedY = Integer.MIN_VALUE;
                    for (String id : component) {
                        negotiatedY = Math.max(negotiatedY, claimY.getOrDefault(id, Integer.MIN_VALUE));
                    }
                    for (String id : component) {
                        designYByJunction
                            .computeIfAbsent(id, ignored -> new HashMap<>())
                            .put(column, negotiatedY);
                        negotiated.add(id);
                    }
                    preserved.add(negotiatedY);
                }
            }
            for (ColumnClaim claim : claims) {
                if (negotiated.contains(claim.nodeId())) {
                    continue;
                }
                designYByJunction
                    .computeIfAbsent(claim.nodeId(), ignored -> new HashMap<>())
                    .put(column, claim.targetY());
                preserved.add(claim.targetY());
            }
        }

        Map<ColumnKey, Set<Integer>> frozenPreserved = new HashMap<>();
        for (var entry : preservedYsByColumn.entrySet()) {
            frozenPreserved.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return new ColumnElevationPlan(designYByJunction, frozenPreserved);
    }

    private static boolean isGradeSeparatedClaim(
            RoadNetwork network,
            String nodeId,
            JunctionValidationManifest manifest) {
        if (manifest != null && manifest.hasGradeSeparation()) {
            return true;
        }
        if (network == null || nodeId == null) {
            return false;
        }
        RoadNode node = network.getNode(nodeId);
        return node != null && (node.isGradeSeparated() || node.isStackGradeSeparated());
    }

    private static boolean anyConnectedPair(List<ColumnClaim> claims, RoadNetwork network) {
        for (int i = 0; i < claims.size(); i++) {
            for (int j = i + 1; j < claims.size(); j++) {
                if (nodesShareEdge(network, claims.get(i).nodeId(), claims.get(j).nodeId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Set<String>> connectedComponents(List<ColumnClaim> claims, RoadNetwork network) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (ColumnClaim claim : claims) {
            adjacency.put(claim.nodeId(), new HashSet<>());
        }
        for (int i = 0; i < claims.size(); i++) {
            for (int j = i + 1; j < claims.size(); j++) {
                String a = claims.get(i).nodeId();
                String b = claims.get(j).nodeId();
                if (nodesShareEdge(network, a, b)) {
                    adjacency.get(a).add(b);
                    adjacency.get(b).add(a);
                }
            }
        }
        List<Set<String>> components = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (ColumnClaim claim : claims) {
            if (visited.contains(claim.nodeId())) {
                continue;
            }
            Set<String> component = floodComponent(claim.nodeId(), adjacency);
            visited.addAll(component);
            components.add(component);
        }
        return components;
    }

    private static Set<String> floodComponent(String start, Map<String, Set<String>> adjacency) {
        Set<String> component = new HashSet<>();
        List<String> stack = new ArrayList<>();
        stack.add(start);
        while (!stack.isEmpty()) {
            String id = stack.removeLast();
            if (!component.add(id)) {
                continue;
            }
            stack.addAll(adjacency.getOrDefault(id, Set.of()));
        }
        return component;
    }

    private static boolean nodesShareEdge(RoadNetwork network, String a, String b) {
        if (network == null || a == null || b == null || a.equals(b)) {
            return false;
        }
        for (RoadEdge edge : network.getEdgesAtNode(a)) {
            if (edge == null) {
                continue;
            }
            if (a.equals(edge.getStartNodeId()) && b.equals(edge.getEndNodeId())) {
                return true;
            }
            if (b.equals(edge.getStartNodeId()) && a.equals(edge.getEndNodeId())) {
                return true;
            }
        }
        return false;
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
            String nodeId,
            RoadJunctionGenerator.JunctionBlocks blocks,
            Map<ColumnKey, List<BlockPos>> solidsByColumn,
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
        Map<ColumnKey, Integer> designByColumn = plan != null
            ? plan.designYByJunction().getOrDefault(nodeId, Map.of())
            : Map.of();

        for (Map.Entry<ColumnKey, Integer> entry : field.targetYByColumn().entrySet()) {
            ColumnKey column = entry.getKey();
            Integer targetY = entry.getValue();
            if (column == null || targetY == null) {
                continue;
            }
            int designY = designByColumn.getOrDefault(column, targetY);
            Set<Integer> preservedYs = plan != null
                ? plan.preservedYsByColumn().getOrDefault(column, Set.of())
                : Set.of();
            JunctionWorldFootprint.ElevationRole role = field.role(column);
            reconcileColumn(
                aggregate, column, designY, elevatedDeckY, protectedBlocks, preservedYs, role, index);
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
            Map<ColumnKey, List<BlockPos>> solidsByColumn) {
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

        // 设计 Y 已裁定后，作废同列非保留高度的期望，防止封口按旧期望回铺双层。
        revokeConflictingSurfaceExpectations(aggregate, column, targetY, preservedYs);
    }

    static void revokeConflictingSurfaceExpectations(
            RoadGenerationResult aggregate,
            ColumnKey column,
            int targetY,
            Set<Integer> preservedYs) {
        if (aggregate == null || aggregate.expectedRoadSurfaces == null || column == null) {
            return;
        }
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos pos : aggregate.expectedRoadSurfaces.keySet()) {
            if (pos == null
                    || pos.getX() != column.worldX()
                    || pos.getZ() != column.worldZ()
                    || pos.getY() == targetY) {
                continue;
            }
            if (preservedYs != null && preservedYs.contains(pos.getY())) {
                continue;
            }
            stale.add(pos);
        }
        for (BlockPos pos : stale) {
            aggregate.expectedRoadSurfaces.remove(pos);
            aggregate.expectedRoadSurfaces.remove(pos.toImmutable());
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
        aggregate.placementMaterialPriorities.put(pos, Integer.valueOf(RoadSolidMaterialPriority.CLEARANCE));
        // 同步作废设计期望，避免 UnderpassPavementSealPass 把已清层再铺回来。
        if (aggregate.expectedRoadSurfaces != null) {
            aggregate.expectedRoadSurfaces.remove(pos);
            aggregate.expectedRoadSurfaces.remove(pos.toImmutable());
        }
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

    record ColumnClaim(String nodeId, int targetY, int junctionY, boolean gradeSeparated) {
    }

    record ColumnElevationPlan(
            Map<String, Map<ColumnKey, Integer>> designYByJunction,
            Map<ColumnKey, Set<Integer>> preservedYsByColumn) {
    }
}
