package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.WorldProjectionMath;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 折线参考路径（直线、折线、自由绘）。 */
public final class PolylineSourcePath implements PowerLineSourcePath {
    private final List<Vec2d> points;
    private final boolean closed;

    private PolylineSourcePath(List<Vec2d> points, boolean closed) {
        this.points = copyPoints(points);
        this.closed = closed;
    }

    public static PolylineSourcePath open(List<Vec2d> points) {
        return new PolylineSourcePath(points, false);
    }

    public static PolylineSourcePath of(List<Vec2d> points, boolean closed) {
        return new PolylineSourcePath(points, closed);
    }

    public List<Vec2d> points() {
        return points;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public double worldLength(ICoordinateService coordinates) {
        return WorldProjectionMath.pathWorldLength(coordinates, polylinePoints());
    }

    @Override
    public Vec2d pointAtStation(double worldStationBlocks, ICoordinateService coordinates) {
        double length = worldLength(coordinates);
        double station = ClosedPathGeometry.normalizeStation(this, worldStationBlocks, length);
        return WorldProjectionMath.canvasPointAtWorldStation(
            coordinates,
            polylinePoints(),
            station);
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
            forward = pointAtStation(length, coords).subtract(pointAtStation(0.0, coords));
        }
        if (forward.lengthSquared() < 1e-12) {
            return new Vec2d(1, 0);
        }
        return forward.normalize();
    }

    @Override
    public List<Double> mandatoryStations(double cornerAngleThresholdDeg, ICoordinateService coordinates) {
        ICoordinateService coords = Objects.requireNonNull(coordinates, "coordinates");
        if (closed) {
            return closedCornerStations(cornerAngleThresholdDeg, coords);
        }
        List<Vec2d> polyline = polylinePoints();
        if (polyline.isEmpty()) {
            return List.of();
        }
        if (polyline.size() == 1) {
            return List.of(0.0);
        }
        List<Vec2d> mandatory = PowerPoleLayoutUtils.mandatoryPolePoints(polyline, cornerAngleThresholdDeg);
        List<Double> stations = new ArrayList<>(mandatory.size());
        for (Vec2d point : mandatory) {
            stations.add(stationAtPoint(point, coords));
        }
        return stations;
    }

    @Override
    public double stationAtPoint(Vec2d canvasPoint, ICoordinateService coordinates) {
        return PowerPoleLayoutUtils.computeStationingOnPolyline(
            polylinePoints(),
            canvasPoint,
            coordinates);
    }

    private List<Double> closedCornerStations(double cornerAngleThresholdDeg, ICoordinateService coordinates) {
        if (points.size() < 3) {
            return List.of();
        }
        int count = points.size();
        List<Double> stations = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vec2d previous = points.get((i - 1 + count) % count);
            Vec2d current = points.get(i);
            Vec2d next = points.get((i + 1) % count);
            if (PowerPoleLayoutUtils.isCorner(
                List.of(previous, current, next),
                1,
                cornerAngleThresholdDeg)) {
                stations.add(stationAtPoint(current, coordinates));
            }
        }
        return stations;
    }

    private List<Vec2d> polylinePoints() {
        if (!closed || points.size() < 2) {
            return points;
        }
        if (points.getFirst().distance(points.getLast()) <= 1e-6) {
            return points;
        }
        List<Vec2d> loop = new ArrayList<>(points.size() + 1);
        loop.addAll(points);
        loop.add(points.getFirst().copy());
        return loop;
    }

    private static List<Vec2d> copyPoints(List<Vec2d> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Vec2d> copy = new ArrayList<>(source.size());
        for (Vec2d point : source) {
            if (point != null) {
                copy.add(point.copy());
            }
        }
        return copy;
    }
}
