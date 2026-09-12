package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.EllipticalArcShape;
import com.plot.core.model.Shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 认领时捕获的参考路径快照，用于在画布图形仍存在或已被删除时重建 {@link PowerLineSourcePath}。
 */
public final class PowerLineSourceDescriptor {
    public enum Kind {
        POLYLINE,
        BEZIER,
        ELLIPSE,
        ARC,
        ELLIPTICAL_ARC
    }

    private final Kind kind;
    private final String shapeId;
    private final boolean closed;
    private final List<Vec2d> polylinePoints;
    private final List<Vec2d> bezierControlPoints;
    private final Vec2d ellipseCenter;
    private final double ellipseRadiusX;
    private final double ellipseRadiusY;
    private final double ellipseRotation;
    private final double arcStartAngle;
    private final double arcEndAngle;

    private PowerLineSourceDescriptor(
            Kind kind,
            String shapeId,
            boolean closed,
            List<Vec2d> polylinePoints,
            List<Vec2d> bezierControlPoints,
            Vec2d ellipseCenter,
            double ellipseRadiusX,
            double ellipseRadiusY,
            double ellipseRotation,
            double arcStartAngle,
            double arcEndAngle) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.shapeId = shapeId;
        this.closed = closed;
        this.polylinePoints = copyList(polylinePoints);
        this.bezierControlPoints = copyList(bezierControlPoints);
        this.ellipseCenter = ellipseCenter != null ? ellipseCenter.copy() : null;
        this.ellipseRadiusX = ellipseRadiusX;
        this.ellipseRadiusY = ellipseRadiusY;
        this.ellipseRotation = ellipseRotation;
        this.arcStartAngle = arcStartAngle;
        this.arcEndAngle = arcEndAngle;
    }

    public static PowerLineSourceDescriptor capture(Shape shape) {
        PowerLineSourcePath path = PowerLinePathAdapters.tryFrom(shape);
        if (path == null) {
            throw new IllegalArgumentException("Shape is not an adoptable power line path");
        }
        if (path instanceof PolylineSourcePath polyline) {
            return polyline(shape.getId(), polyline.points(), polyline.isClosed());
        }
        if (path instanceof BezierSourcePath bezier && shape instanceof BezierCurveShape curve) {
            return bezier(shape.getId(), curve.getControlPoints(), bezier.isClosed());
        }
        if (path instanceof EllipseLoopSourcePath ellipse) {
            return ellipse(
                shape.getId(),
                ellipse.center(),
                ellipse.radiusX(),
                ellipse.radiusY(),
                ellipse.rotation());
        }
        if (path instanceof ArcSourcePath && shape instanceof ArcShape arc) {
            return arc(
                shape.getId(),
                arc.getCenter(),
                arc.getRadius(),
                arc.getStartAngle(),
                arc.getEndAngle());
        }
        if (path instanceof EllipticalArcSourcePath && shape instanceof EllipticalArcShape arc) {
            return ellipticalArc(
                shape.getId(),
                arc.getCenter(),
                arc.getRadiusX(),
                arc.getRadiusY(),
                arc.getRotation(),
                arc.getStartAngle(),
                arc.getEndAngle());
        }
        throw new IllegalArgumentException("Unsupported source path type");
    }

    public static PowerLineSourceDescriptor polyline(String shapeId, List<Vec2d> points, boolean closed) {
        return new PowerLineSourceDescriptor(
            Kind.POLYLINE, shapeId, closed, points, List.of(),
            null, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public static PowerLineSourceDescriptor bezier(String shapeId, List<Vec2d> controlPoints, boolean closed) {
        return new PowerLineSourceDescriptor(
            Kind.BEZIER, shapeId, closed, List.of(), controlPoints,
            null, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public static PowerLineSourceDescriptor ellipse(
            String shapeId,
            Vec2d center,
            double radiusX,
            double radiusY,
            double rotation) {
        return new PowerLineSourceDescriptor(
            Kind.ELLIPSE, shapeId, true, List.of(), List.of(),
            center, radiusX, radiusY, rotation, 0.0, 0.0);
    }

    public static PowerLineSourceDescriptor arc(
            String shapeId,
            Vec2d center,
            double radius,
            double startAngle,
            double endAngle) {
        return new PowerLineSourceDescriptor(
            Kind.ARC, shapeId, false, List.of(), List.of(),
            center, radius, radius, 0.0, startAngle, endAngle);
    }

    public static PowerLineSourceDescriptor ellipticalArc(
            String shapeId,
            Vec2d center,
            double radiusX,
            double radiusY,
            double rotation,
            double startAngle,
            double endAngle) {
        return new PowerLineSourceDescriptor(
            Kind.ELLIPTICAL_ARC, shapeId, false, List.of(), List.of(),
            center, radiusX, radiusY, rotation, startAngle, endAngle);
    }

    public Kind kind() {
        return kind;
    }

    public String shapeId() {
        return shapeId;
    }

    public boolean closed() {
        return closed;
    }

    public List<Vec2d> polylinePoints() {
        return polylinePoints;
    }

    public List<Vec2d> bezierControlPoints() {
        return bezierControlPoints;
    }

    public Vec2d ellipseCenter() {
        return ellipseCenter != null ? ellipseCenter.copy() : null;
    }

    public double ellipseRadiusX() {
        return ellipseRadiusX;
    }

    public double ellipseRadiusY() {
        return ellipseRadiusY;
    }

    public double ellipseRotation() {
        return ellipseRotation;
    }

    public double arcStartAngle() {
        return arcStartAngle;
    }

    public double arcEndAngle() {
        return arcEndAngle;
    }

    public int fingerprint() {
        int hash = Objects.hash(kind, shapeId, closed);
        hash = 31 * hash + pointsFingerprint(polylinePoints);
        hash = 31 * hash + pointsFingerprint(bezierControlPoints);
        if (ellipseCenter != null) {
            hash = 31 * hash + Double.hashCode(ellipseCenter.x);
            hash = 31 * hash + Double.hashCode(ellipseCenter.y);
        }
        hash = 31 * hash + Double.hashCode(ellipseRadiusX);
        hash = 31 * hash + Double.hashCode(ellipseRadiusY);
        hash = 31 * hash + Double.hashCode(ellipseRotation);
        hash = 31 * hash + Double.hashCode(arcStartAngle);
        hash = 31 * hash + Double.hashCode(arcEndAngle);
        return hash;
    }

    public PowerLineSourcePath toSourcePath() {
        return switch (kind) {
            case POLYLINE -> PolylineSourcePath.of(polylinePoints, closed);
            case BEZIER -> bezierFromFlatControls(bezierControlPoints, closed);
            case ELLIPSE -> new EllipseLoopSourcePath(
                ellipseCenter, ellipseRadiusX, ellipseRadiusY, ellipseRotation);
            case ARC -> new ArcSourcePath(
                ellipseCenter, ellipseRadiusX, arcStartAngle, arcEndAngle);
            case ELLIPTICAL_ARC -> new EllipticalArcSourcePath(
                ellipseCenter,
                ellipseRadiusX,
                ellipseRadiusY,
                ellipseRotation,
                arcStartAngle,
                arcEndAngle);
        };
    }

    public static PowerLineSourcePath resolve(Shape shape, PowerLineSourceDescriptor descriptor) {
        if (shape != null) {
            PowerLineSourcePath live = PowerLinePathAdapters.tryFrom(shape);
            if (live != null) {
                return live;
            }
        }
        if (descriptor != null) {
            return descriptor.toSourcePath();
        }
        return null;
    }

    private static BezierSourcePath bezierFromFlatControls(List<Vec2d> flat, boolean closed) {
        if (flat == null || flat.size() < 4) {
            return new BezierSourcePath(List.of(), closed);
        }
        int segmentCount = (flat.size() - 1) / 3;
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
        return new BezierSourcePath(segments, closed);
    }

    private static int pointsFingerprint(List<Vec2d> points) {
        int hash = 1;
        for (Vec2d point : points) {
            if (point == null) {
                continue;
            }
            hash = 31 * hash + Double.hashCode(point.x);
            hash = 31 * hash + Double.hashCode(point.y);
        }
        return hash;
    }

    private static List<Vec2d> copyList(List<Vec2d> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Vec2d> copy = new ArrayList<>(source.size());
        for (Vec2d point : source) {
            if (point != null) {
                copy.add(point.copy());
            }
        }
        return List.copyOf(copy);
    }
}
