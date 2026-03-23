package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.api.render.IRenderVisitor;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.model.Shape;
import com.plot.core.graphics.DrawContext;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 椭圆弧形状
 * 
 * <p>用于处理非均匀缩放后的圆弧，当圆弧经过非均匀缩放后会变成椭圆弧。
 * 这是ArcShape在非均匀变换下的正确表示。</p>
 * 
 * <p>定义参数：</p>
 * <ul>
 *   <li>center: 椭圆中心点</li>
 *   <li>radiusX: X轴半径</li>
 *   <li>radiusY: Y轴半径</li>
 *   <li>rotation: 椭圆旋转角度（弧度）</li>
 *   <li>startAngle: 起始角度（弧度）</li>
 *   <li>endAngle: 结束角度（弧度）</li>
 * </ul>
 */
public class EllipticalArcShape extends Shape {

    private Vec2d center;
    private double radiusX;
    private double radiusY;
    private double rotation;
    private double startAngle;
    private double endAngle;
    
    // 缓存字段
    private List<Vec2d> cachedPoints;
    private BoundingBox cachedBoundingBox;
    private boolean cacheValid = false;
    
    public EllipticalArcShape(Vec2d center, double radiusX, double radiusY, 
                             double rotation, double startAngle, double endAngle) {
        super(center != null ? center : new Vec2d(0, 0));
        this.center = center != null ? center : new Vec2d(0, 0);
        this.radiusX = Math.max(0, radiusX);
        this.radiusY = Math.max(0, radiusY);
        this.rotation = rotation;
        this.startAngle = startAngle;
        this.endAngle = endAngle;
    }
    
    /**
     * 从ArcShape创建EllipticalArcShape
     */
    public EllipticalArcShape(ArcShape arcShape, AffineTransform transform) {
        super(arcShape.getCenter());
        this.center = transform.transform(arcShape.getCenter());
        
        // 通过变换x轴和y轴的单位向量来计算新椭圆的半轴和旋转角
        Vec2d xAxis = transform.transformVector(new Vec2d(arcShape.getRadius(), 0));
        Vec2d yAxis = transform.transformVector(new Vec2d(0, arcShape.getRadius()));
        
        this.radiusX = xAxis.length();
        this.radiusY = yAxis.length();
        this.rotation = Math.atan2(xAxis.y, xAxis.x);
        
        // 角度需要根据变换进行调整
        this.startAngle = arcShape.getStartAngle() + transform.getRotation();
        this.endAngle = arcShape.getEndAngle() + transform.getRotation();
    }
    
    // Getters and Setters
    public Vec2d getCenter() { return center; }
    public void setCenter(Vec2d center) { 
        this.center = center; 
        invalidateCache(); 
    }
    
    public double getRadiusX() { return radiusX; }

    public double getRadiusY() { return radiusY; }
    
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { 
        this.rotation = rotation; 
        invalidateCache(); 
    }
    
    public double getStartAngle() { return startAngle; }
    public void setStartAngle(double startAngle) { 
        this.startAngle = startAngle; 
        invalidateCache(); 
    }
    
    public double getEndAngle() { return endAngle; }
    public void setEndAngle(double endAngle) { 
        this.endAngle = endAngle; 
        invalidateCache(); 
    }
    
    @Override
    public void translate(Vec2d offset) {
        center = center.add(offset);
        invalidateCache();
    }
    
    @Override
    public void rotate(double angle, Vec2d center) {
        this.center = GeometryUtils.rotate(this.center.subtract(center), angle).add(center);
        this.rotation += angle;
        this.startAngle += angle;
        this.endAngle += angle;
        invalidateCache();
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 椭圆弧变换后仍然是椭圆弧
        Vec2d newCenter = transformMatrix.transform(center);
        
        // 通过变换x轴和y轴的单位向量来计算新椭圆的半轴和旋转角
        Vec2d xAxis = transformMatrix.transformVector(new Vec2d(radiusX, 0));
        Vec2d yAxis = transformMatrix.transformVector(new Vec2d(0, radiusY));
        
        double newRadiusX = xAxis.length();
        double newRadiusY = yAxis.length();
        double newRotation = rotation + transformMatrix.getRotation();
        
        // 角度需要根据变换进行调整
        double newStartAngle = startAngle + transformMatrix.getRotation();
        double newEndAngle = endAngle + transformMatrix.getRotation();
        
        // 更新参数
        this.center = newCenter;
        this.radiusX = newRadiusX;
        this.radiusY = newRadiusY;
        this.rotation = newRotation;
        this.startAngle = newStartAngle;
        this.endAngle = newEndAngle;
        
        invalidateCache();
        return this;
    }
    
