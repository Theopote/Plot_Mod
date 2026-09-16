package com.plot.plugin.pattern;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
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
import com.plot.plugin.pattern.model.PatternFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 铺装区域几何工具（认领与坐标转换）。
 */
public final class PatternGeometryUtils {
    private static final double MIN_REGION_AREA = 1e-6;

    private PatternGeometryUtils() {
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
        return Math.abs(PolygonRegionUtils.signedAreaOfRing(points)) > MIN_REGION_AREA;
    }

    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, ICoordinateService transformer) {
        return PolygonRegionUtils.canvasToBlockXZ(canvasPos, transformer);
    }
}
