package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes corridor plan columns owned by the junction.
 * <p>
 * Prefers {@link JunctionCorridorBuildContext#worldFootprint()} ColumnKey membership so corridor
 * trim and junction pave/clearance share one world-column mask. Falls back to polygon PIP when
 * the footprint is empty.
 * <p>
 * When {@link JunctionCorridorBuildContext#preserveClearanceInExclusion()} is set (grade-sep
 * lower road), only surface and slope are removed so open clearance still punches underpass air.
 */
public final class JunctionCorridorPlanFilter {
    private JunctionCorridorPlanFilter() {
    }

    public static void apply(RoadCorridorPlan plan, JunctionCorridorBuildContext context) {
        if (plan == null || context == null) {
            return;
        }
        boolean useColumnMask = context.hasColumnMask();
        if (!useColumnMask && context.exclusionPolygons().isEmpty()) {
            return;
        }
        Set<ColumnKey> trimColumns = useColumnMask
            ? context.worldFootprint().corridorTrimColumns()
            : Set.of();
        boolean preserveClearance = context.preserveClearanceInExclusion();
        Set<ColumnKey> surfaceOrSlope = new HashSet<>();
        Set<ColumnKey> clearanceOnly = new HashSet<>();
        for (var entry : plan.surfaceMask().entries()) {
            if (ownedByJunction(entry.getKey(), entry.getValue().planPoint(), trimColumns,
                    context.exclusionPolygons(), useColumnMask)) {
                surfaceOrSlope.add(entry.getKey());
            }
        }
        for (var entry : plan.slopeEntries()) {
            if (ownedByJunction(entry.getKey(), entry.getValue().planPoint(), trimColumns,
                    context.exclusionPolygons(), useColumnMask)) {
                surfaceOrSlope.add(entry.getKey());
            }
        }
        for (var entry : plan.clearanceEntries()) {
            if (surfaceOrSlope.contains(entry.getKey())) {
                continue;
            }
            if (ownedByJunction(entry.getKey(), entry.getValue().planPoint(), trimColumns,
                    context.exclusionPolygons(), useColumnMask)) {
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

    private static boolean ownedByJunction(
            ColumnKey key,
            Vec2d planPoint,
            Set<ColumnKey> trimColumns,
            List<List<Vec2d>> polygons,
            boolean useColumnMask) {
        if (useColumnMask) {
            return key != null && trimColumns.contains(key);
        }
        return planPoint != null && insideAny(planPoint, polygons);
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
