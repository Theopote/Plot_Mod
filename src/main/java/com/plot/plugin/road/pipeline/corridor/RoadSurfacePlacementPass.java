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
     * Other approaches can add batter/bridge fill after the original (empty) terrain was inspected.
     * Clear SUBGRADE and BRIDGE intrusions in reserved open-road headroom (not ROAD/SHOULDER surfaces).
     * <p>
     * When the same column already has an elevated pavement (ROAD/BRIDGE/…) above the open surface,
     * stop below that deck and never strip BRIDGE structure that supports it — otherwise grade-sep
     * underpass headroom clearing eats the upper road underside after network merge.
     * <p>
     * Pavement Y lookup is O(log H) per surface column via a one-shot XZ index (not O(P) per surface).
     */
    public static void clearIntrudingFill(RoadGenerationResult result) {
        if (result == null || result.openRoadClearanceTops.isEmpty()) {
            return;
        }
        Map<ColumnKey, NavigableSet<Integer>> pavementYsByColumn = buildPavementYIndex(result);
        result.openRoadClearanceTops.forEach((surface, top) -> {
            Integer elevatedPavementY = lowestProtectedPavementAbove(
                pavementYsByColumn, surface.getX(), surface.getY(), surface.getZ());
            int clearTo = elevatedPavementY != null ? Math.min(top, elevatedPavementY - 1) : top;
            for (int y = surface.getY() + 1; y <= clearTo; y++) {
                var pos = new BlockPos(surface.getX(), y, surface.getZ());
                var record = result.placementRecords.get(pos);
                if (record == null || "minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                var layer = result.placementLayers.get(pos);
                // SUBGRADE/BRIDGE fill from other approaches, and stray TUNNEL lining that
                // leaked into an OPEN/BRIDGE headroom column.
                if (layer != RoadSolidLayer.SUBGRADE
                        && layer != RoadSolidLayer.BRIDGE
                        && layer != RoadSolidLayer.TUNNEL) {
                    continue;
                }
                // Keep elevated underside (bridge shell / subgrade) under the deck.
                if (elevatedPavementY != null
                        && y >= elevatedPavementY - 2
                        && (layer == RoadSolidLayer.BRIDGE || layer == RoadSolidLayer.SUBGRADE)) {
                    continue;
                }
                result.recordPlacementOverride(pos, record.previousBlockId, "minecraft:air");
                result.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
                result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.CLEARANCE);
            }
        });
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
        return ys.higher(surfaceY);
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
