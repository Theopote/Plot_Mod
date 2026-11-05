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
     * For lower (at-grade) edges under a deck: max Y that open clearance may clear.
     * {@code floorY} is elevated pavement Y；净空停在承重层（pavement − supportDepth）之下。
     */
    public Integer maxOpenClearTopY(Vec2d planPoint) {
        if (elevatedRoadEdge || planPoint == null || protectedZones.isEmpty()) {
            return null;
        }
        Integer max = null;
        for (ProtectedUnderpassZone zone : protectedZones) {
            if (!RoadGeometryUtils.pointInPolygon(planPoint, zone.polygon())) {
                continue;
            }
            int supportBottom = zone.floorY() - JunctionTerrainClearance.ELEVATED_DECK_SUPPORT_DEPTH;
            int cap = supportBottom - 1;
            max = max == null ? cap : Math.min(max, cap);
        }
        return max;
    }
}
