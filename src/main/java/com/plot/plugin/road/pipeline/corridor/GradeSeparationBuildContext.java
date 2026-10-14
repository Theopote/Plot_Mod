package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;

import java.util.List;

/**
 * Vertical protection for underpass layers when generating an elevated road edge.
 * Blocks clearance / grading at {@code y <= floorY} inside {@code polygon}.
 */
public record GradeSeparationBuildContext(
        boolean elevatedRoadEdge,
        List<ProtectedUnderpassZone> protectedZones) {

    public record ProtectedUnderpassZone(List<Vec2d> polygon, int floorY) {
        public ProtectedUnderpassZone {
            polygon = polygon != null ? List.copyOf(polygon) : List.of();
        }
    }

    public static final GradeSeparationBuildContext NONE = new GradeSeparationBuildContext(false, List.of());

    public GradeSeparationBuildContext {
        protectedZones = protectedZones != null ? List.copyOf(protectedZones) : List.of();
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
