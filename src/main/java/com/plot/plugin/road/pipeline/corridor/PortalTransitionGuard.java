package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * Portal station index for capping cut/clearance/slope above the designed tunnel roof.
 * <p>
 * Indexed by world XZ columns (not world-X only, and not {@code planPoint.y} as "lateral"),
 * so portals on N–S / offset / diagonal roads still receive roof caps.
 */
final class PortalTransitionGuard {
    private final Map<ColumnKey, Integer> designYByColumn;
    private final List<PortalBackfillSite> sites;

    private PortalTransitionGuard(Map<ColumnKey, Integer> designYByColumn, List<PortalBackfillSite> sites) {
        this.designYByColumn = designYByColumn;
        this.sites = sites;
    }

    static PortalTransitionGuard from(RoadCorridorPlan plan) {
        Map<ColumnKey, Integer> designYByColumn = new LinkedHashMap<>();
        if (plan != null) {
            for (var entry : plan.clearanceEntries()) {
                ClearanceCell cell = entry.getValue();
                if (cell.constructionType() != RoadConstructionType.TUNNEL_PORTAL) {
                    continue;
                }
                designYByColumn.merge(entry.getKey(), cell.designSurfaceY(), Math::max);
            }
        }
        return new PortalTransitionGuard(designYByColumn, plan != null ? plan.portalBackfillSites() : List.of());
    }

    Integer designSurfaceYNear(int worldX, int worldZ, int searchBand) {
        if (designYByColumn.isEmpty() || searchBand < 0) {
            return null;
        }
        ColumnKey direct = new ColumnKey(worldX, worldZ);
        Integer atColumn = designYByColumn.get(direct);
        if (atColumn != null) {
            return atColumn;
        }
        Integer nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (var entry : designYByColumn.entrySet()) {
            ColumnKey key = entry.getKey();
            int distance = Math.max(
                Math.abs(key.worldX() - worldX),
                Math.abs(key.worldZ() - worldZ));
            if (distance > searchBand || distance >= nearestDistance) {
                continue;
            }
            nearestDistance = distance;
            nearest = entry.getValue();
        }
        return nearest;
    }

    private Integer designSurfaceYNear(
            Vec2d planPoint,
            RoadCorridorPlanBuilder.PlanHost host,
            int roadHalfWidth) {
        if (planPoint == null || host == null || designYByColumn.isEmpty()) {
            return null;
        }
        int band = Math.max(
            RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS,
            Math.abs(roadHalfWidth) + RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS);
        return designSurfaceYNear(host.worldX(planPoint), host.worldZ(planPoint), band);
    }

    int capClearTopY(
            Vec2d planPoint,
            int requestedTopY,
            RoadCorridorPlanBuilder.PlanHost host,
            int roadHalfWidth) {
        if (!coversRoofColumn(planPoint, host)) return requestedTopY;
        Integer designY = designSurfaceYNear(planPoint, host, roadHalfWidth);
        if (designY == null) {
            return requestedTopY;
        }
        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(host, designY);
        return Math.min(requestedTopY, roofTop);
    }

    int capCutTopY(
            Vec2d planPoint,
            int groundY,
            RoadCorridorPlanBuilder.PlanHost host,
            int roadHalfWidth) {
        if (!coversRoofColumn(planPoint, host)) return groundY;
        Integer designY = designSurfaceYNear(planPoint, host, roadHalfWidth);
        if (designY == null) {
            return groundY;
        }
        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(host, designY);
        return Math.min(groundY, roofTop);
    }

    /** Outside the actual roof footprint, excavate to terrain top instead of leaving a lid. */
    private boolean coversRoofColumn(Vec2d point, RoadCorridorPlanBuilder.PlanHost host) {
        if (sites.isEmpty() || point == null || host == null) return true;
        var style = host.tunnelStyle();
        for (var site : sites) {
            double scale = site.unitsPerBlock() > 1e-9 ? site.unitsPerBlock() : 1.0;
            var delta = point.subtract(site.center());
            var normal = site.leftNormal().normalize();
            var forward = site.forward().normalize();
            double along = (delta.x * forward.x + delta.y * forward.y) / scale;
            if (Math.abs(along) > RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS + 0.5) continue;
            double lateral = (delta.x * normal.x + delta.y * normal.y) / scale;
            int lining = Math.min(com.plot.plugin.road.tunnel.TunnelStyle.MAX_LINING_THICKNESS,
                style.liningThickness() + 1);
            int width = site.envelopeWidthBlocks() + style.sideClearance() * 2 + lining * 2;
            if (lateral >= com.plot.plugin.road.RoadDimensionUtils.minLateralOffset(width) - 0.5
                    && lateral <= com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(width) + 0.5) return true;
        }
        return false;
    }

    /**
     * Portal approach batter must not stack fill or cut faces at/above the designed tunnel roof.
     * Caps to {@code roofTop - 1} so slope material cannot sit flush with the roof crown.
     */
    int capSlopeSurfaceY(
            Vec2d planPoint,
            int requestedSurfaceY,
            RoadCorridorPlanBuilder.PlanHost host,
            int roadHalfWidth) {
        Integer designY = designSurfaceYNear(planPoint, host, roadHalfWidth);
        if (designY == null) {
            return requestedSurfaceY;
        }
        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(host, designY);
        return Math.min(requestedSurfaceY, roofTop - 1);
    }
}
