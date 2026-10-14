package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
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
        PortalTransitionGuard portalGuard = PortalTransitionGuard.from(plan);
        for (var entry : plan.clearanceEntries()) {
            ColumnKey key = entry.getKey();
            ClearanceCell cell = entry.getValue();
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            int cleared = clearColumn(solids, cell, key, terrain, host, portalGuard);
            if (metrics != null) {
                metrics.cutVolume += cleared;
            }
        }
    }

    private static int clearColumn(
            RoadSolidModel solids,
            ClearanceCell cell,
            ColumnKey key,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host,
            PortalTransitionGuard portalGuard) {
        int cleared = 0;
        if (cell.mode() == RoadCorridorConstructionMode.TUNNEL) {
            return cleared;
        }
        int designY = cell.designSurfaceY();
        int topY = Math.max(cell.clearTopY(), designY);
        if (host != null && portalGuard != null) {
            ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
            int roadHalfWidth = Math.abs(
                com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(
                    RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection)));
            topY = portalGuard.capClearTopY(cell.planPoint(), topY, host, roadHalfWidth);
        }
        if (cell.mode() == RoadCorridorConstructionMode.PORTAL && host != null) {
            ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
            int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
            TunnelPortalExcavationMask mask = TunnelPortalExcavationMask.forPortal(
                host.tunnelStyle(), envelopeWidth, designY);
            topY = Math.min(topY, mask.roofTopY());
            for (int y = designY + 1; y <= topY; y++) {
                if (cell.corridorLateral() != null && !mask.allowsClearance(cell.corridorLateral(), y)) {
                    continue;
                }
                cleared += clearObstruction(solids, cell, key, terrain, y);
            }
            return cleared;
        }
        for (int y = designY + 1; y <= topY; y++) {
            cleared += clearObstruction(solids, cell, key, terrain, y);
        }
        return cleared;
    }

    private static int clearObstruction(
            RoadSolidModel solids,
            ClearanceCell cell,
            ColumnKey key,
            TerrainSampler terrain,
            int y) {
        if (!hasObstruction(terrain, key.worldX(), y, key.worldZ())) {
            return 0;
        }
        solids.add(
            cell.planPoint(),
            y,
            RoadSolidLayer.TUNNEL,
            "minecraft:air",
            RoadSolidMaterialPriority.AIR);
        return 1;
    }

    static boolean hasObstruction(TerrainSampler terrain, int worldX, int y, int worldZ) {
        return terrain.isSolidBlock(worldX, y, worldZ)
            || terrain.isRoadClearableDecoration(worldX, y, worldZ)
            || terrain.isConstructionVolumeObstruction(worldX, y, worldZ);
    }
}
