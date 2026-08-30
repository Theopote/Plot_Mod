package com.plot.plugin.road.vertical;

/**
 * 竖曲线变坡点（PVI）：桩号、标高，可选对称竖曲线长度。
 * <p>
 * 首尾 PVI 的 {@code curveLength} 在 v1 中忽略；仅中间变坡点可设竖曲线。
 */
public final class PointOfVerticalIntersection {

    private final double station;
    private final double elevation;
    private final Double curveLength;

    public PointOfVerticalIntersection(double station, double elevation) {
        this(station, elevation, null);
    }

    public PointOfVerticalIntersection(double station, double elevation, Double curveLength) {
        if (!Double.isFinite(station) || !Double.isFinite(elevation)) {
            throw new IllegalArgumentException("station and elevation must be finite");
        }
        if (curveLength != null && (curveLength < 0.0 || !Double.isFinite(curveLength))) {
            throw new IllegalArgumentException("curveLength must be non-negative");
        }
        this.station = station;
        this.elevation = elevation;
        this.curveLength = curveLength;
    }

    public static PointOfVerticalIntersection of(double station, double elevation) {
        return new PointOfVerticalIntersection(station, elevation);
    }

    public static PointOfVerticalIntersection withCurve(double station, double elevation, double curveLength) {
        return new PointOfVerticalIntersection(station, elevation, curveLength);
    }

    public double getStation() {
        return station;
    }

    public double getElevation() {
        return elevation;
    }

    public Double getCurveLength() {
        return curveLength;
    }

    public boolean hasCurve() {
        return curveLength != null && curveLength > 0.0;
    }

    public PointOfVerticalIntersection copy() {
        return new PointOfVerticalIntersection(station, elevation, curveLength);
    }
}
