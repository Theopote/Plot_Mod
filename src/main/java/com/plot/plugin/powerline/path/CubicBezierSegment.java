package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.drawing.helper.BezierUtils;

/**
 * 单段三次贝塞尔曲线（画布坐标）。
 */
public record CubicBezierSegment(Vec2d anchor1, Vec2d control1, Vec2d control2, Vec2d anchor2) {

    public Vec2d pointAt(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return BezierUtils.evaluateCubicBezier(anchor1, control1, control2, anchor2, clamped);
    }

    public Vec2d tangentAt(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        Vec2d p01 = anchor1.lerp(control1, clamped);
        Vec2d p12 = control1.lerp(control2, clamped);
        Vec2d p23 = control2.lerp(anchor2, clamped);
        Vec2d p012 = p01.lerp(p12, clamped);
        Vec2d p123 = p12.lerp(p23, clamped);
        Vec2d tangent = p123.subtract(p012);
        if (tangent.lengthSquared() < 1e-12) {
            return anchor2.subtract(anchor1);
        }
        return tangent.normalize();
    }
}
