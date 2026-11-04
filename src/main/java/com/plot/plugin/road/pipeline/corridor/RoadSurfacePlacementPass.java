package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;

/** The rasterized pavement footprint owns its surface, including diagonal tunnel mouths. */
public final class RoadSurfacePlacementPass {
    private RoadSurfacePlacementPass() { }

    public static void apply(RoadGenerationResult result, RoadCorridorPlan plan, int clearHeight) {
        apply(result, plan, clearHeight, GradeSeparationBuildContext.NONE);
    }

    public static void apply(
            RoadGenerationResult result,
            RoadCorridorPlan plan,
            int clearHeight,
            GradeSeparationBuildContext gradeSeparation) {
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
            // Missing records: do not fabricate undo baselines (integrity validator reports them).
            // Still recorded openRoadClearanceTops above so grade-sep air can clear intruding fill.
            if (existing == null) {
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
    /**
     * Other approaches can add batter/bridge fill after the original (empty) terrain was inspected.
     * Clear SUBGRADE and BRIDGE intrusions in reserved open-road headroom (not ROAD/SHOULDER surfaces).
     * <p>
     * When the same column already has an elevated pavement (ROAD/BRIDGE/…) above the open surface,
     * stop below that deck and never strip BRIDGE structure that supports it — otherwise grade-sep
     * underpass headroom clearing eats the upper road underside after network merge.
     */
    public static void clearIntrudingFill(RoadGenerationResult result) {
        result.openRoadClearanceTops.forEach((surface, top) -> {
            Integer elevatedPavementY = lowestProtectedPavementAbove(
                result, surface.getX(), surface.getY(), surface.getZ());
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

    private static Integer lowestProtectedPavementAbove(
            RoadGenerationResult result, int worldX, int surfaceY, int worldZ) {
        Integer lowest = null;
        for (var entry : result.placementLayers.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() != worldX || pos.getZ() != worldZ || pos.getY() <= surfaceY) {
                continue;
            }
            if (!isElevatedPavementLayer(entry.getValue())) {
                continue;
            }
            var record = result.placementRecords.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            lowest = lowest == null ? pos.getY() : Math.min(lowest, pos.getY());
        }
        return lowest;
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
