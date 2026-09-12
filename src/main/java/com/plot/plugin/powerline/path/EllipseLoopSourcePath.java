package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;

import java.util.List;
import java.util.Objects;

/** 椭圆/圆闭合参考路径（参数化，不预离散）。 */
public final class EllipseLoopSourcePath implements PowerLineSourcePath {
    private final Vec2d center;
    private final double radiusX;
    private final double radiusY;
    private final double rotation;

    public EllipseLoopSourcePath(Vec2d center, double radiusX, double radiusY, double rotation) {
        this.center = center != null ? center.copy() : new Vec2d(0, 0);
        this.radiusX = Math.max(0.0, radiusX);
        this.radiusY = Math.max(0.0, radiusY);
        this.rotation = rotation;
    }

    public static EllipseLoopSourcePath circle(Vec2d center, double radius) {
        return new EllipseLoopSourcePath(center, radius, radius, 0.0);
    }

    public Vec2d center() {
        return center.copy();
    }

    public double radiusX() {
        return radiusX;
    }

    public double radiusY() {
        return radiusY;
    }

    public double rotation() {
        return rotation;
    }

    @Override
    public boolean isClosed() {
        return true;
    }

    @Override
    public double worldLength(ICoordinateService coordinates) {
        return arcLengthTable(coordinates).totalLength();
    }

    @Override
    public Vec2d pointAtStation(double worldStationBlocks, ICoordinateService coordinates) {
        PathArcLengthSampler.ArcLengthTable table = arcLengthTable(coordinates);
        double station = ClosedPathGeometry.normalizeStation(this, worldStationBlocks, table.totalLength());
        return PathArcLengthSampler.interpolateAtStation(table, station, coordinates);
    }

    @Override
    public Vec2d tangentAtStation(double worldStationBlocks, ICoordinateService coordinates) {
        ICoordinateService coords = Objects.requireNonNull(coordinates, "coordinates");
        double length = worldLength(coords);
        if (length <= 1e-12) {
            return new Vec2d(1, 0);
        }
        double ahead = ClosedPathGeometry.normalizeStation(this, worldStationBlocks + 0.5, length);
        double behind = ClosedPathGeometry.normalizeStation(this, worldStationBlocks - 0.5, length);
        Vec2d forward = pointAtStation(ahead, coords).subtract(pointAtStation(behind, coords));
        if (forward.lengthSquared() < 1e-12) {
            return pointAtParameter(0.0).subtract(pointAtParameter(0.01)).normalize();
        }
        return forward.normalize();
    }

    @Override
    public List<Double> mandatoryStations(double cornerAngleThresholdDeg, ICoordinateService coordinates) {
        return List.of();
    }

    @Override
    public double stationAtPoint(Vec2d canvasPoint, ICoordinateService coordinates) {
        return PathArcLengthSampler.projectPoint(arcLengthTable(coordinates), canvasPoint, coordinates);
    }

    Vec2d pointAtParameter(double parameter) {
        double angle = 2.0 * Math.PI * parameter;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double x = radiusX * Math.cos(angle);
        double y = radiusY * Math.sin(angle);
        double rotatedX = x * cos - y * sin;
        double rotatedY = x * sin + y * cos;
        return new Vec2d(center.x + rotatedX, center.y + rotatedY);
    }

    private PathArcLengthSampler.ArcLengthTable arcLengthTable(ICoordinateService coordinates) {
        return PathArcLengthSampler.sample(
            this::pointAtParameter,
            64,
            1.0,
            coordinates);
    }
}
