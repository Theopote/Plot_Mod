package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
import com.plot.plugin.road.tunnel.TunnelRoofGapSealer;
import com.plot.core.terrain.TerrainSampler;

/**
 * Restores terrain behind portal lining when open clearance over-excavated preserve cells.
 * Uses real portal station geometry ({@link PortalBackfillSite}), not canvas-X buckets.
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
        var tunnelStyle = host.tunnelStyle();
        String liningMaterialId = host.resolveBlockId(tunnelStyle.liningMaterial());
        int transitionBand = RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS;
        for (PortalBackfillSite site : plan.portalBackfillSites()) {
            sealPortalSite(solids, site, terrain, host, tunnelStyle, liningMaterialId, transitionBand);
        }
    }

    private static void sealPortalSite(
            com.plot.plugin.road.solid.RoadSolidModel solids,
            PortalBackfillSite site,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host,
            com.plot.plugin.road.tunnel.ResolvedTunnelStyle tunnelStyle,
            String liningMaterialId,
            int transitionBand) {
        int envelopeWidth = Math.max(1, site.envelopeWidthBlocks());
        double scale = site.unitsPerBlock() > 1e-9 ? site.unitsPerBlock() : 1.0;
        Vec2d leftNormal = site.leftNormal().lengthSquared() > 1e-12
            ? site.leftNormal().normalize()
            : new Vec2d(0, 1);
        Vec2d forward = site.forward().lengthSquared() > 1e-12
            ? site.forward().normalize()
            : new Vec2d(leftNormal.y, -leftNormal.x);
        int designY = site.designSurfaceY();
        TunnelPortalExcavationMask portalMask = TunnelPortalExcavationMask.forPortal(
            tunnelStyle, envelopeWidth, designY);
        TunnelCrossSectionMask section = portalMask.crossSection();
        int lining = Math.min(
            com.plot.plugin.road.tunnel.TunnelStyle.MAX_LINING_THICKNESS,
            tunnelStyle.liningThickness() + 1);
        RoadTerrainClearanceColumnResolver columnResolver = new RoadTerrainClearanceColumnResolver(host);

        for (int step = -transitionBand; step <= transitionBand; step++) {
            Vec2d center = site.center().add(forward.multiply(step * scale));
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
                leftNormal,
                scale,
                columnResolver,
                terrain,
                liningMaterialId,
                "minecraft:stone");
        }
    }

    private static final class RoadTerrainClearanceColumnResolver
            implements com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver {
        private final RoadCorridorPlanBuilder.PlanHost host;

        private RoadTerrainClearanceColumnResolver(RoadCorridorPlanBuilder.PlanHost host) {
            this.host = host;
        }

        @Override
        public int worldX(Vec2d planPoint) {
            return host.worldX(planPoint);
        }

        @Override
        public int worldZ(Vec2d planPoint) {
            return host.worldZ(planPoint);
        }
    }
}
