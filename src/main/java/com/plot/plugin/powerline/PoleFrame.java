package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonUtils;

/**
 * 杆塔局部坐标系：forward 沿线路，right 为横担方向。
 */
public record PoleFrame(
        Vec2d origin,
        Vec2d forward,
        Vec2d right,
        int groundY) {

    public static PoleFrame fromPole(Vec2d planPosition, Vec2d tangent, int groundY) {
        Vec2d origin = planPosition != null ? planPosition.copy() : new Vec2d(0, 0);
        Vec2d forward = normalizeOrFallback(tangent);
        Vec2d right = PolygonUtils.leftNormal(forward);
        if (right.lengthSquared() < 1e-12) {
            right = new Vec2d(0, 1);
        }
        return new PoleFrame(origin, forward, right, groundY);
    }

    public Vec2d toPlanPoint(double lateralOffset, double longitudinalOffset) {
        return origin
            .add(right.multiply(lateralOffset))
            .add(forward.multiply(longitudinalOffset));
    }

    private static Vec2d normalizeOrFallback(Vec2d tangent) {
        if (tangent == null || tangent.lengthSquared() < 1e-12) {
            return new Vec2d(1, 0);
        }
        return tangent.normalize();
    }
}
