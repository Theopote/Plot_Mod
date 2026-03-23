package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.api.render.IRenderVisitor;
import com.plot.api.shape.IExtendableShape;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.model.Shape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.graphics.style.LineStyle;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 表示自由绘制的路径
 */
public class FreeDrawPath extends Shape implements IExtendableShape {
    private List<Vec2d> points;
    private static final Logger LOGGER = LoggerFactory.getLogger(FreeDrawPath.class);

    public FreeDrawPath(List<Vec2d> points) {
        super(points != null && !points.isEmpty() ? points.getFirst() : new Vec2d(0, 0));
        this.points = validateAndCleanPoints(points);
    }

    public void setPoints(List<Vec2d> points) {
        this.points = validateAndCleanPoints(points);
    }

    public List<Vec2d> getPoints() {
        return new ArrayList<>(points);
    }

    @Override
    public void draw(DrawContext context) {
        if (points.size() < 2) {
            LOGGER.debug("Path.draw: 点数不足，无法绘制");
            return;
        }

        // 优先使用DrawContext中设置的样式（用于选中和高亮状态）
        ShapeStyle activeStyle = context.getCurrentStyle();
        if (activeStyle == null) {
            // 如果DrawContext没有设置样式，使用图形自己的样式
            activeStyle = (ShapeStyle) getStyle();
        }
        
        if (activeStyle == null) {
            LOGGER.error("Path.draw: 样式为空，无法绘制");
            return;
        }
        
        LOGGER.debug("Path.draw: 开始绘制路径，点数={}", points.size());
        
        // 检查DrawContext是否有效
        if (context.getDrawList() == null) {
            LOGGER.error("Path.draw: DrawList为空，无法绘制");
            return;
        }
        
        // 记录相机状态
        CanvasCamera camera = context.getCamera();
        LOGGER.debug("Path.draw: 相机状态={}", camera != null ? "有效" : "无效");

        // 绘制路径的所有线段
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            LOGGER.debug("Path.draw: 绘制线段 ({}, {}) -> ({}, {})", p1.x, p1.y, p2.x, p2.y);
            
            // 使用LineStyle绘制线段，支持线宽
            if (activeStyle.getLineStyle() instanceof LineStyle lineStyle) {
                context.drawLine(p1, p2, lineStyle);
            } else {
                // 如果没有LineStyle，使用默认颜色绘制
                Color lineColor = activeStyle.getLineStyle().getColor();
            context.drawLine(p1, p2, lineColor);
            }
        }
        
