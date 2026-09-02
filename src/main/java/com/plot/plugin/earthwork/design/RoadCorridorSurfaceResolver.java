package com.plot.plugin.earthwork.design;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.GradingZone;

/**
 * 道路走廊分区：按关联 {@link RoadSurfaceLookup} 采样设计标高。
 */
public final class RoadCorridorSurfaceResolver {
    private RoadCorridorSurfaceResolver() {
    }

    public static int evaluateTargetY(
            Vec2d canvasCenter,
            GradingZone zone,
            DesignSurface surface,
            RoadSurfaceLookup lookup,
            int fallbackElevation) {
        if (canvasCenter == null) {
            return fallbackElevation;
        }
        String roadEdgeRef = resolveRoadEdgeRef(zone, surface);
        if (roadEdgeRef.isBlank() || lookup == null) {
            return fallbackElevation;
        }
        Integer sampled = lookup.sampleDesignY(roadEdgeRef, canvasCenter);
        return sampled != null ? sampled : fallbackElevation;
    }

    public static String resolveRoadEdgeRef(GradingZone zone, DesignSurface surface) {
        if (zone != null && zone.getRoadEdgeRef() != null && !zone.getRoadEdgeRef().isBlank()) {
            return zone.getRoadEdgeRef();
        }
        if (surface != null && surface.getRoadEdgeRef() != null && !surface.getRoadEdgeRef().isBlank()) {
            return surface.getRoadEdgeRef();
        }
        return "";
    }
}
