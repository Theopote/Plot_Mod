package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * 路口进口局部铺装条带：沿真实中心线折线偏移左右路缘并闭合成多边形。
 * 直线进口退化为矩形；弯曲进口保留曲率；半宽可随沿程变化。
 */
public final class JunctionPavementBand {
    private static final double MIN_SEG = 1e-8;
    private static final double SAMPLE_STEP = 1.0;

    private JunctionPavementBand() {
    }

    /**
     * 恒定半宽 stub（兼容旧调用）。
     */
    public static List<Vec2d> buildStubPolygon(
            List<Vec2d> outwardCenterline,
            double halfWidth,
            double backAlong) {
        return buildStubPolygon(outwardCenterline, along -> halfWidth, backAlong);
    }

    /**
     * @param outwardCenterline 从路口中心沿进口向外的中心线（至少 2 点）
     * @param halfWidthAtAlong  沿程半宽：参数为自路口向外的距离（回伸段用 along=0 的宽度）
     * @param backAlong         向路口内侧回伸长度
     */
    public static List<Vec2d> buildStubPolygon(
            List<Vec2d> outwardCenterline,
            DoubleUnaryOperator halfWidthAtAlong,
            double backAlong) {
        if (outwardCenterline == null || outwardCenterline.size() < 2 || halfWidthAtAlong == null) {
            return List.of();
        }
        double back = Math.max(0.0, backAlong);
        List<Vec2d> spine = densify(extendBack(outwardCenterline, back), SAMPLE_STEP);
        if (spine.size() < 2) {
            return List.of();
        }

        boolean variable = false;
        double firstHw = Math.max(0.0, halfWidthAtAlong.applyAsDouble(0.0));
        List<Vec2d> left = new ArrayList<>(spine.size());
        List<Vec2d> right = new ArrayList<>(spine.size());
        double along = -back;
        for (int i = 0; i < spine.size(); i++) {
            Vec2d point = spine.get(i);
            Vec2d tangent = tangentAt(spine, i);
            if (tangent.lengthSquared() < MIN_SEG) {
                continue;
            }
            Vec2d normal = tangent.normalize().perpendicular();
            double queryAlong = Math.max(0.0, along);
            double hw = Math.max(0.0, halfWidthAtAlong.applyAsDouble(queryAlong));
            if (Math.abs(hw - firstHw) > 1e-6) {
                variable = true;
            }
            if (hw <= 1e-9) {
                along += i + 1 < spine.size() ? point.distance(spine.get(i + 1)) : 0.0;
                continue;
            }
            left.add(point.add(normal.multiply(hw)));
            right.add(point.subtract(normal.multiply(hw)));
            if (i + 1 < spine.size()) {
                along += point.distance(spine.get(i + 1));
            }
        }
        if (left.size() < 2 || right.size() < 2) {
            // 回退恒定宽 OffsetHandler（更稳的锐角衔接）
            if (firstHw <= 1e-9) {
                return List.of();
            }
            return buildConstantOffsetPolygon(extendBack(outwardCenterline, back), firstHw);
        }
        if (!variable) {
            return buildConstantOffsetPolygon(extendBack(outwardCenterline, back), firstHw);
        }
        List<Vec2d> ring = new ArrayList<>(left.size() + right.size());
        ring.addAll(left);
        for (int i = right.size() - 1; i >= 0; i--) {
            ring.add(right.get(i));
        }
        return dedupeRing(ring);
    }

    private static List<Vec2d> buildConstantOffsetPolygon(List<Vec2d> spine, double halfWidth) {
        if (spine.size() < 2 || halfWidth <= 1e-9) {
            return List.of();
        }
        List<Vec2d> left = OffsetHandler.offsetPolyline(spine, halfWidth);
        List<Vec2d> right = OffsetHandler.offsetPolyline(spine, -halfWidth);
        if (left.size() < 2 || right.size() < 2) {
            return List.of();
        }
        List<Vec2d> ring = new ArrayList<>(left.size() + right.size());
        ring.addAll(left);
        for (int i = right.size() - 1; i >= 0; i--) {
            ring.add(right.get(i));
        }
        return dedupeRing(ring);
    }

    /** 中心线起点沿首段反向延长 backAlong。 */
    static List<Vec2d> extendBack(List<Vec2d> outward, double backAlong) {
        List<Vec2d> spine = new ArrayList<>(outward.size() + 1);
        Vec2d start = outward.getFirst();
        Vec2d next = outward.get(1);
        Vec2d dir = next.subtract(start);
        if (backAlong > 1e-9 && dir.lengthSquared() > MIN_SEG) {
            spine.add(start.subtract(dir.normalize().multiply(backAlong)));
        }
        spine.addAll(outward);
        return spine;
    }

    static List<Vec2d> densify(List<Vec2d> points, double step) {
        if (points == null || points.size() < 2 || step <= 1e-6) {
            return points != null ? List.copyOf(points) : List.of();
        }
        List<Vec2d> out = new ArrayList<>();
        out.add(points.getFirst());
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            double len = a.distance(b);
            if (len < MIN_SEG) {
                continue;
            }
            int parts = Math.max(1, (int) Math.ceil(len / step));
            for (int p = 1; p <= parts; p++) {
                double t = (double) p / parts;
                out.add(new Vec2d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t));
            }
        }
        return out;
    }

    private static Vec2d tangentAt(List<Vec2d> spine, int index) {
        if (index <= 0) {
            return spine.get(1).subtract(spine.get(0));
        }
        if (index >= spine.size() - 1) {
            return spine.get(spine.size() - 1).subtract(spine.get(spine.size() - 2));
        }
        return spine.get(index + 1).subtract(spine.get(index - 1));
    }

    private static List<Vec2d> dedupeRing(List<Vec2d> ring) {
        if (ring == null || ring.isEmpty()) {
            return List.of();
        }
        List<Vec2d> out = new ArrayList<>();
        for (Vec2d point : ring) {
            if (point == null) {
                continue;
            }
            if (!out.isEmpty() && out.getLast().distance(point) < 1e-4) {
                continue;
            }
            out.add(point);
        }
        if (out.size() >= 2 && out.getFirst().distance(out.getLast()) < 1e-4) {
            out.removeLast();
        }
        return out.size() >= 3 ? List.copyOf(out) : List.of();
    }
}
