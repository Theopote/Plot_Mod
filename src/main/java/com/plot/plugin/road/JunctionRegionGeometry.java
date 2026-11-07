package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/**
 * 平交路口区域几何：重叠核区、原始铺装并集、路缘圆角补铺与最终铺装。
 * <p>
 * 重叠区仅用于识别路口核；真正外形由铺装并集外边界上的凹角补弧决定。
 */
public record JunctionRegionGeometry(
        List<Vec2d> overlapRegion,
        List<Vec2d> originalRoadUnion,
        List<Vec2d> finalPavementRegion,
        List<List<Vec2d>> filletAdditions,
        List<CurbArc> curbArcs,
        List<ApproachBoundary> approaches,
        double requestedCornerRadius,
        List<Double> effectiveCornerRadii) {

    public JunctionRegionGeometry {
        overlapRegion = copyRing(overlapRegion);
        originalRoadUnion = copyRing(originalRoadUnion);
        finalPavementRegion = copyRing(finalPavementRegion);
        filletAdditions = filletAdditions == null
            ? List.of()
            : filletAdditions.stream().map(JunctionRegionGeometry::copyRing).toList();
        curbArcs = curbArcs != null ? List.copyOf(curbArcs) : List.of();
        approaches = approaches != null ? List.copyOf(approaches) : List.of();
        requestedCornerRadius = Math.max(0.0, requestedCornerRadius);
        effectiveCornerRadii = effectiveCornerRadii != null
            ? List.copyOf(effectiveCornerRadii) : List.of();
    }

    public static JunctionRegionGeometry empty() {
        return new JunctionRegionGeometry(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0.0, List.of());
    }

    public boolean hasPavement() {
        return finalPavementRegion.size() >= 3;
    }

    /** 锐角并集或任一街角补铺覆盖该点（圆角不再并入最终环，避免极角拼接扇形膨胀）。 */
    public boolean containsPavement(Vec2d point) {
        if (point == null) {
            return false;
        }
        if (RoadGeometryUtils.pointInPolygon(point, finalPavementRegion)) {
            return true;
        }
        for (List<Vec2d> addition : filletAdditions) {
            if (addition != null && addition.size() >= 3
                    && RoadGeometryUtils.pointInPolygon(point, addition)) {
                return true;
            }
        }
        return false;
    }

    public double minEffectiveCornerRadius() {
        double min = Double.POSITIVE_INFINITY;
        for (double radius : effectiveCornerRadii) {
            if (radius > 1e-9) {
                min = Math.min(min, radius);
            }
        }
        return Double.isFinite(min) ? min : 0.0;
    }

    public double maxEffectiveCornerRadius() {
        double max = 0.0;
        for (double radius : effectiveCornerRadii) {
            max = Math.max(max, radius);
        }
        return max;
    }

    /** 适配现有预览/生成共用的 {@link JunctionGeometryResult}。 */
    public JunctionGeometryResult toJunctionGeometryResult() {
        if (!hasPavement()) {
            return JunctionGeometryResult.empty();
        }
        List<Vec2d> boundaries = approaches.stream()
            .flatMap(a -> a.mouthCorners().stream())
            .toList();
        List<JunctionGeometryResult.FilletCorner> fillets = curbArcs.stream()
            .map(arc -> new JunctionGeometryResult.FilletCorner(
                arc.center(), arc.tangentStart(), arc.tangentEnd(), arc.radius(), arc.cornerAngle()))
            .toList();
        // fillPolygon：在重叠核角织入路缘弧，供清障外形 / 预览轮廓；与锐角 final 环分离
        List<Vec2d> fill = JunctionRegionBuilder.expandUnionWithStreetArcs(
            originalRoadUnion, overlapRegion, curbArcs, 10);
        if (fill.size() < 3) {
            fill = finalPavementRegion;
        }
        return new JunctionGeometryResult(
            fill,
            originalRoadUnion,
            boundaries,
            requestedCornerRadius,
            minEffectiveCornerRadius(),
            fillets,
            overlapRegion,
            filletAdditions);
    }

    private static List<Vec2d> copyRing(List<Vec2d> ring) {
        return ring != null ? List.copyOf(ring) : List.of();
    }

    /** 一条道路在交叉处的局部路缘边界。 */
    public record ApproachBoundary(
            Vec2d direction,
            double halfWidth,
            List<Vec2d> mouthCorners) {
        public ApproachBoundary {
            if (direction != null && direction.lengthSquared() > 1e-12) {
                direction = direction.normalize();
            } else {
                direction = new Vec2d(0, 0);
            }
            halfWidth = Math.max(0.0, halfWidth);
            mouthCorners = mouthCorners != null ? List.copyOf(mouthCorners) : List.of();
        }
    }

    /** 单个街角路缘圆弧（圆心在铺装外侧，弧向街角扩展）。 */
    public record CurbArc(
            Vec2d center,
            Vec2d tangentStart,
            Vec2d tangentEnd,
            double radius,
            double cornerAngle,
            String shrinkReason) {
        public CurbArc {
            if (center != null) {
                center = center.copy();
            }
            if (tangentStart != null) {
                tangentStart = tangentStart.copy();
            }
            if (tangentEnd != null) {
                tangentEnd = tangentEnd.copy();
            }
            radius = Math.max(0.0, radius);
            cornerAngle = Math.max(0.0, cornerAngle);
            shrinkReason = shrinkReason != null ? shrinkReason : "";
        }
    }
}
