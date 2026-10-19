package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.api.graphics.IShapeStyle;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import com.plot.utils.ExceptionDebug;

import java.util.ArrayList;
import java.util.List;

/**
 * 炸开/合并图形的几何计算。
 */
public final class ExplodeJoinHandler {

    public static final double DEFAULT_JOIN_TOLERANCE = 1e-3;

    private ExplodeJoinHandler() {
    }

    public static boolean canExplode(Shape shape) {
        return shape instanceof PolylineShape
                || shape instanceof Polygon
                || shape instanceof RectangleShape
                || shape instanceof CircleShape
                || shape instanceof ArcShape
                || shape instanceof EllipseShape;
    }

    public static List<Shape> explodeShapes(List<Shape> shapes) {
        List<Shape> result = new ArrayList<>();
        if (shapes == null) {
            return result;
        }
        for (Shape shape : shapes) {
            if (shape == null) {
                continue;
            }
            if (shape instanceof LineShape line) {
                result.add(cloneLine(line));
                continue;
            }
            List<LineShape> segments = explodeToSegments(shape);
            if (segments.isEmpty()) {
                result.add(shape);
            } else {
                result.addAll(segments);
            }
        }
        return result;
    }

    public static List<Shape> joinShapes(List<Shape> shapes) {
        return joinShapes(shapes, DEFAULT_JOIN_TOLERANCE);
    }

    public static List<Shape> joinShapes(List<Shape> shapes, double tolerance) {
        List<LineShape> segments = collectJoinableSegments(shapes);
        if (segments.size() < 2) {
            return shapes == null ? List.of() : new ArrayList<>(shapes);
        }

        boolean[] used = new boolean[segments.size()];
        List<Shape> result = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            if (used[i]) {
                continue;
            }
            List<Vec2d> chain = new ArrayList<>();
            IShapeStyle style = segments.get(i).getStyle();
            int mergedSegmentCount = growChain(chain, segments, used, i, tolerance);
            if (mergedSegmentCount >= 2 && chain.size() >= 2) {
                boolean closed = chain.size() >= 3
                        && pointsNear(chain.getFirst(), chain.getLast(), tolerance);
                if (closed) {
                    chain.removeLast();
                }
                if (chain.size() >= 2) {
                    PolylineShape polyline = new PolylineShape(chain, closed);
                    copyStyle(style, polyline);
                    result.add(polyline);
                    continue;
                }
            }
            result.add(cloneLine(segments.get(i)));
        }

