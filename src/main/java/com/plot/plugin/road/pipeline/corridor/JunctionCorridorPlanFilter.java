package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes corridor plan columns that fall inside junction fill polygons.
 */
public final class JunctionCorridorPlanFilter {
    private JunctionCorridorPlanFilter() {
    }

    public static void apply(RoadCorridorPlan plan, JunctionCorridorBuildContext context) {
        if (plan == null || context == null || context.exclusionPolygons().isEmpty()) {
            return;
        }
        List<ColumnKey> toRemove = new ArrayList<>();
        for (var entry : plan.surfaceMask().entries()) {
            Vec2d planPoint = entry.getValue().planPoint();
            if (planPoint != null && insideAny(planPoint, context.exclusionPolygons())) {
                toRemove.add(entry.getKey());
            }
        }
        for (var entry : plan.clearanceEntries()) {
            if (toRemove.contains(entry.getKey())) {
                continue;
            }
            Vec2d planPoint = entry.getValue().planPoint();
            if (planPoint != null && insideAny(planPoint, context.exclusionPolygons())) {
                toRemove.add(entry.getKey());
            }
        }
        for (var entry : plan.slopeEntries()) {
            if (toRemove.contains(entry.getKey())) {
                continue;
            }
            Vec2d planPoint = entry.getValue().planPoint();
            if (planPoint != null && insideAny(planPoint, context.exclusionPolygons())) {
                toRemove.add(entry.getKey());
            }
        }
        for (ColumnKey key : toRemove) {
            plan.removeColumn(key);
        }
    }

    private static boolean insideAny(Vec2d point, List<List<Vec2d>> polygons) {
        for (List<Vec2d> polygon : polygons) {
            if (RoadGeometryUtils.pointInPolygon(point, polygon)) {
                return true;
            }
        }
        return false;
    }
}
