package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.RoadConstructionType;
import com.plot.core.terrain.TerrainSampler;

/**
 * Gives slope-batter columns a clearance envelope whose floor is the batter face.
 * OPEN pavement punch stays on {@link com.plot.plugin.road.geometry.RoadCorridorWidth#pavementEnvelopeWidthBlocks};
 * this pass only fills columns the rays actually occupy, so urban (no batter) is unchanged.
 */
public final class SlopeEnvelopeClearancePass {
    private SlopeEnvelopeClearancePass() {
    }

    public static void apply(RoadCorridorPlan plan, TerrainSampler terrain) {
        if (plan == null || terrain == null || plan.slopeEntries().isEmpty()) {
            return;
        }
        for (var entry : plan.slopeEntries()) {
            ColumnKey key = entry.getKey();
            SlopeColumnCell slope = entry.getValue();
            if (key == null || slope == null || plan.isSlopeColumnIncomplete(key)) {
                continue;
            }
            if (plan.clearanceCell(key) != null) {
                continue;
            }
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            int floorY = slopeFloorY(slope);
            int clearTopY = terrain.sampleColumnTopY(slope.planPoint());
            if (clearTopY <= floorY) {
                continue;
            }
            plan.putClearance(key, new ClearanceCell(
                slope.planPoint(),
                floorY,
                clearTopY,
                RoadCorridorConstructionMode.OPEN,
                RoadConstructionType.ROAD));
        }
    }

    static int slopeFloorY(SlopeColumnCell slope) {
        if (slope.hasCut() && slope.hasFill()) {
            return Math.min(slope.cutSurfaceY(), slope.fillSurfaceY());
        }
        if (slope.hasCut()) {
            return slope.cutSurfaceY();
        }
        return slope.fillSurfaceY();
    }
}
