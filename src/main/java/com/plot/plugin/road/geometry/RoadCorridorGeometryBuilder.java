package com.plot.plugin.road.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonNormalizer;
import com.plot.core.geometry.polygon.PolygonOffset;
import com.plot.core.geometry.polygon.PolygonUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;

import java.util.ArrayList;
import java.util.List;

/** 根据中心线与半宽生成道路走廊二维几何。 */
public final class RoadCorridorGeometryBuilder {
    private RoadCorridorGeometryBuilder() {
    }

    public static RoadCorridorGeometry build(List<Vec2d> centerline, double halfWidth) {
        return build(centerline, halfWidth, isGeometricallyClosed(centerline));
    }

    public static RoadCorridorGeometry build(List<Vec2d> centerline, double halfWidth, boolean closed) {
        if (centerline == null || centerline.size() < 2 || halfWidth <= 0.0) {
            return empty(centerline, closed);
        }
        boolean effectiveClosed = closed || isGeometricallyClosed(centerline);
        if (effectiveClosed) {
            return buildClosed(centerline, halfWidth);
        }
        return buildOpen(centerline, halfWidth);
    }

    public static boolean isGeometricallyClosed(List<Vec2d> centerline) {
        if (centerline == null || centerline.size() < 3) {
            return false;
        }
        return RoadGeometryUtils.pointsNear(
            centerline.getFirst(),
            centerline.getLast(),
            RoadNetworkBuilder.NODE_TOLERANCE);
    }

    private static RoadCorridorGeometry buildOpen(List<Vec2d> centerline, double halfWidth) {
        List<Vec2d> sanitized = OpenPolylineOffset.sanitizeOpenCenterline(centerline);
        if (sanitized.size() < 2) {
            return empty(centerline, false);
        }

        OpenPolylineOffset.OffsetResult left = OpenPolylineOffset.offset(sanitized, halfWidth);
        OpenPolylineOffset.OffsetResult right = OpenPolylineOffset.offset(sanitized, -halfWidth);
        if (left.points().size() < 2 || right.points().size() < 2) {
            return empty(sanitized, false);
        }

        List<String> warnings = new ArrayList<>();
        warnings.addAll(left.warnings());
        warnings.addAll(right.warnings());

        List<Vec2d> fill = new ArrayList<>(left.points().size() + right.points().size());
        fill.addAll(left.points());
        for (int index = right.points().size() - 1; index >= 0; index--) {
            fill.add(right.points().get(index).copy());
        }

        return new RoadCorridorGeometry(
            sanitized,
            left.points(),
            right.points(),
            List.of(fill),
            false,
            warnings);
    }

    private static RoadCorridorGeometry buildClosed(List<Vec2d> centerline, double halfWidth) {
        List<Vec2d> ring = sanitizeClosedRing(centerline);
        if (ring.size() < 3) {
            return empty(centerline, true);
        }

        List<String> warnings = new ArrayList<>();
        PolygonOffset.OffsetResult outerResult = PolygonOffset.offsetOutward(ring, halfWidth);
        PolygonOffset.OffsetResult innerResult = PolygonOffset.offsetInward(ring, halfWidth);
        warnings.addAll(outerResult.warnings());
        warnings.addAll(innerResult.warnings());

        List<Vec2d> outer = outerResult.pointsOrEmpty();
        List<Vec2d> inner = innerResult.pointsOrEmpty();
        if (outer.size() < 3 || inner.size() < 3) {
            warnings.add("closed_offset_failed");
            return empty(ring, true);
        }

        return new RoadCorridorGeometry(
            ring,
            outer,
            inner,
            List.of(outer, inner),
            true,
            warnings);
    }

    static List<Vec2d> sanitizeClosedRing(List<Vec2d> centerline) {
        List<Vec2d> sanitized = OpenPolylineOffset.sanitizeOpenCenterline(centerline);
        if (sanitized.size() >= 2
                && RoadGeometryUtils.pointsNear(
                    sanitized.getFirst(),
                    sanitized.getLast(),
                    RoadNetworkBuilder.NODE_TOLERANCE)) {
            sanitized = new ArrayList<>(sanitized.subList(0, sanitized.size() - 1));
        }
        return PolygonNormalizer.removeDuplicateVertices(sanitized);
    }

    private static RoadCorridorGeometry empty(List<Vec2d> centerline, boolean closed) {
        List<Vec2d> copied = centerline == null ? List.of() : OpenPolylineOffset.sanitizeOpenCenterline(centerline);
        return new RoadCorridorGeometry(copied, List.of(), List.of(), List.of(), closed, List.of());
    }
}
