package com.masterplanner.core.geometry.shapes;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.render.IRenderVisitor;
import com.masterplanner.api.shape.IExtendableShape;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.geometry.GeometryUtils;
import com.masterplanner.core.geometry.AffineTransform;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.core.geometry.RasterizationUtils;
import com.masterplanner.ui.tools.impl.modify.helper.IShapeVisitor;

import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * 直线段形状
 */
public class LineShape extends Shape implements IExtendableShape {
    // 浮点数精度常量
    private static final double EPSILON = 1e-10;
    
    private Vec2d start;
    private Vec2d end;
    
    // 缓存字段，用于性能优化
    private Vec2d v; // 方向向量 (end - start)
    private double lengthSquared; // 线段长度的平方
    private List<Vec2d> controlPoints; // 缓存的控制点列表
    
    public LineShape(Vec2d start, Vec2d end) {
        // 调用父类构造函数必须是第一条语句
        super(start != null ? start : new Vec2d(0, 0));
        
        // 处理空值，避免重复检查
        Vec2d safeStart = start != null ? start : new Vec2d(0, 0);
        Vec2d safeEnd = end != null ? end : new Vec2d(0, 0);
        
        // 初始化字段
        this.start = safeStart;
        this.end = safeEnd;
        
        // 初始化缓存值
        updateCachedValues();
    }
    
    /**
     * 更新缓存的计算值
     */
    private void updateCachedValues() {
        v = end.subtract(start);
        lengthSquared = v.dot(v);
        
        // 更新控制点列表
        if (controlPoints == null) {
            controlPoints = new ArrayList<>(Arrays.asList(start, end));
        } else {
            controlPoints.clear();
            controlPoints.add(start);
            controlPoints.add(end);
        }
    }
    
    public Vec2d getStart() {
        return start;
    }
    
    public void setStart(Vec2d start) {
        this.start = start;
        updateCachedValues();
    }
    
    public Vec2d getEnd() {
        return end;
    }
    
    public void setEnd(Vec2d end) {
        this.end = end;
        updateCachedValues();
    }
    
    @Override
    public void translate(Vec2d offset) {
        start = start.add(offset);
        end = end.add(offset);
        // 修复：虽然平移不改变方向向量和长度，但需要更新控制点列表的位置
        updateCachedValues();
    }
    
    /**
     * 以线段中点为中心旋转线段
     * @param angle 旋转角度（弧度）
     */
    public void rotate(double angle) {
        Vec2d center = start.add(end).multiply(0.5); // 中点
        rotate(angle, center);
    }
    
    @Override
    public void rotate(double angle, Vec2d center) {
        start = GeometryUtils.rotate(start.subtract(center), angle).add(center);
        end = GeometryUtils.rotate(end.subtract(center), angle).add(center);
        updateCachedValues();
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 直接对起点和终点应用变换
        this.start = transformMatrix.transform(this.start);
        this.end = transformMatrix.transform(this.end);
        updateCachedValues(); // 更新方向向量和长度
        return this; // 线段变换后仍然是线段
    }
    
    /**
     * 以线段中点为中心缩放线段
     * @param scale 缩放比例
     */
    public void scale(Vec2d scale) {
        Vec2d center = start.add(end).multiply(0.5); // 中点
        scale(scale, center);
    }
    
    @Override
    public void scale(Vec2d scale, Vec2d center) {
        start = center.add(start.subtract(center).multiply(scale));
        end = center.add(end.subtract(center).multiply(scale));
        updateCachedValues();
    }
    
    @Override
    public BoundingBox getBoundingBox() {
        return new BoundingBox(
            new Vec2d(
                Math.min(start.x, end.x),
                Math.min(start.y, end.y)
            ),
            new Vec2d(
                Math.max(start.x, end.x),
                Math.max(start.y, end.y)
            )
        );
    }
    
