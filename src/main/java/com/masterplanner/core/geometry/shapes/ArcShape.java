package com.masterplanner.core.geometry.shapes;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.render.IRenderVisitor;
import com.masterplanner.api.shape.IExtendableShape;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.geometry.GeometryUtils;
import com.masterplanner.core.geometry.AffineTransform;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.ui.tools.impl.modify.helper.IShapeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * 圆弧形状
 * <p>
 * 注意：此类的contains方法使用线宽作为容差值，这意味着它判断的是点是否在圆弧线宽的范围内，
 * 而不是严格意义上的圆弧曲线本身。这在UI交互中是有意为之的行为，使用户更容易选中圆弧。
 * 如果需要更严格的几何判断，请使用containsExactly方法。
 */
public class ArcShape extends Shape implements IExtendableShape {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArcShape.class);
    private Vec2d center;
    private double radius;
    private double startAngle; // 起始角度（弧度）
    private double endAngle;   // 结束角度（弧度）
    
    // 缓存相关字段
    private List<Vec2d> cachedPoints = null;
    private BoundingBox cachedBoundingBox = null;
    // 移除 cacheValid 字段，cachedPoints 是否为 null 已经足够表达缓存状态
    
    // 预览相关字段
    private int previewControlPointIndex = -1;
    private Vec2d previewControlPoint = null;
    private Vec2d previewCenter = null;
    private double previewRadius = 0;
    private double previewStartAngle = 0;
    private double previewEndAngle = 0;
    
    // 添加段数和方向控制
    private int segments = 32; // 默认段数
    
    // 常量定义
    private static final double GEOMETRY_EPSILON = 1e-10;
    private static final double DEFAULT_TOLERANCE = 0.001;
    private static final double SEMICIRCLE_EPSILON = 1e-6;
    private static final double TWO_PI = 2 * Math.PI;
    // private static final int DEFAULT_SEGMENTS = 32; // 暂时未使用
    private static final int MAX_SEGMENTS = 360;
    private static final int MIN_SEGMENTS = 8;
    
    public ArcShape(Vec2d center, double radius, double startAngle, double endAngle) {
        super(center != null ? center : new Vec2d(0, 0));
        this.center = center != null ? center : new Vec2d(0, 0);
        this.radius = Math.max(0, radius);
        this.startAngle = startAngle;
        this.endAngle = endAngle;
        normalizeAngles();
        invalidateCache();
    }
    
    /**
     * 规范化角度，确保起始角度小于结束角度，且角度差不超过2π
     * 重构后的版本更加简洁和健壮，消除了半圆的特殊处理逻辑
     */
    private void normalizeAngles() {
        // 1. 将 startAngle 规范化到 [0, 2π) 范围内
        startAngle = normalizeAngle(startAngle);
        
        // 2. 将 endAngle 相对于 startAngle 进行规范化
        //    确保 endAngle 在 [startAngle, startAngle + 2π) 范围内
        endAngle = normalizeAngle(endAngle);
        if (endAngle < startAngle) {
            endAngle += TWO_PI;
        }
        
        // 3. 限制角度差不超过2π
        if (endAngle - startAngle > TWO_PI) {
            endAngle = startAngle + TWO_PI;
        }
    }
    
    /**
     * 规范化单个角度，确保角度在 [0, 2π) 范围内
     * @param angle 要规范化的角度
     * @return 规范化后的角度
     */
    private double normalizeAngle(double angle) {
        while (angle < 0) angle += TWO_PI;
        while (angle >= TWO_PI) angle -= TWO_PI;
        return angle;
    }
    
    public Vec2d getCenter() { return center; }
    public void setCenter(Vec2d center) { 
        this.center = center; 
        invalidateCache();
    }
    public double getRadius() { return radius; }
    public void setRadius(double radius) { 
        this.radius = radius; 
        invalidateCache();
    }
    public double getStartAngle() { return startAngle; }
    public void setStartAngle(double angle) { 
        startAngle = angle; 
        normalizeAngles(); 
        invalidateCache();
    }
    public double getEndAngle() { return endAngle; }
    public void setEndAngle(double angle) { 
        endAngle = angle; 
        normalizeAngles(); 
        invalidateCache();
    }
    
    /**
     * 判断角度是否在弧内
     * 重构后的版本更加简洁和可靠，配合新的 normalizeAngles 方法
     */
    private boolean isAngleInArc(double angle) {
        // 前提: this.startAngle 和 this.endAngle 已经过新的 normalizeAngles 处理
        // this.startAngle 在 [0, 2PI)
        // this.endAngle 在 [this.startAngle, this.startAngle + 2PI)
        
        angle = normalizeAngle(angle);
        if (angle < this.startAngle) {
            angle += TWO_PI;
        }
        
        return angle >= this.startAngle && angle <= this.endAngle;
    }
    
    // 获取弧上的点
    private Vec2d getPointAtAngle(double angle) {
        return new Vec2d(
            center.x + radius * Math.cos(angle),
            center.y + radius * Math.sin(angle)
        );
    }
    
    @Override
    public void translate(Vec2d offset) {
        center = center.add(offset);
        invalidateCache();
    }
    
    @Override
    public void rotate(double angle, Vec2d rotationCenter) {
        center = GeometryUtils.rotate(center.subtract(rotationCenter), angle).add(rotationCenter);
        startAngle += angle;
        endAngle += angle;
        normalizeAngles();
        invalidateCache();
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        if (transformMatrix.isUniform()) {
            // 均匀变换：可以保持为圆弧
            this.center = transformMatrix.transform(this.center);
            this.radius *= transformMatrix.getScaleX(); // 在均匀缩放下，getScaleX() == getScaleY()
            this.startAngle += transformMatrix.getRotation();
            this.endAngle += transformMatrix.getRotation();
            normalizeAngles();
            invalidateCache();
            return this;
        } else {
            // 非均匀变换：必须转换为椭圆弧
            return new EllipticalArcShape(this, transformMatrix);
        }
    }
    
    @Override
    public void scale(Vec2d scale, Vec2d scaleCenter) {
        center = new Vec2d(
            scaleCenter.x + (center.x - scaleCenter.x) * scale.x,
            scaleCenter.y + (center.y - scaleCenter.y) * scale.y
        );
        radius *= Math.sqrt((scale.x * scale.x + scale.y * scale.y) / 2);
        invalidateCache();
    }
    
    @Override
    public BoundingBox getBoundingBox() {
        if (cachedBoundingBox != null) {
            return cachedBoundingBox;
        }
        
        List<Vec2d> criticalPoints = getCriticalPointsForBoundingBox();
        cachedBoundingBox = calculateBoundingBoxFromPoints(criticalPoints);
        return cachedBoundingBox;
    }
    
    /**
     * 获取用于计算边界框的关键点
     * @return 关键点列表
     */
    private List<Vec2d> getCriticalPointsForBoundingBox() {
        List<Vec2d> points = new ArrayList<>();
        
        // 添加起点和终点
        points.add(getPointAtAngle(startAngle));
        points.add(getPointAtAngle(endAngle));
        
        // 检查象限点是否在弧内
        double[] quadrantAngles = {0, Math.PI/2, Math.PI, Math.PI*3/2};
        for (double angle : quadrantAngles) {
            if (isAngleInArc(angle)) {
                points.add(getPointAtAngle(angle));
            }
        }
        
        return points;
    }
    
    /**
     * 根据点集计算边界框
     * @param points 点集
     * @return 边界框
     */
    private BoundingBox calculateBoundingBoxFromPoints(List<Vec2d> points) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        
        for (Vec2d point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        
        return new BoundingBox(
            new Vec2d(minX, minY),
            new Vec2d(maxX, maxY)
        );
    }
    
    @Override
    public boolean contains(Vec2d point) {
        // 将点转换到局部坐标系
        Vec2d localPoint = transform.inverseTransform(point);
        
        // 检查点是否在圆弧的半径范围内
        if (!isPointOnArcRadius(localPoint)) {
            return false;
        }
        
        // 检查点是否在圆弧的角度范围内
        return isPointInArcAngleRange(localPoint);
    }
    
    /**
     * 检查点是否在圆弧的半径范围内
     * 注意：此方法使用线宽作为容差值，这意味着它判断的是点是否在圆弧线宽的范围内，
     * 而不是严格意义上的圆弧曲线本身。这在UI交互中是有意为之的行为，使用户更容易
     * 选中圆弧。如果需要更严格的几何判断，请使用isPointExactlyOnArcRadius方法。
     * 
     * @param point 要检查的点
     * @return 如果点在圆弧的半径范围内（考虑线宽），则返回 true
     */
    private boolean isPointOnArcRadius(Vec2d point) {
        double distance = point.distance(center);
        double tolerance = getLineStyle().getWidth() / 2;
        return Math.abs(distance - radius) <= tolerance;
    }
    
    /**
     * 严格检查点是否在圆弧上（不考虑线宽）
     * 此方法使用固定的小容差值，用于需要精确几何判断的场景。
     * 
     * @param point 要检查的点
     * @param epsilon 可选的容差值
     * @return 如果点严格在圆弧上，则返回 true
     */
    private boolean isPointExactlyOnArcRadius(Vec2d point, double epsilon) {
        double distance = point.distance(center);
        return Math.abs(distance - radius) <= epsilon;
    }
    
    
    /**
     * 检查点是否在圆弧的角度范围内
     * @param point 要检查的点
     * @return 如果点在圆弧的角度范围内，则返回 true
     */
    private boolean isPointInArcAngleRange(Vec2d point) {
        double angle = Math.atan2(point.y - center.y, point.x - center.x);
        return isAngleInArc(angle);
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        Vec2d direction = point.subtract(center);
        double angle = Math.atan2(direction.y, direction.x);
        
        if (isAngleInArc(angle)) {
            return getPointAtAngle(angle);
        } else {
            Vec2d p1 = getPointAtAngle(startAngle);
            Vec2d p2 = getPointAtAngle(endAngle);
            return point.distance(p1) < point.distance(p2) ? p1 : p2;
        }
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        List<Vec2d> points = new ArrayList<>(3);
        points.add(center);
        points.add(getPointAtAngle(startAngle));
        points.add(getPointAtAngle(endAngle));
        
        // 应用变换
        if (getTransform() != null) {
            List<Vec2d> transformedPoints = new ArrayList<>(3);
            for (Vec2d p : points) {
                transformedPoints.add(getTransform().transform(p));
            }
            return transformedPoints;
        }
        
        return points;
    }
    
    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index == 0) {
            center = point;
        } else if (index == 1 || index == 2) {
            Vec2d direction = point.subtract(center);
            double angle = Math.atan2(direction.y, direction.x);
            if (index == 1) {
                startAngle = angle;
            } else {
                endAngle = angle;
            }
            radius = direction.length();
            normalizeAngles();
        }
        invalidateCache();
    }
    
    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }
    
    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        if (other instanceof LineShape) {
            return getIntersectionsWithLine((LineShape) other);
        } else if (other instanceof CircleShape) {
            return getIntersectionsWithCircle((CircleShape) other);
        } else if (other instanceof ArcShape) {
            return getIntersectionsWithArc((ArcShape) other);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 获取与直线的交点
     * <p>
     * 注意：此方法通过先计算与完整圆的交点，然后筛选出在弧内的点来工作。
     * 在大多数情况下这种方法是准确的，但在某些边缘情况下可能需要额外的数值处理。
     * 
     * @param line 直线
     * @return 交点列表
     */
    private List<Vec2d> getIntersectionsWithLine(LineShape line) {
        List<Vec2d> result = new ArrayList<>();
        
        // 先获取与完整圆的交点
        CircleShape circle = new CircleShape(center, radius);
        List<Vec2d> circleIntersections = circle.getIntersectionPoints(line);
        
        // 只保留在弧内的交点
        for (Vec2d point : circleIntersections) {
            if (isPointInArcAngleRange(point)) {
                result.add(point);
            }
        }
        
        return result;
    }
    
    /**
     * 获取与圆的交点
     * <p>
     * 注意：此方法通过先计算与完整圆的交点，然后筛选出在弧内的点来工作。
     * 在大多数情况下这种方法是准确的，但在某些边缘情况下可能需要额外的数值处理。
     * 
     * @param circle 圆
     * @return 交点列表
     */
    private List<Vec2d> getIntersectionsWithCircle(CircleShape circle) {
        List<Vec2d> result = new ArrayList<>();
        
        // 先获取与完整圆的交点
        CircleShape thisCircle = new CircleShape(center, radius);
        List<Vec2d> circleIntersections = thisCircle.getIntersectionPoints(circle);
        
        // 只保留在弧内的交点
        for (Vec2d point : circleIntersections) {
            if (isPointInArcAngleRange(point)) {
                result.add(point);
            }
        }
        
        return result;
    }
    
    /**
     * 获取与另一个圆弧的交点
     * <p>
     * 注意：此方法通过先计算与完整圆的交点，然后筛选出在两个弧内的点来工作。
     * 同时，它还检查每个弧的端点是否落在另一个弧上，以处理特殊情况。
     * 在大多数情况下这种方法是准确的，但对于几乎相切的弧可能需要额外的数值处理。
     * 
     * @param arc 另一个圆弧
     * @return 交点列表
     */
    private List<Vec2d> getIntersectionsWithArc(ArcShape arc) {
        List<Vec2d> result = new ArrayList<>();
        // 使用常量定义的数值容差
        
        // 先获取与完整圆的交点
        CircleShape thisCircle = new CircleShape(center, radius);
        CircleShape otherCircle = new CircleShape(arc.center, arc.radius);
        List<Vec2d> circleIntersections = thisCircle.getIntersectionPoints(otherCircle);
        
        // 检查交点是否在两个弧内
        for (Vec2d point : circleIntersections) {
            if (isPointInArcAngleRange(point) && arc.isPointInArcAngleRange(point)) {
                addIntersectionPoint(point, result);
            }
        }
        
        // 处理特殊情况：检查一个弧的端点是否在另一个弧上
        // 这处理了圆弧端点恰好落在另一个圆弧上的情况
        List<Vec2d> thisEndpoints = getEndpoints();
        for (Vec2d endpoint : thisEndpoints) {
            if (arc.containsExactly(endpoint, GEOMETRY_EPSILON)) {
                addIntersectionPoint(endpoint, result);
            }
        }
        
        // 检查另一个弧的端点是否在当前弧上
        List<Vec2d> otherEndpoints = arc.getEndpoints();
        for (Vec2d endpoint : otherEndpoints) {
            if (this.containsExactly(endpoint, GEOMETRY_EPSILON)) {
                addIntersectionPoint(endpoint, result);
            }
        }
        
        return result;
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 确定延伸方向
        boolean fromStart = point.distance(getPointAtAngle(startAngle)) < 
                          point.distance(getPointAtAngle(endAngle));
        
        // 创建延伸弧
        double newAngle = fromStart ? 
            startAngle - maxDistance / radius :
            endAngle + maxDistance / radius;
            
        ArcShape extendedArc = new ArcShape(
            center, radius,
            fromStart ? newAngle : startAngle,
            fromStart ? endAngle : newAngle
        );
        
        return extendedArc.getIntersectionsWith(other);
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        List<Vec2d> points = new ArrayList<>();
        points.add(getPointAtAngle(startAngle));
        points.add(getPointAtAngle(endAngle));
        return points;
    }
    
    /**
     * 获取圆弧上指定点的切线向量
     * <p>
     * 该方法计算圆弧上给定点的切线向量。切线向量是与径向向量（从圆心指向该点的向量）
     * 垂直的单位向量。在二维平面中，切线向量可以通过将径向单位向量逆时针旋转90度得到。
     * <p>
     * 计算步骤：
     * 1. 计算从圆心到点的径向向量
     * 2. 将径向向量标准化为单位向量
     * 3. 将单位向量逆时针旋转90度，得到切线向量
     * <p>
     * 对于单位向量(x,y)，其逆时针旋转90度的向量为(-y,x)
     * <p>
     * 注意：如果输入点不在圆弧上，方法会先找到圆弧上最近的点，然后计算该点的切线。
     * 
     * @param point 圆弧上或附近的点
     * @return 该点处的单位切线向量
     */
    @Override
    public Vec2d getTangentAt(Vec2d point) {
        // 将点转换到局部坐标系
        Vec2d localPoint = transform.inverseTransform(point);
        
        // 检查点是否在圆弧上，如果不在，找到最近的点
        Vec2d pointOnArc;
        double distToCenter = localPoint.distance(center);
        if (Math.abs(distToCenter - radius) > DEFAULT_TOLERANCE) {
            // 点不在圆上，调整到圆上
            if (distToCenter < GEOMETRY_EPSILON) {
                // 如果点非常接近圆心，使用起始点
                pointOnArc = getPointAtAngle(startAngle);
            } else {
                // 计算点与圆心的角度
                double angle = Math.atan2(localPoint.y - center.y, localPoint.x - center.x);
                
                // 检查角度是否在弧范围内
                if (isAngleInArc(angle)) {
                    // 在弧范围内，使用该角度的点
                    pointOnArc = getPointAtAngle(angle);
                } else {
                    // 不在弧范围内，使用最近的端点
                    Vec2d startPoint = getPointAtAngle(startAngle);
                    Vec2d endPoint = getPointAtAngle(endAngle);
                    pointOnArc = (localPoint.distance(startPoint) < localPoint.distance(endPoint)) 
                                ? startPoint : endPoint;
                }
            }
        } else {
            // 点已经在圆上，检查是否在弧范围内
            double angle = Math.atan2(localPoint.y - center.y, localPoint.x - center.x);
            if (isAngleInArc(angle)) {
                pointOnArc = localPoint;
            } else {
                // 不在弧范围内，使用最近的端点
                Vec2d startPoint = getPointAtAngle(startAngle);
                Vec2d endPoint = getPointAtAngle(endAngle);
                pointOnArc = (localPoint.distance(startPoint) < localPoint.distance(endPoint)) 
                            ? startPoint : endPoint;
            }
        }
        
        // 计算从圆心到圆弧上点的径向向量并标准化
        Vec2d radial = pointOnArc.subtract(center).normalize();
        
        // 返回切线向量（径向向量逆时针旋转90度）
        return new Vec2d(-radial.y, radial.x);
    }
    
    @Override
    public double getSignedDistance(Vec2d point) {
        // 对于圆弧：点到圆周的距离仍基于半径差，但符号应根据点相对弧的朝向
        double radial = point.distance(center) - radius;
        // 使用弧上最近点的切线判定侧向：若点在切线左侧为正，右侧为负
        Vec2d closest = getClosestPoint(point);
        Vec2d tangent = getTangentAt(closest);
        Vec2d v = point.subtract(closest);
        double crossZ = tangent.x * v.y - tangent.y * v.x;
        double sign = Math.signum(crossZ);
        if (sign == 0) sign = 1.0;
        return sign * Math.abs(radial);
    }
    
    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        List<Shape> result = new ArrayList<>();
        if (points.isEmpty()) return result;
        
        // 将点按角度排序
        List<Vec2d> sortedPoints = sortPointsByAngle(points);
        
        // 创建子弧
        return createSubArcs(sortedPoints);
    }
    
    /**
     * 按角度排序点
     * @param points 要排序的点
     * @return 排序后的点列表
     */
    private List<Vec2d> sortPointsByAngle(List<Vec2d> points) {
        List<Vec2d> sortedPoints = new ArrayList<>(points);
        sortedPoints.sort((p1, p2) -> {
            double a1 = getPointAngle(p1);
            double a2 = getPointAngle(p2);
            return Double.compare(a1, a2);
        });
        return sortedPoints;
    }
    
    /**
     * 获取点相对于圆心的角度，并确保它在弧的角度范围内
     * @param point 点
     * @return 角度
     */
    private double getPointAngle(Vec2d point) {
        double angle = Math.atan2(point.y - center.y, point.x - center.x);
        angle = normalizeAngle(angle);
        
            // 如果角度不在弧内，但需要用于排序，则调整角度使其在同一周期内比较
            if (!isAngleInArc(angle)) {
                // 如果角度小于起始角度，加上2π使其在同一周期内比较
                if (angle < normalizeAngle(startAngle)) {
                    angle += TWO_PI;
                }
            }
        
        return angle;
    }
    
    /**
     * 根据排序后的点创建子弧
     * @param sortedPoints 按角度排序的点
     * @return 子弧列表
     */
    private List<Shape> createSubArcs(List<Vec2d> sortedPoints) {
        List<Shape> result = new ArrayList<>();
        
        double lastAngle = startAngle;
        for (Vec2d point : sortedPoints) {
            double angle = Math.atan2(point.y - center.y, point.x - center.x);
            if (!isAngleInArc(angle)) continue;
            
            result.add(createSubArc(lastAngle, angle));
            lastAngle = angle;
        }
        result.add(createSubArc(lastAngle, endAngle));
        
        return result;
    }
    
    /**
     * 创建子弧
     * 提取的辅助方法，用于减少重复代码
     * @param startAng 起始角度
     * @param endAng 结束角度
     * @return 子弧
     */
    private ArcShape createSubArc(double startAng, double endAng) {
        ArcShape subArc = new ArcShape(center, radius, startAng, endAng);
        
        // 复制样式
        if (getStyle() != null) {
            subArc.setStyle(getStyle().clone());
        }
        
        // 复制变换
        if (getTransform() != null) {
            subArc.setTransform(getTransform().clone());
        }
        
        return subArc;
    }
    
    @Override
    public Shape extend(Vec2d point, double distance) {
        boolean fromStart = point.distance(getPointAtAngle(startAngle)) < 
                          point.distance(getPointAtAngle(endAngle));
                          
        double newAngle = fromStart ? 
            startAngle - distance / radius :
            endAngle + distance / radius;
            
        return new ArcShape(
            center, radius,
            fromStart ? newAngle : startAngle,
            fromStart ? endAngle : newAngle
        );
    }
    
    @Override
    public Shape extend(Vec2d extendPoint, Vec2d targetPoint) {
        // 1. 计算目标点的角度，并规范化到 [0, 2π)
        double targetAngle = Math.atan2(targetPoint.y - center.y, targetPoint.x - center.x);
        targetAngle = normalizeAngle(targetAngle);

        // 2. 确定是延伸起点还是终点
        boolean fromStart = extendPoint.distance(getPointAtAngle(startAngle)) < 
                          extendPoint.distance(getPointAtAngle(endAngle));

        LOGGER.debug("ArcShape.extend - 延伸参数: 延伸点={}, 目标点={}, 目标角度={}, 从起点延伸={}",
                extendPoint, targetPoint, Math.toDegrees(targetAngle), fromStart);
        LOGGER.debug("ArcShape.extend - 原圆弧: startAngle={}, endAngle={}",
                Math.toDegrees(startAngle), Math.toDegrees(endAngle));

        ArcShape extended;
        if (fromStart) {
            // 延伸起点
            // 为了正确比较，将 endAngle 和 targetAngle 都转换到以 startAngle 为基准的坐标系
            double relativeEndAngle = normalizeAngle(endAngle - startAngle);
            double relativeTargetAngle = normalizeAngle(targetAngle - startAngle);

            // 如果目标角度在 "回缩" 的方向（即在原有弧段内），则不改变终点
            // 否则，新的终点就是原来的终点，起点变为目标点
            if (relativeTargetAngle > relativeEndAngle) {
                // 这是回缩操作，我们应该创建一个从 targetAngle 到 endAngle 的新弧
                extended = new ArcShape(center, radius, targetAngle, endAngle);
            } else {
                // 这是延伸操作，起点变为 targetAngle，终点不变
                extended = new ArcShape(center, radius, targetAngle, endAngle);
            }

        } else {
            // 延伸终点
            // 为了正确比较，将 targetAngle 转换到以 startAngle 为基准的坐标系
            double relativeTargetAngle = targetAngle;
            if (relativeTargetAngle < startAngle) {
                relativeTargetAngle += TWO_PI;
            }

            // 新的终点就是目标点，起点不变
            extended = new ArcShape(center, radius, startAngle, relativeTargetAngle);
        }
        
        // 继承样式
        if (getStyle() != null) {
            extended.setStyle(getStyle().clone());
        }

        LOGGER.debug("ArcShape.extend - 新圆弧: startAngle={}, endAngle={}",
                Math.toDegrees(extended.getStartAngle()), Math.toDegrees(extended.getEndAngle()));
        
        return extended;
    }



    @Override
    public Shape trimToPoint(Vec2d point) {
        double angle = Math.atan2(point.y - center.y, point.x - center.x);
        
        // 选择最近的端点作为保留端
        Vec2d startPoint = getPointAtAngle(startAngle);
        Vec2d endPoint = getPointAtAngle(endAngle);
        boolean fromStart = point.distance(startPoint) > point.distance(endPoint);
        
        return new ArcShape(
            center, radius,
            fromStart ? startAngle : angle,
            fromStart ? angle : endAngle
        );
    }
    
    @Override
    public Shape createOffset(double distance) {
        double newRadius = radius + distance;
        if (newRadius <= 0) {
            // 半径不能为负；返回最小半径的同向弧
            newRadius = Math.max(0.1, newRadius);
        }
        return new ArcShape(center, newRadius, startAngle, endAngle);
    }
    
    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> result = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            LineShape line = new LineShape(points.get(i), points.get(i + 1));
            result.addAll(getIntersectionPoints(line));
        }
        
        return result;
    }
    
    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        return !getIntersectionsWithPolyline(points).isEmpty();
    }
    
    @Override
    public ArcShape clone() {
        ArcShape cloned = new ArcShape(center, radius, startAngle, endAngle);
        
        // 复制样式
        if (getStyle() != null) {
            cloned.setStyle(getStyle().clone());
        }
        
        // 复制变换
        if (getTransform() != null) {
            cloned.setTransform(getTransform().clone());
        }
        
        // 重置状态
        cloned.cachedPoints = null; // 新克隆的对象不共享缓存
        cloned.previewControlPoint = null;
        cloned.previewCenter = null;
        cloned.previewControlPointIndex = -1;
        cloned.previewRadius = 0;
        cloned.previewStartAngle = 0;
        cloned.previewEndAngle = 0;
        
        return cloned;
    }
    
    @Override
    public String serialize() {
        return String.format("ARC %.2f,%.2f %.2f %.2f,%.2f",
            center.x, center.y, radius, startAngle, endAngle);
    }
    
    @Override
    public void deserialize(String data) {
        String[] parts = data.split(" ");
        if (parts.length >= 4 && parts[0].equals("ARC")) {
            String[] c = parts[1].split(",");
            center = new Vec2d(
                Double.parseDouble(c[0]),
                Double.parseDouble(c[1])
            );
            radius = Double.parseDouble(parts[2]);
            String[] angles = parts[3].split(",");
            startAngle = Double.parseDouble(angles[0]);
            endAngle = Double.parseDouble(angles[1]);
            normalizeAngles();
        }
    }
    
    @Override
    public void draw(DrawContext context) {
        // 优先使用DrawContext中设置的样式（用于选中和高亮状态）
        ShapeStyle activeStyle = context.getCurrentStyle();
        if (activeStyle == null) {
            // 如果DrawContext没有设置样式，使用图形自己的样式
            activeStyle = (ShapeStyle) getStyle();
        }
        
        if (!activeStyle.getLineStyle().isVisible()) return;
        
        // 如果有预览状态，绘制预览
        if (previewControlPointIndex >= 0 && previewControlPoint != null) {
            drawPreview(context);
            return;
        }
        
        // 获取变换后的点
        List<Vec2d> transformedPoints = getTransformedPoints(context);
        
        // 绘制线段
        drawLineSegments(transformedPoints, context, activeStyle);
    }
    
    /**
     * 绘制预览
     * 重构后的版本不修改对象状态，直接使用预览参数进行计算和绘制
     * @param context 绘制上下文
     */
    private void drawPreview(DrawContext context) {
        // 直接使用 preview* 字段来生成和绘制预览图形
        List<Vec2d> previewBasePoints = generateArcPoints(previewCenter, previewRadius, previewStartAngle, previewEndAngle, context);
        
        // 应用变换
        List<Vec2d> transformedPoints = new ArrayList<>();
        for (Vec2d point : previewBasePoints) {
            transformedPoints.add(transform.transform(point));
        }
        
        // 使用虚线样式绘制预览
        LineStyle previewStyle = (LineStyle) getLineStyle().clone();
        previewStyle.setType(LineStyle.LineType.DASHED);
        
        // 绘制预览线段
        for (int i = 0; i < transformedPoints.size() - 1; i++) {
            context.drawLine(transformedPoints.get(i), transformedPoints.get(i + 1), previewStyle);
        }
        
        // 绘制预览控制点
        drawPreviewControlPoints(context);
    }
    
    
    /**
     * 绘制预览控制点
     * @param context 绘制上下文
     */
    private void drawPreviewControlPoints(DrawContext context) {
        // 创建预览控制点列表
        List<Vec2d> previewControlPoints = new ArrayList<>();
        previewControlPoints.add(previewCenter);
        previewControlPoints.add(new Vec2d(
            previewCenter.x + previewRadius * Math.cos(previewStartAngle),
            previewCenter.y + previewRadius * Math.sin(previewStartAngle)
        ));
        previewControlPoints.add(new Vec2d(
            previewCenter.x + previewRadius * Math.cos(previewEndAngle),
            previewCenter.y + previewRadius * Math.sin(previewEndAngle)
        ));
        
        // 绘制预览控制点
        for (int i = 0; i < previewControlPoints.size(); i++) {
            Vec2d point = previewControlPoints.get(i);
            Vec2d transformedPoint = transform.transform(point);
            
            // 当前正在拖动的控制点使用不同颜色
            Color pointColor = (i == previewControlPointIndex) ? 
                Color.ORANGE : Color.BLUE;
            
            // 绘制控制点
            context.fillCircle(transformedPoint, 5, pointColor);
            context.drawCircle(transformedPoint, 5, Color.WHITE);
        }
    }
    
    /**
     * 获取变换后的圆弧点集
     * @param context 绘制上下文
     * @return 变换后的点列表
     */
    private List<Vec2d> getTransformedPoints(DrawContext context) {
        // 计算弧线的离散点（未变换）
        List<Vec2d> basePoints = generateArcPoints(center, radius, startAngle, endAngle, context);
        
        // 对每个点应用完整的变换矩阵
        List<Vec2d> transformedPoints = new ArrayList<>();
        for (Vec2d point : basePoints) {
            transformedPoints.add(transform.transform(point));
        }
        
        return transformedPoints;
    }
    
    /**
     * 生成圆弧上的点
     * @param arcCenter 圆弧中心
     * @param arcRadius 圆弧半径
     * @param arcStartAngle 起始角度
     * @param arcEndAngle 结束角度
     * @param context 绘制上下文
     * @return 圆弧上的点列表
     */
    private List<Vec2d> generateArcPoints(Vec2d arcCenter, double arcRadius, double arcStartAngle, double arcEndAngle, DrawContext context) {
        // 计算圆弧的总角度
        double totalAngle = arcEndAngle - arcStartAngle;
        
        // 检查是否为反向半圆（endAngle < startAngle 且角度差接近π）
        boolean isReverseSemicircle = false;
        if (totalAngle < 0) {
            double reverseAngle = totalAngle + TWO_PI;
            if (Math.abs(reverseAngle - Math.PI) < SEMICIRCLE_EPSILON) {
                // 这是反向半圆，保持原始角度关系
                isReverseSemicircle = true;
                totalAngle = Math.abs(totalAngle); // 使用绝对值作为总角度
            } else {
                totalAngle += TWO_PI;
            }
        }
        
        // 获取相机缩放级别
        int calculatedSegments = getCalculatedSegments(arcRadius, context, totalAngle);

        double angleStep = totalAngle / calculatedSegments;
        
        // 生成圆弧上的点
        List<Vec2d> points = new ArrayList<>();
        for (int i = 0; i <= calculatedSegments; i++) {
            double angle;
            if (isReverseSemicircle) {
                // 反向半圆：从startAngle向endAngle反向生成点
                angle = arcStartAngle - i * angleStep;
            } else {
                // 正向圆弧：从startAngle向endAngle正向生成点
                angle = arcStartAngle + i * angleStep;
            }
            Vec2d point = new Vec2d(
                arcCenter.x + arcRadius * Math.cos(angle),
                arcCenter.y + arcRadius * Math.sin(angle)
            );
            points.add(point);
        }
        
        return points;
    }

    private int getCalculatedSegments(double arcRadius, DrawContext context, double totalAngle) {
        float zoom = 1.0f;
        if (context != null && context.getCamera() != null) {
            zoom = context.getCamera().getZoom();
        }

        // 根据圆弧长度和缩放级别计算段数
        // 如果设置了固定段数，则使用设置的段数；否则动态计算
        int calculatedSegments;
        if (segments > 0) {
            calculatedSegments = segments;
        } else {
            // 缩放级别越高，需要的段数越多；缩放级别越低，需要的段数越少
            double pixelDensity = 4.0 / Math.max(0.1, zoom); // 每4个像素一个段
            calculatedSegments = Math.max(16, (int)(arcRadius * Math.abs(totalAngle) / pixelDensity));

            // 限制最大段数，避免性能问题
            calculatedSegments = Math.min(calculatedSegments, MAX_SEGMENTS);
        }
        return calculatedSegments;
    }

    /**
     * 生成圆弧上的点（无上下文版本，用于非绘制场景）
     * @param arcCenter 圆弧中心
     * @param arcRadius 圆弧半径
     * @param arcStartAngle 起始角度
     * @param arcEndAngle 结束角度
     * @return 圆弧上的点列表
     */
    private List<Vec2d> generateArcPoints(Vec2d arcCenter, double arcRadius, double arcStartAngle, double arcEndAngle) {
        // 使用默认像素密度
        return generateArcPoints(arcCenter, arcRadius, arcStartAngle, arcEndAngle, null);
    }
    
    /**
     * 绘制线段
     * @param points 要绘制的点列表
     * @param context 绘制上下文
     * @param activeStyle 当前活动样式
     */
    private void drawLineSegments(List<Vec2d> points, DrawContext context, ShapeStyle activeStyle) {
        // 使用传入的活动样式
        for (int i = 0; i < points.size() - 1; i++) {
            // 使用 DrawContext 的 drawLine 方法，支持线宽和样式
            if (activeStyle.getLineStyle() instanceof LineStyle) {
                context.drawLine(points.get(i), points.get(i + 1), (LineStyle) activeStyle.getLineStyle());
            } else {
                context.drawLine(points.get(i), points.get(i + 1), activeStyle.getLineStyle().getColor());
            }
        }
    }
    
    @Override
    public List<Vec2d> getPoints() {
        // 如果缓存有效，直接返回缓存的点
        if (cachedPoints != null) {
            return new ArrayList<>(cachedPoints); // 返回副本，防止外部修改
        }
        
        // 计算弧线的离散点（未变换）
        List<Vec2d> basePoints = generateArcPoints(center, radius, startAngle, endAngle);
        
        // 对每个点应用完整的变换矩阵
        cachedPoints = new ArrayList<>();
        for (Vec2d point : basePoints) {
            cachedPoints.add(transform.transform(point));
        }
        
        return new ArrayList<>(cachedPoints); // 返回副本，防止外部修改
    }
    
    /**
     * 使缓存失效，在形状参数变化时调用
     */
    private void invalidateCache() {
        cachedPoints = null;
        cachedBoundingBox = null; // 同时使边界框缓存失效
    }
    
    /**
     * 设置控制点预览
     * @param index 控制点索引
     * @param point 预览位置
     */
    public void setPreviewControlPoint(int index, Vec2d point) {
        previewControlPointIndex = index;
        previewControlPoint = point;
        
        // 保存当前状态用于预览
        previewCenter = center;
        previewRadius = radius;
        previewStartAngle = startAngle;
        previewEndAngle = endAngle;
        
        // 根据控制点索引计算预览状态
        if (index == 0) {
            // 中心点预览
            previewCenter = point;
        } else if (index == 1 || index == 2) {
            // 起点或终点预览
            Vec2d direction = point.subtract(center);
            double angle = Math.atan2(direction.y, direction.x);
            double newRadius = direction.length();
            
            if (index == 1) {
                // 起点预览
                previewStartAngle = angle;
            } else {
                // 终点预览
                previewEndAngle = angle;
            }
            previewRadius = newRadius;

            // 规范化预览角度
            normalizePreviewAngles();
        }
    }
    
    /**
     * 清除控制点预览
     */
    public void clearPreviewControlPoint() {
        previewControlPointIndex = -1;
        previewControlPoint = null;
        previewCenter = null;
        previewRadius = 0;
        previewStartAngle = 0;
        previewEndAngle = 0;
    }
    
    /**
     * 应用控制点预览
     */
    public void applyPreviewControlPoint() {
        if (previewControlPointIndex >= 0 && previewControlPoint != null) {
            setControlPoint(previewControlPointIndex, previewControlPoint);
            clearPreviewControlPoint();
        }
    }
    
    /**
     * 规范化预览角度
     */
    private void normalizePreviewAngles() {
        // 规范化到 [0, 2π) 范围
        while (previewStartAngle < 0) previewStartAngle += TWO_PI;
        while (previewStartAngle >= TWO_PI) previewStartAngle -= TWO_PI;
        while (previewEndAngle < 0) previewEndAngle += TWO_PI;
        while (previewEndAngle >= TWO_PI) previewEndAngle -= TWO_PI;
        
        // 确保终点角度大于起点角度
        if (previewEndAngle < previewStartAngle) {
            previewEndAngle += TWO_PI;
        }
        
        // 限制角度差不超过2π
        if (previewEndAngle - previewStartAngle > TWO_PI) {
            previewEndAngle = previewStartAngle + TWO_PI;
        }
    }
    
    /**
     * 设置圆弧的段数
     * @param segments 段数
     */
    public void setSegments(int segments) {
        if (this.segments != segments) {
            this.segments = Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, segments)); // 限制在合理范围内
            invalidateCache();
        }
    }
    
    /**
     * 获取圆弧的段数
     * @return 段数
     */
    public int getSegments() {
        return segments;
    }
    
    /**
     * 严格检查点是否在圆弧上（不考虑线宽）
     * 此方法使用固定的小容差值，用于需要精确几何判断的场景。
     * 
     * @param point 要检查的点
     * @param epsilon 可选的容差值
     * @return 如果点严格在圆弧上，则返回 true
     */
    public boolean containsExactly(Vec2d point, double epsilon) {
        // 将点转换到局部坐标系
        Vec2d localPoint = transform.inverseTransform(point);
        
        // 检查点是否在圆弧的半径范围内（使用精确检测）
        if (!isPointExactlyOnArcRadius(localPoint, epsilon)) {
            return false;
        }
        
        // 检查点是否在圆弧的角度范围内
        return isPointInArcAngleRange(localPoint);
    }
    
    /**
     * 严格检查点是否在圆弧上（使用默认容差值）
     * 
     * @param point 要检查的点
     * @return 如果点严格在圆弧上，则返回 true
     */
    public boolean containsExactly(Vec2d point) {
        return containsExactly(point, DEFAULT_TOLERANCE);
    }
    
    /**
     * 判断两个点是否在数值上相等（考虑容差）
     *
     * @param p1 第一个点
     * @param p2 第二个点
     * @return 如果两点距离小于容差，则返回true
     */
    private boolean pointsEqual(Vec2d p1, Vec2d p2) {
        return p1.distance(p2) < GEOMETRY_EPSILON;
    }
    
    /**
     * 判断点是否已存在于点集中（考虑容差）
     *
     * @param point  要检查的点
     * @param points 点集
     * @return 如果点已存在，则返回true
     */
    private boolean pointExistsIn(Vec2d point, List<Vec2d> points) {
        for (Vec2d existing : points) {
            if (pointsEqual(point, existing)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 安全地添加交点，避免重复
     *
     * @param point  要添加的交点
     * @param result 结果列表
     */
    private void addIntersectionPoint(Vec2d point, List<Vec2d> result) {
        if (!pointExistsIn(point, result)) {
            result.add(point);
        }
    }
    
    @Override
    public Shape accept(IShapeVisitor visitor) {
        return visitor.visit(this);
    }
    
    @Override
    public void accept(IRenderVisitor visitor,
                       imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        visitor.render(this, drawList, camera);
    }
    
    @Override
    public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        List<Shape> newShapes = new ArrayList<>();
        
        // 检查打断点是否在圆弧上
        if (!GeometryUtils.isPointOnCircle(center, radius, firstBreakPoint)) {
            return newShapes; // 第一个打断点不在圆弧上
        }
        
        if ("TWO_POINT".equals(breakMode) && secondBreakPoint != null) {
            if (!GeometryUtils.isPointOnCircle(center, radius, secondBreakPoint)) {
                return newShapes; // 第二个打断点不在圆弧上
            }
            
            // 计算两个打断点的角度
            double firstAngle = getPointAngle(firstBreakPoint);
            double secondAngle = getPointAngle(secondBreakPoint);
            
            // 改进的逻辑：直接移除两点之间的弧段，符合用户预期
            // 创建三个可能的弧段：起点到第一点、第一点到第二点、第二点到终点
            
            // 弧段1：起点到第一点
            if (firstAngle > startAngle) {
                ArcShape firstArc = createSubArc(startAngle, firstAngle);
                newShapes.add(firstArc);
            }
            
            // 弧段2：第二点到终点
            if (secondAngle < endAngle) {
                ArcShape secondArc = createSubArc(secondAngle, endAngle);
                newShapes.add(secondArc);
            }
            
            // 注意：第一点到第二点之间的弧段被移除，不添加到结果中
            // 这符合标准CAD软件中"删除两点之间的部分"的行为
        } else {
            // 单点打断模式
            double breakAngle = getPointAngle(firstBreakPoint);
            
            // 创建两个子圆弧
            if (breakAngle > startAngle) {
                ArcShape firstArc = createSubArc(startAngle, breakAngle);
                newShapes.add(firstArc);
            }
            
            if (breakAngle < endAngle) {
                ArcShape secondArc = createSubArc(breakAngle, endAngle);
                newShapes.add(secondArc);
            }
        }
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        // 计算点到圆弧的距离
        double distanceToCenter = center.distance(point);
        double distanceToCircle = Math.abs(distanceToCenter - radius);
        
        // 如果点不在圆弧的角度范围内，计算到端点的距离
        if (!isPointInArcAngleRange(point)) {
            Vec2d startPoint = getPointAtAngle(startAngle);
            Vec2d endPoint = getPointAtAngle(endAngle);
            double distanceToStart = point.distance(startPoint);
            double distanceToEnd = point.distance(endPoint);
            return Math.min(distanceToCircle, Math.min(distanceToStart, distanceToEnd));
        }
        
        return distanceToCircle;
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        try {
            // 获取变换后的圆弧属性
            Vec2d transformedCenter = getTransform().transform(center);
            double transformedRadius = radius * getTransform().getScale().x;
            
            // 转换到屏幕坐标
            Vec2d screenCenter = camera.worldToScreen(transformedCenter);
            // float screenRadius = (float) (transformedRadius * camera.getZoom()); // 未使用的变量
            
            // 检查是否为填充圆弧
            boolean isFilled = getStyle() != null && 
                             getStyle().getFillStyle() != null && 
                             getStyle().getFillStyle().isVisible();
            
            // 生成圆弧上的点 - 使用动态分段计算
            double totalAngle = endAngle - startAngle;
            if (totalAngle < 0) totalAngle += 2 * Math.PI;
            
            // 使用现有的动态分段计算逻辑
            int segments = getCalculatedSegments(transformedRadius, null, totalAngle);
            List<Vec2d> arcPoints = new ArrayList<>();
            
            // 添加中心点（用于填充）
            if (isFilled) {
                arcPoints.add(screenCenter);
            }
            
            for (int i = 0; i <= segments; i++) {
                double t = startAngle + (endAngle - startAngle) * i / segments;
                double x = transformedRadius * Math.cos(t);
                double y = transformedRadius * Math.sin(t);
                
                Vec2d worldPoint = new Vec2d(transformedCenter.x + x, transformedCenter.y + y);
                Vec2d screenPoint = camera.worldToScreen(worldPoint);
                arcPoints.add(screenPoint);
            }
            
            if (isFilled && arcPoints.size() >= 3) {
                // 填充圆弧 - 使用三角形扇形填充
                Vec2d center = arcPoints.getFirst();
                for (int i = 1; i < arcPoints.size() - 1; i++) {
                    Vec2d p1 = arcPoints.get(i);
                    Vec2d p2 = arcPoints.get(i + 1);
                    
                    // 从样式获取填充颜色
                    int fillColor = getStyle() != null && getStyle().getFillStyle() != null ? 
                        getStyle().getFillStyle().getColor().getRGB() : 0x80FFFFFF;
                    drawList.addTriangleFilled(
                        (float) center.x, (float) center.y,
                        (float) p1.x, (float) p1.y,
                        (float) p2.x, (float) p2.y,
                        fillColor
                    );
                }
            }
            
            // 绘制圆弧轮廓
            for (int i = 1; i < arcPoints.size() - 1; i++) {
                Vec2d p1 = arcPoints.get(i);
                Vec2d p2 = arcPoints.get(i + 1);
                // 从样式获取线条颜色
                int lineColor = getStyle() != null && getStyle().getLineStyle() != null ? 
                    getStyle().getLineStyle().getColor().getRGB() : 0x80FFFFFF;
                float lineWidth = getStyle() != null && getStyle().getLineStyle() != null ? 
                    (float) getStyle().getLineStyle().getWidth() : 1.0f;
                drawList.addLine(
                    (float) p1.x, (float) p1.y,
                    (float) p2.x, (float) p2.y,
                    lineColor, lineWidth
                );
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常
            LOGGER.error("渲染圆弧ImGui时发生错误: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public List<Vec2d> getKeyPoints() {
        List<Vec2d> keyPoints;
        
        try {
            // 添加控制点
            keyPoints = new ArrayList<>(getControlPoints());
            
            // 添加中点（圆弧的中间角度点）
            double midAngle = (startAngle + endAngle) / 2;
            if (endAngle < startAngle) {
                midAngle = (startAngle + endAngle + 2 * Math.PI) / 2;
                if (midAngle > 2 * Math.PI) {
                    midAngle -= 2 * Math.PI;
                }
            }
            
            Vec2d midPoint = new Vec2d(
                center.x + radius * Math.cos(midAngle),
                center.y + radius * Math.sin(midAngle)
            );
            
            // 应用变换
            if (getTransform() != null) {
                midPoint = getTransform().transform(midPoint);
            }
            
            keyPoints.add(midPoint);
            
        } catch (Exception e) {
            LOGGER.error("获取圆弧关键点时发生错误: {}", e.getMessage(), e);
            return super.getKeyPoints();
        }
        
        return keyPoints;
    }
}