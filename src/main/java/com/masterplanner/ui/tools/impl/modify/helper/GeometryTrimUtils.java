package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.*;
import com.masterplanner.core.model.Shape;

import java.util.ArrayList;
import java.util.List;

/**
 * 几何修剪工具类
 * 提供通用的几何计算方法，供边界修剪和栅栏修剪使用
 */
public class GeometryTrimUtils {
    
    private static final double TRIM_TOLERANCE = 5.0;
    private static final double INTERSECTION_TOLERANCE = 1e-6;
    
    // ====== 交点计算相关方法 ======
    
    /**
     * 找到图形与边界图形的所有交点
     */
    public List<Vec2d> findIntersections(Shape shape, List<Shape> boundaryShapes) {
        List<Vec2d> allIntersections = new ArrayList<>();
        
        if (boundaryShapes == null || boundaryShapes.isEmpty()) {
            return allIntersections;
        }
        
        for (Shape boundaryShape : boundaryShapes) {
            List<Vec2d> intersections = calculateShapeIntersections(shape, boundaryShape);
            allIntersections.addAll(intersections);
        }
        
        // 移除重复的交点
        return removeDuplicatePoints(allIntersections);
    }
    
    /**
     * 计算两个图形之间的交点
     */
    private List<Vec2d> calculateShapeIntersections(Shape shape1, Shape shape2) {
        if (shape1 instanceof CircleShape circle1 && shape2 instanceof CircleShape circle2) {
            return calculateCircleCircleIntersections(circle1, circle2);
        }

        if (shape1 instanceof CircleShape circle && !(shape2 instanceof CircleShape)) {
            List<Vec2d> shape2Points = getShapePointsForIntersection(shape2);
            if (shape2Points.size() >= 2) {
                return calculateCircleWithSampledCurveIntersections(circle, shape2Points);
            }
        }

        if (shape2 instanceof CircleShape circle && !(shape1 instanceof CircleShape)) {
            List<Vec2d> shape1Points = getShapePointsForIntersection(shape1);
            if (shape1Points.size() >= 2) {
                return calculateCircleWithSampledCurveIntersections(circle, shape1Points);
            }
        }

        List<Vec2d> intersections = new ArrayList<>();
        
        // 获取两个图形的采样点
        List<Vec2d> points1 = getShapePointsForIntersection(shape1);
        List<Vec2d> points2 = getShapePointsForIntersection(shape2);
        
        // 计算线段之间的交点
        for (int i = 0; i < points1.size() - 1; i++) {
            Vec2d p1 = points1.get(i);
            Vec2d p2 = points1.get(i + 1);
            
            for (int j = 0; j < points2.size() - 1; j++) {
                Vec2d p3 = points2.get(j);
                Vec2d p4 = points2.get(j + 1);
                
                Vec2d intersection = calculateLineLineIntersection(p1, p2, p3, p4);
                if (intersection != null) {
                    intersections.add(intersection);
                }
            }
        }
        
        return intersections;
    }

    private List<Vec2d> calculateCircleCircleIntersections(CircleShape c1, CircleShape c2) {
        List<Vec2d> intersections = new ArrayList<>();

        Vec2d p0 = c1.getCenter();
        Vec2d p1 = c2.getCenter();
        double r0 = c1.getRadius();
        double r1 = c2.getRadius();

        double d = p0.distance(p1);
        double epsilon = Math.max(INTERSECTION_TOLERANCE, Math.max(r0, r1) * 1e-9);

        if (d < epsilon && Math.abs(r0 - r1) < epsilon) {
            return intersections;
        }

        if (d > r0 + r1 + epsilon) {
            return intersections;
        }

        if (d < Math.abs(r0 - r1) - epsilon) {
            return intersections;
        }

        if (d < epsilon) {
            return intersections;
        }

        double a = (r0 * r0 - r1 * r1 + d * d) / (2.0 * d);
        double hSquared = r0 * r0 - a * a;
        if (hSquared < -epsilon) {
            return intersections;
        }

        double h = Math.sqrt(Math.max(0.0, hSquared));

        double x2 = p0.x + a * (p1.x - p0.x) / d;
        double y2 = p0.y + a * (p1.y - p0.y) / d;

        double rx = -(p1.y - p0.y) * (h / d);
        double ry = (p1.x - p0.x) * (h / d);

        Vec2d i1 = new Vec2d(x2 + rx, y2 + ry);
        Vec2d i2 = new Vec2d(x2 - rx, y2 - ry);

        intersections.add(i1);
        if (i1.distance(i2) > epsilon) {
            intersections.add(i2);
        }

        return intersections;
    }

    private List<Vec2d> calculateCircleWithSampledCurveIntersections(CircleShape circle, List<Vec2d> curvePoints) {
        List<Vec2d> intersections = new ArrayList<>();
        if (curvePoints == null || curvePoints.size() < 2) {
            return intersections;
        }

        for (int i = 0; i < curvePoints.size() - 1; i++) {
            Vec2d p1 = curvePoints.get(i);
            Vec2d p2 = curvePoints.get(i + 1);
            intersections.addAll(calculateCircleSegmentIntersections(circle, p1, p2));
        }

        return removeDuplicatePoints(intersections);
    }

    private List<Vec2d> calculateCircleSegmentIntersections(CircleShape circle, Vec2d p1, Vec2d p2) {
        List<Vec2d> intersections = new ArrayList<>();

        Vec2d center = circle.getCenter();
        double radius = circle.getRadius();
        Vec2d d = p2.subtract(p1);
        Vec2d f = p1.subtract(center);

        double a = d.dot(d);
        if (a < INTERSECTION_TOLERANCE) {
            return intersections;
        }

        double b = 2.0 * f.dot(d);
        double c = f.dot(f) - radius * radius;

        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < -INTERSECTION_TOLERANCE) {
            return intersections;
        }

        if (Math.abs(discriminant) <= INTERSECTION_TOLERANCE) {
            double t = -b / (2.0 * a);
            if (t >= 0.0 && t <= 1.0) {
                intersections.add(p1.add(d.multiply(t)));
            }
            return intersections;
        }

        double sqrtDisc = Math.sqrt(discriminant);
        double t1 = (-b - sqrtDisc) / (2.0 * a);
        double t2 = (-b + sqrtDisc) / (2.0 * a);

        if (t1 >= 0.0 && t1 <= 1.0) {
            intersections.add(p1.add(d.multiply(t1)));
        }
        if (t2 >= 0.0 && t2 <= 1.0) {
            intersections.add(p1.add(d.multiply(t2)));
        }

