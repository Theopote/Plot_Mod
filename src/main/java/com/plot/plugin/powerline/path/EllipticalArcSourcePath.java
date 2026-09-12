package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;

import java.util.List;
import java.util.Objects;

/** 椭圆弧参考路径（开放）。 */
public final class EllipticalArcSourcePath implements PowerLineSourcePath {
    private final Vec2d center;
    private final double radiusX;
    private final double radiusY;
    private final double rotation;
    private final double startAngle;
    private final double endAngle;
    private final double cosRotation;
    private final double sinRotation;

    public EllipticalArcSourcePath(
            Vec2d center,
            double radiusX,
            double radiusY,
            double rotation,
            double startAngle,
            double endAngle) {
        this.center = center != null ? center.copy() : new Vec2d(0, 0);
        this.radiusX = Math.max(0.0, radiusX);
        this.radiusY = Math.max(0.0, radiusY);
        this.rotation = rotation;
        this.startAngle = startAngle;
        this.endAngle = endAngle;
        this.cosRotation = Math.cos(rotation);
        this.sinRotation = Math.sin(rotation);
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public double worldLength(ICoordinateService coordinates) {
        return arcLengthTable(coordinates).totalLength();
    }

    @Override
    public Vec2d pointAtStation(double worldStationBlocks, ICoordinateService coordinates) {
        PathArcLengthSampler.ArcLengthTable table = arcLengthTable(coordinates);
        double station = Math.max(0.0, Math.min(table.totalLength(), worldStationBlocks));
        return PathArcLengthSampler.interpolateAtStation(table, station, coordinates);
    }

    @Override
    public Vec2d tangentAtStation(double worldStationBlocks, ICoordinateService coordinates) {
        ICoordinateService coords = Objects.requireNonNull(coordinates, "coordinates");
        double ahead = Math.min(worldLength(coords), worldStationBlocks + 0.5);
        double behind = Math.max(0.0, worldStationBlocks - 0.5);
        Vec2d forward = pointAtStation(ahead, coords).subtract(pointAtStation(behind, coords));
        if (forward.lengthSquared() < 1e-12) {
            return pointAtParameter(0.5).subtract(pointAtParameter(0.49)).normalize();
        }
        return forward.normalize();
    }

    @Override
    public List<Double> mandatoryStations(double cornerAngleThresholdDeg, ICoordinateService coordinates) {
        ICoordinateService coords = Objects.requireNonNull(coordinates, "coordinates");
        return List.of(0.0, worldLength(coords));
    }

    @Override
    public double stationAtPoint(Vec2d canvasPoint, ICoordinateService coordinates) {
        return PathArcLengthSampler.projectPoint(arcLengthTable(coordinates), canvasPoint, coordinates);
    }

    Vec2d pointAtParameter(double parameter) {
        double angle = startAngle + parameter * (endAngle - startAngle);
        double x = radiusX * Math.cos(angle);
        double y = radiusY * Math.sin(angle);
        double rotatedX = x * cosRotation - y * sinRotation;
        double rotatedY = x * sinRotation + y * cosRotation;
        return new Vec2d(center.x + rotatedX, center.y + rotatedY);
    }

    private PathArcLengthSampler.ArcLengthTable arcLengthTable(ICoordinateService coordinates) {
        return PathArcLengthSampler.sample(this::pointAtParameter, 48, 1.0, coordinates);
    }
}
