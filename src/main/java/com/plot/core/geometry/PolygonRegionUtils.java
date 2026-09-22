package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.core.geometry.polygon.PolygonNormalizer;
import com.plot.core.geometry.shapes.Polygon;
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

    /**
     * 统计区域轮廓内的格点数（不分配采样点列表，供 UI 展示用）。
     */
    public static int countFootprintCells(List<Vec2d> outerRing, List<List<Vec2d>> holes) {
        if (outerRing == null || outerRing.size() < 3) {
            return 0;
        }
        Polygon outer = toPolygon(outerRing);
        List<Polygon> holePolygons = new ArrayList<>();
        if (holes != null) {
            for (List<Vec2d> hole : holes) {
                if (hole != null && hole.size() >= 3) {
                    holePolygons.add(toPolygon(hole));
                }
            }
        }
        RectBounds bounds = computeBounds(outerRing, holes);
        int count = 0;
        int minX = (int) Math.floor(bounds.minX());
        int maxX = (int) Math.ceil(bounds.maxX());
        int minZ = (int) Math.floor(bounds.minZ());
        int maxZ = (int) Math.ceil(bounds.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Vec2d center = new Vec2d(x + 0.5, z + 0.5);
                if (!outer.contains(center)) {
                    continue;
                }
                boolean inHole = false;
                for (Polygon holePolygon : holePolygons) {
                    if (holePolygon.contains(center)) {
                        inHole = true;
                        break;
                    }
                }
                if (!inHole) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 统计区域投影到 Minecraft 平面上的方块列数（随当前视图投影变化）。
     */
    public static int countProjectedWorldBlocks(
            List<Vec2d> outerPoints,
            List<List<Vec2d>> holes,
            ICoordinateService coordinates) {
        if (coordinates == null) {
            return countFootprintCells(outerPoints, holes);
        }
        try {
            return countProjectedWorldBlocks(outerPoints, holes, coordinates.captureProjection());
        } catch (RuntimeException ignored) {
            return countFootprintCells(outerPoints, holes);
        }
    }

    public static int countProjectedWorldBlocks(
            List<Vec2d> outerPoints,
            List<List<Vec2d>> holes,
            WorldProjectionSnapshot projection) {
        if (outerPoints == null || outerPoints.size() < 3) {
            return 0;
        }
        if (projection == null || !projection.isValid()) {
            return countFootprintCells(outerPoints, holes);
        }
        try {
            Polygon outer = toPolygon(outerPoints);
            List<Polygon> holePolygons = new ArrayList<>();
            if (holes != null) {
                for (List<Vec2d> hole : holes) {
                    if (hole != null && hole.size() >= 3) {
                        holePolygons.add(toPolygon(hole));
                    }
                }
            }

            double minWx = Double.POSITIVE_INFINITY;
            double minWz = Double.POSITIVE_INFINITY;
            double maxWx = Double.NEGATIVE_INFINITY;
            double maxWz = Double.NEGATIVE_INFINITY;
            for (Vec2d point : outerPoints) {
                if (point == null) {
                    continue;
                }
                Vec2d world = projection.toWorld(point);
                minWx = Math.min(minWx, world.x);
                minWz = Math.min(minWz, world.y);
                maxWx = Math.max(maxWx, world.x);
                maxWz = Math.max(maxWz, world.y);
            }

            int minX = (int) Math.floor(minWx);
            int maxX = (int) Math.ceil(maxWx);
            int minZ = (int) Math.floor(minWz);
            int maxZ = (int) Math.ceil(maxWz);
            long spanX = (long) maxX - minX + 1L;
            long spanZ = (long) maxZ - minZ + 1L;
            if (spanX <= 0 || spanZ <= 0) {
                return 0;
            }
            long cells = spanX * spanZ;
            if (cells > 2_000_000L) {
                return countFootprintCells(outerPoints, holes);
            }

            int count = 0;
            for (int wx = minX; wx <= maxX; wx++) {
                for (int wz = minZ; wz <= maxZ; wz++) {
                    Vec2d canvas = projection.toCanvas(new Vec2d(wx + 0.5, wz + 0.5));
                    if (!outer.contains(canvas)) {
                        continue;
                    }
                    boolean inHole = false;
                    for (Polygon holePolygon : holePolygons) {
                        if (holePolygon.contains(canvas)) {
                            inHole = true;
                            break;
                        }
                    }
                    if (!inHole) {
                        count++;
                    }
                }
            }
            return count;
        } catch (RuntimeException ignored) {
            return countFootprintCells(outerPoints, holes);
        }
    }

    public static List<Vec2d> collectFootprintCellCenters(List<Vec2d> points) {
        return collectFootprintCellCenters(points, List.of());
    }

    /**
     * 收集带孔洞区域轮廓内的格点中心；{@code cellSize} 为画布单位步长（默认 1 = 整格）。
     */
    public static List<Vec2d> collectFootprintCellCenters(List<Vec2d> outerRing, List<List<Vec2d>> holes) {
        return collectFootprintCellCenters(outerRing, holes, 1.0);
    }

    public static List<Vec2d> collectFootprintCellCenters(
            List<Vec2d> outerRing,
            List<List<Vec2d>> holes,
            double cellSize) {
        if (outerRing == null || outerRing.size() < 3) {
            return List.of();
        }
        if (Math.abs(cellSize - 1.0) < 1e-9) {
            return collectUnitFootprintCellCenters(outerRing, holes);
        }
        double step = Math.max(1e-6, cellSize);
        RectBounds bounds = computeBounds(outerRing, holes);
        List<Vec2d> centers = new ArrayList<>();
        double minX = bounds.minX();
        double maxX = bounds.maxX();
        double minZ = bounds.minZ();
        double maxZ = bounds.maxZ();
        double startX = Math.floor(minX / step) * step + step * 0.5;
        double startZ = Math.floor(minZ / step) * step + step * 0.5;
        for (double x = startX; x <= maxX + 1e-9; x += step) {
            for (double z = startZ; z <= maxZ + 1e-9; z += step) {
                Vec2d center = new Vec2d(x, z);
                if (containsPoint(outerRing, holes, center)) {
                    centers.add(center);
                }
            }
        }
        return centers;
    }

    private static List<Vec2d> collectUnitFootprintCellCenters(
            List<Vec2d> outerRing,
            List<List<Vec2d>> holes) {
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
                return new BlockPos(
                    (int) Math.floor(worldPos.x),
                    0,
                    (int) Math.floor(worldPos.y));
            }
        }
        return new BlockPos(
            (int) Math.floor(canvasPos.x),
            0,
            (int) Math.floor(canvasPos.y));
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
