package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Portal station index for capping cut/clearance above designed tunnel roof.
 */
final class PortalTransitionGuard {
    private final Map<Integer, Integer> designYByWorldX;

    private PortalTransitionGuard(Map<Integer, Integer> designYByWorldX) {
        this.designYByWorldX = designYByWorldX;
    }

    static PortalTransitionGuard from(RoadCorridorPlan plan) {
        Map<Integer, Integer> designYByWorldX = new LinkedHashMap<>();
        if (plan != null) {
            for (var entry : plan.clearanceEntries()) {
                ClearanceCell cell = entry.getValue();
                if (cell.constructionType() != RoadConstructionType.TUNNEL_PORTAL) {
                    continue;
                }
                int worldX = entry.getKey().worldX();
                designYByWorldX.merge(worldX, cell.designSurfaceY(), Math::max);
            }
        }
        return new PortalTransitionGuard(designYByWorldX);
    }

    Integer designSurfaceYNear(int worldX) {
        Integer direct = designYByWorldX.get(worldX);
        if (direct != null) {
            return direct;
        }
        int band = RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS;
        Integer nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (var entry : designYByWorldX.entrySet()) {
            int distance = Math.abs(entry.getKey() - worldX);
            if (distance > band || distance >= nearestDistance) {
                continue;
            }
            nearestDistance = distance;
            nearest = entry.getValue();
        }
        return nearest;
    }

    int capClearTopY(
            Vec2d planPoint,
            int requestedTopY,
            RoadCorridorPlanBuilder.PlanHost host,
            int roadHalfWidth) {
        if (planPoint == null || host == null || designYByWorldX.isEmpty()) {
            return requestedTopY;
        }
        Integer designY = designSurfaceYNear(host.worldX(planPoint));
        if (designY == null) {
            return requestedTopY;
        }
        int lateral = (int) Math.round(planPoint.y);
        int band = Math.max(
            RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS,
            roadHalfWidth + RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS);
        if (Math.abs(lateral) > band) {
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
        if (planPoint == null || host == null || designYByWorldX.isEmpty()) {
            return groundY;
        }
        Integer designY = designSurfaceYNear(host.worldX(planPoint));
        if (designY == null) {
            return groundY;
        }
        int lateral = (int) Math.round(planPoint.y);
        int band = Math.max(
            RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS,
            roadHalfWidth + RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS);
        if (Math.abs(lateral) > band) {
            return groundY;
        }
        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(host, designY);
        return Math.min(groundY, roofTop);
    }

    /**
     * Portal approach batter must not stack fill or cut faces above the designed tunnel roof.
     */
    int capSlopeSurfaceY(
            Vec2d planPoint,
            int requestedSurfaceY,
            RoadCorridorPlanBuilder.PlanHost host,
            int roadHalfWidth) {
        if (planPoint == null || host == null || designYByWorldX.isEmpty()) {
            return requestedSurfaceY;
        }
        Integer designY = designSurfaceYNear(host.worldX(planPoint));
        if (designY == null) {
            return requestedSurfaceY;
        }
        int lateral = (int) Math.round(planPoint.y);
        int band = Math.max(
            RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS,
            roadHalfWidth + RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS);
        if (Math.abs(lateral) > band) {
            return requestedSurfaceY;
        }
        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(host, designY);
        return Math.min(requestedSurfaceY, roofTop);
    }
}