        return result;
    }

    public static boolean hasExplodeEffect(List<Shape> original, List<Shape> exploded) {
        if (original == null || exploded == null) {
            return false;
        }
        if (exploded.size() > original.size()) {
            return true;
        }
        long originalLines = original.stream().filter(LineShape.class::isInstance).count();
        return exploded.size() > originalLines;
    }

    public static boolean hasJoinEffect(List<Shape> original, List<Shape> joined) {
        if (original == null || joined == null) {
            return false;
        }
        if (joined.size() < original.size()) {
            return true;
        }
        long joinedPolylines = joined.stream().filter(PolylineShape.class::isInstance).count();
        long originalPolylines = original.stream().filter(PolylineShape.class::isInstance).count();
        return joinedPolylines > originalPolylines;
    }

    private static List<LineShape> explodeToSegments(Shape shape) {
        if (shape instanceof PolylineShape polyline) {
            return segmentsFromPoints(polyline.getPoints(), polyline.isClosed(), polyline.getStyle());
        }
        if (shape instanceof Polygon polygon) {
            return segmentsFromPoints(polygon.getPoints(), polygon.isClosed(), polygon.getStyle());
        }
        if (shape instanceof RectangleShape || shape instanceof CircleShape
                || shape instanceof ArcShape || shape instanceof EllipseShape) {
            List<Vec2d> points = shape.getPoints();
            boolean closed = shape instanceof CircleShape
                    || shape instanceof EllipseShape
                    || (shape instanceof ArcShape arc && Math.abs(arc.getEndAngle() - arc.getStartAngle()) >= Math.PI * 1.99);
            return segmentsFromPoints(points, closed, shape.getStyle());
        }
        return List.of();
    }

    private static List<LineShape> segmentsFromPoints(List<Vec2d> points, boolean closed, IShapeStyle style) {
        List<LineShape> segments = new ArrayList<>();
        if (points == null || points.size() < 2) {
            return segments;
        }
        for (int i = 0; i < points.size() - 1; i++) {
            segments.add(createSegment(points.get(i), points.get(i + 1), style));
        }
        if (closed && points.size() > 2 && !pointsNear(points.getFirst(), points.getLast(), DEFAULT_JOIN_TOLERANCE)) {
            segments.add(createSegment(points.getLast(), points.getFirst(), style));
        }
        return segments;
    }

    private static List<LineShape> collectJoinableSegments(List<Shape> shapes) {
        List<LineShape> segments = new ArrayList<>();
        if (shapes == null) {
            return segments;
        }
        for (Shape shape : shapes) {
            if (shape instanceof LineShape line) {
                segments.add(cloneLine(line));
            } else if (shape instanceof PolylineShape polyline) {
                segments.addAll(segmentsFromPoints(polyline.getPoints(), polyline.isClosed(), polyline.getStyle()));
            }
        }
        return segments;
    }

    private static int growChain(List<Vec2d> chain, List<LineShape> segments, boolean[] used, int seedIndex,
                                 double tolerance) {
        int mergedCount = appendSegment(chain, segments.get(seedIndex), used, seedIndex, tolerance, true) ? 1 : 0;
        boolean extended;
        do {
            extended = extendChainEnd(chain, segments, used, tolerance, true)
                    || extendChainEnd(chain, segments, used, tolerance, false);
            if (extended) {
                mergedCount++;
            }
        } while (extended);
        return mergedCount;
    }

    private static boolean appendSegment(List<Vec2d> chain, LineShape segment, boolean[] used, int index,
                                         double tolerance, boolean markUsed) {
        if (markUsed) {
            if (used[index]) {
                return false;
            }
            used[index] = true;
        }
        Vec2d start = segment.getStart();
        Vec2d end = segment.getEnd();
        if (chain.isEmpty()) {
            chain.add(start);
            chain.add(end);
            return true;
        }
        appendEndpoint(chain, start, end, tolerance);
        return true;
    }

    private static boolean extendChainEnd(List<Vec2d> chain, List<LineShape> segments, boolean[] used,
                                          double tolerance, boolean fromTail) {
        Vec2d anchor = fromTail ? chain.getLast() : chain.getFirst();
        for (int i = 0; i < segments.size(); i++) {
            if (used[i]) {
                continue;
            }
            LineShape segment = segments.get(i);
            Vec2d start = segment.getStart();
            Vec2d end = segment.getEnd();
            if (pointsNear(anchor, start, tolerance)) {
                used[i] = true;
                if (fromTail) {
                    chain.add(end);
                } else {
                    chain.addFirst(start);
                }
                return true;
            }
            if (pointsNear(anchor, end, tolerance)) {
                used[i] = true;
                if (fromTail) {
                    chain.add(start);
                } else {
                    chain.addFirst(end);
                }
                return true;
            }
        }
        return false;
    }

    private static void appendEndpoint(List<Vec2d> chain, Vec2d start, Vec2d end, double tolerance) {
        Vec2d tail = chain.getLast();
        if (pointsNear(tail, start, tolerance)) {
            chain.add(end);
        } else if (pointsNear(tail, end, tolerance)) {
            chain.add(start);
        }
    }

    private static LineShape createSegment(Vec2d start, Vec2d end, IShapeStyle style) {
        LineShape line = new LineShape(start, end);
        copyStyle(style, line);
        return line;
    }

    private static LineShape cloneLine(LineShape line) {
        LineShape clone = new LineShape(line.getStart(), line.getEnd());
        copyStyle(line.getStyle(), clone);
        return clone;
    }

    private static void copyStyle(IShapeStyle style, Shape target) {
        if (style == null) {
            return;
        }
        try {
            target.setStyle(style.clone());
        } catch (Exception e) {
            ExceptionDebug.log("ExplodeJoinHandler: clone shape style", e);
        }
    }

    static boolean pointsNear(Vec2d a, Vec2d b, double tolerance) {
        return a != null && b != null && a.distance(b) <= tolerance;
    }
}
