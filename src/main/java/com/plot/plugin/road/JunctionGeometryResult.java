package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/**
 * 平交路口最终几何：铺装多边形与路缘圆角元数据共用同一计算结果，
 * 避免 UI 预览、走廊裁剪与方块生成各自倒角。
 */
public record JunctionGeometryResult(
        List<Vec2d> fillPolygon,
        List<Vec2d> sharpPolygon,
        List<Vec2d> approachBoundaryPoints,
        double requestedCornerRadius,
        double effectiveCornerRadius,
        List<FilletCorner> fillets) {

    public JunctionGeometryResult {
        fillPolygon = fillPolygon != null ? List.copyOf(fillPolygon) : List.of();
        sharpPolygon = sharpPolygon != null ? List.copyOf(sharpPolygon) : List.of();
        approachBoundaryPoints = approachBoundaryPoints != null
            ? List.copyOf(approachBoundaryPoints) : List.of();
        fillets = fillets != null ? List.copyOf(fillets) : List.of();
        requestedCornerRadius = Math.max(0.0, requestedCornerRadius);
        effectiveCornerRadius = Math.max(0.0, effectiveCornerRadius);
    }

    public static JunctionGeometryResult empty() {
        return new JunctionGeometryResult(List.of(), List.of(), List.of(), 0.0, 0.0, List.of());
    }

    public boolean hasFillPolygon() {
        return fillPolygon.size() >= 3;
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
