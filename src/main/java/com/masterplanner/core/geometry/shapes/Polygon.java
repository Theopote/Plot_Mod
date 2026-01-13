package com.masterplanner.core.geometry.shapes;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.render.IRenderVisitor;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.geometry.GeometryUtils;
import com.masterplanner.core.geometry.AffineTransform;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.tools.impl.modify.helper.IShapeVisitor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 多边形类
 */
public class Polygon extends Shape {
    private static final Logger LOGGER = LoggerFactory.getLogger(Polygon.class);
    
    private List<Vec2d> points;
    private boolean closed;
    
    // 包围盒缓存
    private BoundingBox cachedBoundingBox;
    private boolean isBoundingBoxDirty = true;

    public Polygon(List<Vec2d> points) {
        super(points != null && !points.isEmpty() ? points.getFirst() : new Vec2d(0, 0));
        this.points = validateAndCleanPoints(points);
        this.closed = true;
    }

    public Polygon(List<Vec2d> points, boolean closed) {
        super(points != null && !points.isEmpty() ? points.getFirst() : new Vec2d(0, 0));
        this.points = validateAndCleanPoints(points);
        this.closed = closed;
    }

    @Override
    public Polygon clone() {
        // 关键修复：默认的 Shape.clone() 是浅拷贝，会导致 points 列表在“预览缩放/移动/旋转”时
        // clone 与原对象共享同一 List，从而把原图形直接改坏（星形/三点矩形都用 Polygon）。
        Polygon cloned = (Polygon) super.clone();
        cloned.points = new ArrayList<>(this.points);
        cloned.closed = this.closed;

        // 缓存必须失效（points 重新拷贝/transform 可能不同）
        cloned.cachedBoundingBox = null;
        cloned.isBoundingBoxDirty = true;
        return cloned;
    }

    public void setPoints(List<Vec2d> points) {
        this.points = validateAndCleanPoints(points);
        invalidateBoundingBoxCache();
    }

    public List<Vec2d> getPoints() {
        return new ArrayList<>(points);
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    @Override
    public void draw(DrawContext context) {
        if (points.size() < 2) return;

        // 应用变换矩阵到所有点
        List<Vec2d> transformedPoints = new ArrayList<>();
        for (Vec2d point : points) {
            transformedPoints.add(transform.transform(point));
        }

        // 优先使用DrawContext中设置的样式（用于选中和高亮状态）
        ShapeStyle activeStyle = context.getCurrentStyle();
        if (activeStyle == null) {
            // 如果DrawContext没有设置样式，使用图形自己的样式
            activeStyle = (ShapeStyle) getStyle();
        }
        if (activeStyle == null) return;

        // 注意：移除了本地填充算法
        // 扫描线算法对复杂多边形（如自相交）可能会失败
        // 正确的填充应该由渲染引擎提供，使用 Ear Clipping 或 Tesselation 算法
        // 如果需要填充，应该调用 context.fillPolygon() 或类似的方法

        // 然后绘制线条（如果可见的话）
        if (activeStyle.getLineStyle() != null && activeStyle.getLineStyle().isVisible()) {
            if (activeStyle.getLineStyle() instanceof LineStyle lineStyle) {
                // 绘制线段
                for (int i = 0; i < transformedPoints.size() - 1; i++) {
                    Vec2d p1 = transformedPoints.get(i);
                    Vec2d p2 = transformedPoints.get(i + 1);
                    context.drawLine(p1, p2, lineStyle);
                }

                // 如果是闭合的，绘制最后一条线段
                if (closed && transformedPoints.size() > 2) {
                    Vec2d first = transformedPoints.getFirst();
                    Vec2d last = transformedPoints.getLast();
                    context.drawLine(last, first, lineStyle);
                }
            } else {
                // 如果没有LineStyle，使用默认颜色绘制
                Color color = new Color(activeStyle.getLineStyle().getColor().getRGB());
                
                // 绘制线段
                for (int i = 0; i < transformedPoints.size() - 1; i++) {
                    Vec2d p1 = transformedPoints.get(i);
                    Vec2d p2 = transformedPoints.get(i + 1);
                    context.drawLine(p1, p2, color);
                }

                // 如果是闭合的，绘制最后一条线段
                if (closed && transformedPoints.size() > 2) {
                    Vec2d first = transformedPoints.getFirst();
                    Vec2d last = transformedPoints.getLast();
                    context.drawLine(last, first, color);
                }
            }
        }
    }

    @Override
    public void translate(Vec2d offset) {
        List<Vec2d> newPoints = new ArrayList<>();
        for (Vec2d point : points) {
            newPoints.add(point.add(offset));
        }
        points = newPoints;
        invalidateBoundingBoxCache();
    }

    @Override
    public void rotate(double angle, Vec2d center) {
        List<Vec2d> newPoints = new ArrayList<>();
        for (Vec2d point : points) {
            // 正确的旋转：先平移到原点，旋转，再平移回原位置
            Vec2d rotatedPoint = GeometryUtils.rotate(point.subtract(center), angle).add(center);
            newPoints.add(rotatedPoint);
        }
        points = newPoints;
        invalidateBoundingBoxCache();
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 变换所有定义点
        for (int i = 0; i < this.points.size(); i++) {
            Vec2d point = this.points.get(i);
            this.points.set(i, transformMatrix.transform(point));
        }
        invalidateBoundingBoxCache(); // 变换后必须让缓存失效
        return this; // 多边形变换后仍然是多边形
    }

    @Override
    public BoundingBox getBoundingBox() {
        // 如果缓存有效，直接返回缓存的包围盒
        if (!isBoundingBoxDirty && cachedBoundingBox != null) {
            return cachedBoundingBox;
        }
        
        // 重新计算包围盒
        if (points.isEmpty()) {
            cachedBoundingBox = new BoundingBox(0, 0, 0, 0);
        } else {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;

            for (Vec2d point : points) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }

            cachedBoundingBox = new BoundingBox(minX, minY, maxX, maxY);
        }
        
        // 标记缓存为有效
        isBoundingBoxDirty = false;
        return cachedBoundingBox;
    }
    
