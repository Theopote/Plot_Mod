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
    private final Map<Integer, Integer> designYByCanvasX;

    private PortalTransitionGuard(Map<Integer, Integer> designYByCanvasX) {
        this.designYByCanvasX = designYByCanvasX;
    }

    static PortalTransitionGuard from(RoadCorridorPlan plan) {
        Map<Integer, Integer> designYByCanvasX = new LinkedHashMap<>();
        if (plan != null) {
            for (var entry : plan.clearanceEntries()) {
                ClearanceCell cell = entry.getValue();
                if (cell.constructionType() != RoadConstructionType.TUNNEL_PORTAL) {
                    continue;
                }
                int canvasX = (int) Math.round(cell.planPoint().x);
                designYByCanvasX.merge(canvasX, cell.designSurfaceY(), Math::max);
            }
        }
        return new PortalTransitionGuard(designYByCanvasX);
    }

    Integer designSurfaceYNear(int canvasX) {
        Integer direct = designYByCanvasX.get(canvasX);
        if (direct != null) {
            return direct;
        }
        int band = RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS;
        Integer nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (var entry : designYByCanvasX.entrySet()) {
            int distance = Math.abs(entry.getKey() - canvasX);
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
        if (planPoint == null || host == null || designYByCanvasX.isEmpty()) {
            return requestedTopY;
        }
        int canvasX = (int) Math.round(planPoint.x);
        Integer designY = designSurfaceYNear(canvasX);
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
        if (planPoint == null || host == null || designYByCanvasX.isEmpty()) {
            return groundY;
        }
        int canvasX = (int) Math.round(planPoint.x);
        Integer designY = designSurfaceYNear(canvasX);
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
}