    @Override
    public boolean containsPoint(Vec2d point, double tolerance) {
        // 使用缓存的方向向量计算点到线段的距离
        Vec2d w = point.subtract(start);
        
        double c1 = w.dot(v);
        if (c1 <= EPSILON) {
            // 点在起点外侧，计算到起点的距离
            return point.distance(start) <= tolerance;
        }
        
        if (c1 >= lengthSquared - EPSILON) {
            // 点在终点外侧，计算到终点的距离
            return point.distance(end) <= tolerance;
        }
        
        // 点在线段投影上，计算点到线的垂直距离
        double b = c1 / lengthSquared;
        Vec2d pb = start.add(v.multiply(b));
        double distance = point.distance(pb);
        
        // 添加调试日志
        org.slf4j.LoggerFactory.getLogger(LineShape.class).debug("LineShape: 点 {} 到线段 ({} -> {}) 的距离为 {}，容差为 {}，结果: {}", point, start, end, distance, tolerance, distance <= tolerance);
        
        return distance <= tolerance;
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        Vec2d w = point.subtract(start);
        
        double c1 = w.dot(v);
        if (c1 <= EPSILON) return start;
        
        if (lengthSquared - c1 <= EPSILON) return end;
        
        double b = c1 / lengthSquared;
        return start.add(v.multiply(b));
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        return Collections.unmodifiableList(controlPoints);
    }
    
    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index < 0 || index > 1) {
            throw new IllegalArgumentException("Index must be 0 or 1");
        }
        if (index == 0) start = point;
        else end = point;
        updateCachedValues();
    }
    
    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }
    
    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        if (other instanceof LineShape) {
            return GeometryUtils.segmentIntersection(
                start, end,
                ((LineShape) other).start,
                ((LineShape) other).end
            );
        }
        return other.getIntersectionPoints(this);
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 计算延伸方向
        Vec2d direction;
        if (point.distance(start) < point.distance(end)) {
            direction = start.subtract(end).normalize();
        } else {
            direction = end.subtract(start).normalize();
        }
        
        // 创建延伸线段
        Vec2d extendedPoint = point.add(direction.multiply(maxDistance));
        LineShape extendedLine = new LineShape(point, extendedPoint);
        
        return extendedLine.getIntersectionsWith(other);
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        List<Vec2d> points = new ArrayList<>();
        points.add(start);
        points.add(end);
        return points;
    }

    @Override
    public List<Vec2d> getKeyPoints() {
        List<Vec2d> keyPoints = new ArrayList<>();
        
        try {
            // 添加端点
            keyPoints.add(start);
            keyPoints.add(end);
            
            // 添加中点
            Vec2d midpoint = new Vec2d(
                (start.x + end.x) * 0.5,
                (start.y + end.y) * 0.5
            );
            keyPoints.add(midpoint);
            
        } catch (Exception e) {
            // 如果计算失败，回退到默认实现
            return super.getKeyPoints();
        }
        
        return keyPoints;
    }
    
    @Override
    public Vec2d getTangentAt(Vec2d point) {
        return end.subtract(start).normalize();
    }
    
    @Override
    public double getSignedDistance(Vec2d point) {
        Vec2d v = end.subtract(start);
        Vec2d w = point.subtract(start);
        double cross = v.cross(w);
        return cross / v.length();
    }
    
    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        List<Shape> result = new ArrayList<>();
        if (points.isEmpty()) return result;
        
        // 将所有点按照到起点的距离排序
        List<Vec2d> sortedPoints = new ArrayList<>(points);
        sortedPoints.sort((p1, p2) -> 
            Double.compare(p1.distance(start), p2.distance(start)));
        
        // 找到最近的分割点
        Vec2d splitPoint = null;
        double minDistance = Double.MAX_VALUE;
        for (Vec2d point : sortedPoints) {
            double distance = point.distance(pickPoint);
            if (distance < minDistance) {
                minDistance = distance;
                splitPoint = point;
            }
        }
        
        if (splitPoint != null) {
            // 创建两个新线段
            result.add(new LineShape(start, splitPoint));
            result.add(new LineShape(splitPoint, end));
        }
        
        return result;
    }
    
    @Override
    public Shape extend(Vec2d point, double distance) {
        // 确定延伸方向
        Vec2d direction;
        Vec2d newStart = start;
        Vec2d newEnd = end;
        
        if (point.distance(start) < point.distance(end)) {
            direction = start.subtract(end).normalize();
            newStart = start.add(direction.multiply(distance));
        } else {
            direction = end.subtract(start).normalize();
            newEnd = end.add(direction.multiply(distance));
        }
        
        return new LineShape(newStart, newEnd);
    }
    
    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        // 确定延伸端点
        if (point.distance(start) < point.distance(end)) {
            return new LineShape(toPoint, end);
        } else {
            return new LineShape(start, toPoint);
        }
    }
    
    @Override
    public Shape trimToPoint(Vec2d point) {
        return new LineShape(start, point);
    }
    
    @Override
    public Shape createOffset(double distance) {
        // 使用缓存的方向向量
        if (lengthSquared < EPSILON) {
            // 退化情况：起点等于终点，返回原线段的副本
            return new LineShape(start, end);
        }
        
        // 计算法向量并归一化
        Vec2d normal = new Vec2d(-v.y, v.x);
        // 使用已缓存的长度平方进行归一化
        double length = Math.sqrt(lengthSquared);
        normal = normal.multiply(1.0 / length);
        
        // 计算偏移量并创建新线段
        Vec2d offset = normal.multiply(distance);
        return new LineShape(
            start.add(offset),
            end.add(offset)
        );
    }
    
    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> result = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            result.addAll(GeometryUtils.segmentIntersection(
                start, end, p1, p2
            ));
        }
        
        return result;
    }
    
    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        return !getIntersectionsWithPolyline(points).isEmpty();
    }
    
    @Override
    public void draw(DrawContext context) {
        // 优先使用DrawContext中设置的样式（用于选中和高亮状态）
        ShapeStyle activeStyle = context.getCurrentStyle();
        if (activeStyle == null) {
            // 如果DrawContext没有设置样式，使用图形自己的样式
            activeStyle = (ShapeStyle) getStyle();
        }
        
        // 确保样式是ShapeStyle类型
        if (!(activeStyle instanceof ShapeStyle)) {
            throw new IllegalStateException("LineShape requires a ShapeStyle");
        }
        
        // 确保LineStyle存在且可见
        if (activeStyle.getLineStyle() == null) {
            throw new IllegalStateException("LineShape requires a LineStyle");
        }
        
        if (!activeStyle.getLineStyle().isVisible()) return;
        
        // 获取变换后的点
        Vec2d transformedStart = getTransform().transform(start);
        Vec2d transformedEnd = getTransform().transform(end);
        
        // 使用DrawContext的drawLine方法，传递LineStyle
        if (activeStyle.getLineStyle() instanceof LineStyle lineStyle) {
            context.drawLine(transformedStart, transformedEnd, lineStyle);
        } else {
            // 如果没有LineStyle，使用默认颜色绘制
            Color color = new Color(activeStyle.getLineStyle().getColor().getRGB());
            context.drawLine(transformedStart, transformedEnd, color);
        }
    }
    
    @Override
    public boolean contains(Vec2d point) {
        // 计算点到线段的距离
        Vec2d w = point.subtract(start);
        
        // 获取线宽，如果样式为null则使用默认值
        double lineWidth = 1.0;
        if (getStyle() != null && getStyle().getLineStyle() != null) {
            lineWidth = getStyle().getLineStyle().getWidth();
        }
        
        double c1 = w.dot(v);
        if (c1 <= EPSILON) return point.subtract(start).length() <= lineWidth / 2;
        
        if (lengthSquared - c1 <= EPSILON) return point.subtract(end).length() <= lineWidth / 2;
        
        double b = c1 / lengthSquared;
        Vec2d pb = start.add(v.multiply(b));
        return point.subtract(pb).length() <= lineWidth / 2;
    }
    
    @Override
    public Shape clone() {
        Shape shape = super.clone();
        LineShape clone = new LineShape(
            new Vec2d(start.x, start.y),
            new Vec2d(end.x, end.y)
        );
        clone.setStyle(getStyle().clone());
        return clone;
    }
    
    @Override
    public String serialize() {
        return String.format("LINE %.2f,%.2f %.2f,%.2f",
            start.x, start.y, end.x, end.y);
    }
    
    @Override
    public void deserialize(String data) {
        try {
            String[] parts = data.split(" ");
            if (parts.length < 3 || !parts[0].equals("LINE")) {
                throw new IllegalArgumentException("Invalid format: expected 'LINE x1,y1 x2,y2'");
            }
            
            String[] p1 = parts[1].split(",");
            String[] p2 = parts[2].split(",");
            
            if (p1.length != 2 || p2.length != 2) {
                throw new IllegalArgumentException("Invalid coordinate format: expected 'x,y'");
            }
            
            start = new Vec2d(
                Double.parseDouble(p1[0]),
                Double.parseDouble(p1[1])
            );
            
            end = new Vec2d(
                Double.parseDouble(p2[0]),
                Double.parseDouble(p2[1])
            );
            
            updateCachedValues();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse coordinate values: " + data, e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize LineShape: " + data, e);
        }
    }
    
    @Override
    public List<Vec2d> getPoints() {
        List<Vec2d> points = new ArrayList<>();
        
        // 获取变换后的起点和终点
        Vec2d transformedStart = transform.transform(start);
        Vec2d transformedEnd = transform.transform(end);
        
        // 添加起点和终点
        points.add(transformedStart);
        points.add(transformedEnd);
        
        return points;
    }
    
    /**
     * 获取直线光栅化后的方块位置
     * 使用布雷森汉姆算法优化性能
     * 
     * @return 直线路径上的方块位置列表
     */
    @Override
    public List<Vec2d> getBlockPositions() {
        // 获取变换后的起点和终点
        Vec2d transformedStart = transform.transform(start);
        Vec2d transformedEnd = transform.transform(end);
        
        // 使用布雷森汉姆算法光栅化直线
        return RasterizationUtils.rasterizeLine(transformedStart, transformedEnd);
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
        
        try {
            // 坐标统一：将输入点转换到形状局部坐标系
            Vec2d lp1 = firstBreakPoint;
            Vec2d lp2 = secondBreakPoint;
            if (getTransform() != null) {
                lp1 = getTransform().inverseTransform(firstBreakPoint);
                if (secondBreakPoint != null) {
                    lp2 = getTransform().inverseTransform(secondBreakPoint);
                }
            }
            // 验证打断点的有效性
            if (lp1 == null) {
                System.err.println("打断直线时发生错误: 第一个打断点为空");
                return newShapes;
            }
            
            // 使用更宽松的容差来验证点是否在直线上
            double tolerance = 5.0; // 5像素的容差
            
            if ("SINGLE_POINT".equals(breakMode)) {
                // 单点打断：在打断点处分割成两段
                if (GeometryUtils.isPointOnLine(start, end, lp1, tolerance)) {
                    // 创建第一段（如果打断点不是起点）
                    if (!lp1.equals(start) && lp1.distance(start) > tolerance) {
                        LineShape firstSegment = new LineShape(start, lp1);
                        firstSegment.setStyle(getStyle().clone());
                        if (getTransform() != null) firstSegment.setTransform(getTransform().clone());
                        newShapes.add(firstSegment);
                    }
                    
                    // 创建第二段（如果打断点不是终点）
                    if (!lp1.equals(end) && lp1.distance(end) > tolerance) {
                        LineShape secondSegment = new LineShape(lp1, end);
                        secondSegment.setStyle(getStyle().clone());
                        if (getTransform() != null) secondSegment.setTransform(getTransform().clone());
                        newShapes.add(secondSegment);
                    }
                    
                    // 如果没有创建任何段，说明打断点太靠近端点
                    if (newShapes.isEmpty()) {
                        System.err.println("打断点太靠近端点，无法创建有效段");
                    }
                } else {
                    System.err.println("打断点不在直线上");
                }
            } else if ("TWO_POINT".equals(breakMode) && lp2 != null) {
                // 两点打断：移除两点间的部分
                if (GeometryUtils.isPointOnLine(start, end, lp1, tolerance) && 
                    GeometryUtils.isPointOnLine(start, end, lp2, tolerance)) {
                    
                    // 确保第一个点在第二个点之前
                    Vec2d first = lp1;
                    Vec2d second = lp2;
                    
                    double firstDistance = GeometryUtils.getDistanceFromStart(start, end, first);
                    double secondDistance = GeometryUtils.getDistanceFromStart(start, end, second);
                    
                    if (firstDistance > secondDistance) {
                        Vec2d temp = first;
                        first = second;
                        second = temp;
                    }
                    
                    // 创建第一段（如果存在）
                    if (!first.equals(start) && first.distance(start) > tolerance) {
                        LineShape firstSegment = new LineShape(start, first);
                        firstSegment.setStyle(getStyle().clone());
                        if (getTransform() != null) firstSegment.setTransform(getTransform().clone());
                        newShapes.add(firstSegment);
                    }
                    
                    // 创建第三段（如果存在）
                    if (!second.equals(end) && second.distance(end) > tolerance) {
                        LineShape thirdSegment = new LineShape(second, end);
                        thirdSegment.setStyle(getStyle().clone());
                        if (getTransform() != null) thirdSegment.setTransform(getTransform().clone());
                        newShapes.add(thirdSegment);
                    }
                    
                    // 如果没有创建任何段，说明两个打断点太靠近端点
                    if (newShapes.isEmpty()) {
                        System.err.println("两个打断点太靠近端点，无法创建有效段");
                    }
                } else {
                    System.err.println("一个或两个打断点不在直线上");
                }
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常，返回空列表
            System.err.println("打断直线时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        return GeometryUtils.pointToSegmentDistance(point, start, end);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        try {
            // 获取变换后的起点和终点
            Vec2d transformedStart = getTransform().transform(start);
            Vec2d transformedEnd = getTransform().transform(end);
            
            // 转换到屏幕坐标
            Vec2d screenStart = camera.worldToScreen(transformedStart);
            Vec2d screenEnd = camera.worldToScreen(transformedEnd);
            
            // 绘制线段
            drawList.addLine(
                (float) screenStart.x, (float) screenStart.y,
                (float) screenEnd.x, (float) screenEnd.y,
                0x80FFFFFF, 1.0f // 白色，半透明
            );
            
        } catch (Exception e) {
            // 记录错误但不抛出异常
            System.err.println("渲染直线ImGui时发生错误: " + e.getMessage());
        }
    }
}
