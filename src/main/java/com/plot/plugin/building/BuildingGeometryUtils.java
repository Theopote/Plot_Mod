package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑几何工具（等距采样、闭合多边形偏移等）
 */
public final class BuildingGeometryUtils {
    private static final double TOLERANCE = 1e-3;

    private BuildingGeometryUtils() {
    }

    public static List<Shape> findAdoptableFootprints(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return List.of();
        }
        List<Shape> footprints = new ArrayList<>();
        for (Shape shape : shapes) {
            if (isAdoptableFootprint(shape)) {
                footprints.add(shape);
            }
        }
        return footprints;
    }

    public static boolean isAdoptableFootprint(Shape shape) {
        if (shape instanceof Polygon polygon) {
            return polygon.getPoints().size() >= 3;
        }
        return shape instanceof RectangleShape;
    }

    public static List<Vec2d> extractFootprintPoints(Shape shape) {
        if (shape == null) {
            return List.of();
        }
        List<Vec2d> points = shape.getPoints();
        if (points == null || points.size() < 3) {
            return List.of();
        }
        return copyPoints(points);
    }

    public static boolean detectRectangular(List<Vec2d> points) {
        return isAxisAlignedRectangle(points, TOLERANCE);
    }

    /**
     * 是否满足坡屋顶生成前提：4 点且边与世界坐标轴平行（旋转矩形返回 false）。
     */
    public static boolean isSlopedRoofEligible(List<Vec2d> points) {
        return isAxisAlignedRectangle(points, TOLERANCE);
    }

    public static boolean isAxisAlignedRectangle(List<Vec2d> points, double tolerance) {
        if (points == null || points.size() != 4) {
            return false;
        }

        for (int i = 0; i < 4; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % 4);
            Vec2d edge = end.subtract(start);
            boolean horizontal = Math.abs(edge.y) <= tolerance;
            boolean vertical = Math.abs(edge.x) <= tolerance;
            if (!horizontal && !vertical) {
                return false;
            }
            if (edge.length() <= tolerance) {
                return false;
            }
        }
        return true;
    }

    public static Vec2d computeCentroid(List<Vec2d> points) {
        return PolygonRegionUtils.computeCentroid(points);
    }

    public static Polygon toPolygon(List<Vec2d> points) {
        return PolygonRegionUtils.toPolygon(points);
    }

    public static List<Vec2d> offsetClosedPolygon(List<Vec2d> points, double distance) {
        if (points == null || points.size() < 3) {
            return new ArrayList<>();
        }

        int n = points.size();
        List<LineShape> offsetSegments = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get((i + 1) % n);
            Vec2d direction = p2.subtract(p1);
            double length = direction.length();
            if (length < 1e-8) {
                continue;
            }
            Vec2d normal = new Vec2d(-direction.y, direction.x).normalize();
            Vec2d offset = normal.multiply(distance);
            offsetSegments.add(new LineShape(p1.add(offset), p2.add(offset)));
        }

        List<Vec2d> offsetPoints = new ArrayList<>();
        for (int i = 0; i < offsetSegments.size(); i++) {
            LineShape seg1 = offsetSegments.get(i);
            LineShape seg2 = offsetSegments.get((i + 1) % offsetSegments.size());
            List<Vec2d> intersections = seg1.getIntersectionPoints(seg2);
            if (!intersections.isEmpty()) {
                offsetPoints.add(intersections.getFirst());
            } else {
                offsetPoints.add(seg1.getEnd());
            }
        }
        return offsetPoints;
    }

    public static List<Vec2d> offsetInward(List<Vec2d> points, double distance) {
        if (points == null || points.size() < 3 || distance <= 0) {
            return copyPoints(points);
        }
        double sign = BuildingFootprint.signedArea(points) >= 0 ? -1.0 : 1.0;
        return offsetClosedPolygon(points, sign * distance);
    }

    public static List<Vec2d> sampleAlongClosedPath(List<Vec2d> points, double spacing, double skipNearEndsDistance) {
        List<Vec2d> result = new ArrayList<>();
        if (points == null || points.size() < 3 || spacing <= 0) {
            return result;
        }

        double totalLength = calculateClosedPathLength(points);
        if (totalLength <= 2 * skipNearEndsDistance) {
            return result;
        }

        double distance = skipNearEndsDistance;
        while (distance <= totalLength - skipNearEndsDistance) {
            Vec2d point = pointAtClosedDistance(points, distance);
            if (point != null) {
                result.add(point);
            }
            distance += spacing;
        }
        return result;
    }

    public static List<WallSample> sampleAlongWallSegments(List<Vec2d> points, double spacing) {
        List<WallSample> samples = new ArrayList<>();
        if (points == null || points.size() < 3 || spacing <= 0) {
            return samples;
        }

        int n = points.size();
        for (int segmentIndex = 0; segmentIndex < n; segmentIndex++) {
            Vec2d start = points.get(segmentIndex);
            Vec2d end = points.get((segmentIndex + 1) % n);
            double segmentLength = start.distance(end);
            if (segmentLength < spacing * 0.5) {
                continue;
            }

            double distance = spacing * 0.5;
            while (distance <= segmentLength - spacing * 0.5) {
                double t = distance / segmentLength;
                Vec2d point = start.lerp(end, t);
                Vec2d tangent = end.subtract(start).normalize();
                Vec2d inwardNormal = leftNormal(tangent);
                if (BuildingFootprint.signedArea(points) >= 0) {
                    inwardNormal = inwardNormal.multiply(-1);
                }
                samples.add(new WallSample(segmentIndex, point, tangent, inwardNormal));
                distance += spacing;
            }
        }
        return samples;
    }

    public static double calculateClosedPathLength(List<Vec2d> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double length = 0.0;
        for (int i = 0; i < points.size(); i++) {
            length += points.get(i).distance(points.get((i + 1) % points.size()));
        }
        return length;
    }

    public static Vec2d pointAtClosedDistance(List<Vec2d> points, double targetDistance) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (targetDistance <= 0) {
            return points.getFirst().copy();
        }

        double accumulated = 0.0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % n);
            double segmentLength = start.distance(end);
            if (segmentLength < 1e-9) {
                continue;
            }
            if (accumulated + segmentLength >= targetDistance) {
                double t = (targetDistance - accumulated) / segmentLength;
                return start.lerp(end, t);
            }
            accumulated += segmentLength;
        }
        return points.getFirst().copy();
    }

    public static Vec2d pointOnWallSegment(List<Vec2d> points, int segmentIndex, double positionRatio) {
        if (points == null || points.size() < 3) {
            return null;
        }
        int n = points.size();
        int index = Math.floorMod(segmentIndex, n);
        double ratio = Math.max(0.0, Math.min(1.0, positionRatio));
        Vec2d start = points.get(index);
        Vec2d end = points.get((index + 1) % n);
        return start.lerp(end, ratio);
    }

    public static Vec2d leftNormal(Vec2d tangent) {
        if (tangent == null || tangent.length() < 1e-9) {
            return new Vec2d(0, 1);
        }
        Vec2d normalized = tangent.normalize();
        return new Vec2d(-normalized.y, normalized.x);
    }

    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, CoordinateTransformer transformer) {
        return PolygonRegionUtils.canvasToBlockXZ(canvasPos, transformer);
    }

    public static RectBounds computeBounds(List<Vec2d> points) {
        return toLocalBounds(PolygonRegionUtils.computeBounds(points));
    }

    public static String resolveBlockId(String material) {
        if (material == null || material.isBlank()) {
            return BuildingFootprint.DEFAULT_FOUNDATION_FILL;
        }
        return material.trim();
    }

    public static List<Vec2d> copyPoints(List<Vec2d> points) {
        return PolygonRegionUtils.copyPoints(points);
    }

    /**
     * 收集多边形轮廓内的整数格点（中心点采样，用于楼板/屋顶填充）
     */
    public static List<Vec2d> collectFootprintCellCenters(List<Vec2d> points) {
        return PolygonRegionUtils.collectFootprintCellCenters(points);
    }

    public static RectBounds normalizedRectBounds(List<Vec2d> points) {
        return toLocalBounds(PolygonRegionUtils.normalizedRectBounds(points));
    }

    private static RectBounds toLocalBounds(PolygonRegionUtils.RectBounds bounds) {
        return new RectBounds(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
    }

    public record WallSample(int segmentIndex, Vec2d point, Vec2d tangent, Vec2d inwardNormal) {
    }

    public record RectBounds(double minX, double maxX, double minZ, double maxZ) {
        public double width() {
            return maxX - minX;
        }

        public double depth() {
            return maxZ - minZ;
        }

        public Vec2d center() {
            return new Vec2d((minX + maxX) / 2.0, (minZ + maxZ) / 2.0);
        }
    }
}
