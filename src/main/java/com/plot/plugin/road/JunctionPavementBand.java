package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 路口进口局部铺装条带：沿真实中心线折线偏移左右路缘并闭合成多边形。
 * 直线进口退化为矩形；弯曲进口保留曲率，避免无限切线外推失真。
 */
public final class JunctionPavementBand {
    private static final double MIN_SEG = 1e-8;

    private JunctionPavementBand() {
    }

    /**
     * @param outwardCenterline 从路口中心沿进口向外的中心线（至少 2 点）
     * @param halfWidth         硬表面半宽
     * @param backAlong         向路口内侧回伸长度（嵌入贯通道路重叠核）
     */
    public static List<Vec2d> buildStubPolygon(
            List<Vec2d> outwardCenterline,
            double halfWidth,
            double backAlong) {
        if (outwardCenterline == null || outwardCenterline.size() < 2 || halfWidth <= 1e-9) {
            return List.of();
        }
        List<Vec2d> spine = extendBack(outwardCenterline, Math.max(0.0, backAlong));
        if (spine.size() < 2) {
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
