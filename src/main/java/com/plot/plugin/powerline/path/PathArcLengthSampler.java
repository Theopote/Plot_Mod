package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleFunction;

/**
 * 将参数化路径按世界距离采样，供里程查询与插值。
 */
final class PathArcLengthSampler {
    private static final int SAMPLES_PER_UNIT = 4;
    private static final int MIN_SAMPLES_PER_SEGMENT = 8;
    private static final int MAX_SAMPLES_PER_SEGMENT = 256;

    private PathArcLengthSampler() {
    }

    static ArcLengthTable sample(
            DoubleFunction<Vec2d> pointAtParameter,
            int segmentCount,
            double parameterLength,
            ICoordinateService coordinates) {
        Objects.requireNonNull(pointAtParameter, "pointAtParameter");
        ICoordinateService coords = Objects.requireNonNull(coordinates, "coordinates");
        if (segmentCount <= 0 || parameterLength <= 0.0) {
            Vec2d point = pointAtParameter.apply(0.0);
            return new ArcLengthTable(
                new double[]{0.0},
                new Vec2d[]{point != null ? point.copy() : new Vec2d(0, 0)},
                0.0);
        }

        int samples = Math.min(
            MAX_SAMPLES_PER_SEGMENT,
            Math.max(MIN_SAMPLES_PER_SEGMENT, segmentCount * SAMPLES_PER_UNIT * 8));
        List<Double> stations = new ArrayList<>(samples + 1);
        List<Vec2d> points = new ArrayList<>(samples + 1);
        stations.add(0.0);
        Vec2d first = pointAtParameter.apply(0.0);
        points.add(first != null ? first.copy() : new Vec2d(0, 0));

        double total = 0.0;
        Vec2d previous = points.getFirst();
        for (int i = 1; i <= samples; i++) {
            double parameter = parameterLength * i / samples;
            Vec2d current = pointAtParameter.apply(parameter);
            if (current == null) {
                continue;
            }
            double step = coords.projectedDistance(previous, current);
            if (step > 1e-12) {
                total += step;
                stations.add(total);
                points.add(current.copy());
                previous = current;
            }
        }
        return new ArcLengthTable(
            toArray(stations),
            points.toArray(new Vec2d[0]),
            total);
    }

    static Vec2d interpolateAtStation(ArcLengthTable table, double worldStation, ICoordinateService coordinates) {
        if (table.points.length == 0) {
            return new Vec2d(0, 0);
        }
        if (table.totalLength <= 1e-12 || worldStation <= 0.0) {
            return table.points[0].copy();
        }
        if (worldStation >= table.totalLength - 1e-9) {
            return table.points[table.points.length - 1].copy();
        }
        int index = findStationIndex(table.stations, worldStation);
        double fromStation = table.stations[index];
        double toStation = table.stations[index + 1];
        Vec2d fromPoint = table.points[index];
        Vec2d toPoint = table.points[index + 1];
        double span = toStation - fromStation;
        if (span <= 1e-12) {
            return fromPoint.copy();
        }
        double t = (worldStation - fromStation) / span;
        return fromPoint.lerp(toPoint, t);
    }

    static double projectPoint(ArcLengthTable table, Vec2d canvasPoint, ICoordinateService coordinates) {
        if (table.points.length == 0 || canvasPoint == null) {
            return 0.0;
        }
        if (table.points.length == 1) {
            return 0.0;
        }
        double bestStation = 0.0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < table.points.length - 1; i++) {
            Vec2d a = table.points[i];
            Vec2d b = table.points[i + 1];
            double segWorldLen = coordinates.projectedDistance(a, b);
            if (segWorldLen < 1e-12) {
                continue;
            }
            Vec2d ab = b.subtract(a);
            double t = canvasPoint.subtract(a).dot(ab) / ab.lengthSquared();
            t = Math.max(0.0, Math.min(1.0, t));
            Vec2d projected = a.lerp(b, t);
            double distance = coordinates.projectedDistance(projected, canvasPoint);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestStation = table.stations[i] + segWorldLen * t;
            }
        }
        return bestStation;
    }

    private static int findStationIndex(double[] stations, double worldStation) {
        int low = 0;
        int high = stations.length - 2;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (stations[mid] <= worldStation) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private static double[] toArray(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    record ArcLengthTable(double[] stations, Vec2d[] points, double totalLength) {
    }
}
