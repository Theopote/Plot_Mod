package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonNormalizer;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.api.world.ICoordinateService;
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

    /**
     * 规范化区域外轮廓：去连续重复点，去掉与起点重合的闭合重复点，至少保留 3 个顶点。
     * 未显式闭合的折线保留为开放顶点序列，后续面积/填充按首尾相连处理。
     */
    public static List<Vec2d> normalizeRegionOutline(List<Vec2d> points) {
        return PolygonNormalizer.normalizeOutline(points);
    }

    public static Polygon toPolygon(List<Vec2d> points) {
        return new Polygon(copyPoints(points));
    }

    public static boolean containsPoint(List<Vec2d> outerRing, List<List<Vec2d>> holes, Vec2d point) {
        if (point == null || outerRing == null || outerRing.size() < 3) {
            return false;
        }
        if (!toPolygon(outerRing).contains(point)) {
            return false;
        }
        if (holes == null || holes.isEmpty()) {
            return true;
        }
        for (List<Vec2d> hole : holes) {
            if (hole != null && hole.size() >= 3 && toPolygon(hole).contains(point)) {
                return false;
            }
        }
        return true;
    }

    public static boolean containsPoint(RegionGeometry geometry, Vec2d point) {
        if (geometry == null) {
            return false;
        }
        return geometry.contains(point);
    }

    public static double computeSignedArea(List<Vec2d> outerRing, List<List<Vec2d>> holes) {
        double area = signedAreaOfRing(outerRing);
        if (holes != null) {
            for (List<Vec2d> hole : holes) {
                area -= Math.abs(signedAreaOfRing(hole));
            }
        }
        return area;
    }

    public static double computeSignedArea(RegionGeometry geometry) {
        if (geometry == null) {
            return 0.0;
        }
        return geometry.signedArea();
    }

    public static double signedAreaOfRing(List<Vec2d> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        double area = 0.0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get((i + 1) % n);
            area += a.x * b.y - b.x * a.y;
        }
        return area / 2.0;
    }

    public static RectBounds computeBounds(List<Vec2d> outerRing, List<List<Vec2d>> holes) {
        RectBounds outer = computeBounds(outerRing);
        if (holes == null || holes.isEmpty()) {
            return outer;
        }
        double minX = outer.minX();
        double maxX = outer.maxX();
        double minZ = outer.minZ();
        double maxZ = outer.maxZ();
        for (List<Vec2d> hole : holes) {
            RectBounds holeBounds = computeBounds(hole);
            minX = Math.min(minX, holeBounds.minX());
            maxX = Math.max(maxX, holeBounds.maxX());
            minZ = Math.min(minZ, holeBounds.minZ());
            maxZ = Math.max(maxZ, holeBounds.maxZ());
        }
        return new RectBounds(minX, maxX, minZ, maxZ);
    }

    public static RectBounds computeBounds(RegionGeometry geometry) {
        if (geometry == null) {
            return new RectBounds(0, 0, 0, 0);
        }
        return geometry.bounds();
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

    public static List<Vec2d> collectFootprintCellCenters(List<Vec2d> points) {
        return collectFootprintCellCenters(points, List.of());
    }

    /**
     * 收集带孔洞区域轮廓内的整数格点中心。
     */
    public static List<Vec2d> collectFootprintCellCenters(List<Vec2d> outerRing, List<List<Vec2d>> holes) {
        if (outerRing == null || outerRing.size() < 3) {
            return List.of();
        }
        RectBounds bounds = computeBounds(outerRing, holes);
        List<Vec2d> centers = new ArrayList<>();
        int minX = (int) Math.floor(bounds.minX());
        int maxX = (int) Math.ceil(bounds.maxX());
        int minZ = (int) Math.floor(bounds.minZ());
        int maxZ = (int) Math.ceil(bounds.maxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Vec2d center = new Vec2d(x + 0.5, z + 0.5);
                if (containsPoint(outerRing, holes, center)) {
                    centers.add(center);
                }
            }
        }
        return centers;
    }

    public static List<Vec2d> collectFootprintCellCenters(RegionGeometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return List.of();
        }
        return geometry.collectFootprintCellCenters();
    }

    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, ICoordinateService transformer) {
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
