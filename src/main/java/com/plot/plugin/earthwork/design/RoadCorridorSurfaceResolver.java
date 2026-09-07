package com.plot.plugin.earthwork.design;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.utils.PlotI18n;

import java.util.List;

/**
 * 道路走廊分区：按关联 {@link RoadSurfaceLookup} 采样设计标高（fail-closed，禁止回退场地平均）。
 */
public final class RoadCorridorSurfaceResolver {
    private RoadCorridorSurfaceResolver() {
    }

    /** 要求可解析道路设计面但未满足时抛出。 */
    public static final class UnresolvedRoadDesignSurfaceException extends IllegalStateException {
        public UnresolvedRoadDesignSurfaceException(String message) {
            super(message);
        }
    }

    /**
     * 预览前校验：道路走廊必须能解析到设计标高，否则返回引用失败状态。
     */
    public static ResolutionResult<Void> validateDesignSurface(
            GradingZone zone,
            DesignSurface surface,
            RoadSurfaceLookup lookup) {
        String zoneName = zone != null ? zone.getName() : "";
        String roadEdgeRef = resolveRoadEdgeRef(zone, surface);
        if (roadEdgeRef.isBlank()) {
            return ResolutionResult.missingReference(
                null,
                PlotI18n.tr("plugin.earthwork.validation.road_corridor_no_reference", zoneName));
        }
        if (lookup == null || lookup == RoadSurfaceLookup.NONE) {
            return ResolutionResult.invalidReference(
                null,
                PlotI18n.tr(
                    "plugin.earthwork.validation.road_corridor_unresolved_design_surface",
                    zoneName,
                    roadEdgeRef));
        }
        Vec2d probe = zoneProbePoint(zone);
        Integer sampled = lookup.sampleDesignY(roadEdgeRef, probe);
        if (sampled == null) {
            return ResolutionResult.invalidReference(
                null,
                PlotI18n.tr(
                    "plugin.earthwork.validation.road_corridor_unresolved_design_surface",
                    zoneName,
                    roadEdgeRef));
        }
        return ResolutionResult.resolved(null);
    }

    public static int evaluateTargetY(
            Vec2d canvasCenter,
            GradingZone zone,
            DesignSurface surface,
            RoadSurfaceLookup lookup) {
        if (canvasCenter == null) {
            throw unresolvedAtCell(zone, surface, "missing cell center");
        }
        String roadEdgeRef = resolveRoadEdgeRef(zone, surface);
        if (roadEdgeRef.isBlank()) {
            throw unresolvedAtCell(zone, surface, "missing road edge reference");
        }
        if (lookup == null || lookup == RoadSurfaceLookup.NONE) {
            throw unresolvedAtCell(zone, surface, "road lookup unavailable");
        }
        Integer sampled = lookup.sampleDesignY(roadEdgeRef, canvasCenter);
        if (sampled == null) {
            throw unresolvedAtCell(zone, surface, "no design elevation at " + formatPoint(canvasCenter));
        }
        return sampled;
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

    private static Vec2d zoneProbePoint(GradingZone zone) {
        if (zone == null) {
            return new Vec2d(0, 0);
        }
        List<Vec2d> points = zone.getOuterPoints();
        if (points == null || points.isEmpty()) {
            return new Vec2d(0, 0);
        }
        double x = 0;
        double z = 0;
        for (Vec2d point : points) {
            if (point == null) {
                continue;
            }
            x += point.x;
            z += point.y;
        }
        return new Vec2d(x / points.size(), z / points.size());
    }

    private static UnresolvedRoadDesignSurfaceException unresolvedAtCell(
            GradingZone zone,
            DesignSurface surface,
            String reason) {
        String zoneName = zone != null ? zone.getName() : "";
        String ref = resolveRoadEdgeRef(zone, surface);
        String message = ref.isBlank()
            ? PlotI18n.tr("plugin.earthwork.validation.road_corridor_no_reference", zoneName)
            : PlotI18n.tr(
                "plugin.earthwork.validation.road_corridor_unresolved_design_surface",
                zoneName,
                ref);
        if (reason != null && !reason.isBlank()) {
            message = message + " (" + reason + ")";
        }
        return new UnresolvedRoadDesignSurfaceException(message);
    }

    private static String formatPoint(Vec2d point) {
        return point.x + "," + point.y;
    }
}
