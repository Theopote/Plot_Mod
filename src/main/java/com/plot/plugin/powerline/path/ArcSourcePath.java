package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;

import java.util.List;
import java.util.Objects;

/** 圆弧参考路径（开放）。 */
public final class ArcSourcePath implements PowerLineSourcePath {
    private final Vec2d center;
    private final double radius;
    private final double startAngle;
    private final double endAngle;

    public ArcSourcePath(Vec2d center, double radius, double startAngle, double endAngle) {
        this.center = center != null ? center.copy() : new Vec2d(0, 0);
        this.radius = Math.max(0.0, radius);
        this.startAngle = startAngle;
        this.endAngle = endAngle;
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
        double length = worldLength(coords);
        return List.of(0.0, length);
    }

    @Override
    public double stationAtPoint(Vec2d canvasPoint, ICoordinateService coordinates) {
        return PathArcLengthSampler.projectPoint(arcLengthTable(coordinates), canvasPoint, coordinates);
    }

    Vec2d pointAtParameter(double parameter) {
        double angle = startAngle + parameter * (endAngle - startAngle);
        return new Vec2d(
            center.x + radius * Math.cos(angle),
            center.y + radius * Math.sin(angle));
    }

    private PathArcLengthSampler.ArcLengthTable arcLengthTable(ICoordinateService coordinates) {
        return PathArcLengthSampler.sample(this::pointAtParameter, 48, 1.0, coordinates);
    }
}
