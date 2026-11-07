package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/**
 * 平交路口最终几何：铺装多边形与路缘圆角元数据共用同一计算结果，
 * 避免 UI 预览、走廊裁剪与方块生成各自倒角。
 * <p>
 * 区域式结果额外携带 {@link #overlapRegion()}（路口核）与
 * {@link #filletAdditions()}（街角补铺）；生成器只应对这两类区域施加路口材质，
 * 进口路臂仍由各道路走廊保持原材质。
 */
public record JunctionGeometryResult(
        List<Vec2d> fillPolygon,
        List<Vec2d> sharpPolygon,
        List<Vec2d> approachBoundaryPoints,
        double requestedCornerRadius,
        double effectiveCornerRadius,
        List<FilletCorner> fillets,
        List<Vec2d> overlapRegion,
        List<List<Vec2d>> filletAdditions) {

    public JunctionGeometryResult(
            List<Vec2d> fillPolygon,
            List<Vec2d> sharpPolygon,
            List<Vec2d> approachBoundaryPoints,
            double requestedCornerRadius,
            double effectiveCornerRadius,
            List<FilletCorner> fillets) {
        this(
            fillPolygon,
            sharpPolygon,
            approachBoundaryPoints,
            requestedCornerRadius,
            effectiveCornerRadius,
            fillets,
            List.of(),
            List.of());
    }

    public JunctionGeometryResult {
        fillPolygon = fillPolygon != null ? List.copyOf(fillPolygon) : List.of();
        sharpPolygon = sharpPolygon != null ? List.copyOf(sharpPolygon) : List.of();
        approachBoundaryPoints = approachBoundaryPoints != null
            ? List.copyOf(approachBoundaryPoints) : List.of();
        fillets = fillets != null ? List.copyOf(fillets) : List.of();
        overlapRegion = overlapRegion != null ? List.copyOf(overlapRegion) : List.of();
        filletAdditions = filletAdditions == null
            ? List.of()
            : filletAdditions.stream()
                .map(ring -> ring != null ? List.copyOf(ring) : List.<Vec2d>of())
                .toList();
        requestedCornerRadius = Math.max(0.0, requestedCornerRadius);
        effectiveCornerRadius = Math.max(0.0, effectiveCornerRadius);
    }

    public static JunctionGeometryResult empty() {
        return new JunctionGeometryResult(
            List.of(), List.of(), List.of(), 0.0, 0.0, List.of(), List.of(), List.of());
    }

    public boolean hasFillPolygon() {
        return fillPolygon.size() >= 3;
    }

    /** 区域式核区可用时，路口材质责任区为重叠核 ∪ 圆角补铺。 */
    public boolean hasRegionZones() {
        return overlapRegion.size() >= 3;
    }

    /**
     * 单个路缘圆角：圆心在角内侧，弧连接两条相邻进口边界上的切点。
     */
    public record FilletCorner(
            Vec2d center,
            Vec2d tangentStart,
            Vec2d tangentEnd,
            double radius,
            double cornerAngleRadians) {
        public FilletCorner {
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
            cornerAngleRadians = Math.max(0.0, cornerAngleRadians);
        }
    }
}
