package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
import com.plot.plugin.road.tunnel.TunnelRoofGapSealer;
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
            com.plot.plugin.road.solid.RoadSolidModel solids,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
        if (solids == null || plan == null || terrain == null || host == null) {
            return;
        }
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        var tunnelStyle = host.tunnelStyle();
        String liningMaterialId = host.resolveBlockId(tunnelStyle.liningMaterial());
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
            TunnelPortalExcavationMask portalMask = TunnelPortalExcavationMask.forPortal(
                tunnelStyle, envelopeWidth, designY);
            TunnelCrossSectionMask section = portalMask.crossSection();
            int lining = Math.min(
                com.plot.plugin.road.tunnel.TunnelStyle.MAX_LINING_THICKNESS,
                tunnelStyle.liningThickness() + 1);
            for (int canvasX = portalCanvasX - transitionBand; canvasX <= portalCanvasX + transitionBand; canvasX++) {
                Vec2d center = new Vec2d(canvasX, 0);
                if (!terrain.isChunkLoaded(host.worldX(center), host.worldZ(center))) {
                    continue;
                }
                TunnelRoofGapSealer.sealAboveRoofShell(
                    solids,
                    section,
                    tunnelStyle.shape(),
                    designY,
                    tunnelStyle.clearHeight(),
                    envelopeWidth,
                    tunnelStyle.sideClearance(),
                    lining,
                    center,
                    new Vec2d(0, 1),
                    1.0,
                    new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
                        @Override
                        public int worldX(Vec2d planPoint) {
                            return host.worldX(planPoint);
                        }

                        @Override
                        public int worldZ(Vec2d planPoint) {
                            return host.worldZ(planPoint);
                        }
                    },
                    terrain,
                    liningMaterialId,
                    "minecraft:stone");
            }
        }
    }
}
