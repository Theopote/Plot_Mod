package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.api.render.IRenderVisitor;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.model.Shape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.graphics.style.LineStyle;
import com.plot.core.geometry.RasterizationUtils;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

/**
 * 圆形状
 */
public class CircleShape extends Shape {
    private static final Logger LOGGER = LoggerFactory.getLogger(CircleShape.class);
    private Vec2d center;
    private double radius;
    private static final double EPSILON = 1e-10; // 添加自己的精度常量
    
    public CircleShape(Vec2d center, double radius) {
        super(center != null ? center : new Vec2d(0, 0));
        this.center = center != null ? center : new Vec2d(0, 0);
        this.radius = radius;
    }
    
    public Vec2d getCenter() { return center; }
    public void setCenter(Vec2d center) { this.center = center; }
    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = radius; }
    
    @Override
    public void translate(Vec2d offset) {
        center = center.add(offset);
    }
    
    @Override
    public void rotate(double angle, Vec2d rotationCenter) {
        this.center = GeometryUtils.rotate(center.subtract(rotationCenter), angle).add(rotationCenter);
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        if (transformMatrix.isUniform()) {
            // 均匀变换：可以保持为圆形
            this.center = transformMatrix.transform(this.center);
            this.radius *= transformMatrix.getScaleX(); // 在均匀缩放下，getScaleX() == getScaleY()
            return this;
        } else {
            // 非均匀变换：必须转换为椭圆
            Vec2d newCenter = transformMatrix.transform(this.center);
            
            // 通过变换x轴和y轴的单位向量来计算新椭圆的半轴和旋转角
            Vec2d xAxis = transformMatrix.transformVector(new Vec2d(this.radius, 0));
            Vec2d yAxis = transformMatrix.transformVector(new Vec2d(0, this.radius));
            
            double newRadiusX = xAxis.length();
            double newRadiusY = yAxis.length();
            double newRotation = Math.atan2(xAxis.y, xAxis.x);
            
            EllipseShape ellipse = new EllipseShape(newCenter, newRadiusX, newRadiusY, newRotation);
            if (this.getStyle() != null) {
                ellipse.setStyle(this.getStyle().clone());
            }
            return ellipse;
        }
    }
    
    @Override
    public void scale(Vec2d scale, Vec2d scaleCenter) {
        // 支持非等比缩放
        // 如果缩放是等比的，保持为圆形；否则使用非等比缩放
        if (Math.abs(scale.x - scale.y) < 1e-10) {
            // 等比缩放：保持为圆形
            this.center = scaleCenter.add(this.center.subtract(scaleCenter).multiply(scale));
            this.radius *= scale.x;
        } else {
            // 非等比缩放：将圆形转换为椭圆
            // 计算X和Y方向的半径
            this.center = scaleCenter.add(this.center.subtract(scaleCenter).multiply(scale));
            // 对于非等比缩放，我们需要将圆形转换为椭圆
            // 但由于scale()方法不能改变图形类型，这里先使用平均缩放
            // 真正的转换应该在TransformCommand中通过transform()方法处理
            this.radius *= Math.sqrt((scale.x * scale.x + scale.y * scale.y) / 2);
        }
    }
    
    @Override
    public BoundingBox getBoundingBox() {
        return new BoundingBox(
            new Vec2d(center.x - radius, center.y - radius),
            new Vec2d(center.x + radius, center.y + radius)
        );
    }
    
    @Override
    public boolean containsPoint(Vec2d point, double tolerance) {
        // 计算点到圆心的距离
        double distance = point.subtract(center).length();
        
        // 转换为具体实现类
        ShapeStyle shapeStyle = (ShapeStyle) getStyle();
        
        // 检查是否有填充样式
        boolean hasFill = shapeStyle != null && 
                          shapeStyle.getFillStyle() != null && 
                          shapeStyle.getFillStyle().isVisible();
        
        // 如果是填充的圆形，检查点是否在圆内
        if (hasFill) {
            return distance <= radius;
        }
        
        // 否则检查点是否在圆周线上（考虑线宽）
        double lineWidth = (shapeStyle != null && shapeStyle.getLineStyle() != null) ? 
                           shapeStyle.getLineStyle().getWidth() : 1.0;
        return Math.abs(distance - radius) <= Math.max(tolerance, lineWidth / 2);
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        Vec2d direction = point.subtract(center);
        if (direction.length() < EPSILON) {
            return center.add(new Vec2d(radius, 0)); // 如果点在圆心，返回右侧点
        }
        return center.add(direction.normalize().multiply(radius));
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        List<Vec2d> points = new ArrayList<>();
        points.add(center);
        points.add(center.add(new Vec2d(radius, 0))); // 右侧点用于调整半径
        return points;
    }

    @Override
    public List<Vec2d> getKeyPoints() {
        List<Vec2d> keyPoints = new ArrayList<>();
        
        try {
            // 添加圆心
            keyPoints.add(center);
            
            // 添加四个象限点（上、下、左、右）
            keyPoints.add(new Vec2d(center.x, center.y + radius)); // 上
            keyPoints.add(new Vec2d(center.x, center.y - radius)); // 下
            keyPoints.add(new Vec2d(center.x - radius, center.y)); // 左
            keyPoints.add(new Vec2d(center.x + radius, center.y)); // 右
            
        } catch (Exception e) {
            // 如果计算失败，回退到默认实现
            return super.getKeyPoints();
        }
        
        return keyPoints;
    }
    
    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index == 0) {
            center = point;
        } else if (index == 1) {
            radius = point.distance(center);
        }
    }
    
    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }
    
    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        if (other instanceof LineShape) {
            return getLineIntersections((LineShape) other);
        } else if (other instanceof CircleShape) {
            return getCircleIntersections((CircleShape) other);
        }
        return other.getIntersectionPoints(this);
    }
    
    private List<Vec2d> getLineIntersections(LineShape line) {
        List<Vec2d> result = new ArrayList<>();
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
        
        // 计算线段到圆心的最短距离
        double a = end.x - start.x;
        double b = end.y - start.y;
        double c = start.x - center.x;
        double d = start.y - center.y;
        
        double A = a * a + b * b;
        double B = 2 * (a * c + b * d);
        double C = c * c + d * d - radius * radius;
        
        // 求解二次方程
        double discriminant = B * B - 4 * A * C;
        if (discriminant < 0) return result;
        
        double t1 = (-B + Math.sqrt(discriminant)) / (2 * A);
        double t2 = (-B - Math.sqrt(discriminant)) / (2 * A);
        
        // 检查交点是否在线段上
        if (t1 >= 0 && t1 <= 1) {
            result.add(new Vec2d(
                start.x + t1 * a,
                start.y + t1 * b
            ));
        }
        if (t2 >= 0 && t2 <= 1) {
            result.add(new Vec2d(
                start.x + t2 * a,
                start.y + t2 * b
            ));
        }
        
        return result;
    }
    
    private List<Vec2d> getCircleIntersections(CircleShape other) {
        List<Vec2d> result = new ArrayList<>();
        
        double d = center.distance(other.center);
        double r1 = radius;
        double r2 = other.radius;
        
        // 检查是否相切或不相交
        if (d > r1 + r2 || d < Math.abs(r1 - r2)) return result;
        if (d < EPSILON && Math.abs(r1 - r2) < EPSILON) return result;
        
        // 计算交点
        double a = (r1 * r1 - r2 * r2 + d * d) / (2 * d);
        double h = Math.sqrt(r1 * r1 - a * a);
        
        Vec2d p2 = other.center;
        Vec2d v = p2.subtract(center);
        Vec2d u = v.normalize();
        
        Vec2d p = center.add(u.multiply(a));
        Vec2d n = new Vec2d(-u.y, u.x);
        
        result.add(p.add(n.multiply(h)));
        if (h > EPSILON) {
            result.add(p.add(n.multiply(-h)));
        }
        
        return result;
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 圆不需要延伸
        return new ArrayList<>();
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        // 圆没有端点
        return new ArrayList<>();
    }
    
    @Override
    public Vec2d getTangentAt(Vec2d point) {
        Vec2d radial = point.subtract(center).normalize();
        return new Vec2d(-radial.y, radial.x);
    }
    
    @Override
    public double getSignedDistance(Vec2d point) {
        // 符号：内为负、外为正，数值为到圆周距离
        return point.distance(center) - radius;
    }
    
    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        List<Shape> result = new ArrayList<>();
        if (points.size() < 2) return result;
        
        // 将点按照角度排序
        List<Vec2d> sortedPoints = new ArrayList<>(points);
        Vec2d reference = pickPoint.subtract(center).normalize();
        sortedPoints.sort((p1, p2) -> {
            double angle1 = GeometryUtils.angleBetween(reference, p1.subtract(center).normalize());
            double angle2 = GeometryUtils.angleBetween(reference, p2.subtract(center).normalize());
            return Double.compare(angle1, angle2);
        });
        
        // 创建圆弧
        for (int i = 0; i < sortedPoints.size(); i++) {
            Vec2d start = sortedPoints.get(i);
            Vec2d end = sortedPoints.get((i + 1) % sortedPoints.size());
            double startAngle = Math.atan2(start.y - center.y, start.x - center.x);
            double endAngle = Math.atan2(end.y - center.y, end.x - center.x);
            result.add(new ArcShape(center, radius, startAngle, endAngle));
        }
        
        return result;
    }
    
    @Override
    public Shape extend(Vec2d point, double distance) {
        // 圆不需要延伸
        return clone();
    }
    
    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        // 圆不需要延伸
        return clone();
    }
    
    @Override
    public Shape trimToPoint(Vec2d point) {
        // 圆不能被修剪
        return clone();
    }
    
    @Override
    public Shape createOffset(double distance) {
        double newRadius = radius + distance;
        if (newRadius <= 0) {
            newRadius = Math.max(0.1, newRadius);
        }
        return new CircleShape(center, newRadius);
    }
    
    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> result = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            LineShape line = new LineShape(p1, p2);
            result.addAll(getLineIntersections(line));
        }
        
        return result;
    }
    
    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        return !getIntersectionsWithPolyline(points).isEmpty();
    }
    
    @Override
    public Shape clone() {
        CircleShape clone = new CircleShape(
            new Vec2d(center.x, center.y),
            radius
        );
        clone.setStyle(getStyle().clone());
        return clone;
    }
    
    @Override
    public String serialize() {
        return String.format("CIRCLE %.2f,%.2f %.2f",
            center.x, center.y, radius);
    }
    
    @Override
    public void deserialize(String data) {
        String[] parts = data.split(" ");
        if (parts.length >= 3 && parts[0].equals("CIRCLE")) {
            String[] c = parts[1].split(",");
            center = new Vec2d(
                Double.parseDouble(c[0]),
                Double.parseDouble(c[1])
            );
            radius = Double.parseDouble(parts[2]);
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
        
        // 获取变换后的中心点和半径
        Vec2d transformedCenter = transform.transform(center);
        double transformedRadius = radius * transform.getScale().x;
        
        // 如果有填充样式且可见，先绘制填充
        if (activeStyle.getFillStyle() != null && activeStyle.getFillStyle().isVisible()) {
            java.awt.Color fillColor = activeStyle.getFillStyle().getColor();
            if (fillColor != null) {
                // TODO: 实现圆形填充
                // context.fillCircle(transformedCenter, transformedRadius, fillColor);
            }
        }
        
        // 如果线条样式可见，绘制轮廓
        if (activeStyle.getLineStyle().isVisible()) {
            // 使用DrawContext的标准圆形绘制方法，支持线宽和样式
            if (activeStyle.getLineStyle() instanceof LineStyle lineStyle) {
                context.drawCircle(transformedCenter, (float)transformedRadius, lineStyle);
            } else {
                java.awt.Color lineColor = activeStyle.getLineStyle().getColor();
                context.drawCircle(transformedCenter, (float)transformedRadius, lineColor);
            }
        }
    }

    @Override
    public boolean contains(Vec2d point) {
        // 使用 containsPoint 方法，容差设为 0，避免代码重复
        return containsPoint(point, 0.0);
    }

    @Override
    public void mirror(Vec2d start, Vec2d end) {
        // 计算镜像线的向量和法向量
        Vec2d lineVec = end.subtract(start).normalize();
        Vec2d normal = new Vec2d(-lineVec.y, lineVec.x);
        
        // 镜像中心点
        double dist = center.subtract(start).dot(normal);
        center = center.subtract(normal.multiply(2 * dist));
    }

    @Override
    public List<Vec2d> getPoints() {
        List<Vec2d> points = new ArrayList<>();
        
        // 获取变换后的圆心和半径
        Vec2d transformedCenter = transform.transform(center);
        double transformedRadius = radius * transform.getScale().x; // 使用 x 缩放因子
        
        // 动态计算分段数，基于半径和缩放级别
        int segments = calculateDynamicSegments(transformedRadius);
        
        // 生成圆上的点
        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            points.add(new Vec2d(
                transformedCenter.x + transformedRadius * Math.cos(angle),
                transformedCenter.y + transformedRadius * Math.sin(angle)
            ));
        }
        
        return points;
    }
    
    /**
     * 动态计算圆形分段数，基于半径和缩放级别
     * @param radius 圆的半径
     * @return 计算得出的分段数
     */
    private int calculateDynamicSegments(double radius) {
        // 基础分段数，确保最小质量
        int baseSegments = 16;
        
        // 根据半径动态调整分段数
        // 半径越大，需要的分段数越多以保持视觉质量
        int calculatedSegments = Math.max(baseSegments, (int)(radius / 2.0));
        
        // 限制最大分段数，避免性能问题
        calculatedSegments = Math.min(calculatedSegments, 360);
        
        return calculatedSegments;
    }
    
    /**
     * 获取圆形光栅化后的方块位置
     * 使用中点圆算法优化性能
     * 
     * @return 圆形轮廓上的方块位置列表
     */
    @Override
    public List<Vec2d> getBlockPositions() {
        // 获取变换后的圆心和半径
        Vec2d transformedCenter = transform.transform(center);
        double transformedRadius = radius * transform.getScale().x; // 使用 x 缩放因子
        
        // 检查是否有填充样式
        ShapeStyle shapeStyle = (ShapeStyle) getStyle();
        boolean hasFill = shapeStyle != null && 
                          shapeStyle.getFillStyle() != null && 
                          shapeStyle.getFillStyle().isVisible();
        
        if (hasFill) {
            // 如果有填充，返回填充圆形的方块位置
            return RasterizationUtils.rasterizeFilledCircle(transformedCenter, transformedRadius);
        } else {
            // 否则返回圆形轮廓的方块位置
            return RasterizationUtils.rasterizeCircle(transformedCenter, transformedRadius);
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
    public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
        List<Shape> newShapes = new ArrayList<>();
        
        try {
            if ("SINGLE_POINT".equals(breakMode)) {
                // 将点击点转换到形状本地坐标系再计算角度，避免变换导致的误差
                Vec2d localP = getTransform() != null ? getTransform().inverseTransform(firstBreakPoint) : firstBreakPoint;
                double a = Math.atan2(localP.y - center.y, localP.x - center.x);
                // 根据点击角度按 [0->a] 与 [a->2π] 分割（不删除任何部分）
                ArcShape arc1 = new ArcShape(new Vec2d(center.x, center.y), radius, 0.0, a);
                ArcShape arc2 = new ArcShape(new Vec2d(center.x, center.y), radius, a, 2 * Math.PI);
                if (getStyle() != null) { arc1.setStyle(getStyle().clone()); arc2.setStyle(getStyle().clone()); }
                if (getTransform() != null) { arc1.setTransform(getTransform().clone()); arc2.setTransform(getTransform().clone()); }
                newShapes.add(arc1);
                newShapes.add(arc2);
            } else if ("TWO_POINT".equals(breakMode) && secondBreakPoint != null) {
                // 改进：圆形两点打断 - 移除用户点击的两点之间的弧段
                Vec2d localP1 = getTransform() != null ? getTransform().inverseTransform(firstBreakPoint) : firstBreakPoint;
                Vec2d localP2 = getTransform() != null ? getTransform().inverseTransform(secondBreakPoint) : secondBreakPoint;
                double a1 = Math.atan2(localP1.y - center.y, localP1.x - center.x);
                double a2 = Math.atan2(localP2.y - center.y, localP2.x - center.x);
                
                // 规范化角度到 [0, 2π)
                if (a1 < 0) a1 += 2 * Math.PI;
                if (a2 < 0) a2 += 2 * Math.PI;
                
                // 确保 a1 <= a2，如果不符合则交换
                if (a1 > a2) {
                    double temp = a1;
                    a1 = a2;
                    a2 = temp;
                }
                
                // 创建两个弧段：从 a1 到 a2 的弧段被移除，保留剩余部分
                // 弧段1：从 a2 到 2π + a1（跨越0度）
                if (a2 < 2 * Math.PI) {
                    ArcShape arc1 = new ArcShape(new Vec2d(center.x, center.y), radius, a2, 2 * Math.PI);
                    if (getStyle() != null) arc1.setStyle(getStyle().clone());
                    if (getTransform() != null) arc1.setTransform(getTransform().clone());
                    newShapes.add(arc1);
                }
                
                // 弧段2：从 0 到 a1
                if (a1 > 0) {
                    ArcShape arc2 = new ArcShape(new Vec2d(center.x, center.y), radius, 0, a1);
                    if (getStyle() != null) arc2.setStyle(getStyle().clone());
                    if (getTransform() != null) arc2.setTransform(getTransform().clone());
                    newShapes.add(arc2);
                }
                
                // 如果两个弧段都为空（即 a1 = 0 且 a2 = 2π），则整个圆被移除
                // 这种情况下 newShapes 保持为空列表，表示没有剩余部分
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常，返回空列表
            LOGGER.error("打断圆形时发生错误", e);
        }
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        return GeometryUtils.getDistanceToCircle(center, radius, point);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        try {
            // 获取变换后的圆心和半径
            Vec2d transformedCenter = getTransform().transform(center);
            double transformedRadius = radius * getTransform().getScale().x; // 使用 x 缩放因子
            
            // 转换到屏幕坐标
            Vec2d screenCenter = camera.worldToScreen(transformedCenter);
            float screenRadius = (float) (transformedRadius * camera.getZoom());

            // 根据选中/高亮状态确定线条与填充颜色、线宽（对齐椭圆的视觉反馈逻辑）
            int lineColor;
            float lineWidth;
            int fillColor;

            if (isSelected()) {
                // 选中：亮黄色，加粗
                lineColor = 0xFFFFD700; // 亮黄色
                lineWidth = 2.5f;
                fillColor = 0x50FFD700; // 半透明亮黄色
            } else if (isHighlighted()) {
                // 高亮：橙色
                lineColor = 0xFFFF8C00; // 橙色
                lineWidth = 2.0f;
                fillColor = 0x40FF8C00; // 半透明橙色
            } else {
                // 正常：使用样式或默认白色
                if (getStyle() != null && getStyle().getLineStyle() != null) {
                    java.awt.Color styleColor = getStyle().getLineStyle().getColor();
                    lineColor = styleColor != null ? styleColor.getRGB() : 0xFFFFFFFF;
                    lineWidth = getStyle().getLineStyle().getWidth();
                } else {
                    lineColor = 0xFFFFFFFF; // 默认白色
                    lineWidth = 1.0f;
                }
                fillColor = 0x80FFFFFF; // 默认半透明白色
            }

            // 是否填充
            boolean isFilled = getStyle() != null &&
                               getStyle().getFillStyle() != null &&
                               getStyle().getFillStyle().isVisible();

            if (isFilled) {
                // 填充圆形
                drawList.addCircleFilled(
                    (float) screenCenter.x, (float) screenCenter.y,
                    screenRadius,
                    fillColor
                );
            }

            // 描边圆形
            drawList.addCircle(
                (float) screenCenter.x, (float) screenCenter.y,
                screenRadius,
                lineColor, 36, lineWidth
            );
            
        } catch (Exception e) {
            // 记录错误但不抛出异常
            LOGGER.error("渲染圆形ImGui时发生错误", e);
        }
    }
}
