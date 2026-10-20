package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;

/**
 * Shift 正交/45° 约束：将终点约束到相对起点最近的 0°/45°/90°/… 方向。
 */
public final class OrthogonalConstraint {
    private OrthogonalConstraint() {
    }

    /**
     * 将 {@code end} 约束到相对 {@code start} 最近的 45° 倍角方向，保持长度。
     */
    public static Vec2d apply45Degree(Vec2d start, Vec2d end) {
        if (start == null || end == null) {
            return end;
        }

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.hypot(dx, dy);
        if (length < 1e-9) {
            return end;
        }

        double angle = Math.atan2(dy, dx);
        double step = Math.PI / 4.0;
        double snappedAngle = Math.round(angle / step) * step;

        return new Vec2d(
                start.x + length * Math.cos(snappedAngle),
                start.y + length * Math.sin(snappedAngle)
        );
    }
}
