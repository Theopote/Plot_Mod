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
     */
    public static void clearIntrudingFill(RoadGenerationResult result) {
        result.openRoadClearanceTops.forEach((surface, top) -> {
            for (int y = surface.getY() + 1; y <= top; y++) {
                var pos = new BlockPos(surface.getX(), y, surface.getZ());
                var record = result.placementRecords.get(pos);
                if (record == null || "minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                var layer = result.placementLayers.get(pos);
                if (layer != RoadSolidLayer.SUBGRADE && layer != RoadSolidLayer.BRIDGE) {
                    continue;
                }
                result.recordPlacementOverride(pos, record.previousBlockId, "minecraft:air");
                result.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
                result.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.CLEARANCE);
            }
        });
    }

}
