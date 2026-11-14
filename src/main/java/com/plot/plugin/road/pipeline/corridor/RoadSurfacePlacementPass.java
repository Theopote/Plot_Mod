package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Function;

/** The rasterized pavement footprint owns its surface, including diagonal tunnel mouths. */
public final class RoadSurfacePlacementPass {
    private RoadSurfacePlacementPass() { }

    public static void apply(RoadGenerationResult result, RoadCorridorPlan plan, int clearHeight) {
        apply(result, plan, clearHeight, GradeSeparationBuildContext.NONE, null);
    }

    public static void apply(
            RoadGenerationResult result,
            RoadCorridorPlan plan,
            int clearHeight,
            GradeSeparationBuildContext gradeSeparation) {
        apply(result, plan, clearHeight, gradeSeparation, null);
    }

    /**
     * @param previousBlockId resolves undo baselines for surface cells that never received a
     *     solid emit (e.g. grade-sep lower pavement gaps). Prefer world projection; null falls
     *     back to {@code minecraft:air}.
     */
    public static void apply(
            RoadGenerationResult result,
            RoadCorridorPlan plan,
            int clearHeight,
            GradeSeparationBuildContext gradeSeparation,
            Function<BlockPos, String> previousBlockId) {
        for (var entry : plan.surfaceMask().entries()) {
            var cell = entry.getValue();
            if (cell.materialId() == null) continue;
            var pos = new BlockPos(entry.getKey().worldX(), cell.surfaceY(), entry.getKey().worldZ());
            result.recordExpectedRoadSurface(new ExpectedRoadSurface(
                pos, cell.materialId(), cell.layer(), result.edgeId));
            var existing = result.placementRecords.get(pos);
            var clearance = plan.clearanceCell(entry.getKey());
            if (clearance != null && (clearance.mode() == RoadCorridorConstructionMode.OPEN
                    || clearance.mode() == RoadCorridorConstructionMode.BRIDGE)) {
                int top = cell.surfaceY() + Math.max(1, clearHeight);
                Integer deckCap = gradeSeparation != null
                    ? gradeSeparation.maxOpenClearTopY(cell.planPoint())
                    : null;
                if (deckCap != null) {
                    top = Math.min(top, deckCap);
                }
                result.openRoadClearanceTops.put(pos, top);
            }
            if (existing == null) {
                // Final pavement mask owns the cell: stamp designed material even when solids
                // never wrote a record (crossing gaps under grade-sep decks).
                String previous = resolvePrevious(previousBlockId, pos);
                result.recordPlacementOverride(pos, previous, cell.materialId());
                result.placementLayers.put(pos, cell.layer());
                result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.DEFAULT);
                continue;
            }
            var layer = result.placementLayers.get(pos);
            if (!"minecraft:air".equals(existing.newBlockId) && layer != RoadSolidLayer.TUNNEL
                    && layer != RoadSolidLayer.SUBGRADE && layer != RoadSolidLayer.BRIDGE) continue;
            result.recordPlacementOverride(pos, existing.previousBlockId, cell.materialId());
            result.placementLayers.put(pos, cell.layer());
            result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.DEFAULT);
        }
    }

    private static String resolvePrevious(Function<BlockPos, String> previousBlockId, BlockPos pos) {
        if (previousBlockId != null) {
            String resolved = previousBlockId.apply(pos);
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        return "minecraft:air";
    }

    /**
     * Clear SUBGRADE and BRIDGE intrusions in reserved open-road headroom (not ROAD/SHOULDER surfaces).
     * <p>
     * When the same column already has an elevated pavement above the open surface,
     * stop below that deck and never strip structural {@link RoadSolidLayer#BRIDGE} underlay —
     * but dirt/grass {@link RoadSolidLayer#SUBGRADE} under the deck is terrain residue and must go.
     * <p>
     * Also walks stacked pavement columns (upper above lower) so bridge overhangs outside
     * {@code openRoadClearanceTops} still get their shaft cleared.
     */
    public static void clearIntrudingFill(RoadGenerationResult result) {
        if (result == null) {
            return;
        }
        Map<ColumnKey, NavigableSet<Integer>> pavementYsByColumn = buildPavementYIndex(result);
        if (!result.openRoadClearanceTops.isEmpty()) {
            result.openRoadClearanceTops.forEach((surface, top) -> {
                Integer elevatedPavementY = lowestProtectedPavementAbove(
                    pavementYsByColumn, surface.getX(), surface.getY(), surface.getZ());
                int clearTo = elevatedPavementY != null
                    ? Math.min(top, elevatedPavementY - JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH - 1)
                    : top;
                clearShaftColumn(result, surface.getX(), surface.getZ(), surface.getY(), clearTo, elevatedPavementY);
                if (elevatedPavementY != null) {
                    sealElevatedDeckSupport(result, surface.getX(), elevatedPavementY, surface.getZ());
                }
            });
        }
        // 上层路面柱：即使没有下层 openRoadClearanceTops，也清桥底净空并封承重层。
        for (var entry : pavementYsByColumn.entrySet()) {
            NavigableSet<Integer> ys = entry.getValue();
            if (ys == null || ys.size() < 2) {
                continue;
            }
            ColumnKey column = entry.getKey();
            for (int elevatedY : ys) {
                Integer lowerY = ys.lower(elevatedY);
                if (lowerY == null) {
                    continue;
                }
                if (elevatedY - lowerY <= JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH + 1) {
                    continue;
                }
                int clearTo = elevatedY - JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH - 1;
                clearShaftColumn(result, column.worldX(), column.worldZ(), lowerY, clearTo, elevatedY);
                sealElevatedDeckSupport(result, column.worldX(), elevatedY, column.worldZ());
            }
        }
    }

    private static void clearShaftColumn(
            RoadGenerationResult result,
            int worldX,
            int worldZ,
            int surfaceY,
            int clearTo,
            Integer elevatedPavementY) {
        for (int y = surfaceY + 1; y <= clearTo; y++) {
            var pos = new BlockPos(worldX, y, worldZ);
            var record = result.placementRecords.get(pos);
            var layer = result.placementLayers.get(pos);
            // A solid tunnel lining ends the lower cavity. Never excavate its roof
            // or the mountain between that roof and an unrelated upper pavement.
            if (layer == RoadSolidLayer.TUNNEL && record != null
                    && !"minecraft:air".equals(record.newBlockId)) {
                break;
            }
            // 只保留上层结构性 BRIDGE 底板；泥土 SUBGRADE 不算承重层。
            if (elevatedPavementY != null
                    && y >= elevatedPavementY - JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH
                    && layer == RoadSolidLayer.BRIDGE
                    && record != null
                    && !isTerrainResidueBlock(record.newBlockId)) {
                continue;
            }
            if (record == null) {
                // 仅立交下层净空：补齐缺失 AIR，清除未进入 placement 的原地形。
                // 普通开敞路不得写入 air→air，否则空天空也会膨胀 placementRecords（破坏 golden）。
                // 纵坡台阶漏清由 clearance designY←surfaceMask 修正，不依赖此处灌空气记录。
                if (elevatedPavementY != null) {
                    result.recordPlacementOverride(pos, "minecraft:air", "minecraft:air");
                    result.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
                    result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.CLEARANCE);
                }
                continue;
            }
            if ("minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            if (layer != RoadSolidLayer.SUBGRADE
                    && layer != RoadSolidLayer.BRIDGE
                    && layer != RoadSolidLayer.TUNNEL) {
                continue;
            }
            result.recordPlacementOverride(pos, record.previousBlockId, "minecraft:air");
            result.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
            result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.CLEARANCE);
        }
    }

    /**
     * 上层路面正下方一格必须是结构性底板；缺失或泥土/草方残留时补 BRIDGE。
     * <ul>
     *   <li>已有有效 BRIDGE 底板：保留原材质</li>
     *   <li>已有隧道/棚洞结构实体：保留（真实山体下穿），不换成桥面石材</li>
     *   <li>缺失时：优先继承邻近同高 BRIDGE 底板材质，否则用桥面板缺省石材</li>
     * </ul>
     */
    static void sealElevatedDeckSupport(RoadGenerationResult result, int worldX, int elevatedPavementY, int worldZ) {
        RoadSolidLayer pavementLayer = result.placementLayers.get(new BlockPos(worldX, elevatedPavementY, worldZ));
        if (pavementLayer != RoadSolidLayer.ROAD && pavementLayer != RoadSolidLayer.MARKING) {
            return;
        }
        int supportY = elevatedPavementY - JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH;
        BlockPos support = new BlockPos(worldX, supportY, worldZ);
        var record = result.placementRecords.get(support);
        var layer = result.placementLayers.get(support);
        if (record != null && !"minecraft:air".equals(record.newBlockId)
                && isElevatedPavementLayer(layer)) {
            return;
        }
        if (record != null
                && layer == RoadSolidLayer.BRIDGE
                && !isTerrainResidueBlock(record.newBlockId)) {
            return;
        }
        // Legitimate tunnel/shelter lining under the deck — do not replace with bridge underlay.
        if (record != null
                && layer == RoadSolidLayer.TUNNEL
                && record.newBlockId != null
                && !"minecraft:air".equals(record.newBlockId)
                && !isTerrainResidueBlock(record.newBlockId)) {
            return;
        }
        String previous = record != null ? record.previousBlockId : "minecraft:air";
        if (previous == null || previous.isBlank()) {
            previous = "minecraft:air";
        }
        String material = resolveElevatedDeckSupportMaterial(result, worldX, supportY, worldZ);
        result.recordPlacementOverride(support, previous, material);
        result.placementLayers.put(support, RoadSolidLayer.BRIDGE);
        result.placementMaterialPriorities.put(support, RoadSolidMaterialPriority.STRUCTURAL);
    }

    /**
     * Prefer an existing BRIDGE underlay material near this column so open grade-sep
     * decks stay visually continuous with the elevated road's own slab.
     */
    static String resolveElevatedDeckSupportMaterial(
            RoadGenerationResult result, int worldX, int supportY, int worldZ) {
        if (result == null) {
            return JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_BLOCK;
        }
        // Search a few blocks — elevated decks often overhang the lower corridor.
        int[][] dirs = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {3, 0}, {-3, 0}, {0, 3}, {0, -3},
            {4, 0}, {-4, 0}, {0, 4}, {0, -4}
        };
        for (int[] dir : dirs) {
            BlockPos neighbor = new BlockPos(worldX + dir[0], supportY, worldZ + dir[1]);
            if (result.placementLayers.get(neighbor) != RoadSolidLayer.BRIDGE) {
                continue;
            }
            var record = result.placementRecords.get(neighbor);
            if (record == null || record.newBlockId == null || isTerrainResidueBlock(record.newBlockId)) {
                continue;
            }
            if ("minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            return record.newBlockId;
        }
        return JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_BLOCK;
    }

    static boolean isTerrainResidueBlock(String blockId) {
        if (blockId == null || blockId.isBlank() || "minecraft:air".equals(blockId)) {
            return true;
        }
        return switch (blockId) {
            case "minecraft:dirt",
                 "minecraft:grass_block",
                 "minecraft:coarse_dirt",
                 "minecraft:rooted_dirt",
                 "minecraft:podzol",
                 "minecraft:mycelium",
                 "minecraft:mud",
                 "minecraft:dirt_path",
                 "minecraft:farmland" -> true;
            default -> false;
        };
    }

    static Map<ColumnKey, NavigableSet<Integer>> buildPavementYIndex(RoadGenerationResult result) {
        Map<ColumnKey, NavigableSet<Integer>> index = new HashMap<>();
        if (result == null) {
            return index;
        }
        for (var entry : result.placementLayers.entrySet()) {
            if (!isElevatedPavementLayer(entry.getValue())) {
                continue;
            }
            BlockPos pos = entry.getKey();
            var record = result.placementRecords.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            index.computeIfAbsent(new ColumnKey(pos.getX(), pos.getZ()), ignored -> new TreeSet<>())
                .add(pos.getY());
        }
        return index;
    }

    static Integer lowestProtectedPavementAbove(
            Map<ColumnKey, NavigableSet<Integer>> pavementYsByColumn,
            int worldX,
            int surfaceY,
            int worldZ) {
        if (pavementYsByColumn == null || pavementYsByColumn.isEmpty()) {
            return null;
        }
        NavigableSet<Integer> ys = pavementYsByColumn.get(new ColumnKey(worldX, worldZ));
        if (ys == null || ys.isEmpty()) {
            return null;
        }
        return ys.higher(surfaceY + JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH + 1);
    }

    private static boolean isElevatedPavementLayer(RoadSolidLayer layer) {
        // Pavement only — BRIDGE shells alone in headroom are still intrusions.
        // BRIDGE under an elevated ROAD is kept separately when pavement is detected.
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            || layer == RoadSolidLayer.MARKING;
    }

}
