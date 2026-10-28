package com.plot.plugin.earthwork.geometry;
import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.shapes.AnnotationShape;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.EllipticalArcShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.TextShape;
import com.plot.core.model.Shape;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 土方几何工具
 */
public final class EarthworkGeometryUtils {
    private static final double MIN_REGION_AREA = 1e-6;

    private EarthworkGeometryUtils() {
    }

    public static List<Shape> findAdoptableRegions(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return List.of();
        }
        List<Shape> regions = new ArrayList<>();
        for (Shape shape : shapes) {
            if (isAdoptableRegion(shape)) {
                regions.add(shape);
            }
        }
        return regions;
    }

    public static boolean isAdoptableRegion(Shape shape) {
        List<Vec2d> points = extractRegionPoints(shape);
        return points.size() >= 3 && hasMeaningfulArea(points);
    }

    /**
     * 从画布图形提取整平区域外轮廓；开放折线/样条会自动按首尾相连作为封闭区域。
     */
    public static List<Vec2d> extractRegionPoints(Shape shape) {
        if (shape == null) {
            return List.of();
        }
        return PolygonRegionUtils.normalizeRegionOutline(extractRawBoundaryPoints(shape));
    }

    static List<Vec2d> extractRawBoundaryPoints(Shape shape) {
        if (shape == null || isExcludedRegionShape(shape)) {
            return List.of();
        }
        switch (shape) {
            case PolylineShape polyline -> {
                return PolygonRegionUtils.copyPoints(polyline.getPoints());
            }
            case Polygon polygon -> {
                return PolygonRegionUtils.copyPoints(polygon.getPoints());
            }
            case FreeDrawPath freeDraw -> {
                return PolygonRegionUtils.copyPoints(freeDraw.getPoints());
            }
            case BezierCurveShape bezier -> {
                List<Vec2d> curvePoints = bezier.getCurvePoints();
                return curvePoints != null ? PolygonRegionUtils.copyPoints(curvePoints) : List.of();
            }
            default -> {
            }
        }
        if (shape instanceof RectangleShape
            || shape instanceof CircleShape
            || shape instanceof EllipseShape) {
            return PolygonRegionUtils.copyPoints(shape.getPoints());
        }
        if (shape instanceof ArcShape || shape instanceof EllipticalArcShape) {
            return List.of();
        }

        try {
            List<Vec2d> points = shape.getPoints();
            if (points != null && points.size() >= 3 && isClosedPointLoop(points)) {
                return PolygonRegionUtils.copyPoints(points);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return List.of();
    }

    private static boolean isExcludedRegionShape(Shape shape) {
        return shape instanceof TextShape
            || shape instanceof AnnotationShape
            || shape instanceof LineShape;
    }

    private static boolean isClosedPointLoop(List<Vec2d> points) {
        if (points == null || points.size() < 3) {
            return false;
        }
        Vec2d first = points.getFirst();
        Vec2d last = points.getLast();
        return first != null && last != null && first.distance(last) <= 1e-6;
    }

    private static boolean hasMeaningfulArea(List<Vec2d> points) {
        return Math.abs(GradingRegion.signedArea(points)) > MIN_REGION_AREA;
    }

    /** 孔洞添加校验结果。 */
    public enum HoleAddIssue {
        NONE(""),
        NO_VALID_SELECTION("plugin.earthwork.geometry_no_valid_selection"),
        OUTSIDE_OUTER("plugin.earthwork.geometry_hole_outside_outer"),
        TOO_SMALL("plugin.earthwork.geometry_hole_too_small"),
        TOO_LARGE("plugin.earthwork.geometry_hole_too_large");

        private final String statusKey;

        HoleAddIssue(String statusKey) {
            this.statusKey = statusKey;
        }

        public String statusKey() {
            return statusKey;
        }
    }

    /**
     * 校验孔洞是否可加入外环：全部顶点须落在外环内（且不落在已有孔洞内），
     * 面积不能过小，也不能接近吞掉整个外环。
     */
    public static HoleAddIssue validateHoleForRegion(
            List<Vec2d> outerPoints,
            List<List<Vec2d>> existingHoles,
            List<Vec2d> holePoints) {
        if (outerPoints == null || outerPoints.size() < 3) {
            return HoleAddIssue.NO_VALID_SELECTION;
        }
        if (holePoints == null || holePoints.size() < 3) {
            return HoleAddIssue.NO_VALID_SELECTION;
        }
        if (!hasMeaningfulArea(holePoints)) {
            return HoleAddIssue.TOO_SMALL;
        }
        double outerArea = Math.abs(GradingRegion.signedArea(outerPoints));
        double holeArea = Math.abs(GradingRegion.signedArea(holePoints));
        if (holeArea >= outerArea * 0.95) {
            return HoleAddIssue.TOO_LARGE;
        }
        List<List<Vec2d>> holes = existingHoles != null ? existingHoles : List.of();
        for (Vec2d point : holePoints) {
            if (point == null || !PolygonRegionUtils.containsPoint(outerPoints, holes, point)) {
                return HoleAddIssue.OUTSIDE_OUTER;
            }
        }
        return HoleAddIssue.NONE;
    }

    /**
     * 从画布多选中挑选最合适的孔洞轮廓：在全部合法候选里取面积最小者。
     * 避免「第一个封闭图形是外环/区外图形」时直接失败、忽略真正的内环选区。
     */
    public static List<Vec2d> findBestHoleFromSelection(
            List<Shape> selectedShapes,
            List<Vec2d> outerPoints,
            List<List<Vec2d>> existingHoles) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            return List.of();
        }
        List<Vec2d> best = List.of();
        double bestArea = Double.POSITIVE_INFINITY;
        for (Shape shape : selectedShapes) {
            List<Vec2d> hole = extractRegionPoints(shape);
            if (validateHoleForRegion(outerPoints, existingHoles, hole) != HoleAddIssue.NONE) {
                continue;
            }
            double area = Math.abs(GradingRegion.signedArea(hole));
            if (area < bestArea) {
                bestArea = area;
                best = hole;
            }
        }
        return best;
    }

    /** 解释为何选区未能生成孔洞（用于状态栏）。 */
    public static HoleAddIssue diagnoseHoleSelection(
            List<Shape> selectedShapes,
            List<Vec2d> outerPoints,
            List<List<Vec2d>> existingHoles) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            return HoleAddIssue.NO_VALID_SELECTION;
        }
        HoleAddIssue fallback = HoleAddIssue.NO_VALID_SELECTION;
        boolean sawClosed = false;
        for (Shape shape : selectedShapes) {
            List<Vec2d> hole = extractRegionPoints(shape);
            if (hole.size() < 3) {
                continue;
            }
            sawClosed = true;
            HoleAddIssue issue = validateHoleForRegion(outerPoints, existingHoles, hole);
            if (issue == HoleAddIssue.NONE) {
                return HoleAddIssue.NONE;
            }
            fallback = issue;
        }
        return sawClosed ? fallback : HoleAddIssue.NO_VALID_SELECTION;
    }

    public static Polygon toPolygon(List<Vec2d> points) {
        return PolygonRegionUtils.toPolygon(points);
    }

    public static List<Vec2d> collectFootprintCellCenters(List<Vec2d> points) {
        return PolygonRegionUtils.collectFootprintCellCenters(points);
    }

    public static List<Vec2d> collectPreviewGridCenters(List<Vec2d> points, int previewGridSize) {
        return collectSampleCenters(points, previewGridSize);
    }

    public static boolean matchesPreviewGrid(Vec2d center, int previewGridSize) {
        if (center == null) {
            return false;
        }
        if (previewGridSize <= 1) {
            return true;
        }
        int blockX = (int) Math.floor(center.x);
        int blockZ = (int) Math.floor(center.y);
        return blockX % previewGridSize == 0 && blockZ % previewGridSize == 0;
    }

    public static List<Vec2d> collectSampleCenters(List<Vec2d> points, int gridSize) {
        List<Vec2d> allCenters = collectFootprintCellCenters(points);
        if (gridSize <= 1 || allCenters.isEmpty()) {
            return allCenters;
        }

        List<Vec2d> sampled = new ArrayList<>();
        for (Vec2d center : allCenters) {
            int blockX = (int) Math.floor(center.x);
            int blockZ = (int) Math.floor(center.y);
            if (blockX % gridSize == 0 && blockZ % gridSize == 0) {
                sampled.add(center);
            }
        }
        return sampled.isEmpty() ? allCenters : sampled;
    }

    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, ICoordinateService transformer) {
        return PolygonRegionUtils.canvasToBlockXZ(canvasPos, transformer);
    }

    public static String resolveFillBlockId(String material) {
        if (material == null || material.isBlank()) {
            return GradingRegion.DEFAULT_FILL_MATERIAL;
        }
        return material.trim();
    }

    public static final String EXCAVATION_BLOCK_ID = "minecraft:air";

    /**
     * 挖方裸露地表材质；空表示不替换设计面标高处的现有方块。
     */
    public static String resolveCutSurfaceBlockId(String material) {
        if (material == null || material.isBlank()) {
            return null;
        }
        return material.trim();
    }

    public static PolygonRegionUtils.RectBounds computeBounds(List<Vec2d> points) {
        return PolygonRegionUtils.computeBounds(points);
    }

    public static Vec2d computeCentroid(List<Vec2d> points) {
        return PolygonRegionUtils.computeCentroid(points);
    }

    public static boolean containsCanvasPoint(List<Vec2d> outerPoints, Vec2d canvasPoint) {
        return containsCanvasPoint(com.plot.core.geometry.RegionGeometry.of(outerPoints), canvasPoint);
    }

    public static boolean containsCanvasPoint(
            List<Vec2d> outerPoints,
            List<List<Vec2d>> holes,
            Vec2d canvasPoint) {
        return containsCanvasPoint(com.plot.core.geometry.RegionGeometry.of(outerPoints, holes), canvasPoint);
    }

    public static boolean containsCanvasPoint(com.plot.core.geometry.RegionGeometry geometry, Vec2d canvasPoint) {
        return geometry != null && geometry.contains(canvasPoint);
    }

    public static List<Vec2d> collectFootprintCellCenters(com.plot.core.geometry.RegionGeometry geometry) {
        return PolygonRegionUtils.collectFootprintCellCenters(geometry);
    }

    /**
     * 点到多边形边界的最短距离（画布坐标 / 方块平面）。
     */
    public static double distanceToPolygonBoundary(List<Vec2d> polygon, Vec2d point) {
        if (polygon == null || polygon.size() < 2 || point == null) {
            return 0.0;
        }
        double minDistance = Double.MAX_VALUE;
        int count = polygon.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = polygon.get(i);
            Vec2d end = polygon.get((i + 1) % count);
            if (start == null || end == null) {
                continue;
            }
            minDistance = Math.min(minDistance, GeometryUtils.pointToSegmentDistance(point, start, end));
        }
        return minDistance;
    }
}
