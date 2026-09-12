package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;

import java.util.ArrayList;
import java.util.List;

/** 将画布 {@link Shape} 转为 {@link PowerLineSourcePath}。 */
public final class PowerLinePathAdapters {

    private PowerLinePathAdapters() {
    }

    public static boolean isAdoptable(Shape shape) {
        return tryFrom(shape) != null;
    }

    public static PowerLineSourcePath from(Shape shape) {
        PowerLineSourcePath path = tryFrom(shape);
        if (path == null) {
            throw new IllegalArgumentException("Shape is not an adoptable power line path");
        }
        return path;
    }

    public static PowerLineSourcePath tryFrom(Shape shape) {
        if (shape == null) {
            return null;
        }
        if (shape instanceof LineShape line) {
            return PolylineSourcePath.open(copyPoints(line.getPoints()));
        }
        if (shape instanceof PolylineShape polyline) {
            return PolylineSourcePath.of(copyPoints(polyline.getPoints()), polyline.isClosed());
        }
        if (shape instanceof FreeDrawPath freeDraw) {
            return PolylineSourcePath.open(copyPoints(freeDraw.getPoints()));
        }
        if (shape instanceof BezierCurveShape bezier) {
            return bezierPath(bezier);
        }
        if (shape instanceof CircleShape circle) {
            if (circle.getRadius() <= 1e-9) {
                return null;
            }
            return EllipseLoopSourcePath.circle(circle.getCenter(), circle.getRadius());
        }
        if (shape instanceof EllipseShape ellipse) {
            if (ellipse.getRadiusX() <= 1e-9 || ellipse.getRadiusY() <= 1e-9) {
                return null;
            }
            return new EllipseLoopSourcePath(
                ellipse.getCenter(),
                ellipse.getRadiusX(),
                ellipse.getRadiusY(),
                ellipse.getRotation());
        }
        if (shape instanceof Polygon polygon) {
            if (!polygon.isClosed() || polygon.getPoints().size() < 3) {
                return null;
            }
            return PolylineSourcePath.of(copyClosedVertices(polygon.getPoints()), true);
        }
        if (shape instanceof RectangleShape rectangle) {
            List<Vec2d> outline = copyPoints(rectangle.getPoints());
            if (outline.size() < 3) {
                return null;
            }
            return PolylineSourcePath.of(copyClosedVertices(outline), true);
        }
        return null;
    }

    private static BezierSourcePath bezierPath(BezierCurveShape bezier) {
        List<Vec2d> flat = bezier.getControlPoints();
        int segmentCount = bezier.getSegmentCount();
        if (segmentCount <= 0 || flat.size() < 4) {
            return null;
        }
        List<CubicBezierSegment> segments = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            int base = i * 3;
            if (base + 3 >= flat.size()) {
                break;
            }
            segments.add(new CubicBezierSegment(
                flat.get(base).copy(),
                flat.get(base + 1).copy(),
                flat.get(base + 2).copy(),
                flat.get(base + 3).copy()));
        }
        if (segments.isEmpty()) {
            return null;
        }
        return new BezierSourcePath(segments, bezier.isClosed());
    }

    private static List<Vec2d> copyClosedVertices(List<Vec2d> source) {
        List<Vec2d> points = copyPoints(source);
        if (points.size() >= 2 && points.getFirst().distance(points.getLast()) <= 1e-6) {
            return new ArrayList<>(points.subList(0, points.size() - 1));
        }
        return points;
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
