package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
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
import com.plot.infrastructure.coordinate.CoordinateTransformer;
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
        if (shape instanceof PolylineShape polyline) {
            return PolygonRegionUtils.copyPoints(polyline.getPoints());
        }
        if (shape instanceof Polygon polygon) {
            return PolygonRegionUtils.copyPoints(polygon.getPoints());
        }
        if (shape instanceof FreeDrawPath freeDraw) {
            return PolygonRegionUtils.copyPoints(freeDraw.getPoints());
        }
        if (shape instanceof BezierCurveShape bezier) {
            List<Vec2d> curvePoints = bezier.getCurvePoints();
            return curvePoints != null ? PolygonRegionUtils.copyPoints(curvePoints) : List.of();
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

    public static Polygon toPolygon(List<Vec2d> points) {
        return PolygonRegionUtils.toPolygon(points);
    }

    public static List<Vec2d> collectFootprintCellCenters(List<Vec2d> points) {
        return PolygonRegionUtils.collectFootprintCellCenters(points);
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

    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, CoordinateTransformer transformer) {
        return PolygonRegionUtils.canvasToBlockXZ(canvasPos, transformer);
    }

    public static String resolveFillBlockId(String material) {
        if (material == null || material.isBlank()) {
            return GradingRegion.DEFAULT_FILL_MATERIAL;
        }
        return material.trim();
    }

    public static String resolveCutBlockId(String material) {
        if (material == null || material.isBlank()) {
            return "minecraft:air";
        }
        return material.trim();
    }

    public static PolygonRegionUtils.RectBounds computeBounds(List<Vec2d> points) {
        return PolygonRegionUtils.computeBounds(points);
    }

    public static Vec2d computeCentroid(List<Vec2d> points) {
        return PolygonRegionUtils.computeCentroid(points);
    }
}
