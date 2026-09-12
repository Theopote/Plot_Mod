package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.powerline.TowerRoleClassifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 开放贝塞尔样条参考路径。 */
public final class BezierSourcePath implements PowerLineSourcePath {
    private final List<CubicBezierSegment> segments;
    private final boolean closed;

    public BezierSourcePath(List<CubicBezierSegment> segments, boolean closed) {
        this.segments = List.copyOf(segments);
        this.closed = closed;
    }

    public List<CubicBezierSegment> segments() {
        return segments;
    }

    @Override
    public boolean isClosed() {
        return closed;
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
        double ahead = Math.min(length, worldStationBlocks + 0.5);
        double behind = Math.max(0.0, worldStationBlocks - 0.5);
        Vec2d forward = pointAtStation(ahead, coords).subtract(pointAtStation(behind, coords));
        if (forward.lengthSquared() < 1e-12) {
            return segments.getFirst().tangentAt(0.0);
        }
        return forward.normalize();
    }

    @Override
    public List<Double> mandatoryStations(double cornerAngleThresholdDeg, ICoordinateService coordinates) {
        if (closed) {
            return List.of();
        }
        ICoordinateService coords = Objects.requireNonNull(coordinates, "coordinates");
        double length = worldLength(coords);
        if (length <= 1e-12) {
            return List.of(0.0);
        }
        List<Double> stations = new ArrayList<>();
        stations.add(0.0);
        for (int i = 1; i < segments.size(); i++) {
            CubicBezierSegment previous = segments.get(i - 1);
            CubicBezierSegment current = segments.get(i);
            Vec2d incoming = previous.tangentAt(1.0);
            Vec2d outgoing = current.tangentAt(0.0);
            double deflection = TowerRoleClassifier.computeDeflectionAngle(incoming, outgoing);
            if (deflection > cornerAngleThresholdDeg) {
                stations.add(stationAtPoint(previous.anchor2(), coords));
            }
        }
        stations.add(length);
        return stations;
    }

    @Override
    public double stationAtPoint(Vec2d canvasPoint, ICoordinateService coordinates) {
        return PathArcLengthSampler.projectPoint(arcLengthTable(coordinates), canvasPoint, coordinates);
    }

    private PathArcLengthSampler.ArcLengthTable arcLengthTable(ICoordinateService coordinates) {
        return PathArcLengthSampler.sample(
            this::pointAtParameter,
            segments.size(),
            segments.size(),
            coordinates);
    }

    private Vec2d pointAtParameter(double parameter) {
        if (segments.isEmpty()) {
            return new Vec2d(0, 0);
        }
        int index = (int) Math.floor(parameter);
        if (index >= segments.size()) {
            index = segments.size() - 1;
        }
        double localT = parameter - index;
        if (index == segments.size() - 1 && localT >= 1.0 - 1e-9) {
            localT = 1.0;
        } else {
            localT = Math.max(0.0, Math.min(1.0, localT));
        }
        return segments.get(index).pointAt(localT);
    }
}