    @Override
    public void scale(Vec2d scale, Vec2d scaleCenter) {
        // 椭圆弧的缩放：参考EllipseShape的实现
        // 1. 缩放中心点
        if (!center.equals(scaleCenter)) {
            Vec2d centerOffset = center.subtract(scaleCenter);
            centerOffset = new Vec2d(
                centerOffset.x * scale.x,
                centerOffset.y * scale.y
            );
            center = scaleCenter.add(centerOffset);
        }
        
        // 2. 检查是否为均匀缩放
        if (Math.abs(scale.x - scale.y) < 1e-6) {
            // 均匀缩放：直接缩放半径
            radiusX *= scale.x;
            radiusY *= scale.y;
            invalidateCache();
            return;
        }
        
        // 3. 检查椭圆是否未旋转
        if (Math.abs(rotation % Math.PI) < 1e-6) {
            // 未旋转椭圆：直接缩放对应轴的半径
            radiusX *= scale.x;
            radiusY *= scale.y;
            invalidateCache();
            return;
        }
        
        // 4. 旋转椭圆的非均匀缩放：使用transform方法
        // 创建以指定中心点进行缩放的变换矩阵
        AffineTransform translate1 = AffineTransform.createTranslation(-scaleCenter.x, -scaleCenter.y);
        AffineTransform scaleTransform = AffineTransform.createScale(scale.x, scale.y);
        AffineTransform translate2 = AffineTransform.createTranslation(scaleCenter.x, scaleCenter.y);
        
        // 组合变换：translate2 * scaleTransform * translate1
        AffineTransform transform = translate2.multiply(scaleTransform).multiply(translate1);
        
        // 使用transform方法进行变换
        transform(transform);
    }
    
    @Override
    public BoundingBox getBoundingBox() {
        if (!cacheValid || cachedBoundingBox == null) {
            calculateBoundingBox();
        }
        return cachedBoundingBox;
    }
    
    private void calculateBoundingBox() {
        List<Vec2d> points = getPoints();
        if (points.isEmpty()) {
            cachedBoundingBox = new BoundingBox(center, center);
            return;
        }
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (Vec2d point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        
        cachedBoundingBox = new BoundingBox(new Vec2d(minX, minY), new Vec2d(maxX, maxY));
    }
    
    @Override
    public List<Vec2d> getPoints() {
        if (!cacheValid || cachedPoints == null) {
            calculatePoints();
        }
        return new ArrayList<>(cachedPoints);
    }
    
    private void calculatePoints() {
        cachedPoints = new ArrayList<>();
        
        // 计算椭圆弧上的点
        int numPoints = Math.max(16, (int) Math.ceil(Math.abs(endAngle - startAngle) * 8));
        double angleStep = (endAngle - startAngle) / (numPoints - 1);
        
        for (int i = 0; i < numPoints; i++) {
            double angle = startAngle + i * angleStep;
            double x = center.x + radiusX * Math.cos(angle) * Math.cos(rotation) - 
                      radiusY * Math.sin(angle) * Math.sin(rotation);
            double y = center.y + radiusX * Math.cos(angle) * Math.sin(rotation) + 
                      radiusY * Math.sin(angle) * Math.cos(rotation);
            cachedPoints.add(new Vec2d(x, y));
        }
    }
    
    @Override
    public boolean contains(Vec2d point) {
        // 将点转换到椭圆的局部坐标系
        Vec2d localPoint = point.subtract(center);
        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);
        
        double localX = localPoint.x * cos - localPoint.y * sin;
        double localY = localPoint.x * sin + localPoint.y * cos;
        
        // 检查点是否在椭圆内
        double ellipseValue = (localX * localX) / (radiusX * radiusX) + 
                             (localY * localY) / (radiusY * radiusY);
        
        if (ellipseValue > 1.0) {
            return false; // 不在椭圆内
        }
        
        // 检查角度是否在范围内
        double angle = Math.atan2(localY, localX);
        return isAngleInRange(angle);
    }
    
    private boolean isAngleInRange(double angle) {
        // 标准化角度到 [0, 2π] 范围
        while (angle < 0) angle += 2 * Math.PI;
        while (angle >= 2 * Math.PI) angle -= 2 * Math.PI;
        
        double normalizedStart = startAngle;
        double normalizedEnd = endAngle;
        
        while (normalizedStart < 0) normalizedStart += 2 * Math.PI;
        while (normalizedStart >= 2 * Math.PI) normalizedStart -= 2 * Math.PI;
        while (normalizedEnd < 0) normalizedEnd += 2 * Math.PI;
        while (normalizedEnd >= 2 * Math.PI) normalizedEnd -= 2 * Math.PI;
        
        if (normalizedStart <= normalizedEnd) {
            return angle >= normalizedStart && angle <= normalizedEnd;
        } else {
            return angle >= normalizedStart || angle <= normalizedEnd;
        }
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        // 简化实现：返回椭圆弧上最近的点
        List<Vec2d> points = getPoints();
        if (points.isEmpty()) {
            return center;
        }
        
        Vec2d closest = points.getFirst();
        double minDistance = point.distance(closest);
        
        for (Vec2d arcPoint : points) {
            double distance = point.distance(arcPoint);
            if (distance < minDistance) {
                minDistance = distance;
                closest = arcPoint;
            }
        }
        
        return closest;
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        List<Vec2d> controlPoints = new ArrayList<>();
        controlPoints.add(center);
        
        // 添加起始点和结束点
        Vec2d startPoint = new Vec2d(
            center.x + radiusX * Math.cos(startAngle) * Math.cos(rotation) - 
            radiusY * Math.sin(startAngle) * Math.sin(rotation),
            center.y + radiusX * Math.cos(startAngle) * Math.sin(rotation) + 
            radiusY * Math.sin(startAngle) * Math.cos(rotation)
        );
        controlPoints.add(startPoint);
        
        Vec2d endPoint = new Vec2d(
            center.x + radiusX * Math.cos(endAngle) * Math.cos(rotation) - 
            radiusY * Math.sin(endAngle) * Math.sin(rotation),
            center.y + radiusX * Math.cos(endAngle) * Math.sin(rotation) + 
            radiusY * Math.sin(endAngle) * Math.cos(rotation)
        );
        controlPoints.add(endPoint);
        
        return controlPoints;
    }
    
