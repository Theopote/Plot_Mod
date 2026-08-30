package com.plot.plugin.road.alignment;

/**
 * 平面线形上一点：位置、切线方位角（弧度，数学角：0=+X，逆时针为正）、曲率（左转为正，1/半径）。
 */
public record AlignmentPose(double x, double y, double bearingRadians, double curvature) {

    public AlignmentPose {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(bearingRadians)) {
            throw new IllegalArgumentException("pose components must be finite");
        }
        if (!Double.isFinite(curvature)) {
            curvature = 0.0;
        }
    }

    public static AlignmentPose origin(double startBearingRadians) {
        return new AlignmentPose(0.0, 0.0, startBearingRadians, 0.0);
    }

    public AlignmentPose translated(double dx, double dy) {
        return new AlignmentPose(x + dx, y + dy, bearingRadians, curvature);
    }
}
