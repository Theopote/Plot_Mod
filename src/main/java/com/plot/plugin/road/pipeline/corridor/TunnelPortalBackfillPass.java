package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
import com.plot.plugin.road.tunnel.TunnelRoofGapSealer;
import com.plot.core.terrain.TerrainSampler;

/**
 * Restores terrain behind portal lining when open clearance over-excavated preserve cells.
 * Seals only the hillside / tunnel interior, never the open approach in front of the mouth.
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
            sealPortalSite(solids, plan, site, terrain, host, tunnelStyle, liningMaterialId, transitionBand);
        }
    }

    private static void sealPortalSite(
            com.plot.plugin.road.solid.RoadSolidModel solids,
            RoadCorridorPlan plan,
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
        int intoHill = resolveIntoHillSign(plan, host, site.center(), forward, transitionBand, scale);

        for (int step = -transitionBand; step <= transitionBand; step++) {
            if (intoHill != 0 && Integer.signum(step) == -intoHill) {
                continue;
            }
            Vec2d center = site.center().add(forward.multiply(step * scale));
            if (!terrain.isChunkLoaded(host.worldX(center), host.worldZ(center))) {
                continue;
            }
            if (isOpenApproachColumn(plan, host, center)) {
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

    /** +1 along {@code forward} is hillside / tunnel; -1 is the opposite hemisphere. */
    static int resolveIntoHillSign(
            RoadCorridorPlan plan,
            RoadCorridorPlanBuilder.PlanHost host,
            Vec2d portalCenter,
            Vec2d forward,
            int transitionBand,
            double scale) {
        int tunnelPlus = 0;
        int tunnelMinus = 0;
        int openPlus = 0;
        int openMinus = 0;
        for (int step = 1; step <= transitionBand; step++) {
            RoadCorridorConstructionMode plus = columnMode(
                plan, host, portalCenter.add(forward.multiply(step * scale)));
            RoadCorridorConstructionMode minus = columnMode(
                plan, host, portalCenter.add(forward.multiply(-step * scale)));
            if (plus == RoadCorridorConstructionMode.TUNNEL) {
                tunnelPlus++;
            } else if (plus == RoadCorridorConstructionMode.OPEN || plus == RoadCorridorConstructionMode.BRIDGE) {
                openPlus++;
            }
            if (minus == RoadCorridorConstructionMode.TUNNEL) {
                tunnelMinus++;
            } else if (minus == RoadCorridorConstructionMode.OPEN || minus == RoadCorridorConstructionMode.BRIDGE) {
                openMinus++;
            }
        }
        if (tunnelPlus > tunnelMinus) {
            return 1;
        }
        if (tunnelMinus > tunnelPlus) {
            return -1;
        }
        if (openPlus > openMinus) {
            return -1;
        }
        if (openMinus > openPlus) {
            return 1;
        }
        return 0;
    }

    static boolean isOpenApproachColumn(
            RoadCorridorPlan plan,
            RoadCorridorPlanBuilder.PlanHost host,
            Vec2d center) {
        RoadCorridorConstructionMode mode = columnMode(plan, host, center);
        return mode == RoadCorridorConstructionMode.OPEN || mode == RoadCorridorConstructionMode.BRIDGE;
    }

    private static RoadCorridorConstructionMode columnMode(
            RoadCorridorPlan plan,
            RoadCorridorPlanBuilder.PlanHost host,
            Vec2d center) {
        if (plan == null || host == null || center == null) {
            return null;
        }
        ClearanceCell cell = plan.clearanceCell(new ColumnKey(host.worldX(center), host.worldZ(center)));
        return cell != null ? cell.mode() : null;
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