        LOGGER.debug("Path.draw: 路径绘制完成");
    }

    @Override
    public Shape clone() {
        Shape shape = super.clone();
        return new FreeDrawPath(new ArrayList<>(points));
    }

    @Override
    public BoundingBox getBoundingBox() {
        if (points.isEmpty()) return new BoundingBox(0, 0, 0, 0);
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        
        for (Vec2d p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        
        // 注意：BoundingBox(minX, minY, maxX, maxY) 接收的是坐标极值，不是宽高
        return new BoundingBox(minX, minY, maxX, maxY);
    }

    @Override
    public void translate(Vec2d offset) {
        points.replaceAll(vec2d -> vec2d.add(offset));
    }

    @Override
    public Vec2d getPosition() {
        return points.isEmpty() ? new Vec2d(0, 0) : points.getFirst();
    }

    @Override
    public void setPosition(Vec2d position) {
        if (!points.isEmpty()) {
            Vec2d offset = position.subtract(getPosition());
            translate(offset);
        }
    }

    @Override
    public void rotate(double angle, Vec2d center) {
        if (points.isEmpty()) return;
        
        // 对每个点进行旋转
        for (int i = 0; i < points.size(); i++) {
            Vec2d p = points.get(i);
            
            // 计算相对于中心点的向量
            double dx = p.x - center.x;
            double dy = p.y - center.y;
            
            // 应用旋转变换
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double newX = center.x + dx * cos - dy * sin;
            double newY = center.y + dx * sin + dy * cos;
            
            // 更新点的位置
            points.set(i, new Vec2d(newX, newY));
        }
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 变换所有定义点
        points.replaceAll(transformMatrix::transform);
        return this; // 自由绘制路径变换后仍然是自由绘制路径
    }

    @Override
    public double getRotation() {
        // 对于自由绘制的路径，没有单一的旋转角度
        return 0.0;
    }

    @Override
    public void setRotation(double angle) {
        // 对于自由绘制的路径，设置旋转角度没有意义
        // 如果需要旋转，应该使用 rotate 方法
    }

    @Override
    public Vec2d getScale() {
        // 返回默认缩放
        return new Vec2d(1.0, 1.0);
    }

    public void setScale(Vec2d scale) {
        // 暂不支持缩放
    }

    @Override
    public boolean contains(Vec2d point) {
        return containsPoint(point, 5.0); // 使用默认容差
    }
    
    /**
     * 检查点是否在自由绘制路径上（带容差）
     * 
     * @param point 要检查的点
     * @param tolerance 容差值（像素）
     * @return 如果点在路径上则返回true
     */
    public boolean containsPoint(Vec2d point, double tolerance) {
        if (points.size() < 2) return false;
        
        // 检查点是否在路径的任意线段附近
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            // 计算点到线段的距离
            double distance = distanceToLineSegment(point, p1, p2);
            if (distance <= tolerance) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 计算点到线段的距离的辅助方法
     * 这是一个通用的几何计算方法，可以被多个方法复用
     */
    private double distanceToLineSegment(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        return GeometryUtils.pointToSegmentDistance(point, lineStart, lineEnd);
    }

    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        if (points.isEmpty()) return point;
        if (points.size() == 1) return points.getFirst();
        
        Vec2d closestPoint = null;
        double minDistance = Double.MAX_VALUE;
        
        // 遍历所有线段，找到最近的点
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            // 计算线段上的最近点
            double l2 = p1.distanceSquared(p2);
            if (l2 == 0) {
                // 线段退化为点
                double dist = point.distance(p1);
                if (dist < minDistance) {
                    minDistance = dist;
                    closestPoint = p1;
                }
                continue;
            }
            
            // 计算投影参数 t
            double t = Math.max(0, Math.min(1, 
                ((point.x - p1.x) * (p2.x - p1.x) + 
                 (point.y - p1.y) * (p2.y - p1.y)) / l2));
            
            // 计算投影点
            double projX = p1.x + t * (p2.x - p1.x);
            double projY = p1.y + t * (p2.y - p1.y);
            Vec2d projPoint = new Vec2d(projX, projY);
            
            // 更新最近点
            double distance = point.distance(projPoint);
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = projPoint;
            }
        }
        
        return closestPoint != null ? closestPoint : points.getFirst();
    }

    @Override
    public List<Vec2d> getControlPoints() {
        return new ArrayList<>(points);
    }

    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index >= 0 && index < points.size()) {
            points.set(index, point);
        }
    }

    @Override
    public boolean intersects(Shape other) {
        // 首先检查边界框是否相交
        if (!getBoundingBox().intersects(other.getBoundingBox())) {
            return false;
        }

        // 获取另一个形状的控制点
        List<Vec2d> otherPoints = other.getControlPoints();
        if (otherPoints.size() < 2) return false;

        // 检查线段之间的相交
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);

            for (int j = 0; j < otherPoints.size() - 1; j++) {
                Vec2d q1 = otherPoints.get(j);
                Vec2d q2 = otherPoints.get(j + 1);

                if (getLineSegmentIntersection(p1, p2, q1, q2) != null) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        // 使用 getIntersectionsWith 的实现
        return getIntersectionsWith(other);
    }

    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        List<Vec2d> intersections = new ArrayList<>();
        
        if (points.size() < 2) return intersections;
        
        // 对于不同类型的图形，使用不同的交点计算方法
        switch (other) {
            case FreeDrawPath otherPath -> {
                // 与其他自由绘制路径的交点
                List<Vec2d> otherPoints = otherPath.getPoints();
                intersections = calculatePolylineIntersections(points, otherPoints);
            }
            case LineShape line -> {
                // 与直线的交点
                Vec2d lineStart = line.getStart();
                Vec2d lineEnd = line.getEnd();

                for (int i = 0; i < points.size() - 1; i++) {
                    Vec2d p1 = points.get(i);
                    Vec2d p2 = points.get(i + 1);

                    Vec2d intersection = getLineSegmentIntersection(p1, p2, lineStart, lineEnd);
                    if (intersection != null) {
                        intersections.add(intersection);
                    }
                }
            }
            case PolylineShape polyline -> {
                // 与多段线的交点
                List<Vec2d> polylinePoints = polyline.getPoints();
                intersections = calculatePolylineIntersections(points, polylinePoints);
            }
            case CircleShape circle -> {
                // 与圆的交点
                Vec2d center = circle.getCenter();
                double radius = circle.getRadius();

                for (int i = 0; i < points.size() - 1; i++) {
                    Vec2d p1 = points.get(i);
                    Vec2d p2 = points.get(i + 1);

                    List<Vec2d> circleIntersections = calculateCircleLineIntersections(center, radius, p1, p2);
                    intersections.addAll(circleIntersections);
                }
            }
            case EllipseShape ellipse -> {
                // 与椭圆的交点
                Vec2d center = ellipse.getCenter();
                double radiusX = ellipse.getRadiusX();
                double radiusY = ellipse.getRadiusY();
                double rotation = ellipse.getRotation();

                for (int i = 0; i < points.size() - 1; i++) {
                    Vec2d p1 = points.get(i);
                    Vec2d p2 = points.get(i + 1);

                    List<Vec2d> ellipseIntersections = calculateEllipseLineIntersections(center, radiusX, radiusY, rotation, p1, p2);
                    intersections.addAll(ellipseIntersections);
                }
            }
            case ArcShape arc -> {
                // 与圆弧的交点
                Vec2d center = arc.getCenter();
                double radius = arc.getRadius();
                double startAngle = arc.getStartAngle();
                double endAngle = arc.getEndAngle();

                for (int i = 0; i < points.size() - 1; i++) {
                    Vec2d p1 = points.get(i);
                    Vec2d p2 = points.get(i + 1);

                    List<Vec2d> arcIntersections = calculateArcLineIntersections(center, radius, startAngle, endAngle, p1, p2);
                    intersections.addAll(arcIntersections);
                }
            }
            case BezierCurveShape bezier -> {
                // 与贝塞尔曲线的交点
                List<Vec2d> bezierPoints = bezier.getControlPoints();
                if (bezierPoints.size() >= 2) {
                    // 将贝塞尔曲线离散化为多个线段，然后计算交点
                    List<Vec2d> discretizedBezier = discretizeBezierCurve(bezierPoints, 20);
                    intersections = calculatePolylineIntersections(points, discretizedBezier);
                }
            }
            case RectangleShape rectangle -> {
                // 与矩形的交点
                BoundingBox bbox = rectangle.getBoundingBox();
                if (bbox != null) {
                    Vec2d min = bbox.getMin();
                    Vec2d max = bbox.getMax();
                    
                    // 创建矩形的四条边
                    List<Vec2d> rectPoints = List.of(
                        min, new Vec2d(max.x, min.y), max, new Vec2d(min.x, max.y), min
                    );
                    intersections = calculatePolylineIntersections(points, rectPoints);
                }
            }
            case null, default -> {
                // 对于其他类型的图形，尝试使用通用的交点计算方法
                try {
                    // 将当前路径转换为多段线，然后使用多段线的交点计算方法
                    PolylineShape polyline = new PolylineShape(points, false);
                    intersections = polyline.getIntersectionsWith(other);
                } catch (Exception e) {
                    if (other != null) {
                        LOGGER.warn("无法计算与图形 {} 的交点: {}", other.getClass().getSimpleName(), e.getMessage());
                    }
                }
            }
        }
        
        // 移除重复的交点
        return removeDuplicateIntersections(intersections);
    }
    
    /**
     * 计算圆与线段的交点
     */
    private List<Vec2d> calculateCircleLineIntersections(Vec2d center, double radius, Vec2d p1, Vec2d p2) {
        List<Vec2d> intersections = new ArrayList<>();
        
        // 将线段转换为相对于圆心的坐标
        double x1 = p1.x - center.x;
        double y1 = p1.y - center.y;
        double x2 = p2.x - center.x;
        double y2 = p2.y - center.y;
        
        // 计算线段的方向向量
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dr = Math.sqrt(dx * dx + dy * dy);
        
        if (dr < 1e-10) return intersections; // 线段退化为点
        
        // 计算判别式
        double D = x1 * y2 - x2 * y1;
        double discriminant = radius * radius * dr * dr - D * D;
        
        if (discriminant < 0) return intersections; // 无交点
        
        // 计算交点
        double sqrtDisc = Math.sqrt(discriminant);
        double sign = dy < 0 ? -1 : 1;
        
        double x3 = (D * dy + sign * dx * sqrtDisc) / (dr * dr);
        double y3 = (-D * dx + Math.abs(dy) * sqrtDisc) / (dr * dr);
        double x4 = (D * dy - sign * dx * sqrtDisc) / (dr * dr);
        double y4 = (-D * dx - Math.abs(dy) * sqrtDisc) / (dr * dr);
        
        // 检查交点是否在线段上
        Vec2d intersection1 = new Vec2d(x3 + center.x, y3 + center.y);
        Vec2d intersection2 = new Vec2d(x4 + center.x, y4 + center.y);
        
        if (isPointOnLineSegment(intersection1, p1, p2)) {
            intersections.add(intersection1);
        }
        if (isPointOnLineSegment(intersection2, p1, p2)) {
            intersections.add(intersection2);
        }
        
        return intersections;
    }
    
    /**
     * 计算椭圆与线段的交点
     */
    private List<Vec2d> calculateEllipseLineIntersections(Vec2d center, double radiusX, double radiusY, double rotation, Vec2d p1, Vec2d p2) {
        List<Vec2d> intersections = new ArrayList<>();
        
        // 将线段转换到椭圆的局部坐标系
        Vec2d localP1 = p1.subtract(center);
        Vec2d localP2 = p2.subtract(center);
        
        // 应用反向旋转
        if (Math.abs(rotation) > 1e-10) {
            double cos = Math.cos(-rotation);
            double sin = Math.sin(-rotation);
            localP1 = new Vec2d(localP1.x * cos - localP1.y * sin, localP1.x * sin + localP1.y * cos);
            localP2 = new Vec2d(localP2.x * cos - localP2.y * sin, localP2.x * sin + localP2.y * cos);
        }
        
        // 使用标准椭圆-直线相交算法
        double dx = localP2.x - localP1.x;
        double dy = localP2.y - localP1.y;
        double dr = Math.sqrt(dx * dx + dy * dy);
        
        if (dr < 1e-10) return intersections; // 线段退化为点
        
        // 计算判别式
        double D = localP1.x * localP2.y - localP2.x * localP1.y;
        double discriminant = radiusX * radiusX * radiusY * radiusY * dr * dr - D * D;
        
        if (discriminant < 0) return intersections; // 无交点
        
        // 计算交点
        double sqrtDisc = Math.sqrt(discriminant);
        double sign = dy < 0 ? -1 : 1;
        
        double x1 = (D * dy + sign * dx * sqrtDisc) / (dr * dr);
        double y1 = (-D * dx + Math.abs(dy) * sqrtDisc) / (dr * dr);
        double x2 = (D * dy - sign * dx * sqrtDisc) / (dr * dr);
        double y2 = (-D * dx - Math.abs(dy) * sqrtDisc) / (dr * dr);
        
        // 检查交点是否在线段上
        Vec2d intersection1 = new Vec2d(x1, y1);
        Vec2d intersection2 = new Vec2d(x2, y2);
        
        if (isPointOnLineSegment(intersection1, localP1, localP2)) {
            // 应用正向旋转和偏移
            if (Math.abs(rotation) > 1e-10) {
                double cos = Math.cos(rotation);
                double sin = Math.sin(rotation);
                intersection1 = new Vec2d(intersection1.x * cos - intersection1.y * sin, intersection1.x * sin + intersection1.y * cos);
            }
            intersections.add(intersection1.add(center));
        }
        
        if (isPointOnLineSegment(intersection2, localP1, localP2)) {
            // 应用正向旋转和偏移
            if (Math.abs(rotation) > 1e-10) {
                double cos = Math.cos(rotation);
                double sin = Math.sin(rotation);
                intersection2 = new Vec2d(intersection2.x * cos - intersection2.y * sin, intersection2.x * sin + intersection2.y * cos);
            }
            intersections.add(intersection2.add(center));
        }
        
        return intersections;
    }
    
    /**
     * 计算圆弧与线段的交点
     */
    private List<Vec2d> calculateArcLineIntersections(Vec2d center, double radius, double startAngle, double endAngle, Vec2d p1, Vec2d p2) {
        List<Vec2d> intersections = new ArrayList<>();
        
        // 首先计算与完整圆的交点
        List<Vec2d> circleIntersections = calculateCircleLineIntersections(center, radius, p1, p2);
        
        // 然后过滤出在圆弧范围内的交点
        for (Vec2d intersection : circleIntersections) {
            Vec2d relative = intersection.subtract(center);
            double angle = Math.atan2(relative.y, relative.x);
            
            // 标准化角度到 [0, 2π] 范围
            if (angle < 0) angle += 2 * Math.PI;
            if (startAngle < 0) startAngle += 2 * Math.PI;
            if (endAngle < 0) endAngle += 2 * Math.PI;
            
            // 检查角度是否在圆弧范围内
            boolean inRange;
            if (startAngle <= endAngle) {
                inRange = angle >= startAngle && angle <= endAngle;
            } else {
                // 跨越0度的情况
                inRange = angle >= startAngle || angle <= endAngle;
            }
            
            if (inRange) {
                intersections.add(intersection);
            }
        }
        
        return intersections;
    }
    
    /**
     * 将贝塞尔曲线离散化为多个线段
     */
    private List<Vec2d> discretizeBezierCurve(List<Vec2d> controlPoints, int segments) {
        List<Vec2d> result = new ArrayList<>();
        
        if (controlPoints.size() < 2) return result;
        
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Vec2d point = evaluateBezierCurve(controlPoints, t);
            result.add(point);
        }
        
        return result;
    }
    
    /**
     * 计算贝塞尔曲线上指定参数t处的点
     */
    private Vec2d evaluateBezierCurve(List<Vec2d> controlPoints, double t) {
        int n = controlPoints.size() - 1;
        Vec2d result = new Vec2d(0, 0);
        
        for (int i = 0; i <= n; i++) {
            double bernstein = bernsteinPolynomial(n, i, t);
            result = result.add(controlPoints.get(i).multiply(bernstein));
        }
        
        return result;
    }
    
    /**
     * 计算伯恩斯坦多项式
     */
    private double bernsteinPolynomial(int n, int i, double t) {
        return binomialCoefficient(n, i) * Math.pow(t, i) * Math.pow(1 - t, n - i);
    }
    
    /**
     * 计算二项式系数
     */
    private double binomialCoefficient(int n, int k) {
        if (k > n - k) k = n - k; // 利用对称性
        
        double result = 1.0;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }
    
    /**
     * 移除重复的交点
     */
    private List<Vec2d> removeDuplicateIntersections(List<Vec2d> intersections) {
        List<Vec2d> result = new ArrayList<>();
        double tolerance = 1e-6;
        
        for (Vec2d intersection : intersections) {
            boolean isDuplicate = false;
            for (Vec2d existing : result) {
                if (intersection.distance(existing) < tolerance) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                result.add(intersection);
            }
        }
        
        return result;
    }

    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 简单实现：返回空列表
        return new ArrayList<>();
    }

    @Override
    public List<Vec2d> getEndpoints() {
        List<Vec2d> endpoints = new ArrayList<>();
        if (!points.isEmpty()) {
            endpoints.add(points.getFirst());
            endpoints.add(points.getLast());
        }
        return endpoints;
    }

    @Override
    public Vec2d getTangentAt(Vec2d point) {
        // 简单实现：返回最近线段的方向向量
        if (points.size() < 2) return new Vec2d(1, 0);
        
        // 找到最近的线段
        Vec2d closestPoint = getClosestPoint(point);
        int segmentIndex = -1;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            double distance = distanceToLineSegment(closestPoint, p1, p2);
            if (distance < minDistance) {
                minDistance = distance;
                segmentIndex = i;
            }
        }
        
        if (segmentIndex >= 0) {
            Vec2d p1 = points.get(segmentIndex);
            Vec2d p2 = points.get(segmentIndex + 1);
            return p2.subtract(p1).normalize();
        }
        
        return new Vec2d(1, 0);
    }

    @Override
    public double getSignedDistance(Vec2d point) {
        // 对于路径，我们只返回无符号距离
        if (points.size() < 2) return Double.MAX_VALUE;
        
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            double distance = distanceToLineSegment(point, p1, p2);
            minDistance = Math.min(minDistance, distance);
        }
        
        return minDistance;
    }

    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        List<Shape> result = new ArrayList<>();
        if (this.points.size() < 2 || points.isEmpty()) return result;

        // 找到最近的分割点
        Vec2d splitPoint = getClosestPoint(pickPoint);
        
        // 找到包含分割点的线段索引
        int segmentIndex = findSegmentContainingPoint(splitPoint);
        if (segmentIndex < 0) {
            LOGGER.warn("无法找到包含分割点的线段");
            return result;
        }
        
        // 创建两个新的路径，使用更精确的分割方法
        List<Vec2d> path1Points = new ArrayList<>();
        List<Vec2d> path2Points = new ArrayList<>();
        
        // 第一段：从起点到分割点
        for (int i = 0; i <= segmentIndex; i++) {
            path1Points.add(this.points.get(i));
        }
        path1Points.add(splitPoint);
        
        // 第二段：从分割点到终点
        path2Points.add(splitPoint);
        for (int i = segmentIndex + 1; i < this.points.size(); i++) {
            path2Points.add(this.points.get(i));
        }
        
        // 只有当两个路径都至少有两个点时才添加到结果中
        if (path1Points.size() >= 2) {
            FreeDrawPath path1 = new FreeDrawPath(path1Points);
            if (getStyle() != null) path1.setStyle(getStyle().clone());
            if (getTransform() != null) path1.setTransform(getTransform().clone());
            result.add(path1);
        }
        
        if (path2Points.size() >= 2) {
            FreeDrawPath path2 = new FreeDrawPath(path2Points);
            if (getStyle() != null) path2.setStyle(getStyle().clone());
            if (getTransform() != null) path2.setTransform(getTransform().clone());
            result.add(path2);
        }
        
        return result;
    }
    
    /**
     * 找到包含指定点的线段索引
     */
    private int findSegmentContainingPoint(Vec2d point) {
        double minDistance = Double.MAX_VALUE;
        int bestSegment = -1;
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            double distance = distanceToLineSegment(point, p1, p2);
            if (distance < minDistance) {
                minDistance = distance;
                bestSegment = i;
            }
        }
        
        return bestSegment;
    }

    @Override
    public Shape extend(Vec2d point, double distance) {
        if (points.size() < 2) return this;
        
        // 找到最近的端点
        Vec2d start = points.getFirst();
        Vec2d end = points.getLast();
        boolean extendStart = point.distance(start) < point.distance(end);
        
        List<Vec2d> newPoints = new ArrayList<>(points);
        if (extendStart) {
            // 延伸起点
            Vec2d direction = points.get(1).subtract(start).normalize();
            Vec2d newStart = start.subtract(direction.multiply(distance));
            newPoints.set(0, newStart);
        } else {
            // 延伸终点
            Vec2d direction = end.subtract(points.get(points.size() - 2)).normalize();
            Vec2d newEnd = end.add(direction.multiply(distance));
            newPoints.set(newPoints.size() - 1, newEnd);
        }

        FreeDrawPath extendedPath = new FreeDrawPath(newPoints);
        extendedPath.setStyle(this.getStyle().clone());
        return extendedPath;
    }

    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        if (points.size() < 2) return this;
        
        // 找到最近的端点
        Vec2d start = points.getFirst();
        Vec2d end = points.getLast();
        boolean extendStart = point.distance(start) < point.distance(end);
        
        List<Vec2d> newPoints = new ArrayList<>(points);
        if (extendStart) {
            newPoints.set(0, toPoint);
        } else {
            newPoints.set(newPoints.size() - 1, toPoint);
        }

        FreeDrawPath extendedPath = new FreeDrawPath(newPoints);
        extendedPath.setStyle(this.getStyle().clone());
        return extendedPath;
    }

    @Override
    public Shape trimToPoint(Vec2d point) {
        if (points.size() < 2) return this;
        
        // 找到最近的修剪点
        Vec2d trimPoint = getClosestPoint(point);
        int closestSegment = findSegmentContainingPoint(trimPoint);
        
        if (closestSegment < 0) {
            LOGGER.warn("无法找到包含修剪点的线段");
            return this;
        }
        
        // 创建新的点列表，使用更平滑的修剪方法
        List<Vec2d> newPoints = new ArrayList<>();
        
        // 添加修剪点之前的所有点
        for (int i = 0; i <= closestSegment; i++) {
            newPoints.add(points.get(i));
        }
        
        // 添加精确的修剪点
        newPoints.add(trimPoint);
        
        // 验证修剪后的路径是否有效
        if (newPoints.size() < 2) {
            LOGGER.warn("修剪后的路径点数不足");
            return this;
        }
        
        // 检查是否有重复的相邻点，如果有则移除
        newPoints = removeConsecutiveDuplicatePoints(newPoints);
        
        if (newPoints.size() < 2) {
            LOGGER.warn("移除重复点后路径点数不足");
            return this;
        }

        FreeDrawPath trimmedPath = new FreeDrawPath(newPoints);
        if (getStyle() != null) trimmedPath.setStyle(getStyle().clone());
        if (getTransform() != null) trimmedPath.setTransform(getTransform().clone());
        return trimmedPath;
    }
    
    /**
     * 移除连续的重复点，避免创建无效的线段
     */
    private List<Vec2d> removeConsecutiveDuplicatePoints(List<Vec2d> points) {
        if (points.size() <= 1) return points;
        
        List<Vec2d> result = new ArrayList<>();
        result.add(points.getFirst());
        
        for (int i = 1; i < points.size(); i++) {
            Vec2d current = points.get(i);
            Vec2d previous = result.getLast();
            
            // 只有当点之间的距离大于阈值时才添加
            if (current.distance(previous) > 1e-10) {
                result.add(current);
            }
        }
        
        return result;
    }

    @Override
    public Shape createOffset(double distance) {
        if (points.size() < 2) return clone();
        
        List<Vec2d> offsetPoints = new ArrayList<>();
        
        // 对每个点计算偏移
        for (int i = 0; i < points.size(); i++) {
            Vec2d current = points.get(i);
            Vec2d normal;
            
            if (i == 0) {
                // 第一个点
                Vec2d next = points.get(i + 1);
                Vec2d direction = next.subtract(current).normalize();
                normal = new Vec2d(-direction.y, direction.x);
            } else if (i == points.size() - 1) {
                // 最后一个点
                Vec2d prev = points.get(i - 1);
                Vec2d direction = current.subtract(prev).normalize();
                normal = new Vec2d(-direction.y, direction.x);
            } else {
                // 中间点
                Vec2d prev = points.get(i - 1);
                Vec2d next = points.get(i + 1);
                Vec2d dir1 = current.subtract(prev).normalize();
                Vec2d dir2 = next.subtract(current).normalize();
                normal = new Vec2d(-(dir1.y + dir2.y), dir1.x + dir2.x).normalize();
            }
            
            offsetPoints.add(current.add(normal.multiply(distance)));
        }

        FreeDrawPath offsetPath = new FreeDrawPath(offsetPoints);
        offsetPath.setStyle(this.getStyle().clone());
        return offsetPath;
    }

    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> polyline) {
        return calculatePolylineIntersections(points, polyline);
    }
    
    /**
     * 通用的多段线相交检测方法
     * 消除 getIntersectionsWithPolyline 和 getIntersectionsWith(FreeDrawPath) 中的重复代码
     */
    private List<Vec2d> calculatePolylineIntersections(List<Vec2d> polyline1, List<Vec2d> polyline2) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polyline1.size() < 2 || polyline2.size() < 2) return intersections;

        // 遍历第一个多段线的每个线段
        for (int i = 0; i < polyline1.size() - 1; i++) {
            Vec2d p1 = polyline1.get(i);
            Vec2d p2 = polyline1.get(i + 1);

            // 遍历第二个多段线的每个线段
            for (int j = 0; j < polyline2.size() - 1; j++) {
                Vec2d q1 = polyline2.get(j);
                Vec2d q2 = polyline2.get(j + 1);

                // 计算线段相交
                Vec2d intersection = getLineSegmentIntersection(p1, p2, q1, q2);
                if (intersection != null) {
                    intersections.add(intersection);
                }
            }
        }

        return removeDuplicateIntersections(intersections);
    }

    @Override
    public boolean intersectsPolyline(List<Vec2d> polyline) {
        return !getIntersectionsWithPolyline(polyline).isEmpty();
    }

    /**
     * 计算两条线段的交点
     * 使用 GeometryUtils 中的标准方法，消除代码重复
     */
    private Vec2d getLineSegmentIntersection(Vec2d p1, Vec2d p2, Vec2d q1, Vec2d q2) {
        List<Vec2d> intersections = GeometryUtils.segmentIntersection(p1, p2, q1, q2);
        return intersections.isEmpty() ? null : intersections.getFirst();
    }

    /**
     * 检查点是否在线段上
     * 使用 GeometryUtils 中的标准方法，消除代码重复
     */
    private boolean isPointOnLineSegment(Vec2d point, Vec2d start, Vec2d end) {
        return GeometryUtils.isPointOnSegment(point, start, end);
    }

    @Override
    public String serialize() {
        // 使用JSON格式进行序列化，更加健壮和可扩展
        Map<String, Object> data = new HashMap<>();
        data.put("type", "FreeDrawPath");
        data.put("version", "1.0");
        
        // 序列化点列表
        List<Map<String, Double>> pointList = new ArrayList<>();
        for (Vec2d point : points) {
            Map<String, Double> pointData = new HashMap<>();
            pointData.put("x", point.x);
            pointData.put("y", point.y);
            pointList.add(pointData);
        }
        data.put("points", pointList);
        
        // 序列化样式信息（如果有的话）
        if (getStyle() != null) {
            data.put("hasStyle", true);
        }
        
        // 使用简单的JSON格式（避免引入额外的依赖）
        return serializeToSimpleJson(data);
    }

    @Override
    public void deserialize(String data) {
        points.clear();
        if (data == null || data.isEmpty()) return;
        
        try {
            // 尝试解析新的JSON格式
            if (data.trim().startsWith("{")) {
                deserializeFromJson(data);
            } else {
                // 回退到旧的格式以保持向后兼容性
                deserializeFromLegacyFormat(data);
            }
        } catch (Exception e) {
            LOGGER.warn("反序列化FreeDrawPath失败，使用空路径: {}", e.getMessage());
            points.clear();
        }
    }
    
    /**
     * 从JSON格式反序列化
     */
    private void deserializeFromJson(String jsonData) {
        // 简单的JSON解析（避免引入额外依赖）
        Map<String, Object> data = parseSimpleJson(jsonData);
        
        if (data.containsKey("points")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pointList = (List<Map<String, Object>>) data.get("points");
            
            for (Map<String, Object> pointData : pointList) {
                if (pointData.containsKey("x") && pointData.containsKey("y")) {
                    double x = ((Number) pointData.get("x")).doubleValue();
                    double y = ((Number) pointData.get("y")).doubleValue();
                    points.add(new Vec2d(x, y));
                }
            }
        }
    }
    
    /**
     * 从旧格式反序列化（向后兼容）
     */
    private void deserializeFromLegacyFormat(String data) {
        String[] pointStrings = data.split(";");
        for (String pointStr : pointStrings) {
            String[] coords = pointStr.split(",");
            if (coords.length == 2) {
                try {
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    points.add(new Vec2d(x, y));
                } catch (NumberFormatException e) {
                    LOGGER.debug("忽略无效的点数据: {}", pointStr);
                }
            }
        }
    }

    @Override
    public void scale(Vec2d scale, Vec2d center) {
        if (points.isEmpty()) return;
        
        // 对每个点进行缩放
        for (int i = 0; i < points.size(); i++) {
            Vec2d point = points.get(i);
            
            // 计算相对于中心点的向量
            Vec2d relative = point.subtract(center);
            
            // 应用缩放
            Vec2d scaled = new Vec2d(relative.x * scale.x, relative.y * scale.y);
            
            // 更新点的位置
            points.set(i, center.add(scaled));
        }
    }

    @Override
    public Shape accept(IShapeVisitor visitor) {
        return visitor.visit(this);
    }
    
    @Override
    public void accept(IRenderVisitor visitor,
                       imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        visitor.render(this, drawList, camera);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        try {
            if (points.size() < 2) {
                return;
            }
            
            // 绘制路径的所有线段
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                
                // 应用变换
                Vec2d transformedP1 = getTransform().transform(p1);
                Vec2d transformedP2 = getTransform().transform(p2);
                
                // 转换到屏幕坐标
                Vec2d screenP1 = camera.worldToScreen(transformedP1);
                Vec2d screenP2 = camera.worldToScreen(transformedP2);
                
                // 绘制线段
                drawList.addLine(
                    (float) screenP1.x, (float) screenP1.y,
                    (float) screenP2.x, (float) screenP2.y,
                    0x80FFFFFF, 1.0f // 白色，半透明
                );
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常
            LOGGER.error("渲染自由绘制路径ImGui时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 将点投影到有限线段上（不延伸到线段外）
     */
    private Vec2d projectPointOnSegment(Vec2d point, Vec2d segStart, Vec2d segEnd) {
        if (segStart.equals(segEnd)) return segStart;
        
        Vec2d v = segEnd.subtract(segStart);
        Vec2d w = point.subtract(segStart);
        
        double c1 = w.dot(v);
        double c2 = v.dot(v);
        
        if (c1 <= 0) return segStart;  // 投影在起点外，返回起点
        if (c1 >= c2) return segEnd;   // 投影在终点外，返回终点
        
        double b = c1 / c2;
        return segStart.add(v.multiply(b));  // 投影在线段内
    }

    @Override
    public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        List<Shape> newShapes = new ArrayList<>();
        
        // 获取路径的点列表
        List<Vec2d> pathPoints = getPoints();
        if (pathPoints.size() < 2) {
            return newShapes;
        }
        
        // 修复：直接在本地坐标系中操作，确保断点投影的一致性
        // 将世界坐标断点转换为局部坐标系
        Vec2d localFirstPoint = firstBreakPoint;
        Vec2d localSecondPoint = secondBreakPoint;
        if (getTransform() != null) {
            localFirstPoint = getTransform().inverseTransform(firstBreakPoint);
            if (secondBreakPoint != null) {
                localSecondPoint = getTransform().inverseTransform(secondBreakPoint);
            }
        }
        
        // 直接对局部坐标系中的pathPoints进行打断
        // 这样避免了坐标系转换的不一致性
        try {
            if ("SINGLE_POINT".equals(breakMode)) {
                // 单点打断：在打断点处分割路径
                int segmentIndex = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(pathPoints, localFirstPoint);
                if (segmentIndex >= 0) {
                    // 精确投影断点到该线段上
                    Vec2d segStart = pathPoints.get(segmentIndex);
                    Vec2d segEnd = pathPoints.get(segmentIndex + 1);
                    Vec2d projectedPoint = projectPointOnSegment(localFirstPoint, segStart, segEnd);
                    
                    // 创建第一段路径
                    List<Vec2d> firstPoints = new ArrayList<>(pathPoints.subList(0, segmentIndex + 1));
                    firstPoints.add(projectedPoint);
                    if (firstPoints.size() >= 2) {
                        FreeDrawPath firstPath = new FreeDrawPath(firstPoints);
                        if (getStyle() != null) firstPath.setStyle(getStyle().clone());
                        if (getTransform() != null) firstPath.setTransform(getTransform().clone());
                        newShapes.add(firstPath);
                    }
                    
                    // 创建第二段路径
                    List<Vec2d> secondPoints = new ArrayList<>();
                    secondPoints.add(projectedPoint);
                    secondPoints.addAll(pathPoints.subList(segmentIndex + 1, pathPoints.size()));
                    if (secondPoints.size() >= 2) {
                        FreeDrawPath secondPath = new FreeDrawPath(secondPoints);
                        if (getStyle() != null) secondPath.setStyle(getStyle().clone());
                        if (getTransform() != null) secondPath.setTransform(getTransform().clone());
                        newShapes.add(secondPath);
                    }
                }
            } else if ("TWO_POINT".equals(breakMode) && localSecondPoint != null) {
                // 两点打断：移除两点之间的部分
                int firstSegment = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(pathPoints, localFirstPoint);
                int secondSegment = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(pathPoints, localSecondPoint);
                
                if (firstSegment >= 0 && secondSegment >= 0) {
                    // 确保第一个点在第二个点之前
                    if (firstSegment > secondSegment) {
                        int temp = firstSegment;
                        firstSegment = secondSegment;
                        secondSegment = temp;
                        Vec2d tempPoint = localFirstPoint;
                        localFirstPoint = localSecondPoint;
                        localSecondPoint = tempPoint;
                    }
                    
                    Vec2d projectedFirst = projectPointOnSegment(localFirstPoint, 
                        pathPoints.get(firstSegment), pathPoints.get(firstSegment + 1));
                    Vec2d projectedSecond = projectPointOnSegment(localSecondPoint, 
                        pathPoints.get(secondSegment), pathPoints.get(secondSegment + 1));
                    
                    // 创建第一段（起点到第一个断点）
                    List<Vec2d> firstPoints = new ArrayList<>(pathPoints.subList(0, firstSegment + 1));
                    firstPoints.add(projectedFirst);
                    if (firstPoints.size() >= 2) {
                        FreeDrawPath firstPath = new FreeDrawPath(firstPoints);
                        if (getStyle() != null) firstPath.setStyle(getStyle().clone());
                        if (getTransform() != null) firstPath.setTransform(getTransform().clone());
                        newShapes.add(firstPath);
                    }

                    // 创建第二段（第二个断点到终点）
                    if (secondSegment + 1 < pathPoints.size()) {
                        List<Vec2d> secondPoints = new ArrayList<>();
                        secondPoints.add(projectedSecond);
                        secondPoints.addAll(pathPoints.subList(secondSegment + 1, pathPoints.size()));
                        if (secondPoints.size() >= 2) {
                            FreeDrawPath secondPath = new FreeDrawPath(secondPoints);
                            if (getStyle() != null) secondPath.setStyle(getStyle().clone());
                            if (getTransform() != null) secondPath.setTransform(getTransform().clone());
                            newShapes.add(secondPath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 回退到原有方法
            PolylineShape polylineShape = new PolylineShape(pathPoints, false);
            if (getStyle() != null) polylineShape.setStyle(getStyle().clone());
            
            List<Shape> brokenShapes = polylineShape.breakShape(localFirstPoint, localSecondPoint, breakMode);
            for (Shape s : brokenShapes) {
                if (s instanceof PolylineShape) {
                    if (getTransform() != null) s.setTransform(getTransform().clone());
                    if (s.getStyle() == null && getStyle() != null) s.setStyle(getStyle().clone());
                }
                newShapes.add(s);
            }
        }
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        // 计算点到自由绘制路径的距离
        List<Vec2d> pathPoints = getPoints();
        if (pathPoints.size() < 2) {
            return Double.MAX_VALUE;
        }
        
        return GeometryUtils.getDistanceToPolyline(pathPoints, point);
    }
    
    /**
     * 简单的JSON序列化方法（避免引入额外依赖）
     */
    private String serializeToSimpleJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            
            sb.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof List) {
                sb.append("[");
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                boolean listFirst = true;
                for (Object item : list) {
                    if (!listFirst) sb.append(",");
                    listFirst = false;
                    
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapItem = (Map<String, Object>) item;
                        sb.append(serializeToSimpleJson(mapItem));
                    } else {
                        sb.append(item);
                    }
                }
                sb.append("]");
            } else {
                sb.append(value);
            }
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * 简单的JSON解析方法（避免引入额外依赖）
     */
    private Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> result = new HashMap<>();
        
        // 移除外层的大括号
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }
        
        // 简单的键值对解析
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replaceAll("\"", "");
                String value = keyValue[1].trim();
                
                if (value.startsWith("[") && value.endsWith("]")) {
                    // 解析数组
                    result.put(key, parsePointArray(value));
                } else if (value.startsWith("\"") && value.endsWith("\"")) {
                    // 字符串值
                    result.put(key, value.substring(1, value.length() - 1));
                } else {
                    // 数字值
                    try {
                        result.put(key, Double.parseDouble(value));
                    } catch (NumberFormatException e) {
                        result.put(key, value);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 解析点数组
     */
    private List<Map<String, Object>> parsePointArray(String arrayJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 移除方括号
        arrayJson = arrayJson.substring(1, arrayJson.length() - 1);
        
        // 简单的对象解析
        String[] objects = arrayJson.split("},\\{");
        for (int i = 0; i < objects.length; i++) {
            String obj = objects[i];
            if (i == 0) obj = obj + "}";
            else if (i == objects.length - 1) obj = "{" + obj;
            else obj = "{" + obj + "}";

            Map<String, Object> pointData = getStringObjectMap(obj);
            result.add(pointData);
        }
        
        return result;
    }

    private static @NotNull Map<String, Object> getStringObjectMap(String obj) {
        Map<String, Object> pointData = new HashMap<>();
        String[] pairs = obj.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replaceAll("[{}\"]", "");
                String value = keyValue[1].trim().replaceAll("[{}\"]", "");
                try {
                    pointData.put(key, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    pointData.put(key, value);
                }
            }
        }
        return pointData;
    }
    
    /**
     * 验证和清理点列表
     * 移除无效点，确保路径的有效性
     */
    private List<Vec2d> validateAndCleanPoints(List<Vec2d> inputPoints) {
        if (inputPoints == null) {
            LOGGER.debug("输入点列表为null，返回空列表");
            return new ArrayList<>();
        }
        
        List<Vec2d> cleanedPoints = new ArrayList<>();
        int invalidCount = 0;
        
        for (Vec2d point : inputPoints) {
            if (isValidPoint(point)) {
                cleanedPoints.add(point);
            } else {
                invalidCount++;
                LOGGER.debug("发现无效点: {}", point);
            }
        }
        
        if (invalidCount > 0) {
            LOGGER.warn("移除了 {} 个无效点", invalidCount);
        }
        
        // 移除连续的重复点
        cleanedPoints = removeConsecutiveDuplicatePoints(cleanedPoints);
        
        if (cleanedPoints.size() < 2) {
            LOGGER.warn("清理后的点列表长度不足，无法构成有效路径");
        }
        
        return cleanedPoints;
    }
    
    /**
     * 验证点是否有效
     */
    private boolean isValidPoint(Vec2d point) {
        if (point == null) {
            return false;
        }
        
        // 检查坐标是否为有限数值
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y)) {
            return false;
        }
        
        // 检查坐标是否在合理范围内（避免极端值）
        double maxCoordinate = 1e6; // 100万像素
        if (Math.abs(point.x) > maxCoordinate || Math.abs(point.y) > maxCoordinate) {
            LOGGER.warn("点坐标超出合理范围: {}", point);
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证路径是否有效
     */
    public boolean isValid() {
        if (points == null || points.size() < 2) {
            return false;
        }
        
        // 检查所有点是否有效
        for (Vec2d point : points) {
            if (!isValidPoint(point)) {
                return false;
            }
        }
        
        // 检查是否有足够的有效线段
        int validSegments = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            if (p1.distance(p2) > 1e-10) {
                validSegments++;
            }
        }
        
        return validSegments > 0;
    }


} 