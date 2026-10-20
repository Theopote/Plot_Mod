package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;

import java.util.List;

/**
 * Vertical protection when generating an elevated road edge: excavation preserves
 * the lower road surface, while fill must preserve the entire underpass gap.
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
        if (!elevatedRoadEdge || planPoint == null) return false;
        for (var zone : protectedZones) {
            if (y <= zone.surfaceY() && RoadGeometryUtils.pointInPolygon(planPoint, zone.polygon())) return true;
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
}