    /**
     * 标记包围盒缓存为无效
     * 在所有会改变多边形形状的操作后调用
     */
    private void invalidateBoundingBoxCache() {
        isBoundingBoxDirty = true;
    }

    @Override
    public boolean contains(Vec2d point) {
        // 射线法判断点是否在多边形内
        if (points.size() < 3) return false;

        boolean inside = false;
        int j = points.size() - 1;
        for (int i = 0; i < points.size(); j = i++) {
            Vec2d pi = points.get(i);
            Vec2d pj = points.get(j);

            if (((pi.y > point.y) != (pj.y > point.y)) &&
                (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x)) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        if (points.isEmpty()) return point;
        if (points.size() == 1) return points.getFirst();

        Vec2d closestPoint = points.getFirst();
        double minDistance = point.distance(closestPoint);

        // 检查每条边（线段）
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            // 计算点到线段的最近点
            Vec2d pointOnSegment = getClosestPointOnSegment(point, p1, p2);
            double distance = point.distance(pointOnSegment);
            
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = pointOnSegment;
            }
        }
        
        // 如果是闭合多边形，检查最后一条边
        if (closed && points.size() > 1) {
            Vec2d p1 = points.getLast();
            Vec2d p2 = points.getFirst();
            
            // 计算点到线段的最近点
            Vec2d pointOnSegment = getClosestPointOnSegment(point, p1, p2);
            double distance = point.distance(pointOnSegment);
            
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = pointOnSegment;
            }
        }

        return closestPoint;
    }

    /**
     * 计算点到线段的最近点
     * @param point 目标点
     * @param segmentStart 线段起点
     * @param segmentEnd 线段终点
     * @return 线段上距离目标点最近的点
     */
    private Vec2d getClosestPointOnSegment(Vec2d point, Vec2d segmentStart, Vec2d segmentEnd) {
        Vec2d segment = segmentEnd.subtract(segmentStart);
        Vec2d toPoint = point.subtract(segmentStart);
        
        // 计算投影参数 t
        double segmentLengthSq = segment.dot(segment);
        if (segmentLengthSq == 0) {
            // 线段退化为点
            return segmentStart;
        }
        
        double t = toPoint.dot(segment) / segmentLengthSq;
        
        // 限制 t 在 [0, 1] 范围内
        t = Math.max(0, Math.min(1, t));
        
        // 计算投影点
        return segmentStart.add(segment.multiply(t));
    }

    @Override
    public List<Vec2d> getControlPoints() {
        return new ArrayList<>(points);
    }

    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index >= 0 && index < points.size()) {
            points.set(index, point);
            invalidateBoundingBoxCache();
        }
    }

    @Override
    public boolean intersects(Shape other) {
        // 首先进行边界盒检测（快速排除）
        if (!getBoundingBox().intersects(other.getBoundingBox())) {
            return false;
        }
        
        // 对于不同类型的形状，使用精确的相交检测
        return !getIntersectionPoints(other).isEmpty();
    }

    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        return getIntersectionsWith(other);
    }

    @Override
    public String toString() {
        return String.format("Polygon[points=%d, closed=%s]", points.size(), closed);
    }

    // 实现其他必要的抽象方法...
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        List<Vec2d> intersections = new ArrayList<>();
        
        if (points.size() < 2) return intersections;
        
        // 对于不同类型的图形，使用不同的交点计算方法
        switch (other) {
            case FreeDrawPath freePath -> {
                // 与自由绘制路径的交点
                List<Vec2d> pathPoints = freePath.getPoints();
                intersections = calculatePolygonPolylineIntersections(points, pathPoints);
            }
            case PolylineShape polyline -> {
                // 与多段线的交点
                List<Vec2d> polylinePoints = polyline.getPoints();
                intersections = calculatePolygonPolylineIntersections(points, polylinePoints);
            }
            case LineShape line -> {
                // 与直线的交点
                Vec2d lineStart = line.getStart();
                Vec2d lineEnd = line.getEnd();
                intersections = calculatePolygonLineIntersections(points, lineStart, lineEnd);
            }
            case CircleShape circle -> {
                // 与圆的交点
                Vec2d center = circle.getCenter();
                double radius = circle.getRadius();
                intersections = calculatePolygonCircleIntersections(points, center, radius);
            }
            case Polygon otherPolygon -> {
                // 与另一个多边形的交点
                List<Vec2d> otherPoints = otherPolygon.getPoints();
                intersections = calculatePolygonPolygonIntersections(points, otherPoints);
            }
            case EllipseShape ellipse -> // 与椭圆的交点
                    intersections = calculatePolygonEllipseIntersections(points, ellipse);
            case EllipticalArcShape arc -> // 与椭圆弧的交点
                    intersections = calculatePolygonEllipticalArcIntersections(points, arc);
            case ArcShape arc -> // 与圆弧的交点
                    intersections = calculatePolygonArcIntersections(points, arc);
            case BezierCurveShape bezier -> // 与贝塞尔曲线的交点
                    intersections = calculatePolygonBezierIntersections(points, bezier);
            case RectangleShape rectangle -> // 与矩形的交点
                    intersections = calculatePolygonRectangleIntersections(points, rectangle);
            case null, default -> {
                // 对于其他类型的图形，尝试使用通用的交点计算方法
                try {
                    // 将当前多边形转换为多段线，然后使用多段线的交点计算方法
                    PolylineShape polyline = new PolylineShape(points, closed);
                    intersections = polyline.getIntersectionsWith(other);
                } catch (Exception e) {
                    // 如果转换失败，返回空列表
                    LOGGER.warn("无法计算与图形 {} 的交点: {}", 
                        other != null ? other.getClass().getSimpleName() : "null", e.getMessage());
                }
            }
        }
        
        return removeDuplicateIntersections(intersections);
    }

    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
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
        return null;
    }

    @Override
    public double getSignedDistance(Vec2d point) {
        if (points == null || points.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }

        // 计算到各边的最小距离
        double minDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            double d = distancePointToSegment(point, a, b);
            if (d < minDistance) {
                minDistance = d;
            }
        }
        // 闭合最后一条边
        if (points.size() > 2) {
            Vec2d a = points.getLast();
            Vec2d b = points.getFirst();
            double d = distancePointToSegment(point, a, b);
            if (d < minDistance) {
                minDistance = d;
            }
        }

        // 使用射线法判断点是否在多边形内部，内部返回负距离
        boolean inside = contains(point);
        return inside ? -minDistance : minDistance;
    }

    // 计算点到线段的距离
    private static double distancePointToSegment(Vec2d p, Vec2d a, Vec2d b) {
        Vec2d ab = b.subtract(a);
        Vec2d ap = p.subtract(a);
        double denom = ab.dot(ab);
        if (denom <= 1e-12) {
            return p.distance(a);
        }
        double t = Math.max(0.0, Math.min(1.0, ap.dot(ab) / denom));
        Vec2d projection = a.add(ab.multiply(t));
        return p.distance(projection);
    }

    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        throw new UnsupportedOperationException("Polygon.split() 方法尚未实现。多边形分割是一个复杂的几何操作，需要专门的算法。");
    }

    @Override
    public Shape extend(Vec2d point, double distance) {
        throw new UnsupportedOperationException("Polygon.extend() 方法尚未实现。多边形延伸操作对于闭合形状没有明确定义。");
    }

    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        throw new UnsupportedOperationException("Polygon.extend() 方法尚未实现。多边形延伸操作对于闭合形状没有明确定义。");
    }

    @Override
    public Shape trimToPoint(Vec2d point) {
        throw new UnsupportedOperationException("Polygon.trimToPoint() 方法尚未实现。多边形修剪操作对于闭合形状没有明确定义。");
    }

    @Override
    public Shape createOffset(double distance) {
        // 简化的偏移实现：只支持简单的法线偏移
        // 复杂的等距偏移算法应该使用专业的计算几何库（如JTS）
        if (points == null || points.size() < 2) return clone();
        
        List<Vec2d> offsetPoints = new ArrayList<>();
        int n = points.size();
        
        for (int i = 0; i < n; i++) {
            Vec2d prev = points.get(i == 0 ? (closed ? n - 1 : 0) : i - 1);
            Vec2d curr = points.get(i);
            Vec2d next = points.get(i == n - 1 ? (closed ? 0 : n - 1) : i + 1);

            // 计算相邻边的方向向量
            Vec2d dir1 = curr.subtract(prev);
            Vec2d dir2 = next.subtract(curr);
            
            // 处理退化情况
            if (dir1.length() < 1e-8 || dir2.length() < 1e-8) {
                // 退化点，使用相邻有效方向
                Vec2d validDir = dir1.length() >= 1e-8 ? dir1 : dir2;
                if (validDir.length() < 1e-8) {
                    // 如果都退化，使用默认方向
                    validDir = new Vec2d(1, 0);
                }
                Vec2d normal = new Vec2d(-validDir.y, validDir.x).normalize();
                offsetPoints.add(curr.add(normal.multiply(distance)));
                continue;
            }

            // 计算法线向量
            Vec2d n1 = new Vec2d(-dir1.y, dir1.x).normalize();
            Vec2d n2 = new Vec2d(-dir2.y, dir2.x).normalize();
            
            // 简单的法线平均
            Vec2d bisector = n1.add(n2);
            if (bisector.length() < 1e-8) {
                bisector = n1; // 备用法线
            } else {
                bisector = bisector.normalize();
            }
            
            // 应用偏移
            Vec2d offsetPoint = curr.add(bisector.multiply(distance));
            offsetPoints.add(offsetPoint);
        }

        Polygon offsetPolygon = new Polygon(offsetPoints, closed);
        if (getStyle() != null) {
            offsetPolygon.setStyle(getStyle().clone());
        }
        return offsetPolygon;
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
    public String serialize() {
        // 使用简单的JSON格式进行序列化
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"Polygon\",\"version\":\"1.0\",\"closed\":").append(closed).append(",\"points\":[");
        
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) sb.append(",");
            Vec2d point = points.get(i);
            sb.append("{\"x\":").append(point.x).append(",\"y\":").append(point.y).append("}");
        }
        
        sb.append("]}");
        return sb.toString();
    }

    @Override
    public void deserialize(String data) {
        points.clear();
        if (data == null || data.isEmpty()) return;
        
        try {
            // 简单的JSON解析
            if (data.trim().startsWith("{")) {
                // 解析JSON格式
                parseJsonFormat(data);
            } else {
                // 尝试解析旧格式（向后兼容）
                parseLegacyFormat(data);
            }
        } catch (Exception e) {
            LOGGER.error("反序列化Polygon失败: {}", e.getMessage(), e);
            points.clear();
        }
        
        invalidateBoundingBoxCache();
    }
    
    /**
     * 解析JSON格式的数据
     */
    private void parseJsonFormat(String jsonData) {
        // 简单的JSON解析（避免引入额外依赖）
        if (jsonData.contains("\"closed\":")) {
            // 提取closed属性
            int closedStart = jsonData.indexOf("\"closed\":") + 9;
            int closedEnd = jsonData.indexOf(",", closedStart);
            if (closedEnd == -1) closedEnd = jsonData.indexOf("}", closedStart);
            String closedStr = jsonData.substring(closedStart, closedEnd).trim();
            this.closed = Boolean.parseBoolean(closedStr);
        }
        
        // 提取点数据
        int pointsStart = jsonData.indexOf("\"points\":[") + 10;
        int pointsEnd = jsonData.lastIndexOf("]");
        if (pointsStart > 9 && pointsEnd > pointsStart) {
            String pointsData = jsonData.substring(pointsStart, pointsEnd);
            parsePointsFromJson(pointsData);
        }
    }
    
    /**
     * 从JSON中解析点数据
     */
    private void parsePointsFromJson(String pointsData) {
        String[] pointStrings = pointsData.split("},\\{");
        for (String pointString : pointStrings) {
            String pointStr = pointString;
            // 清理大括号
            pointStr = pointStr.replaceAll("[{}]", "");

            String[] coords = pointStr.split(",");
            if (coords.length >= 2) {
                try {
                    double x = Double.parseDouble(coords[0].split(":")[1].trim());
                    double y = Double.parseDouble(coords[1].split(":")[1].trim());
                    points.add(new Vec2d(x, y));
                } catch (NumberFormatException e) {
                    LOGGER.warn("解析点坐标失败: {}", pointStr);
                }
            }
        }
    }
    
    /**
     * 解析旧格式数据（向后兼容）
     */
    private void parseLegacyFormat(String data) {
        // 尝试解析简单的点列表格式
        String[] parts = data.split(";");
        for (String part : parts) {
            String[] coords = part.split(",");
            if (coords.length == 2) {
                try {
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    points.add(new Vec2d(x, y));
                } catch (NumberFormatException e) {
                    LOGGER.warn("解析点坐标失败: {}", part);
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
        invalidateBoundingBoxCache();
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
        
        // 获取多边形的点列表
        List<Vec2d> polygonPoints = getPoints();
        if (polygonPoints.size() < 2) {
            return newShapes;
        }
        
        // 将多边形转换为多段线进行打断
        PolylineShape polylineShape = new PolylineShape(polygonPoints, closed);
        polylineShape.setStyle(getStyle().clone());
        
        List<Shape> brokenShapes = polylineShape.breakShape(firstBreakPoint, secondBreakPoint, breakMode);
        newShapes.addAll(brokenShapes);
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        // 计算点到多边形的距离
        List<Vec2d> polygonPoints = getPoints();
        if (polygonPoints.size() < 2) {
            return Double.MAX_VALUE;
        }
        
        return GeometryUtils.getDistanceToPolyline(polygonPoints, point);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        try {
            if (points.size() < 2) return;
            
            // 获取变换后的点
            List<Vec2d> transformedPoints = new ArrayList<>();
            for (Vec2d point : points) {
                transformedPoints.add(getTransform().transform(point));
            }
            
            // 转换到屏幕坐标
            List<Vec2d> screenPoints = new ArrayList<>();
            for (Vec2d point : transformedPoints) {
                screenPoints.add(camera.worldToScreen(point));
            }
            
            // 注意：移除了有问题的三角形扇形填充算法
            // 原来的算法只对凸多边形有效，对凹多边形会产生错误的渲染结果
            // 正确的填充应该由渲染引擎提供，使用 Ear Clipping 或 Tesselation 算法
            
            // 绘制多边形轮廓
            for (int i = 0; i < screenPoints.size() - 1; i++) {
                Vec2d p1 = screenPoints.get(i);
                Vec2d p2 = screenPoints.get(i + 1);
                drawList.addLine(
                    (float) p1.x, (float) p1.y,
                    (float) p2.x, (float) p2.y,
                    0x80FFFFFF, 1.0f // 白色，半透明
                );
            }
            
            // 如果是闭合的，绘制最后一条线段
            if (closed && screenPoints.size() > 2) {
                Vec2d first = screenPoints.getFirst();
                Vec2d last = screenPoints.getLast();
                drawList.addLine(
                    (float) last.x, (float) last.y,
                    (float) first.x, (float) first.y,
                    0x80FFFFFF, 1.0f // 白色，半透明
                );
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常
            LOGGER.error("渲染多边形ImGui时发生错误: {}", e.getMessage(), e);
        }
    }
     
    
    /**
     * 计算多边形与多段线的交点
     */
    private List<Vec2d> calculatePolygonPolylineIntersections(List<Vec2d> polygonPoints, List<Vec2d> polylinePoints) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polygonPoints.size() < 2 || polylinePoints.size() < 2) return intersections;

        // 遍历多边形的每条边
        for (int i = 0; i < polygonPoints.size(); i++) {
            Vec2d p1 = polygonPoints.get(i);
            Vec2d p2 = polygonPoints.get((i + 1) % polygonPoints.size());

            // 遍历多段线的每个线段
            for (int j = 0; j < polylinePoints.size() - 1; j++) {
                Vec2d q1 = polylinePoints.get(j);
                Vec2d q2 = polylinePoints.get(j + 1);

                // 计算线段相交
                Vec2d intersection = getLineSegmentIntersection(p1, p2, q1, q2);
                if (intersection != null) {
                    intersections.add(intersection);
                }
            }
        }

        return intersections;
    }
    
    /**
     * 计算多边形与直线的交点
     */
    private List<Vec2d> calculatePolygonLineIntersections(List<Vec2d> polygonPoints, Vec2d lineStart, Vec2d lineEnd) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polygonPoints.size() < 2) return intersections;

        // 遍历多边形的每条边
        for (int i = 0; i < polygonPoints.size(); i++) {
            Vec2d p1 = polygonPoints.get(i);
            Vec2d p2 = polygonPoints.get((i + 1) % polygonPoints.size());

            // 计算线段相交
            Vec2d intersection = getLineSegmentIntersection(p1, p2, lineStart, lineEnd);
            if (intersection != null) {
                intersections.add(intersection);
            }
        }

        return intersections;
    }
    
    /**
     * 计算多边形与圆的交点
     */
    private List<Vec2d> calculatePolygonCircleIntersections(List<Vec2d> polygonPoints, Vec2d center, double radius) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polygonPoints.size() < 2) return intersections;

        // 遍历多边形的每条边
        for (int i = 0; i < polygonPoints.size(); i++) {
            Vec2d p1 = polygonPoints.get(i);
            Vec2d p2 = polygonPoints.get((i + 1) % polygonPoints.size());

            // 计算圆与线段的交点
            List<Vec2d> circleIntersections = calculateCircleLineIntersections(center, radius, p1, p2);
            intersections.addAll(circleIntersections);
        }

        return intersections;
    }
    
    /**
     * 计算两个多边形的交点
     */
    private List<Vec2d> calculatePolygonPolygonIntersections(List<Vec2d> polygon1, List<Vec2d> polygon2) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polygon1.size() < 2 || polygon2.size() < 2) return intersections;

        // 遍历第一个多边形的每条边
        for (int i = 0; i < polygon1.size(); i++) {
            Vec2d p1 = polygon1.get(i);
            Vec2d p2 = polygon1.get((i + 1) % polygon1.size());

            // 遍历第二个多边形的每条边
            for (int j = 0; j < polygon2.size(); j++) {
                Vec2d q1 = polygon2.get(j);
                Vec2d q2 = polygon2.get((j + 1) % polygon2.size());

                // 计算线段相交
                Vec2d intersection = getLineSegmentIntersection(p1, p2, q1, q2);
                if (intersection != null) {
                    intersections.add(intersection);
                }
            }
        }

        return intersections;
    }
    
    /**
     * 计算多边形与椭圆的交点
     */
    private List<Vec2d> calculatePolygonEllipseIntersections(List<Vec2d> polygonPoints, EllipseShape ellipse) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polygonPoints.size() < 2) return intersections;

        // 遍历多边形的每条边
        for (int i = 0; i < polygonPoints.size(); i++) {
            Vec2d p1 = polygonPoints.get(i);
            Vec2d p2 = polygonPoints.get((i + 1) % polygonPoints.size());

            // 计算椭圆与线段的交点
            List<Vec2d> ellipseIntersections = calculateEllipseLineIntersections(ellipse, p1, p2);
            intersections.addAll(ellipseIntersections);
        }

        return intersections;
    }
    
    /**
     * 计算多边形与椭圆弧的交点
     */
    private List<Vec2d> calculatePolygonEllipticalArcIntersections(List<Vec2d> polygonPoints, EllipticalArcShape arc) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polygonPoints.size() < 2) return intersections;

        // 遍历多边形的每条边
        for (int i = 0; i < polygonPoints.size(); i++) {
            Vec2d p1 = polygonPoints.get(i);
            Vec2d p2 = polygonPoints.get((i + 1) % polygonPoints.size());

            // 计算椭圆弧与线段的交点
            List<Vec2d> arcIntersections = calculateEllipticalArcLineIntersections(arc, p1, p2);
            intersections.addAll(arcIntersections);
        }

        return intersections;
    }
    
    /**
     * 计算多边形与圆弧的交点
     */
    private List<Vec2d> calculatePolygonArcIntersections(List<Vec2d> polygonPoints, ArcShape arc) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polygonPoints.size() < 2) return intersections;

        // 遍历多边形的每条边
        for (int i = 0; i < polygonPoints.size(); i++) {
            Vec2d p1 = polygonPoints.get(i);
            Vec2d p2 = polygonPoints.get((i + 1) % polygonPoints.size());

            // 计算圆弧与线段的交点
            List<Vec2d> arcIntersections = calculateArcLineIntersections(arc, p1, p2);
            intersections.addAll(arcIntersections);
        }

        return intersections;
    }
    
    /**
     * 计算多边形与贝塞尔曲线的交点
     */
    private List<Vec2d> calculatePolygonBezierIntersections(List<Vec2d> polygonPoints, BezierCurveShape bezier) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polygonPoints.size() < 2) return intersections;

        // 将贝塞尔曲线离散化为多段线
        List<Vec2d> bezierPoints = discretizeBezierCurve(bezier);
        
        // 计算多边形与贝塞尔曲线多段线的交点
        intersections = calculatePolygonPolylineIntersections(polygonPoints, bezierPoints);

        return intersections;
    }
    
    /**
     * 计算多边形与矩形的交点
     */
    private List<Vec2d> calculatePolygonRectangleIntersections(List<Vec2d> polygonPoints, RectangleShape rectangle) {
        List<Vec2d> intersections = new ArrayList<>();
        if (polygonPoints.size() < 2) return intersections;

        // 将矩形转换为多边形
        BoundingBox bbox = rectangle.getBoundingBox();
        if (bbox == null) return intersections;
        
        List<Vec2d> rectPoints = List.of(
            new Vec2d(bbox.getMin().x, bbox.getMin().y),
            new Vec2d(bbox.getMax().x, bbox.getMin().y),
            new Vec2d(bbox.getMax().x, bbox.getMax().y),
            new Vec2d(bbox.getMin().x, bbox.getMax().y)
        );
        
        // 计算两个多边形的交点
        intersections = calculatePolygonPolygonIntersections(polygonPoints, rectPoints);

        return intersections;
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
     * 计算两条线段的交点
     */
    private Vec2d getLineSegmentIntersection(Vec2d p1, Vec2d p2, Vec2d q1, Vec2d q2) {
        List<Vec2d> intersections = GeometryUtils.segmentIntersection(p1, p2, q1, q2);
        return intersections.isEmpty() ? null : intersections.getFirst();
    }
    
    /**
     * 检查点是否在线段上
     */
    private boolean isPointOnLineSegment(Vec2d point, Vec2d start, Vec2d end) {
        return GeometryUtils.isPointOnSegment(point, start, end);
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
    
    /**
     * 计算椭圆与线段的交点
     */
    private List<Vec2d> calculateEllipseLineIntersections(EllipseShape ellipse, Vec2d p1, Vec2d p2) {
        List<Vec2d> intersections = new ArrayList<>();
        
        // 获取椭圆参数
        Vec2d center = ellipse.getCenter();
        double a = ellipse.getRadiusX();
        double b = ellipse.getRadiusY();
        double rotation = ellipse.getRotation();
        
        // 将线段转换到椭圆坐标系
        Vec2d p1Transformed = p1.subtract(center);
        Vec2d p2Transformed = p2.subtract(center);
        
        // 应用反向旋转
        if (Math.abs(rotation) > 1e-10) {
            p1Transformed = GeometryUtils.rotate(p1Transformed, -rotation);
            p2Transformed = GeometryUtils.rotate(p2Transformed, -rotation);
        }
        
        // 计算椭圆与线段的交点
        double dx = p2Transformed.x - p1Transformed.x;
        double dy = p2Transformed.y - p1Transformed.y;
        
        if (Math.abs(dx) < 1e-10 && Math.abs(dy) < 1e-10) {
            return intersections; // 线段退化为点
        }
        
        // 椭圆方程: (x/a)² + (y/b)² = 1
        // 线段参数方程: x = p1.x + t*dx, y = p1.y + t*dy
        // 代入椭圆方程求解 t
        double A = (dx * dx) / (a * a) + (dy * dy) / (b * b);
        double B = 2 * (p1Transformed.x * dx) / (a * a) + 2 * (p1Transformed.y * dy) / (b * b);
        double C = (p1Transformed.x * p1Transformed.x) / (a * a) + (p1Transformed.y * p1Transformed.y) / (b * b) - 1;
        
        double discriminant = B * B - 4 * A * C;
        if (discriminant < 0) {
            return intersections; // 无交点
        }
        
        double sqrtDisc = Math.sqrt(discriminant);
        double t1 = (-B + sqrtDisc) / (2 * A);
        double t2 = (-B - sqrtDisc) / (2 * A);
        
        // 检查交点是否在线段上
        for (double t : new double[]{t1, t2}) {
            if (t >= 0 && t <= 1) {
                Vec2d intersection = new Vec2d(
                    p1Transformed.x + t * dx,
                    p1Transformed.y + t * dy
                );
                
                // 转换回世界坐标系
                if (Math.abs(rotation) > 1e-10) {
                    intersection = GeometryUtils.rotate(intersection, rotation);
                }
                intersection = intersection.add(center);
                
                intersections.add(intersection);
            }
        }
        
        return intersections;
    }
    
    /**
     * 计算椭圆弧与线段的交点
     */
    private List<Vec2d> calculateEllipticalArcLineIntersections(EllipticalArcShape arc, Vec2d p1, Vec2d p2) {
        List<Vec2d> intersections = new ArrayList<>();
        
        // 先计算椭圆与线段的交点
        EllipseShape ellipse = new EllipseShape(arc.getCenter(), arc.getRadiusX(), arc.getRadiusY(), arc.getRotation());
        List<Vec2d> ellipseIntersections = calculateEllipseLineIntersections(ellipse, p1, p2);
        
        // 过滤在椭圆弧范围内的交点
        for (Vec2d intersection : ellipseIntersections) {
            if (arc.contains(intersection)) {
                intersections.add(intersection);
            }
        }
        
        return intersections;
    }
    
    /**
     * 计算圆弧与线段的交点
     */
    private List<Vec2d> calculateArcLineIntersections(ArcShape arc, Vec2d p1, Vec2d p2) {
        List<Vec2d> intersections = new ArrayList<>();
        
        // 先计算圆与线段的交点
        List<Vec2d> circleIntersections = calculateCircleLineIntersections(arc.getCenter(), arc.getRadius(), p1, p2);
        
        // 过滤在圆弧范围内的交点
        for (Vec2d intersection : circleIntersections) {
            if (arc.contains(intersection)) {
                intersections.add(intersection);
            }
        }
        
        return intersections;
    }
    
    /**
     * 将贝塞尔曲线离散化为多段线
     */
    private List<Vec2d> discretizeBezierCurve(BezierCurveShape bezier) {
        List<Vec2d> points = new ArrayList<>();
        int segments = 50; // 离散化段数
        
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Vec2d point = evaluateBezierCurve(bezier, t);
            points.add(point);
        }
        
        return points;
    }
    
    /**
     * 计算贝塞尔曲线上参数 t 处的点
     */
    private Vec2d evaluateBezierCurve(BezierCurveShape bezier, double t) {
        List<Vec2d> controlPoints = bezier.getControlPoints();
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
        if (k > n - k) {
            k = n - k; // 利用对称性
        }
        
        double result = 1.0;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        
        return result;
    }
    
    /**
     * 验证和清理点列表
     * 移除无效点，确保多边形的有效性
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
        
        if (cleanedPoints.size() < 3 && closed) {
            LOGGER.warn("清理后的点列表长度不足，无法构成有效多边形");
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
    
    /**
     * 验证多边形是否有效
     */
    public boolean isValid() {
        if (points == null || points.size() < 3) {
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
        for (int i = 0; i < points.size(); i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get((i + 1) % points.size());
            
            if (p1.distance(p2) > 1e-10) {
                validSegments++;
            }
        }
        
        return validSegments >= 3;
    }
    
    /**
     * 获取多边形的统计信息
     */
    public PolygonStatistics getStatistics() {
        return new PolygonStatistics(this);
    }
    
    /**
     * 多边形统计信息类
     */
    public static class PolygonStatistics {
        private final int pointCount;
        private final int segmentCount;
        private final double totalLength;
        private final double area;
        private final double averageSegmentLength;
        private final double minSegmentLength;
        private final double maxSegmentLength;
        
        public PolygonStatistics(Polygon polygon) {
            this.pointCount = polygon.points.size();
            this.segmentCount = Math.max(0, pointCount);
            
            if (segmentCount == 0) {
                this.totalLength = 0;
                this.area = 0;
                this.averageSegmentLength = 0;
                this.minSegmentLength = 0;
                this.maxSegmentLength = 0;
            } else {
                double total = 0;
                double min = Double.MAX_VALUE;
                double max = 0;
                
                for (int i = 0; i < segmentCount; i++) {
                    Vec2d p1 = polygon.points.get(i);
                    Vec2d p2 = polygon.points.get((i + 1) % polygon.points.size());
                    double length = p1.distance(p2);
                    total += length;
                    min = Math.min(min, length);
                    max = Math.max(max, length);
                }
                
                this.totalLength = total;
                this.area = calculateSignedArea(polygon.points);
                this.averageSegmentLength = total / segmentCount;
                this.minSegmentLength = min;
                this.maxSegmentLength = max;
            }
        }
        
        private double calculateSignedArea(List<Vec2d> points) {
            if (points.size() < 3) return 0.0;
            
            double area = 0.0;
            int n = points.size();
            for (int i = 0; i < n; i++) {
                Vec2d current = points.get(i);
                Vec2d next = points.get((i + 1) % n);
                area += (next.x - current.x) * (next.y + current.y);
            }
            return area / 2.0;
        }
        
        public int getPointCount() { return pointCount; }
        public int getSegmentCount() { return segmentCount; }
        public double getTotalLength() { return totalLength; }
        public double getArea() { return Math.abs(area); }
        public double getAverageSegmentLength() { return averageSegmentLength; }
        public double getMinSegmentLength() { return minSegmentLength; }
        public double getMaxSegmentLength() { return maxSegmentLength; }
        
        @Override
        public String toString() {
            return String.format("PolygonStatistics{points=%d, segments=%d, length=%.2f, area=%.2f, avg=%.2f, min=%.2f, max=%.2f}",
                pointCount, segmentCount, totalLength, Math.abs(area), averageSegmentLength, minSegmentLength, maxSegmentLength);
        }
    }
}