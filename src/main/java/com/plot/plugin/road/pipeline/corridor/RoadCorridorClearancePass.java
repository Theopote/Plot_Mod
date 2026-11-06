package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
import com.plot.plugin.road.tunnel.TunnelRoofGapSealer;
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
            int cleared = clearColumn(solids, plan, cell, key, terrain, host, portalGuard, gradeSeparation);
            if (metrics != null) {
                metrics.cutVolume += cleared;
            }
        }
        // Acute / gore corners often leave one-column wedges outside the stamped corridor.
        // Dilate OPEN/BRIDGE decoration clearance by 1 so canopy cannot remain over the road.
        int dilated = clearAdjacentOpenDecorations(solids, plan, terrain, host, gradeSeparation);
        if (metrics != null) {
            metrics.cutVolume += dilated;
        }
    }

    private static int clearColumn(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            ClearanceCell cell,
            ColumnKey key,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host,
            PortalTransitionGuard portalGuard,
            GradeSeparationBuildContext gradeSeparation) {
        int cleared = 0;
        // Final pavement mask is the sole clearance floor. expandClearanceEnvelope can leave
        // clearance.designY above the stamped surface at 1-block grade steps; Math.max would
        // keep that high floor and leave dirt coplanar with the upper pavement.
        int designY = cell.designSurfaceY();
        SurfaceCell surface = plan != null ? plan.surfaceMask().get(key) : null;
        if (surface != null) {
            designY = surface.surfaceY();
        }
        int topY = Math.max(cell.clearTopY(), designY);
        if (cell.mode() == RoadCorridorConstructionMode.TUNNEL && host != null) {
            return clearTunnelInterior(
                solids, cell, key, terrain, host, designY, topY, gradeSeparation,
                ClearancePurpose.TUNNEL_INTERIOR);
        }
        if (host != null && portalGuard != null) {
            ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
            int roadHalfWidth = Math.abs(
                com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(
                    RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection)));
            topY = portalGuard.capClearTopY(cell.planPoint(), topY, host, roadHalfWidth);
        }
        // Lower road under a grade-sep deck: clear full underpass headroom up to the
        // elevated underside. Do not keep the cave/local clearHeight shrink here —
        // leftover terrain under the deck is exactly what users see at the crossing.
        Integer deckCap = gradeSeparation != null ? gradeSeparation.maxOpenClearTopY(cell.planPoint()) : null;
        if (deckCap != null) {
            topY = Math.max(designY, deckCap);
        }
        if (cell.mode() == RoadCorridorConstructionMode.PORTAL && host != null) {
            int envelopeWidth = resolveEnvelopeWidth(cell, host);
            TunnelPortalExcavationMask mask = TunnelPortalExcavationMask.forPortal(
                host.tunnelStyle(), envelopeWidth, designY);
            topY = Math.min(topY, mask.roofTopY());
            Integer lateral = cell.corridorLateral();
            // Portal envelope columns may be cleared here before structure/backfill
            // stamps a roof — stabilize gravel/sand above the portal roof first.
            int portalRoof = mask.roofTopY();
            if (lateral != null) {
                int columnRoof = mask.crossSection().outerTopY(lateral);
                if (columnRoof != Integer.MIN_VALUE) {
                    portalRoof = columnRoof;
                }
            }
            TunnelRoofGapSealer.stabilizeGravityColumn(
                solids,
                cell.planPoint(),
                key.worldX(),
                key.worldZ(),
                portalRoof,
                terrain,
                "minecraft:stone");
            for (int y = designY + 1; y <= topY; y++) {
                if (lateral != null && !mask.allowsClearance(lateral, y)) {
                    continue;
                }
                if (lateral == null && !portalAllowsClearanceAtY(mask, y)) {
                    continue;
                }
                cleared += clearObstruction(
                    solids, cell, key, terrain, y, gradeSeparation, ClearancePurpose.GRADE_SEPARATION);
            }
            return cleared;
        }
        SlopeColumnCell slope = plan != null ? plan.slopeCell(key) : null;
        int groundY = terrain.sampleSurfaceY(cell.planPoint());
        for (int y = designY + 1; y <= topY; y++) {
            if (retainingFinishOccupies(slope, groundY, y)) {
                continue;
            }
            ClearancePurpose purpose = resolveOpenClearancePurpose(cell, slope, deckCap, y);
            cleared += clearObstruction(solids, cell, key, terrain, y, gradeSeparation, purpose);
        }
        // OPEN/BRIDGE: strip trees and other clearable decoration above the solid clear
        // envelope (cave/local top) without excavating mountain rock beyond topY.
        if (cell.mode() == RoadCorridorConstructionMode.OPEN
                || cell.mode() == RoadCorridorConstructionMode.BRIDGE) {
            cleared += clearDecorationsAbove(
                solids, cell, key, terrain, designY, topY, deckCap, gradeSeparation, slope, groundY);
        }
        return cleared;
    }

    private static ClearancePurpose resolveOpenClearancePurpose(
            ClearanceCell cell,
            SlopeColumnCell slope,
            Integer deckCap,
            int y) {
        if (deckCap != null) {
            return ClearancePurpose.GRADE_SEPARATION;
        }
        if (slope != null && slope.hasCut() && y > slope.cutSurfaceY()) {
            return ClearancePurpose.SLOPE_EXCAVATION;
        }
        if (cell != null && cell.mode() == RoadCorridorConstructionMode.BRIDGE) {
            return ClearancePurpose.GRADE_SEPARATION;
        }
        return ClearancePurpose.ROAD_HEADROOM;
    }

    /**
     * Removes floating canopy / sparse decoration above the solid clearance top.
     * Does not clear engineering rock — only {@link TerrainSampler#isRoadClearableDecoration}.
     */
    private static int clearDecorationsAbove(
            RoadSolidModel solids,
            ClearanceCell cell,
            ColumnKey key,
            TerrainSampler terrain,
            int designY,
            int solidTopY,
            Integer deckCap,
            GradeSeparationBuildContext gradeSeparation) {
        return clearDecorationsAbove(
            solids, cell, key, terrain, designY, solidTopY, deckCap, gradeSeparation, null, Integer.MIN_VALUE);
    }

    private static int clearDecorationsAbove(
            RoadSolidModel solids,
            ClearanceCell cell,
            ColumnKey key,
            TerrainSampler terrain,
            int designY,
            int solidTopY,
            Integer deckCap,
            GradeSeparationBuildContext gradeSeparation,
            SlopeColumnCell slope,
            int groundY) {
        int decoTop = terrain.sampleColumnTopY(cell.planPoint());
        if (deckCap != null) {
            decoTop = Math.min(decoTop, deckCap);
        }
        if (decoTop <= solidTopY) {
            return 0;
        }
        int cleared = 0;
        for (int y = Math.max(designY + 1, solidTopY + 1); y <= decoTop; y++) {
            if (gradeSeparation != null && gradeSeparation.protectsClearance(cell.planPoint(), y)) {
                continue;
            }
            if (retainingFinishOccupies(slope, groundY, y)) {
                continue;
            }
            if (!terrain.isRoadClearableDecoration(key.worldX(), y, key.worldZ())) {
                continue;
            }
            solids.add(
                cell.planPoint(),
                y,
                RoadSolidLayer.TUNNEL,
                "minecraft:air",
                RoadSolidMaterialPriority.AIR,
                deckCap != null ? ClearancePurpose.GRADE_SEPARATION : ClearancePurpose.ROAD_HEADROOM);
            cleared++;
        }
        return cleared;
    }

    /** 宽度截断竖向挡墙占用的高度：清障 AIR 不得写入该区间。 */
    static boolean retainingFinishOccupies(SlopeColumnCell slope, int groundY, int y) {
        if (slope == null || !slope.needsRetainingFinish()) {
            return false;
        }
        if (slope.hasCut()) {
            int faceY = slope.cutSurfaceY();
            return y >= faceY && y < groundY;
        }
        if (slope.hasFill()) {
            int faceY = slope.fillSurfaceY();
            return y > groundY && y <= faceY;
        }
        return false;
    }

    private static final int[][] NEIGHBOR_OFFSETS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    /**
     * One-cell OPEN/BRIDGE decoration dilation for acute gore / sampling gaps that never
     * received a clearance stamp but still overhang the finished road.
     * Neighbor columns are world XZ keys; canvas plan points come from {@link RoadCorridorPlanBuilder.PlanHost}.
     */
    private static int clearAdjacentOpenDecorations(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host,
            GradeSeparationBuildContext gradeSeparation) {
        if (solids == null || plan == null || terrain == null || host == null) {
            return 0;
        }
        int cleared = 0;
        for (var entry : plan.clearanceEntries()) {
            ClearanceCell cell = entry.getValue();
            if (cell.mode() != RoadCorridorConstructionMode.OPEN
                    && cell.mode() != RoadCorridorConstructionMode.BRIDGE) {
                continue;
            }
            ColumnKey key = entry.getKey();
            int solidTopY = Math.max(cell.clearTopY(), cell.designSurfaceY());
            for (int[] offset : NEIGHBOR_OFFSETS) {
                ColumnKey neighbor = new ColumnKey(key.worldX() + offset[0], key.worldZ() + offset[1]);
                if (plan.clearanceCell(neighbor) != null) {
                    continue;
                }
                if (!terrain.isChunkLoaded(neighbor.worldX(), neighbor.worldZ())) {
                    continue;
                }
                // World column → canvas plan point (never treat world XZ as canvas coords).
                var neighborPoint = host.planPointForWorldColumn(neighbor.worldX(), neighbor.worldZ());
                Integer deckCap = gradeSeparation != null
                    ? gradeSeparation.maxOpenClearTopY(neighborPoint)
                    : null;
                cleared += clearDecorationsAbove(
                    solids,
                    new ClearanceCell(
                        neighborPoint,
                        cell.designSurfaceY(),
                        solidTopY,
                        cell.mode(),
                        cell.constructionType()),
                    neighbor,
                    terrain,
                    cell.designSurfaceY(),
                    solidTopY,
                    deckCap,
                    gradeSeparation);
            }
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
            int topY,
            GradeSeparationBuildContext gradeSeparation,
            ClearancePurpose purpose) {
        int envelopeWidth = resolveEnvelopeWidth(cell, host);
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
        Integer deckCap = gradeSeparation != null
            ? gradeSeparation.maxOpenClearTopY(cell.planPoint())
            : null;
        if (deckCap != null) {
            topY = Math.min(topY, Math.max(designY, deckCap));
        }
        Integer lateral = cell.corridorLateral();
        // 无横向坐标时无法把世界柱映射到掩膜格：fail-closed，避免整柱挖穿衬砌壳。
        if (lateral == null) {
            return 0;
        }
        // Stabilize gravity above this cavity column before excavating — plan columns
        // cleared here may never receive a later structure Phase B seal.
        int columnRoof = mask.outerTopY(lateral);
        if (columnRoof == Integer.MIN_VALUE) {
            columnRoof = roofTop;
        }
        TunnelRoofGapSealer.stabilizeGravityColumn(
            solids,
            cell.planPoint(),
            key.worldX(),
            key.worldZ(),
            columnRoof,
            terrain,
            "minecraft:stone");
        int cleared = 0;
        for (int y = designY + 1; y <= topY; y++) {
            if (mask.kindAt(lateral, y) != TunnelCrossSectionMask.CellKind.AIR) {
                continue;
            }
            cleared += clearObstruction(
                solids, cell, key, terrain, y, gradeSeparation, purpose);
        }
        return cleared;
    }

    private static int clearObstruction(
            RoadSolidModel solids,
            ClearanceCell cell,
            ColumnKey key,
            TerrainSampler terrain,
            int y,
            GradeSeparationBuildContext gradeSeparation,
            ClearancePurpose purpose) {
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
            RoadSolidMaterialPriority.AIR,
            purpose != null ? purpose : ClearancePurpose.ROAD_HEADROOM);
        return 1;
    }

    static boolean hasObstruction(TerrainSampler terrain, int worldX, int y, int worldZ) {
        return terrain.isConstructionVolumeObstruction(worldX, y, worldZ)
            || terrain.isSolidBlock(worldX, y, worldZ)
            || terrain.isRoadClearableDecoration(worldX, y, worldZ);
    }

    private static int resolveEnvelopeWidth(ClearanceCell cell, RoadCorridorPlanBuilder.PlanHost host) {
        if (cell.envelopeWidthBlocks() != null && cell.envelopeWidthBlocks() > 0) {
            return cell.envelopeWidthBlocks();
        }
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
        return RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
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