    @Override
    public void setControlPoint(int index, Vec2d point) {
        // 简化实现：只允许移动中心点
        if (index == 0) {
            setCenter(point);
        }
    }
    
    @Override
    public boolean intersects(Shape other) {
        // 简化实现：使用边界框相交测试
        return getBoundingBox().intersects(other.getBoundingBox());
    }
    
    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        // 简化实现：返回空列表
        return new ArrayList<>();
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        return new ArrayList<>();
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        List<Vec2d> endpoints = new ArrayList<>();
        List<Vec2d> points = getPoints();
        if (!points.isEmpty()) {
            endpoints.add(points.getFirst());
            endpoints.add(points.getLast());
        }
        return endpoints;
    }
    
    @Override
    public Vec2d getTangentAt(Vec2d point) {
        // 简化实现：返回零向量
        return new Vec2d(0, 0);
    }
    
    @Override
    public double getSignedDistance(Vec2d point) {
        Vec2d closest = getClosestPoint(point);
        return point.distance(closest);
    }
    
    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        // 简化实现：返回自身
        return Collections.singletonList(this);
    }
    
    @Override
    public Shape extend(Vec2d point, double distance) {
        // 简化实现：返回自身
        return this;
    }
    
    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        // 简化实现：返回自身
        return this;
    }
    
    @Override
    public Shape trimToPoint(Vec2d point) {
        // 简化实现：返回自身
        return this;
    }
    
    @Override
    public Shape createOffset(double distance) {
        // 简化实现：返回自身
        return this;
    }
    
    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        return new ArrayList<>();
    }
    
    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        return false;
    }
    
    @Override
    public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        // 简化实现：返回自身
        return Collections.singletonList(this);
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        return getSignedDistance(point);
    }
    
    @Override
    public void draw(DrawContext context) {
        if (!isVisible() || isDeleted()) {
            return;
        }
        
        List<Vec2d> points = getPoints();
        if (points.size() < 2) {
            return;
        }
        
        // 绘制椭圆弧
        for (int i = 0; i < points.size() - 1; i++) {
            context.drawLine(points.get(i), points.get(i + 1));
        }
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        // ImGui绘制实现
        if (!isVisible() || isDeleted()) {
            return;
        }
        
        List<Vec2d> points = getPoints();
        if (points.size() < 2) {
            return;
        }
        
        // 转换坐标并绘制
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d screenStart = camera.worldToScreen(points.get(i));
            Vec2d screenEnd = camera.worldToScreen(points.get(i + 1));
            
            int color = isSelected() ? 0xFF00FF00 : 0xFFFFFFFF; // 绿色表示选中
            drawList.addLine((float)screenStart.x, (float)screenStart.y, (float)screenEnd.x, (float)screenEnd.y, color, 2.0f);
        }
    }
    
    @Override
    public String serialize() {
        return String.format("EllipticalArc:center=(%.3f,%.3f),radiusX=%.3f,radiusY=%.3f,rotation=%.3f,startAngle=%.3f,endAngle=%.3f",
                center.x, center.y, radiusX, radiusY, rotation, startAngle, endAngle);
    }
    
    @Override
    public void deserialize(String data) {
        // 简化实现：不实现反序列化
    }
    
    @Override
    public Shape accept(IShapeVisitor visitor) {
        return visitor.visit(this);
    }
    
    @Override
    public void accept(IRenderVisitor visitor, imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        // 简化实现：直接绘制
        drawImGui(drawList, camera);
    }
    
    private void invalidateCache() {
        cacheValid = false;
        cachedPoints = null;
        cachedBoundingBox = null;
    }
    
    @Override
    public Shape clone() {
        Shape shape = super.clone();
        EllipticalArcShape clone = new EllipticalArcShape(center, radiusX, radiusY, rotation, startAngle, endAngle);
        clone.setStyle(this.getStyle());
        clone.setSelected(this.isSelected());
        clone.setVisible(this.isVisible());
        clone.setHighlighted(this.isHighlighted());
        return clone;
    }
}