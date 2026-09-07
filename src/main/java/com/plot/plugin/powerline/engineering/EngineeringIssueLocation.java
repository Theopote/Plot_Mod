package com.plot.plugin.powerline.engineering;

import com.plot.api.geometry.Vec2d;

/** 工程问题位置（平面 + 可选里程）。 */
public record EngineeringIssueLocation(
        Vec2d planPoint,
        double stationing,
        String poleSiteId,
        String spanId) {

    public static EngineeringIssueLocation at(Vec2d planPoint) {
        return new EngineeringIssueLocation(
            planPoint != null ? planPoint.copy() : new Vec2d(0, 0),
            0.0,
            null,
            null);
    }

    public static EngineeringIssueLocation at(Vec2d planPoint, double stationing) {
        return new EngineeringIssueLocation(
            planPoint != null ? planPoint.copy() : new Vec2d(0, 0),
            stationing,
            null,
            null);
    }
}
