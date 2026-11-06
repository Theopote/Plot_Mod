package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;

import java.util.List;

/**
 * Vertical protection at grade-separated nodes.
 * <ul>
 *   <li>Elevated edges: fill must not bury the underpass; cut may clear the gap.</li>
 *   <li>Lower edges: open clearance must not carve into/through the elevated deck.</li>
 * </ul>
 */
public record GradeSeparationBuildContext(
        boolean elevatedRoadEdge,
        List<ProtectedUnderpassZone> protectedZones) {

    public record ProtectedUnderpassZone(List<Vec2d> polygon, int floorY, int surfaceY) {
        public ProtectedUnderpassZone(List<Vec2d> polygon, int floorY) {
            this(polygon, floorY, floorY);
        }

        public ProtectedUnderpassZone {
            polygon = polygon != null ? List.copyOf(polygon) : List.of();
        }
    }

    public static final GradeSeparationBuildContext NONE = new GradeSeparationBuildContext(false, List.of());

    public GradeSeparationBuildContext {
        protectedZones = protectedZones != null ? List.copyOf(protectedZones) : List.of();
    }

    /** Excavation may clear the gap; fill must preserve the whole underpass volume. */
    public boolean protectsClearance(Vec2d planPoint, int y) {
        if (!elevatedRoadEdge || planPoint == null) {
            return false;
        }
        for (var zone : protectedZones) {
            if (y <= zone.surfaceY() && RoadGeometryUtils.pointInPolygon(planPoint, zone.polygon())) {
                return true;
            }
        }
        return false;
    }

    public boolean protects(Vec2d planPoint, int y) {
        if (!elevatedRoadEdge || planPoint == null || protectedZones.isEmpty()) {
            return false;
        }
        for (ProtectedUnderpassZone zone : protectedZones) {
            if (y <= zone.floorY() && RoadGeometryUtils.pointInPolygon(planPoint, zone.polygon())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plan-space margin so corridor columns that sit one voxel outside the fill polygon
     * (skew raster residual) still receive the under-deck clearance cap.
     */
    public static final double DECK_CAP_PLAN_MARGIN = 1.0;

    /**
     * For lower (at-grade) edges under a deck: max Y that open clearance may clear.
     * {@code floorY} is elevated pavement Y；净空停在承重层（pavement − supportDepth）之下。
     * <p>
     * Cap is spatial: only columns inside (or within {@link #DECK_CAP_PLAN_MARGIN} of)
     * a protected underpass polygon. Far corridor columns on the same lower edge must
     * keep their local clear height. Skew coverage comes from expanding the zone
     * polygon (elevated projection), not from applying the cap to the whole edge.
     */
    public Integer maxOpenClearTopY(Vec2d planPoint) {
        if (elevatedRoadEdge || planPoint == null || protectedZones.isEmpty()) {
            return null;
        }
        Integer max = null;
        for (ProtectedUnderpassZone zone : protectedZones) {
            if (!coversUnderpassPlanPoint(planPoint, zone.polygon())) {
                continue;
            }
            int supportBottom = zone.floorY() - JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH;
            int cap = supportBottom - 1;
            max = max == null ? cap : Math.min(max, cap);
        }
        return max;
    }

    static boolean coversUnderpassPlanPoint(Vec2d planPoint, List<Vec2d> polygon) {
        if (RoadGeometryUtils.pointInPolygon(planPoint, polygon)) {
            return true;
        }
        return distanceToPolygonBoundarySq(planPoint, polygon)
            <= DECK_CAP_PLAN_MARGIN * DECK_CAP_PLAN_MARGIN;
    }

    private static double distanceToPolygonBoundarySq(Vec2d point, List<Vec2d> polygon) {
        if (point == null || polygon == null || polygon.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Vec2d a = polygon.get(j);
            Vec2d b = polygon.get(i);
            if (a == null || b == null) {
                continue;
            }
            best = Math.min(best, distanceToSegmentSq(point, a, b));
        }
        return best;
    }

    private static double distanceToSegmentSq(Vec2d p, Vec2d a, Vec2d b) {
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double lenSq = abx * abx + aby * aby;
        if (lenSq < 1e-18) {
            double dx = p.x - a.x;
            double dy = p.y - a.y;
            return dx * dx + dy * dy;
        }
        double t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq;
        t = Math.clamp(t, 0.0, 1.0);
        double dx = p.x - (a.x + t * abx);
        double dy = p.y - (a.y + t * aby);
        return dx * dx + dy * dy;
    }
}
