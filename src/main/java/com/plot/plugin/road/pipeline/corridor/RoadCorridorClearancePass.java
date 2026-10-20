package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
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
        apply(solids, plan, terrain, host, metrics, GradeSeparationBuildContext.NONE);
    }

    public static void apply(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host,
            RoadEdgeBuildMetrics metrics,
            GradeSeparationBuildContext gradeSeparation) {
        if (solids == null || plan == null || terrain == null) {
            return;
        }
        if (gradeSeparation == null) {
            gradeSeparation = GradeSeparationBuildContext.NONE;
        }
        PortalTransitionGuard portalGuard = PortalTransitionGuard.from(plan);
        for (var entry : plan.clearanceEntries()) {
            ColumnKey key = entry.getKey();
            ClearanceCell cell = entry.getValue();
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            int cleared = clearColumn(solids, cell, key, terrain, host, portalGuard, gradeSeparation);
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
            PortalTransitionGuard portalGuard,
            GradeSeparationBuildContext gradeSeparation) {
        int cleared = 0;
        int designY = cell.designSurfaceY();
        int topY = Math.max(cell.clearTopY(), designY);
        if (cell.mode() == RoadCorridorConstructionMode.TUNNEL && host != null) {
            return clearTunnelInterior(solids, cell, key, terrain, host, designY, topY);
        }
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
            Integer lateral = cell.corridorLateral();
            for (int y = designY + 1; y <= topY; y++) {
                if (lateral != null && !mask.allowsClearance(lateral, y)) {
                    continue;
                }
                if (lateral == null && !portalAllowsClearanceAtY(mask, y)) {
                    continue;
                }
                cleared += clearObstruction(solids, cell, key, terrain, y, gradeSeparation);
            }
            return cleared;
        }
        for (int y = designY + 1; y <= topY; y++) {
            cleared += clearObstruction(solids, cell, key, terrain, y, gradeSeparation);
        }
        return cleared;
    }

    private static int clearTunnelInterior(
            RoadSolidModel solids,
            ClearanceCell cell,
            ColumnKey key,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host,
            int designY,
            int topY) {
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        var tunnelStyle = host.tunnelStyle();
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            tunnelStyle.shape(),
            envelopeWidth,
            tunnelStyle.sideClearance(),
            tunnelStyle.clearHeight(),
            tunnelStyle.liningThickness(),
            designY,
            false);
        int roofTop = mask.maxY();
        topY = Math.min(topY, roofTop);
        int cleared = 0;
        Integer lateral = cell.corridorLateral();
        for (int y = designY + 1; y <= topY; y++) {
            if (lateral != null && mask.kindAt(lateral, y) != TunnelCrossSectionMask.CellKind.AIR) {
                continue;
            }
            cleared += clearObstruction(solids, cell, key, terrain, y, GradeSeparationBuildContext.NONE);
        }
        return cleared;
    }

    private static int clearObstruction(
            RoadSolidModel solids,
            ClearanceCell cell,
            ColumnKey key,
            TerrainSampler terrain,
            int y,
            GradeSeparationBuildContext gradeSeparation) {
        if (gradeSeparation != null && gradeSeparation.protectsClearance(cell.planPoint(), y)) {
            return 0;
        }
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

    private static boolean portalAllowsClearanceAtY(TunnelPortalExcavationMask mask, int y) {
        if (mask == null) {
            return true;
        }
        TunnelCrossSectionMask section = mask.crossSection();
        if (section == null) {
            return true;
        }
        boolean[] allowed = {false};
        section.forEach((lateral, cellY, kind) -> {
            if (cellY == y && mask.allowsClearance(lateral, cellY)) {
                allowed[0] = true;
            }
        });
        return allowed[0];
    }
}
