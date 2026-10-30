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
        return buildOpen(centerline, halfWidth, OpenPolylineOffset.JoinStyle.MITER);
    }

    /**
     * 画布叠加层走廊：开放折线用 round join，避免锐角 miter 刀尖；闭环仍走多边形偏移。
     */
    public static RoadCorridorGeometry buildForOverlay(
            List<Vec2d> centerline,
            double halfWidth,
            boolean closed) {
        if (centerline == null || centerline.size() < 2 || halfWidth <= 0.0) {
            return empty(centerline, closed);
        }
        boolean effectiveClosed = closed || isGeometricallyClosed(centerline);
        if (effectiveClosed) {
            return buildClosed(centerline, halfWidth);
        }
        return buildOpen(centerline, halfWidth, OpenPolylineOffset.JoinStyle.ROUND);
    }

    /**
     * 按中心线顶点半宽生成走廊（可变横断面叠加层）。半宽列表须与中心线等长；
     * 若全部相等则回退到等距偏移。
     */
    public static RoadCorridorGeometry build(
            List<Vec2d> centerline,
            List<Double> halfWidths,
            boolean closed) {
        if (centerline == null
                || halfWidths == null
                || centerline.size() < 2
                || halfWidths.size() != centerline.size()) {
            return empty(centerline, closed);
        }
        double first = halfWidths.getFirst();
        boolean uniform = true;
        double minPositive = first > 0.0 ? first : 0.0;
        for (double halfWidth : halfWidths) {
            if (Math.abs(halfWidth - first) > 1e-6) {
                uniform = false;
            }
            if (halfWidth > 0.0) {
                minPositive = minPositive > 0.0 ? Math.min(minPositive, halfWidth) : halfWidth;
            }
        }
        if (uniform) {
            return build(centerline, first, closed);
        }
        if (minPositive <= 0.0) {
            return empty(centerline, closed);
        }
        return buildVariableOpen(centerline, halfWidths, closed || isGeometricallyClosed(centerline));
    }

    private static RoadCorridorGeometry buildVariableOpen(
            List<Vec2d> centerline,
            List<Double> halfWidths,
            boolean closed) {
        List<Double> leftDistances = new ArrayList<>(halfWidths.size());
        List<Double> rightDistances = new ArrayList<>(halfWidths.size());
        for (double halfWidth : halfWidths) {
            double safe = Math.max(0.0, halfWidth);
            leftDistances.add(safe);
            rightDistances.add(-safe);
        }
        OpenPolylineOffset.OffsetResult left = OpenPolylineOffset.offsetVariable(centerline, leftDistances);
        OpenPolylineOffset.OffsetResult right = OpenPolylineOffset.offsetVariable(centerline, rightDistances);
        if (left.points().size() < 2 || right.points().size() < 2) {
            return empty(centerline, closed);
        }
        List<String> warnings = new ArrayList<>();
        warnings.addAll(left.warnings());
        warnings.addAll(right.warnings());
        List<Vec2d> fill = new ArrayList<>(left.points().size() + right.points().size());
        fill.addAll(left.points());
        for (int index = right.points().size() - 1; index >= 0; index--) {
            fill.add(right.points().get(index).copy());
        }
        List<Vec2d> sanitized = OpenPolylineOffset.sanitizeOpenCenterline(centerline);
        return new RoadCorridorGeometry(
            sanitized,
            left.points(),
            right.points(),
            List.of(fill),
            closed,
            warnings);
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

    private static RoadCorridorGeometry buildOpen(
            List<Vec2d> centerline,
            double halfWidth,
            OpenPolylineOffset.JoinStyle joinStyle) {
        List<Vec2d> sanitized = OpenPolylineOffset.sanitizeOpenCenterline(centerline);
        if (sanitized.size() < 2) {
            return empty(centerline, false);
        }

        OpenPolylineOffset.OffsetResult left = OpenPolylineOffset.offset(
            sanitized, halfWidth, PolygonUtils.DEFAULT_MITER_LIMIT, joinStyle);
        OpenPolylineOffset.OffsetResult right = OpenPolylineOffset.offset(
            sanitized, -halfWidth, PolygonUtils.DEFAULT_MITER_LIMIT, joinStyle);
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
