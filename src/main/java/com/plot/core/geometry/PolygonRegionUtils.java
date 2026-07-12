package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 闭合区域通用几何工具（点集转多边形、格点采样、包围盒等）
 */
public final class PolygonRegionUtils {
    private PolygonRegionUtils() {
    }

    public static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>();
        if (points != null) {
            for (Vec2d point : points) {
                copy.add(point != null ? point.copy() : new Vec2d(0, 0));
            }
        }
        return copy;
    }

    public static Polygon toPolygon(List<Vec2d> points) {
        return new Polygon(copyPoints(points));
    }

    public static Vec2d computeCentroid(List<Vec2d> points) {
        if (points == null || points.isEmpty()) {
            return new Vec2d(0, 0);
        }
        double sumX = 0.0;
        double sumY = 0.0;
        for (Vec2d point : points) {
            sumX += point.x;
            sumY += point.y;
        }
        return new Vec2d(sumX / points.size(), sumY / points.size());
    }

    public static RectBounds computeBounds(List<Vec2d> points) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        if (points != null) {
            for (Vec2d point : points) {
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minZ = Math.min(minZ, point.y);
                maxZ = Math.max(maxZ, point.y);
            }
        }
        return new RectBounds(minX, maxX, minZ, maxZ);
    }

    public static RectBounds normalizedRectBounds(List<Vec2d> points) {
        return computeBounds(points);
    }

    /**
     * 收集多边形轮廓内的整数格点中心（用于区域填充/采样）
     */
    public static List<Vec2d> collectFootprintCellCenters(List<Vec2d> points) {
        if (points == null || points.size() < 3) {
            return List.of();
        }
        Polygon polygon = toPolygon(points);
        RectBounds bounds = computeBounds(points);
        List<Vec2d> centers = new ArrayList<>();
        int minX = (int) Math.floor(bounds.minX());
        int maxX = (int) Math.ceil(bounds.maxX());
        int minZ = (int) Math.floor(bounds.minZ());
        int maxZ = (int) Math.ceil(bounds.maxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Vec2d center = new Vec2d(x + 0.5, z + 0.5);
                if (polygon.contains(center)) {
                    centers.add(center);
                }
            }
        }
        return centers;
    }

    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, CoordinateTransformer transformer) {
        if (canvasPos == null) {
            return BlockPos.ORIGIN;
        }
        if (transformer != null) {
            Vec2d worldPos = transformer.canvasToMinecraftWorld(canvasPos);
            if (worldPos != null) {
                return new BlockPos((int) worldPos.x, 0, (int) worldPos.y);
            }
        }
        return new BlockPos((int) canvasPos.x, 0, (int) canvasPos.y);
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
