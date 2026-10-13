package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;

/**
 * 基于 {@link RoadCorridorPlan} 的清障 envelope 统一移除设计面以上的妨碍体。
 * 不依赖 {@link com.plot.plugin.road.RoadConstructionType} CUT/FILL 类型。
 */
public final class RoadCorridorClearancePass {
    private RoadCorridorClearancePass() {
    }

    public static void apply(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
        apply(solids, plan, terrain, host, null);
    }

    public static void apply(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host,
            RoadEdgeBuildMetrics metrics) {
        if (solids == null || plan == null || terrain == null) {
            return;
        }
        for (var entry : plan.clearanceEntries()) {
            ColumnKey key = entry.getKey();
            ClearanceCell cell = entry.getValue();
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            int cleared = clearColumn(solids, cell, key, terrain);
            if (metrics != null) {
                metrics.cutVolume += cleared;
            }
        }
    }

    private static int clearColumn(
            RoadSolidModel solids,
            ClearanceCell cell,
            ColumnKey key,
            TerrainSampler terrain) {
        // Clear overhead only (designY+1..top). Design-Y pavement is owned by SurfaceCell emit;
        // TUNNEL-layer AIR outranks ROAD and must not be written at designY.
        // BRIDGE columns still clear overhead (no longer a no-op).
        int cleared = 0;
        int designY = cell.designSurfaceY();
        int topY = Math.max(cell.clearTopY(), designY);
        for (int y = designY + 1; y <= topY; y++) {
            if (!hasObstruction(terrain, key.worldX(), y, key.worldZ())) {
                continue;
            }
            solids.add(
                cell.planPoint(),
                y,
                RoadSolidLayer.TUNNEL,
                "minecraft:air",
                RoadSolidMaterialPriority.AIR);
            cleared++;
        }
        return cleared;
    }

    static boolean hasObstruction(TerrainSampler terrain, int worldX, int y, int worldZ) {
        return terrain.isConstructionVolumeObstruction(worldX, y, worldZ)
            || terrain.isSolidBlock(worldX, y, worldZ)
            || terrain.isRoadClearableDecoration(worldX, y, worldZ);
    }
}
