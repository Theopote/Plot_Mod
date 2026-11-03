package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes corridor plan columns that fall inside junction fill polygons.
 * <p>
 * When {@link JunctionCorridorBuildContext#preserveClearanceInExclusion()} is set (grade-sep
 * lower road), only surface and slope are removed so open clearance still punches underpass air.
 */
public final class JunctionCorridorPlanFilter {
    private JunctionCorridorPlanFilter() {
    }

    public static void apply(RoadCorridorPlan plan, JunctionCorridorBuildContext context) {
        if (plan == null || context == null || context.exclusionPolygons().isEmpty()) {
            return;
        }
        boolean preserveClearance = context.preserveClearanceInExclusion();
        Set<ColumnKey> surfaceOrSlope = new HashSet<>();
        Set<ColumnKey> clearanceOnly = new HashSet<>();
        for (var entry : plan.surfaceMask().entries()) {
            Vec2d planPoint = entry.getValue().planPoint();
            if (planPoint != null && insideAny(planPoint, context.exclusionPolygons())) {
                surfaceOrSlope.add(entry.getKey());
            }
        }
        for (var entry : plan.slopeEntries()) {
            Vec2d planPoint = entry.getValue().planPoint();
            if (planPoint != null && insideAny(planPoint, context.exclusionPolygons())) {
                surfaceOrSlope.add(entry.getKey());
            }
        }
        for (var entry : plan.clearanceEntries()) {
            if (surfaceOrSlope.contains(entry.getKey())) {
                continue;
            }
            Vec2d planPoint = entry.getValue().planPoint();
            if (planPoint != null && insideAny(planPoint, context.exclusionPolygons())) {
                clearanceOnly.add(entry.getKey());
            }
        }
        for (ColumnKey key : surfaceOrSlope) {
            if (preserveClearance) {
                plan.surfaceMask().remove(key);
                plan.removeSlopeCell(key);
            } else {
                plan.removeColumn(key);
            }
        }
        if (!preserveClearance) {
            for (ColumnKey key : clearanceOnly) {
                plan.removeColumn(key);
            }
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