        return intersections;
    }
    
    /**
     * 获取图形用于交点计算的采样点
     */
    private List<Vec2d> getShapePointsForIntersection(Shape shape) {
        switch (shape) {
            case LineShape line -> {
                List<Vec2d> points = new ArrayList<>();
                points.add(line.getStart());
                points.add(line.getEnd());
                return points;
            }
            case RectangleShape rectangle -> {
                return createDenseRectanglePoints(rectangle);
            }
            case FreeDrawPath path -> {
                return createDenseFreeDrawPathPoints(path);
            }
            case PolylineShape polyline -> {
                return createDensePolylinePoints(polyline);
            }
            case CircleShape circle -> {
                return createDenseCirclePoints(circle);
            }
            case ArcShape arc -> {
                return createDenseArcPoints(arc);
            }
            case EllipseShape ellipse -> {
                return createDenseEllipsePoints(ellipse);
            }
            case EllipticalArcShape ellipticalArc -> {
                return createDensePolylineFromPoints(ellipticalArc.getPoints());
            }
            case BezierCurveShape bezier -> {
                return createDenseBezierPoints(bezier);
            }
            case SineCurveShape sine -> {
                return createDenseSinePoints(sine);
            }
            case SpiralShape spiral -> {
                return createDenseSpiralPoints(spiral);
            }
                case CableShape catenary -> {
                return createDenseCatenaryPoints(catenary);
            }
            case Polygon polygon -> {
                return createDensePolygonPoints(polygon);
            }
            case TextShape text -> {
                return createDenseTextPoints(text);
            }
            default -> {
                return createDensePolylineFromPoints(shape.getPoints());
            }
        }
    }

    public List<Vec2d> createDensePolylineFromPoints(List<Vec2d> points) {
        List<Vec2d> densePoints = new ArrayList<>();
        if (points == null || points.isEmpty()) {
            return densePoints;
        }

        if (points.size() == 1) {
            densePoints.add(points.getFirst());
            return densePoints;
        }

        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d current = points.get(i);
            Vec2d next = points.get(i + 1);

            densePoints.add(current);

            double distance = current.distance(next);
            int numPoints = Math.max(1, (int) (distance / 5.0));

            for (int j = 1; j < numPoints; j++) {
                double t = j / (double) numPoints;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }

        densePoints.add(points.getLast());
        return densePoints;
    }
    
    // ====== 密集采样方法 ======
    
    public List<Vec2d> createDenseRectanglePoints(RectangleShape rectangle) {
        List<Vec2d> corners = rectangle.getPoints();
        List<Vec2d> densePoints = new ArrayList<>();
        
        for (int i = 0; i < corners.size(); i++) {
            Vec2d current = corners.get(i);
            Vec2d next = corners.get((i + 1) % corners.size());
            
            densePoints.add(current);
            
            // 在每条边上添加9个中间点
            for (int j = 1; j < 10; j++) {
                double t = j / 10.0;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }
        
        return densePoints;
    }
    
    public List<Vec2d> createDenseFreeDrawPathPoints(FreeDrawPath path) {
        List<Vec2d> curvePoints = path.getPoints();
        List<Vec2d> densePoints = new ArrayList<>();
        
        for (int i = 0; i < curvePoints.size() - 1; i++) {
            Vec2d current = curvePoints.get(i);
            Vec2d next = curvePoints.get(i + 1);
            
            densePoints.add(current);
            
            // 在每段之间添加中间点
            double distance = current.distance(next);
            int numPoints = Math.max(1, (int) (distance / 5.0)); // 每5像素一个点
            
            for (int j = 1; j < numPoints; j++) {
                double t = j / (double) numPoints;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }
        
        densePoints.add(curvePoints.getLast());
        return densePoints;
    }
    
    public List<Vec2d> createDensePolylinePoints(PolylineShape polyline) {
        List<Vec2d> points = polyline.getPoints();
        List<Vec2d> densePoints = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d current = points.get(i);
            Vec2d next = points.get(i + 1);
            
            densePoints.add(current);
            
            // 在每段之间添加中间点
            double distance = current.distance(next);
            int numPoints = Math.max(1, (int) (distance / 5.0));
            
            for (int j = 1; j < numPoints; j++) {
                double t = j / (double) numPoints;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }
        
        densePoints.add(points.getLast());
        return densePoints;
    }
    
    public List<Vec2d> createDenseCirclePoints(CircleShape circle) {
        List<Vec2d> densePoints = new ArrayList<>();
        Vec2d center = circle.getCenter();
        double radius = circle.getRadius();
        
        // 生成36个点（每10度一个点）
        for (int i = 0; i < 36; i++) {
            double angle = i * Math.PI / 18.0;
            Vec2d point = new Vec2d(
                center.x + radius * Math.cos(angle),
                center.y + radius * Math.sin(angle)
            );
            densePoints.add(point);
        }
        
        return densePoints;
    }
    
    public List<Vec2d> createDenseArcPoints(ArcShape arc) {
        List<Vec2d> densePoints = new ArrayList<>();
        Vec2d center = arc.getCenter();
        double radius = arc.getRadius();
        double startAngle = arc.getStartAngle();
        double endAngle = arc.getEndAngle();
        
        // 确保角度范围正确
        if (endAngle < startAngle) {
            endAngle += 2 * Math.PI;
        }
        
        // 计算需要多少个点
        double angleRange = endAngle - startAngle;
        int numPoints = Math.max(10, (int) (angleRange * 180 / Math.PI)); // 每度约3个点
        
        for (int i = 0; i <= numPoints; i++) {
            double angle = startAngle + (i / (double) numPoints) * angleRange;
            Vec2d point = new Vec2d(
                center.x + radius * Math.cos(angle),
                center.y + radius * Math.sin(angle)
            );
            densePoints.add(point);
        }
        
        return densePoints;
    }
    
    public List<Vec2d> createDenseEllipsePoints(EllipseShape ellipse) {
        List<Vec2d> densePoints = new ArrayList<>();
        Vec2d center = ellipse.getCenter();
        double radiusX = ellipse.getRadiusX();
        double radiusY = ellipse.getRadiusY();
        double rotation = ellipse.getRotation();
        
        // 生成72个点（每5度一个点）
        for (int i = 0; i < 72; i++) {
            double angle = i * Math.PI / 36.0;
            
            // 计算椭圆上的点
            double x = radiusX * Math.cos(angle);
            double y = radiusY * Math.sin(angle);
            
            // 应用旋转
            double cosRot = Math.cos(rotation);
            double sinRot = Math.sin(rotation);
            double rotatedX = x * cosRot - y * sinRot;
            double rotatedY = x * sinRot + y * cosRot;
            
            Vec2d point = new Vec2d(
                center.x + rotatedX,
                center.y + rotatedY
            );
            densePoints.add(point);
        }
        
        return densePoints;
    }
    
    public List<Vec2d> createDenseBezierPoints(BezierCurveShape bezier) {
        List<Vec2d> curvePoints = bezier.getCurvePoints();
        List<Vec2d> densePoints = new ArrayList<>();
        
        for (int i = 0; i < curvePoints.size() - 1; i++) {
            Vec2d current = curvePoints.get(i);
            Vec2d next = curvePoints.get(i + 1);
            
            densePoints.add(current);
            
            // 在每段之间添加中间点
            double distance = current.distance(next);
            int numPoints = Math.max(1, (int) (distance / 3.0)); // 每3像素一个点
            
            for (int j = 1; j < numPoints; j++) {
                double t = j / (double) numPoints;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }
        
        densePoints.add(curvePoints.getLast());
        return densePoints;
    }
    
    public List<Vec2d> createDenseSinePoints(SineCurveShape sine) {
        List<Vec2d> points = sine.getPoints();
        List<Vec2d> densePoints = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d current = points.get(i);
            Vec2d next = points.get(i + 1);
            
            densePoints.add(current);
            
            // 在每段之间添加中间点
            double distance = current.distance(next);
            int numPoints = Math.max(1, (int) (distance / 3.0));
            
            for (int j = 1; j < numPoints; j++) {
                double t = j / (double) numPoints;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }
        
        densePoints.add(points.getLast());
        return densePoints;
    }
    
    public List<Vec2d> createDenseSpiralPoints(SpiralShape spiral) {
        List<Vec2d> points = spiral.getPoints();
        List<Vec2d> densePoints = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d current = points.get(i);
            Vec2d next = points.get(i + 1);
            
            densePoints.add(current);
            
            // 在每段之间添加中间点
            double distance = current.distance(next);
            int numPoints = Math.max(1, (int) (distance / 3.0));
            
            for (int j = 1; j < numPoints; j++) {
                double t = j / (double) numPoints;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }
        
        densePoints.add(points.getLast());
        return densePoints;
    }
    
    public List<Vec2d> createDenseCatenaryPoints(CableShape catenary) {
        List<Vec2d> points = catenary.getPoints();
        List<Vec2d> densePoints = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d current = points.get(i);
            Vec2d next = points.get(i + 1);
            
            densePoints.add(current);
            
            // 在每段之间添加中间点
            double distance = current.distance(next);
            int numPoints = Math.max(1, (int) (distance / 3.0));
            
            for (int j = 1; j < numPoints; j++) {
                double t = j / (double) numPoints;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }
        
        densePoints.add(points.getLast());
        return densePoints;
    }
    
    public List<Vec2d> createDensePolygonPoints(Polygon polygon) {
        List<Vec2d> points = polygon.getPoints();
        List<Vec2d> densePoints = new ArrayList<>();
        
        for (int i = 0; i < points.size(); i++) {
            Vec2d current = points.get(i);
            Vec2d next = points.get((i + 1) % points.size());
            
            densePoints.add(current);
            
            // 在每段之间添加中间点
            double distance = current.distance(next);
            int numPoints = Math.max(1, (int) (distance / 5.0));
            
            for (int j = 1; j < numPoints; j++) {
                double t = j / (double) numPoints;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }
        
        return densePoints;
    }
    
    public List<Vec2d> createDenseTextPoints(TextShape text) {
        List<Vec2d> points = text.getPoints();
        List<Vec2d> densePoints = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d current = points.get(i);
            Vec2d next = points.get(i + 1);
            
            densePoints.add(current);
            
            // 在每段之间添加中间点
            double distance = current.distance(next);
            int numPoints = Math.max(1, (int) (distance / 5.0));
            
            for (int j = 1; j < numPoints; j++) {
                double t = j / (double) numPoints;
                Vec2d midPoint = new Vec2d(
                    current.x + t * (next.x - current.x),
                    current.y + t * (next.y - current.y)
                );
                densePoints.add(midPoint);
            }
        }
        
        densePoints.add(points.getLast());
        return densePoints;
    }
    
    // ====== 线段交点计算 ======
    
    /**
     * 计算两条线段的交点
     */
    public Vec2d calculateLineLineIntersection(Vec2d p1, Vec2d p2, Vec2d p3, Vec2d p4) {
        double x1 = p1.x, y1 = p1.y;
        double x2 = p2.x, y2 = p2.y;
        double x3 = p3.x, y3 = p3.y;
        double x4 = p4.x, y4 = p4.y;
        
        double denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        
        if (Math.abs(denominator) < INTERSECTION_TOLERANCE) {
            return null; // 线段平行或重合
        }
        
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denominator;
        double u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denominator;
        
        // 检查参数是否在[0,1]范围内
        if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
            double x = x1 + t * (x2 - x1);
            double y = y1 + t * (y2 - y1);
            return new Vec2d(x, y);
        }
        
        return null;
    }
    
    // ====== 点在图形上的判断 ======
    
    /**
     * 判断点是否在图形上
     */
    public boolean isPointOnShape(Shape shape, Vec2d point) {
        double tolerance = calculateAdaptiveTolerance(shape);
        
        switch (shape) {
            case LineShape line -> {
                return isPointOnLine(line.getStart(), line.getEnd(), point, tolerance);
            }
            case CircleShape circle -> {
                return isPointOnCircle(circle.getCenter(), circle.getRadius(), point, tolerance);
            }
            case ArcShape arc -> {
                return isPointOnArc(arc, point);
            }
            case EllipseShape ellipse -> {
                return isPointOnEllipse(ellipse, point);
            }
            case EllipticalArcShape ellipticalArc -> {
                return isPointOnPolyline(ellipticalArc, point, tolerance);
            }
            case RectangleShape rectangle -> {
                return isPointOnRectangleBoundary(rectangle, point);
            }
            case PolylineShape polyline -> {
                return isPointOnPolyline(polyline, point, tolerance);
            }
            case FreeDrawPath path -> {
                return isPointOnPolyline(path, point, tolerance);
            }
            case BezierCurveShape bezier -> {
                return isPointOnPolyline(bezier, point, tolerance);
            }
            case SineCurveShape sine -> {
                return isPointOnPolyline(sine, point, tolerance);
            }
            case SpiralShape spiral -> {
                return isPointOnPolyline(spiral, point, tolerance);
            }
                case CableShape catenary -> {
                return isPointOnPolyline(catenary, point, tolerance);
            }
            case Polygon polygon -> {
                return isPointOnPolyline(polygon, point, tolerance);
            }
            case TextShape text -> {
                return isPointOnPolyline(text, point, tolerance);
            }
            default -> {
                return isPointOnPolyline(shape, point, tolerance);
            }
        }
    }
    
    private boolean isPointOnLine(Vec2d start, Vec2d end, Vec2d point, double tolerance) {
        double distance = pointToSegmentDistance(start, end, point);
        return distance <= tolerance;
    }
    
    private boolean isPointOnCircle(Vec2d center, double radius, Vec2d point, double tolerance) {
        double distance = center.distance(point);
        return Math.abs(distance - radius) <= tolerance;
    }
    
    public boolean isPointOnArc(ArcShape arc, Vec2d point) {
        Vec2d center = arc.getCenter();
        double radius = arc.getRadius();
        double startAngle = arc.getStartAngle();
        double endAngle = arc.getEndAngle();
        
        // 检查点是否在圆上
        if (!isPointOnCircle(center, radius, point, INTERSECTION_TOLERANCE)) {
            return false;
        }
        
        // 检查点是否在圆弧的角度范围内
        double pointAngle = Math.atan2(point.y - center.y, point.x - center.x);
        pointAngle = normalizeAngle(pointAngle);
        
        return isAngleInRange(pointAngle, startAngle, endAngle);
    }
    
    public boolean isPointOnEllipse(EllipseShape ellipse, Vec2d point) {
        Vec2d center = ellipse.getCenter();
        double radiusX = ellipse.getRadiusX();
        double radiusY = ellipse.getRadiusY();
        double rotation = ellipse.getRotation();
        
        // 将点转换到椭圆的局部坐标系
        Vec2d localPoint = transformPointToEllipseLocal(point, center, rotation);
        
        // 检查点是否在椭圆上
        double normalizedX = localPoint.x / radiusX;
        double normalizedY = localPoint.y / radiusY;
        
        return Math.abs(normalizedX * normalizedX + normalizedY * normalizedY - 1.0) <= INTERSECTION_TOLERANCE;
    }
    
    public boolean isPointOnRectangleBoundary(RectangleShape rectangle, Vec2d point) {
        List<Vec2d> corners = rectangle.getPoints();
        double tolerance = calculateAdaptiveTolerance(rectangle);
        
        for (int i = 0; i < corners.size(); i++) {
            Vec2d current = corners.get(i);
            Vec2d next = corners.get((i + 1) % corners.size());
            
            if (isPointOnLine(current, next, point, tolerance)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isPointOnPolyline(Shape polylineShape, Vec2d point, double tolerance) {
        List<Vec2d> points = getShapePoints(polylineShape);
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d current = points.get(i);
            Vec2d next = points.get(i + 1);
            
            if (isPointOnLine(current, next, point, tolerance)) {
                return true;
            }
        }
        
        return false;
    }
    
    private List<Vec2d> getShapePoints(Shape shape) {
        if (shape instanceof PolylineShape polyline) {
            return polyline.getPoints();
        } else if (shape instanceof FreeDrawPath path) {
            return path.getPoints();
        } else if (shape instanceof BezierCurveShape bezier) {
            return bezier.getCurvePoints();
        } else if (shape instanceof SineCurveShape sine) {
            return sine.getPoints();
        } else if (shape instanceof SpiralShape spiral) {
            return spiral.getPoints();
            } else if (shape instanceof CableShape catenary) {
            return catenary.getPoints();
        } else if (shape instanceof Polygon polygon) {
            return polygon.getPoints();
        } else if (shape instanceof TextShape text) {
            return text.getPoints();
        }
        return shape.getPoints();
    }
    
    // ====== 辅助几何方法 ======
    
    private double pointToLineDistance(Vec2d start, Vec2d end, Vec2d point) {
        double A = end.y - start.y;
        double B = start.x - end.x;
        double C = end.x * start.y - start.x * end.y;
        
        return Math.abs(A * point.x + B * point.y + C) / Math.sqrt(A * A + B * B);
    }

    private double pointToSegmentDistance(Vec2d start, Vec2d end, Vec2d point) {
        Vec2d segment = end.subtract(start);
        double segmentLengthSquared = segment.lengthSquared();

        if (segmentLengthSquared < INTERSECTION_TOLERANCE) {
            return point.distance(start);
        }

        double t = point.subtract(start).dot(segment) / segmentLengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));

        Vec2d projection = start.add(segment.multiply(t));
        return point.distance(projection);
    }
    
    public Vec2d transformPointToEllipseLocal(Vec2d point, Vec2d center, double rotation) {
        // 平移到原点
        double x = point.x - center.x;
        double y = point.y - center.y;
        
        // 旋转
        double cosRot = Math.cos(-rotation);
        double sinRot = Math.sin(-rotation);
        double rotatedX = x * cosRot - y * sinRot;
        double rotatedY = x * sinRot + y * cosRot;
        
        return new Vec2d(rotatedX, rotatedY);
    }
    
    public double normalizeAngle(double angle) {
        while (angle < 0) {
            angle += 2 * Math.PI;
        }
        while (angle >= 2 * Math.PI) {
            angle -= 2 * Math.PI;
        }
        return angle;
    }
    
    public boolean isAngleInRange(double angle, double startAngle, double endAngle) {
        angle = normalizeAngle(angle);
        startAngle = normalizeAngle(startAngle);
        endAngle = normalizeAngle(endAngle);

        if (startAngle <= endAngle) {
            return angle >= startAngle && angle <= endAngle;
        }

        // 跨 0° 的角区间
        return angle >= startAngle || angle <= endAngle;
    }
    
    public List<Double> removeDuplicateAngles(List<Double> angles) {
        List<Double> result = new ArrayList<>();
        double tolerance = 1e-6;
        
        for (Double angle : angles) {
            boolean isDuplicate = false;
            for (Double existingAngle : result) {
                if (Math.abs(normalizeAngle(angle) - normalizeAngle(existingAngle)) < tolerance) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                result.add(angle);
            }
        }
        
        return result;
    }
    
    public double getDistanceFromStart(Vec2d start, Vec2d end, Vec2d point) {
        Vec2d direction = new Vec2d(end.x - start.x, end.y - start.y);
        Vec2d toPoint = new Vec2d(point.x - start.x, point.y - start.y);
        
        double dotProduct = direction.x * toPoint.x + direction.y * toPoint.y;
        double directionLength = Math.sqrt(direction.x * direction.x + direction.y * direction.y);
        
        if (directionLength == 0) {
            return 0;
        }
        
        return dotProduct / directionLength;
    }
    
    public double calculateAdaptiveTolerance(Shape shape) {
        // 根据图形大小动态调整容差
        double baseTolerance = TRIM_TOLERANCE;
        
        // 获取图形的边界框
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        
        List<Vec2d> points = getShapePoints(shape);
        if (points == null || points.isEmpty()) {
            return baseTolerance;
        }

        for (Vec2d point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        
        double size = Math.max(maxX - minX, maxY - minY);
        
        // 根据图形类型和大小调整容差
        if (shape instanceof CircleShape || shape instanceof ArcShape) {
            return baseTolerance * 0.5; // 圆形和圆弧使用更小的容差
        } else if (shape instanceof EllipseShape) {
            return baseTolerance * 0.7; // 椭圆使用中等容差
        } else if (shape instanceof BezierCurveShape || shape instanceof SineCurveShape || 
                   shape instanceof SpiralShape || shape instanceof CableShape) {
            return baseTolerance * 0.8; // 复杂曲线使用较小容差
        } else {
            return baseTolerance; // 其他图形使用标准容差
        }
    }
    
    // ====== 路径构建方法 ======
    
    public List<Vec2d> sortIntersectionsAlongPath(List<Vec2d> pathPoints, List<Vec2d> intersections) {
        List<Vec2d> sorted = new ArrayList<>(intersections);
        sorted.sort((a, b) -> {
            double distA = getDistanceAlongPath(pathPoints, a);
            double distB = getDistanceAlongPath(pathPoints, b);
            return Double.compare(distA, distB);
        });
        return sorted;
    }
    
    private double getDistanceAlongPath(List<Vec2d> pathPoints, Vec2d point) {
        double minDistance = Double.MAX_VALUE;
        double cumulativeDistance = 0;
        
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d current = pathPoints.get(i);
            Vec2d next = pathPoints.get(i + 1);
            
            double segmentDistance = current.distance(next);
            double pointDistance = pointToSegmentDistance(current, next, point);
            
            if (pointDistance < minDistance) {
                minDistance = pointDistance;
                return cumulativeDistance + getDistanceFromStart(current, next, point);
            }
            
            cumulativeDistance += segmentDistance;
        }
        
        return cumulativeDistance;
    }
    
    public List<Vec2d> buildBoundaryTrimmedPath(List<Vec2d> pathPoints, List<Vec2d> sortedIntersections, Vec2d trimPoint) {
        if (sortedIntersections.isEmpty()) {
            return new ArrayList<>(pathPoints);
        }
        
        List<Vec2d> result = new ArrayList<>();
        
        // 找到修剪点在路径上的位置
        int trimIndex = findTrimPointIndex(pathPoints, sortedIntersections, trimPoint);
        
        if (trimIndex == 0) {
            // 修剪点在开始，保留从第一个交点到末尾
            if (!sortedIntersections.isEmpty()) {
                addPointsAfterIntersection(result, pathPoints, sortedIntersections.getFirst());
            }
        } else if (trimIndex >= sortedIntersections.size()) {
            // 修剪点在末尾，保留从开始到最后一个交点
            if (!sortedIntersections.isEmpty()) {
                addPointsBeforeIntersection(result, pathPoints, sortedIntersections.getLast());
            }
        } else {
            // 修剪点在中间，保留不包含修剪点的段
            Vec2d prevIntersection = sortedIntersections.get(trimIndex - 1);
            Vec2d nextIntersection = sortedIntersections.get(trimIndex);
            
            addPointsBetweenIntersections(result, pathPoints, prevIntersection, nextIntersection);
        }
        
        return removeDuplicatePoints(result);
    }
    
    private int findTrimPointIndex(List<Vec2d> pathPoints, List<Vec2d> sortedIntersections, Vec2d trimPoint) {
        double trimDistance = getDistanceAlongPath(pathPoints, trimPoint);
        
        for (int i = 0; i < sortedIntersections.size(); i++) {
            double intersectionDistance = getDistanceAlongPath(pathPoints, sortedIntersections.get(i));
            if (intersectionDistance > trimDistance) {
                return i;
            }
        }
        
        return sortedIntersections.size();
    }
    
    private void addPointsBeforeIntersection(List<Vec2d> result, List<Vec2d> pathPoints, Vec2d intersection) {
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d current = pathPoints.get(i);
            Vec2d next = pathPoints.get(i + 1);
            
            result.add(current);
            
            // 检查是否到达交点
            if (isPointOnLine(current, next, intersection, INTERSECTION_TOLERANCE)) {
                result.add(intersection);
                break;
            }
        }
    }
    
    private void addPointsAfterIntersection(List<Vec2d> result, List<Vec2d> pathPoints, Vec2d intersection) {
        boolean foundIntersection = false;
        
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d current = pathPoints.get(i);
            Vec2d next = pathPoints.get(i + 1);
            
            if (foundIntersection) {
                result.add(current);
            } else if (isPointOnLine(current, next, intersection, INTERSECTION_TOLERANCE)) {
                result.add(intersection);
                result.add(next);
                foundIntersection = true;
            }
        }
        
        if (foundIntersection) {
            result.add(pathPoints.getLast());
        }
    }
    
    private void addPointsBetweenIntersections(List<Vec2d> result, List<Vec2d> pathPoints, Vec2d startIntersection, Vec2d endIntersection) {
        boolean inRange = false;
        
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d current = pathPoints.get(i);
            Vec2d next = pathPoints.get(i + 1);
            
            if (!inRange && isPointOnLine(current, next, startIntersection, INTERSECTION_TOLERANCE)) {
                result.add(startIntersection);
                inRange = true;
            } else if (inRange && isPointOnLine(current, next, endIntersection, INTERSECTION_TOLERANCE)) {
                result.add(endIntersection);
                break;
            } else if (inRange) {
                result.add(current);
            }
        }
    }
    
    public List<Vec2d> removeDuplicatePoints(List<Vec2d> points) {
        List<Vec2d> result = new ArrayList<>();

        for (Vec2d point : points) {
            boolean isDuplicate = false;
            for (Vec2d existingPoint : result) {
                if (point.distance(existingPoint) < INTERSECTION_TOLERANCE) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                result.add(point);
            }
        }
        
        return result;
    }
    
    // ====== 椭圆弧点生成 ======
    
    public List<Vec2d> createEllipseArcPoints(Vec2d center, double radiusX, double radiusY, double rotation, double startAngle, double endAngle) {
        List<Vec2d> points = new ArrayList<>();
        
        // 确保角度范围正确
        if (endAngle < startAngle) {
            endAngle += 2 * Math.PI;
        }
        
        // 计算需要多少个点
        double angleRange = endAngle - startAngle;
        int numPoints = Math.max(10, (int) (angleRange * 180 / Math.PI));
        
        for (int i = 0; i <= numPoints; i++) {
            double angle = startAngle + (i / (double) numPoints) * angleRange;
            
            // 计算椭圆上的点
            double x = radiusX * Math.cos(angle);
            double y = radiusY * Math.sin(angle);
            
            // 应用旋转
            double cosRot = Math.cos(rotation);
            double sinRot = Math.sin(rotation);
            double rotatedX = x * cosRot - y * sinRot;
            double rotatedY = x * sinRot + y * cosRot;
            
            Vec2d point = new Vec2d(
                center.x + rotatedX,
                center.y + rotatedY
            );
            points.add(point);
        }
        
        return points;
    }
    
    // ====== 中心点和半径计算 ======
    
    public Vec2d calculateCenter(List<Vec2d> points) {
        if (points.isEmpty()) {
            return new Vec2d(0, 0);
        }
        
        double sumX = 0, sumY = 0;
        for (Vec2d point : points) {
            sumX += point.x;
            sumY += point.y;
        }
        
        return new Vec2d(sumX / points.size(), sumY / points.size());
    }
    
    public double calculateRadius(List<Vec2d> points, Vec2d center) {
        if (points.isEmpty()) {
            return 0;
        }
        
        double sumSquaredDistances = 0;
        for (Vec2d point : points) {
            double distance = center.distance(point);
            sumSquaredDistances += distance * distance;
        }
        
        return Math.sqrt(sumSquaredDistances / points.size());
    }
    
    // ====== 图形分割方法 ======
    
    /**
     * 在交点处分割图形
     */
    public List<Shape> splitShapeAtIntersections(Shape shape, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        
        if (intersections.isEmpty()) {
            segments.add(shape);
            return segments;
        }
        
        switch (shape) {
            case LineShape line -> {
                return splitLineAtIntersections(line, intersections);
            }
            case PolylineShape polyline -> {
                return splitPolylineAtIntersections(polyline, intersections);
            }
            case ArcShape arc -> {
                return splitArcAtIntersections(arc, intersections);
            }
            case CircleShape circle -> {
                return splitCircleAtIntersections(circle, intersections);
            }
            case EllipseShape ellipse -> {
                return splitEllipseAtIntersections(ellipse, intersections);
            }
            case EllipticalArcShape ellipticalArc -> {
                return splitGenericShapeAtIntersections(ellipticalArc, intersections);
            }
            case BezierCurveShape bezier -> {
                return splitBezierCurveAtIntersections(bezier, intersections);
            }
            case SineCurveShape sine -> {
                return splitSineCurveAtIntersections(sine, intersections);
            }
            case SpiralShape spiral -> {
                return splitSpiralAtIntersections(spiral, intersections);
            }
            case FreeDrawPath path -> {
                return splitFreeDrawPathAtIntersections(path, intersections);
            }
                case CableShape catenary -> {
                return splitCatenaryLineAtIntersections(catenary, intersections);
            }
            default -> {
                return splitGenericShapeAtIntersections(shape, intersections);
            }
        }
    }

    private List<Shape> splitGenericShapeAtIntersections(Shape shape, List<Vec2d> intersections) {
        List<Vec2d> sampledPoints = createDensePolylineFromPoints(shape.getPoints());
        if (sampledPoints.size() < 2) {
            List<Shape> fallback = new ArrayList<>();
            fallback.add(shape);
            return fallback;
        }

        List<Vec2d> sortedIntersections = sortIntersectionsAlongPath(sampledPoints, intersections);
        if (sortedIntersections.isEmpty()) {
            List<Shape> fallback = new ArrayList<>();
            fallback.add(shape);
            return fallback;
        }

        List<Vec2d> splitPoints = new ArrayList<>();
        splitPoints.add(sampledPoints.getFirst());
        splitPoints.addAll(sortedIntersections);
        splitPoints.add(sampledPoints.getLast());
        splitPoints = removeDuplicatePoints(splitPoints);

        List<Shape> segments = new ArrayList<>();
        for (int i = 0; i < splitPoints.size() - 1; i++) {
            Vec2d start = splitPoints.get(i);
            Vec2d end = splitPoints.get(i + 1);
            if (start.distance(end) <= INTERSECTION_TOLERANCE) {
                continue;
            }

            LineShape segment = new LineShape(start, end);
            if (shape.getStyle() != null) {
                segment.setStyle(shape.getStyle().clone());
            }
            if (shape.getTransform() != null) {
                segment.setTransform(shape.getTransform().clone());
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            segments.add(shape);
        }

        return segments;
    }
    
    private List<Shape> splitCircleAtIntersections(CircleShape circle, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        Vec2d center = circle.getCenter();
        double radius = circle.getRadius();
        double circleTolerance = Math.max(INTERSECTION_TOLERANCE, radius * 1e-5);
        
        // 过滤有效的交点
        List<Vec2d> validIntersections = new ArrayList<>();
        for (Vec2d intersection : intersections) {
            if (Math.abs(center.distance(intersection) - radius) <= circleTolerance) {
                validIntersections.add(intersection);
            }
        }
        
        if (validIntersections.size() < 2) {
            segments.add(circle);
            return segments;
        }
        
        // 计算交点的角度
        List<Double> angles = new ArrayList<>();
        for (Vec2d intersection : validIntersections) {
            double angle = Math.atan2(intersection.y - center.y, intersection.x - center.x);
            angles.add(normalizeAngle(angle));
        }
        angles = removeDuplicateAngles(angles);
        angles.sort(Double::compare);

        if (angles.size() < 2) {
            segments.add(circle);
            return segments;
        }

        List<Double> cyclicAngles = new ArrayList<>(angles);
        cyclicAngles.add(angles.getFirst() + 2 * Math.PI);
        
        // 创建圆弧段
        for (int i = 0; i < cyclicAngles.size() - 1; i++) {
            double a1 = cyclicAngles.get(i);
            double a2 = cyclicAngles.get(i + 1);
            ArcShape arc = new ArcShape(center, radius, a1, a2);
            segments.add(arc);
        }
        
        return segments;
    }
    
    private List<Shape> splitEllipseAtIntersections(EllipseShape ellipse, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        Vec2d center = ellipse.getCenter();
        double radiusX = ellipse.getRadiusX();
        double radiusY = ellipse.getRadiusY();
        double rotation = ellipse.getRotation();
        
        // 过滤有效的交点
        List<Vec2d> validIntersections = new ArrayList<>();
        for (Vec2d intersection : intersections) {
            if (isPointOnEllipse(ellipse, intersection)) {
                validIntersections.add(intersection);
            }
        }
        
        if (validIntersections.size() < 2) {
            segments.add(ellipse);
            return segments;
        }
        
        // 计算交点的角度
        List<Double> angles = new ArrayList<>();
        for (Vec2d intersection : validIntersections) {
            Vec2d localPoint = transformPointToEllipseLocal(intersection, center, rotation);
            double angle = Math.atan2(localPoint.y / radiusY, localPoint.x / radiusX);
            angles.add(normalizeAngle(angle));
        }
        angles = removeDuplicateAngles(angles);
        angles.sort(Double::compare);

        if (angles.size() < 2) {
            segments.add(ellipse);
            return segments;
        }

        List<Double> cyclicAngles = new ArrayList<>(angles);
        cyclicAngles.add(angles.getFirst() + 2 * Math.PI);
        
        // 创建椭圆弧段
        for (int i = 0; i < cyclicAngles.size() - 1; i++) {
            double a1 = cyclicAngles.get(i);
            double a2 = cyclicAngles.get(i + 1);
            List<Vec2d> arcPoints = createEllipseArcPoints(center, radiusX, radiusY, rotation, a1, a2);
            if (arcPoints.size() >= 3) {
                PolylineShape arc = new PolylineShape(arcPoints, false);
                segments.add(arc);
            }
        }
        
        return segments;
    }
    
    private List<Shape> splitBezierCurveAtIntersections(BezierCurveShape bezier, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        List<Vec2d> sampledPoints = createDenseBezierPoints(bezier);

        if (intersections.isEmpty() || sampledPoints.size() < 2) {
            segments.add(bezier);
            return segments;
        }

        List<Vec2d> sortedIntersections = sortIntersectionsAlongPath(sampledPoints, intersections);
        List<Vec2d> splitPoints = new ArrayList<>();
        splitPoints.add(sampledPoints.getFirst());
        splitPoints.addAll(sortedIntersections);
        splitPoints.add(sampledPoints.getLast());
        splitPoints = removeDuplicatePoints(splitPoints);

        for (int i = 0; i < splitPoints.size() - 1; i++) {
            Vec2d start = splitPoints.get(i);
            Vec2d end = splitPoints.get(i + 1);
            if (start.distance(end) <= INTERSECTION_TOLERANCE) {
                continue;
            }

            List<Vec2d> segmentPoints = extractPathSection(sampledPoints, start, end);
            if (segmentPoints.size() < 2) {
                continue;
            }

            PolylineShape segment = new PolylineShape(segmentPoints, false);
            if (bezier.getStyle() != null) {
                segment.setStyle(bezier.getStyle().clone());
            }
            if (bezier.getTransform() != null) {
                segment.setTransform(bezier.getTransform().clone());
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            segments.add(bezier);
        }

        return segments;
    }

    private List<Shape> splitLineAtIntersections(LineShape line, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();

        List<Vec2d> splitPoints = new ArrayList<>();
        splitPoints.add(line.getStart());
        for (Vec2d intersection : intersections) {
            if (isPointOnLine(line.getStart(), line.getEnd(), intersection, INTERSECTION_TOLERANCE)) {
                splitPoints.add(intersection);
            }
        }
        splitPoints.add(line.getEnd());

        splitPoints = removeDuplicatePoints(splitPoints);
        splitPoints.sort((a, b) -> Double.compare(
            getDistanceFromStart(line.getStart(), line.getEnd(), a),
            getDistanceFromStart(line.getStart(), line.getEnd(), b)
        ));

        for (int i = 0; i < splitPoints.size() - 1; i++) {
            Vec2d start = splitPoints.get(i);
            Vec2d end = splitPoints.get(i + 1);
            if (start.distance(end) > INTERSECTION_TOLERANCE) {
                LineShape segment = new LineShape(start, end);
                if (line.getStyle() != null) {
                    segment.setStyle(line.getStyle().clone());
                }
                if (line.getTransform() != null) {
                    segment.setTransform(line.getTransform().clone());
                }
                segments.add(segment);
            }
        }

        if (segments.isEmpty()) {
            segments.add(line);
        }

        return segments;
    }

    private List<Shape> splitPolylineAtIntersections(PolylineShape polyline, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        List<Vec2d> points = polyline.getPoints();

        if (points == null || points.size() < 2) {
            segments.add(polyline);
            return segments;
        }

        List<Vec2d> sortedIntersections = sortIntersectionsAlongPath(points, intersections);
        List<Vec2d> splitPoints = new ArrayList<>();
        splitPoints.add(points.getFirst());
        splitPoints.addAll(sortedIntersections);
        splitPoints.add(points.getLast());
        splitPoints = removeDuplicatePoints(splitPoints);

        for (int i = 0; i < splitPoints.size() - 1; i++) {
            Vec2d start = splitPoints.get(i);
            Vec2d end = splitPoints.get(i + 1);
            if (start.distance(end) <= INTERSECTION_TOLERANCE) {
                continue;
            }

            List<Vec2d> segmentPoints = extractPathSection(points, start, end);
            if (segmentPoints.size() < 2) {
                continue;
            }

            PolylineShape segment = new PolylineShape(segmentPoints, false);
            if (polyline.getStyle() != null) {
                segment.setStyle(polyline.getStyle().clone());
            }
            if (polyline.getTransform() != null) {
                segment.setTransform(polyline.getTransform().clone());
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            segments.add(polyline);
        }

        return segments;
    }

    private List<Shape> splitArcAtIntersections(ArcShape arc, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        Vec2d center = arc.getCenter();
        double radius = arc.getRadius();

        List<Double> validAngles = new ArrayList<>();
        validAngles.add(normalizeAngle(arc.getStartAngle()));
        for (Vec2d intersection : intersections) {
            if (isPointOnArc(arc, intersection)) {
                double angle = Math.atan2(intersection.y - center.y, intersection.x - center.x);
                validAngles.add(normalizeAngle(angle));
            }
        }
        validAngles.add(normalizeAngle(arc.getEndAngle()));

        validAngles = removeDuplicateAngles(validAngles);
        validAngles.sort(Double::compare);

        for (int i = 0; i < validAngles.size() - 1; i++) {
            double a1 = validAngles.get(i);
            double a2 = validAngles.get(i + 1);
            if (!isAngleInRange(normalizeAngle((a1 + a2) * 0.5), arc.getStartAngle(), arc.getEndAngle())) {
                continue;
            }

            ArcShape segment = new ArcShape(center, radius, a1, a2);
            if (arc.getStyle() != null) {
                segment.setStyle(arc.getStyle().clone());
            }
            if (arc.getTransform() != null) {
                segment.setTransform(arc.getTransform().clone());
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            segments.add(arc);
        }

        return segments;
    }
    
    private List<Shape> splitSineCurveAtIntersections(SineCurveShape sine, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        List<Vec2d> points = sine.getPoints();
        
        if (intersections.isEmpty() || points.size() < 2) {
            segments.add(sine);
            return segments;
        }
        
        List<Vec2d> sortedIntersections = sortIntersectionsAlongPath(points, intersections);

        List<Vec2d> splitPoints = new ArrayList<>();
        splitPoints.add(points.getFirst());
        splitPoints.addAll(sortedIntersections);
        splitPoints.add(points.getLast());
        splitPoints = removeDuplicatePoints(splitPoints);

        for (int i = 0; i < splitPoints.size() - 1; i++) {
            Vec2d start = splitPoints.get(i);
            Vec2d end = splitPoints.get(i + 1);
            if (start.distance(end) <= INTERSECTION_TOLERANCE) {
                continue;
            }

            List<Vec2d> segmentPoints = extractPathSection(points, start, end);
            if (segmentPoints.size() < 2) {
                continue;
            }

            PolylineShape segment = new PolylineShape(segmentPoints, false);
            if (sine.getStyle() != null) {
                segment.setStyle(sine.getStyle().clone());
            }
            if (sine.getTransform() != null) {
                segment.setTransform(sine.getTransform().clone());
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            segments.add(sine);
        }
        
        return segments;
    }
    
    private List<Shape> splitSpiralAtIntersections(SpiralShape spiral, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        List<Vec2d> points = spiral.getPoints();
        
        if (intersections.isEmpty() || points.size() < 2) {
            segments.add(spiral);
            return segments;
        }
        
        List<Vec2d> sortedIntersections = sortIntersectionsAlongPath(points, intersections);

        List<Vec2d> splitPoints = new ArrayList<>();
        splitPoints.add(points.getFirst());
        splitPoints.addAll(sortedIntersections);
        splitPoints.add(points.getLast());
        splitPoints = removeDuplicatePoints(splitPoints);

        for (int i = 0; i < splitPoints.size() - 1; i++) {
            Vec2d start = splitPoints.get(i);
            Vec2d end = splitPoints.get(i + 1);
            if (start.distance(end) <= INTERSECTION_TOLERANCE) {
                continue;
            }

            List<Vec2d> segmentPoints = extractPathSection(points, start, end);
            if (segmentPoints.size() < 2) {
                continue;
            }

            PolylineShape segment = new PolylineShape(segmentPoints, false);
            if (spiral.getStyle() != null) {
                segment.setStyle(spiral.getStyle().clone());
            }
            if (spiral.getTransform() != null) {
                segment.setTransform(spiral.getTransform().clone());
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            segments.add(spiral);
        }
        
        return segments;
    }
    
    private List<Shape> splitFreeDrawPathAtIntersections(FreeDrawPath path, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        List<Vec2d> points = path.getPoints();
        
        if (intersections.isEmpty() || points.size() < 2) {
            segments.add(path);
            return segments;
        }
        
        List<Vec2d> sortedIntersections = sortIntersectionsAlongPath(points, intersections);

        List<Vec2d> splitPoints = new ArrayList<>();
        splitPoints.add(points.getFirst());
        splitPoints.addAll(sortedIntersections);
        splitPoints.add(points.getLast());
        splitPoints = removeDuplicatePoints(splitPoints);

        for (int i = 0; i < splitPoints.size() - 1; i++) {
            Vec2d start = splitPoints.get(i);
            Vec2d end = splitPoints.get(i + 1);
            if (start.distance(end) <= INTERSECTION_TOLERANCE) {
                continue;
            }

            List<Vec2d> segmentPoints = extractPathSection(points, start, end);
            if (segmentPoints.size() < 2) {
                continue;
            }

            FreeDrawPath segment = new FreeDrawPath(segmentPoints);
            if (path.getStyle() != null) {
                segment.setStyle(path.getStyle().clone());
            }
            if (path.getTransform() != null) {
                segment.setTransform(path.getTransform().clone());
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            segments.add(path);
        }
        
        return segments;
    }

    private List<Vec2d> extractPathSection(List<Vec2d> pathPoints, Vec2d start, Vec2d end) {
        List<Vec2d> section = new ArrayList<>();
        if (pathPoints == null || pathPoints.size() < 2) {
            return section;
        }

        boolean started = false;
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec2d current = pathPoints.get(i);
            Vec2d next = pathPoints.get(i + 1);

            if (!started) {
                if (isPointOnLine(current, next, start, INTERSECTION_TOLERANCE)) {
                    section.add(start);
                    started = true;

                    if (isPointOnLine(current, next, end, INTERSECTION_TOLERANCE)) {
                        if (section.getLast().distance(end) > INTERSECTION_TOLERANCE) {
                            section.add(end);
                        }
                        break;
                    }

                    if (section.getLast().distance(next) > INTERSECTION_TOLERANCE) {
                        section.add(next);
                    }
                }
                continue;
            }

            if (isPointOnLine(current, next, end, INTERSECTION_TOLERANCE)) {
                if (section.getLast().distance(end) > INTERSECTION_TOLERANCE) {
                    section.add(end);
                }
                break;
            }

            if (section.getLast().distance(next) > INTERSECTION_TOLERANCE) {
                section.add(next);
            }
        }

        return removeDuplicatePoints(section);
    }
    
    private List<Shape> splitCatenaryLineAtIntersections(CableShape catenary, List<Vec2d> intersections) {
        List<Shape> segments = new ArrayList<>();
        List<Vec2d> points = catenary.getPoints();
        
        if (intersections.isEmpty() || points.size() < 2) {
            segments.add(catenary);
            return segments;
        }
        
        List<Vec2d> sortedIntersections = sortIntersectionsAlongPath(points, intersections);

        List<Vec2d> splitPoints = new ArrayList<>();
        splitPoints.add(points.getFirst());
        splitPoints.addAll(sortedIntersections);
        splitPoints.add(points.getLast());
        splitPoints = removeDuplicatePoints(splitPoints);

        for (int i = 0; i < splitPoints.size() - 1; i++) {
            Vec2d start = splitPoints.get(i);
            Vec2d end = splitPoints.get(i + 1);
            if (start.distance(end) <= INTERSECTION_TOLERANCE) {
                continue;
            }

            List<Vec2d> segmentPoints = extractPathSection(points, start, end);
            if (segmentPoints.size() < 2) {
                continue;
            }

            PolylineShape segment = new PolylineShape(segmentPoints, false);
            if (catenary.getStyle() != null) {
                segment.setStyle(catenary.getStyle().clone());
            }
            if (catenary.getTransform() != null) {
                segment.setTransform(catenary.getTransform().clone());
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            segments.add(catenary);
        }
        
        return segments;
    }
    
    // ====== 矩形交点排序 ======
    
    public List<Vec2d> sortIntersectionsAlongRectangle(RectangleShape rectangle, List<Vec2d> intersections) {
        List<Vec2d> sorted = new ArrayList<>(intersections);
        List<Vec2d> corners = rectangle.getPoints();
        
        sorted.sort((a, b) -> {
            double distA = getDistanceAlongRectangle(corners, a);
            double distB = getDistanceAlongRectangle(corners, b);
            return Double.compare(distA, distB);
        });
        
        return sorted;
    }
    
    private double getDistanceAlongRectangle(List<Vec2d> corners, Vec2d point) {
        double minDistance = Double.MAX_VALUE;
        double cumulativeDistance = 0;
        
        for (int i = 0; i < corners.size(); i++) {
            Vec2d current = corners.get(i);
            Vec2d next = corners.get((i + 1) % corners.size());
            
            double segmentDistance = current.distance(next);
            double pointDistance = pointToLineDistance(current, next, point);
            
            if (pointDistance < minDistance) {
                minDistance = pointDistance;
                return cumulativeDistance + getDistanceFromStart(current, next, point);
            }
            
            cumulativeDistance += segmentDistance;
        }
        
        return cumulativeDistance;
    }
}