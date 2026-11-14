package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;

/**
 * 路缘圆角圆弧扫掠：外扩补铺必须走切点之间的<strong>短弧</strong>（|扫掠| ≤ π）。
 * <p>
 * 草地楔形角 φ 与切点圆心角 α 互补（α = π − φ）。禁止再根据「弧中点更靠近街角」
 * 改走长弧，否则预览短弧与补铺长弧会不一致，并可能画出近整圆回绕。
 */
public final class FilletArcSweep {
    private FilletArcSweep() {
    }

    /** 切点间短弧扫掠；|结果| ≤ π，范围 (-π, π]。 */
    public static double resolveShort(Vec2d arcCenter, Vec2d start, Vec2d end) {
        if (arcCenter == null || start == null || end == null) {
            return 0.0;
        }
        double startAngle = Math.atan2(start.y - arcCenter.y, start.x - arcCenter.x);
        double endAngle = Math.atan2(end.y - arcCenter.y, end.x - arcCenter.x);
        double sweep = endAngle - startAngle;
        while (sweep <= -Math.PI) {
            sweep += Math.PI * 2.0;
        }
        while (sweep > Math.PI) {
            sweep -= Math.PI * 2.0;
        }
        return sweep;
    }

    public static Vec2d pointOnArc(Vec2d arcCenter, double radius, double angle) {
        return new Vec2d(
            arcCenter.x + Math.cos(angle) * radius,
            arcCenter.y + Math.sin(angle) * radius);
    }

    /** 按已确定的扫掠角采样开折线（预览与补铺共用，避免二次判定方向）。 */
    public static ListSamples sample(
            Vec2d arcCenter,
            Vec2d start,
            Vec2d end,
            double radius,
            double sweepRadians,
            int segments) {
        if (arcCenter == null || start == null || end == null || radius <= 1e-12) {
            return ListSamples.empty();
        }
        double startAngle = Math.atan2(start.y - arcCenter.y, start.x - arcCenter.x);
        double sweep = sweepRadians;
        if (!Double.isFinite(sweep) || Math.abs(sweep) < 1e-12) {
            sweep = resolveShort(arcCenter, start, end);
        }
        // 硬夹：路缘圆角不得近整圆
        if (Math.abs(sweep) > Math.PI + 1e-9) {
            sweep = resolveShort(arcCenter, start, end);
        }
        int segs = Math.max(3, segments);
        java.util.ArrayList<Vec2d> points = new java.util.ArrayList<>(segs + 1);
        points.add(start.copy());
        for (int i = 1; i < segs; i++) {
            double t = (double) i / segs;
            points.add(pointOnArc(arcCenter, radius, startAngle + sweep * t));
        }
        points.add(end.copy());
        return new ListSamples(java.util.List.copyOf(points), sweep);
    }

    public record ListSamples(java.util.List<Vec2d> points, double sweepRadians) {
        public ListSamples {
            points = points != null ? java.util.List.copyOf(points) : java.util.List.of();
        }

        static ListSamples empty() {
            return new ListSamples(java.util.List.of(), 0.0);
        }
    }
}
