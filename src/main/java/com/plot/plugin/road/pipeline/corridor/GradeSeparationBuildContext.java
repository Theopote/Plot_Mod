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

    /**
     * 斜交时上层桥面投影略超出锐角路口多边形；净空上限在保护多边形外再扩一圈裕量，
     * 避免只在多边形内生效时漏清，同时不把整条下层边远端都压在桥底标高约束下。
     */
    public static final double DECK_CAP_PLAN_MARGIN_BLOCKS = 4.0;

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
     * For lower (at-grade) edges under a deck: max Y that open clearance may clear.
     * {@code floorY} is elevated pavement Y；净空停在承重层（pavement − supportDepth）之下。
     * <p>
     * Cap applies near the protected junction footprint (polygon ∪ plan margin) so skew
     * underpass columns just outside the fill polygon stay capped, while distant approach
     * columns on a long lower edge are unaffected.
     */
    public Integer maxOpenClearTopY(Vec2d planPoint) {
        if (elevatedRoadEdge || protectedZones.isEmpty() || planPoint == null) {
            return null;
        }
        Integer max = null;
        for (ProtectedUnderpassZone zone : protectedZones) {
            if (!isWithinDeckCapInfluence(planPoint, zone.polygon(), DECK_CAP_PLAN_MARGIN_BLOCKS)) {
                continue;
            }
            int supportBottom = zone.floorY() - JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH;
            int cap = supportBottom - 1;
            max = max == null ? cap : Math.min(max, cap);
        }
        return max;
    }

    static boolean isWithinDeckCapInfluence(Vec2d planPoint, List<Vec2d> polygon, double margin) {
        if (planPoint == null || polygon == null || polygon.size() < 3) {
            return false;
        }
        if (RoadGeometryUtils.pointInPolygon(planPoint, polygon)) {
            return true;
        }
        if (margin <= 0.0) {
            return false;
        }
        double marginSq = margin * margin;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = polygon.get(i);
            Vec2d b = polygon.get((i + 1) % n);
            if (distanceSquaredToSegment(planPoint, a, b) <= marginSq) {
                return true;
            }
        }
        return false;
    }

    private static double distanceSquaredToSegment(Vec2d point, Vec2d a, Vec2d b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-18) {
            double ex = point.x - a.x;
            double ey = point.y - a.y;
            return ex * ex + ey * ey;
        }
        double t = ((point.x - a.x) * dx + (point.y - a.y) * dy) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double px = a.x + t * dx;
        double py = a.y + t * dy;
        double ex = point.x - px;
        double ey = point.y - py;
        return ex * ex + ey * ey;
    }
}
