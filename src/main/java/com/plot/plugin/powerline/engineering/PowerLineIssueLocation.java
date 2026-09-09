package com.plot.plugin.powerline.engineering;

import com.plot.api.geometry.Vec2d;

/** 线路检查问题位置（平面 + 可选里程）。 */
public record PowerLineIssueLocation(
        Vec2d planPoint,
        double stationing,
        String poleSiteId,
        String spanId) {

    public static PowerLineIssueLocation at(Vec2d planPoint) {
        return new PowerLineIssueLocation(
            planPoint != null ? planPoint.copy() : new Vec2d(0, 0),
            0.0,
            null,
            null);
    }

    public static PowerLineIssueLocation at(Vec2d planPoint, double stationing) {
        return new PowerLineIssueLocation(
            planPoint != null ? planPoint.copy() : new Vec2d(0, 0),
            stationing,
            null,
            null);
    }
}
