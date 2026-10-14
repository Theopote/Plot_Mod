package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
import com.plot.core.terrain.TerrainSampler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Restores terrain behind portal lining when open clearance over-excavated preserve cells.
 */
public final class TunnelPortalBackfillPass {
    private TunnelPortalBackfillPass() {
    }

    public static void apply(
            RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
        if (solids == null || plan == null || terrain == null || host == null) {
            return;
        }
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        Map<Integer, Integer> portalDesignYByCanvasX = new LinkedHashMap<>();
        for (var entry : plan.clearanceEntries()) {
            ClearanceCell cell = entry.getValue();
            if (cell.constructionType() != RoadConstructionType.TUNNEL_PORTAL) {
                continue;
            }
            int canvasX = (int) Math.round(cell.planPoint().x);
            portalDesignYByCanvasX.putIfAbsent(canvasX, cell.designSurfaceY());
        }
        int transitionBand = RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS;
        for (var portalEntry : portalDesignYByCanvasX.entrySet()) {
            int portalCanvasX = portalEntry.getKey();
            int designY = portalEntry.getValue();
            TunnelPortalExcavationMask mask = TunnelPortalExcavationMask.forPortal(
                host.tunnelStyle(), envelopeWidth, designY);
            int minLateral = mask.crossSection().roadMin() - transitionBand;
            int maxLateral = mask.crossSection().roadMax() + transitionBand;
            for (int canvasX = portalCanvasX - transitionBand; canvasX <= portalCanvasX + transitionBand; canvasX++) {
                Vec2d columnOrigin = new Vec2d(canvasX, 0);
                if (!terrain.isChunkLoaded(host.worldX(columnOrigin), host.worldZ(columnOrigin))) {
                    continue;
                }
            for (int lateral = minLateral; lateral <= maxLateral; lateral++) {
                Vec2d planPoint = new Vec2d(canvasX, lateral);
                int worldX = host.worldX(planPoint);
                int worldZ = host.worldZ(planPoint);
                if (!terrain.isChunkLoaded(worldX, worldZ)) {
                    continue;
                }
                int columnTop = terrain.sampleColumnTopY(planPoint);
                for (int y = designY + 1; y <= columnTop; y++) {
                    boolean aboveRoof = y > mask.roofTopY();
                    if (!aboveRoof && !mask.shouldPreserve(lateral, y)) {
                        continue;
                    }
                    if (!terrain.isSolidBlock(worldX, y, worldZ)) {
                        continue;
                    }
                    solids.add(
                        planPoint,
                        y,
                        RoadSolidLayer.TUNNEL,
                        "minecraft:stone",
                        RoadSolidMaterialPriority.STRUCTURAL);
                }
            }
            }
        }
    }
}
