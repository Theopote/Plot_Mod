package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.api.render.IRenderVisitor;
import com.plot.api.shape.IExtendableShape;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.GeometryUtils;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.model.Shape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.LineStyle;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 折线形状
 */
public class PolylineShape extends Shape implements IExtendableShape {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolylineShape.class);
    
    private List<Vec2d> points;
    private boolean closed;
    
    private List<LineShape> cachedSegments = null;
    private boolean segmentsDirty = true;

    private void markSegmentsDirty() { segmentsDirty = true; }

    public PolylineShape(List<Vec2d> points, boolean closed) {
        super(points != null && !points.isEmpty() ? points.getFirst() : new Vec2d(0, 0));
        this.points = new ArrayList<>(points != null ? points : new ArrayList<>());
        this.closed = closed;
    }
    
    public List<Vec2d> getPoints() { return new ArrayList<>(points); }
    public void setPoints(List<Vec2d> points) { this.points = new ArrayList<>(points); markSegmentsDirty(); }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    
    private List<LineShape> getSegments() {
        if (!segmentsDirty && cachedSegments != null) return cachedSegments;
        List<LineShape> segments = new ArrayList<>();
        if (points.size() < 2) {
            cachedSegments = segments;
            segmentsDirty = false;
            return segments;
        }
        for (int i = 0; i < points.size() - 1; i++) {
            segments.add(new LineShape(points.get(i), points.get(i + 1)));
        }
        if (closed && points.size() > 2) {
            segments.add(new LineShape(points.getFirst(), points.getLast()));
        }
        cachedSegments = segments;
        segmentsDirty = false;
        return segments;
    }
    
    @Override
    public void translate(Vec2d offset) {
        points = points.stream()
            .map(p -> p.add(offset))
            .collect(Collectors.toList());
        markSegmentsDirty();
    }
    
    @Override
    public void rotate(double angle, Vec2d center) {
        points = points.stream()
            .map(p -> GeometryUtils.rotate(p.subtract(center), angle).add(center))
            .collect(Collectors.toList());
        markSegmentsDirty();
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 变换所有定义点
        points = points.stream()
            .map(transformMatrix::transform)
            .collect(Collectors.toList());
        markSegmentsDirty();
        return this; // 折线变换后仍然是折线
    }
    
    @Override
    public void scale(Vec2d scale, Vec2d center) {
        points = points.stream()
            .map(p -> center.add(p.subtract(center).multiply(scale)))
            .collect(Collectors.toList());
        markSegmentsDirty();
    }
    
    @Override
    public BoundingBox getBoundingBox() {
        if (points.isEmpty()) return null;
        
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
    public boolean containsPoint(Vec2d point, double tolerance) {
        for (LineShape segment : getSegments()) {
            if (segment.containsPoint(point, tolerance)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        Vec2d closest = null;
        double minDistance = Double.POSITIVE_INFINITY;
        
        for (LineShape segment : getSegments()) {
            Vec2d segmentClosest = segment.getClosestPoint(point);
            double distance = segmentClosest.distance(point);
            
            if (distance < minDistance) {
                minDistance = distance;
                closest = segmentClosest;
            }
        }
        
        return closest;
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        return new ArrayList<>(points);
    }
    
    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index >= 0 && index < points.size()) {
            points.set(index, point);
            markSegmentsDirty();
        }
    }
    
    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }
    
    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        List<Vec2d> intersections = new ArrayList<>();
        
        for (LineShape segment : getSegments()) {
            intersections.addAll(segment.getIntersectionPoints(other));
        }
        
        return intersections;
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 找到最近的端点
        Vec2d nearestEnd = null;
        double minDistance = Double.POSITIVE_INFINITY;
        
        List<Vec2d> endpoints = getEndpoints();
        for (Vec2d endpoint : endpoints) {
            double distance = endpoint.distance(point);
            if (distance < minDistance) {
                minDistance = distance;
                nearestEnd = endpoint;
            }
        }
        
        if (nearestEnd == null) return new ArrayList<>();
        
        // 计算延伸方向
        Vec2d direction;
        if (nearestEnd == points.get(0)) {
            direction = nearestEnd.subtract(points.get(1)).normalize();
        } else {
            direction = nearestEnd.subtract(points.get(points.size() - 2)).normalize();
        }
        
        // 创建延伸线段
        Vec2d extendedPoint = nearestEnd.add(direction.multiply(maxDistance));
        LineShape extendedLine = new LineShape(nearestEnd, extendedPoint);
        
        return extendedLine.getIntersectionsWith(other);
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        List<Vec2d> endpoints = new ArrayList<>();
        if (points.isEmpty()) return endpoints;
        
        if (!closed) {
            endpoints.add(points.getFirst());
            endpoints.add(points.getLast());
        }
        
        return endpoints;
    }
    
    @Override
    public Vec2d getTangentAt(Vec2d point) {
        // 找到最近的线段
        LineShape nearestSegment = null;
        double minDistance = Double.POSITIVE_INFINITY;
        
        for (LineShape segment : getSegments()) {
            double distance = segment.getClosestPoint(point).distance(point);
            if (distance < minDistance) {
                minDistance = distance;
                nearestSegment = segment;
            }
        }
        
        return nearestSegment != null ? nearestSegment.getTangentAt(point) : null;
    }
    
    @Override
    public double getSignedDistance(Vec2d point) {
        double minDistance = Double.POSITIVE_INFINITY;
        
        for (LineShape segment : getSegments()) {
            double distance = segment.getSignedDistance(point);
            if (Math.abs(distance) < Math.abs(minDistance)) {
                minDistance = distance;
            }
        }
        
        return minDistance;
    }
    
    @Override
    public List<Shape> split(List<Vec2d> splitPoints, Vec2d pickPoint) {
        List<Shape> result = new ArrayList<>();
        if (splitPoints.isEmpty() || points.size() < 2) return result;
        
        // 创建包含所有原始顶点和分割点的超级列表
        List<SplitPoint> allPoints = new ArrayList<>();
        
        // 添加原始顶点
        for (int i = 0; i < points.size(); i++) {
            allPoints.add(new SplitPoint(points.get(i), i, true));
        }
        
        // 添加分割点（投影到最近的线段上）
        for (Vec2d splitPoint : splitPoints) {
            Vec2d projectedPoint = projectPointToPolyline(splitPoint);
            if (projectedPoint != null) {
                allPoints.add(new SplitPoint(projectedPoint, -1, false));
            }
        }
        
        // 按在多段线上的位置排序
        allPoints.sort(this::compareSplitPoints);
        
        // 创建子多段线
        List<Vec2d> currentPoints = new ArrayList<>();
        for (SplitPoint sp : allPoints) {
            currentPoints.add(sp.point);
            
            // 如果遇到原始顶点且当前点列表不为空，创建一个新的多段线
            if (sp.isOriginalVertex) {
                if (currentPoints.size() >= 2) {
                    PolylineShape newPolyline = new PolylineShape(new ArrayList<>(currentPoints), false);
                    if (getStyle() != null) {
                        newPolyline.setStyle(getStyle().clone());
                    }
                    result.add(newPolyline);
                }
                currentPoints.clear();
                currentPoints.add(sp.point);
            }
        }
        
        // 处理最后一个子多段线
        if (currentPoints.size() >= 2) {
            PolylineShape newPolyline = new PolylineShape(currentPoints, false);
            if (getStyle() != null) {
                newPolyline.setStyle(getStyle().clone());
            }
            result.add(newPolyline);
        }
        
        return result;
    }
    
    /**
     * 分割点类，用于排序
     */
    private static class SplitPoint {
        final Vec2d point;
        final int originalIndex;
        final boolean isOriginalVertex;
        
        SplitPoint(Vec2d point, int originalIndex, boolean isOriginalVertex) {
            this.point = point;
            this.originalIndex = originalIndex;
            this.isOriginalVertex = isOriginalVertex;
        }
    }
    
    /**
     * 将点投影到多段线上最近的线段
     */
    private Vec2d projectPointToPolyline(Vec2d point) {
        Vec2d closestPoint = null;
        double minDistance = Double.POSITIVE_INFINITY;
        
        for (LineShape segment : getSegments()) {
            Vec2d segmentClosest = segment.getClosestPoint(point);
            double distance = segmentClosest.distance(point);
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = segmentClosest;
            }
        }
        
        return closestPoint;
    }
    
    /**
     * 比较分割点，按在多段线上的位置排序
     */
    private int compareSplitPoints(SplitPoint a, SplitPoint b) {
        if (a.isOriginalVertex && b.isOriginalVertex) {
            return Integer.compare(a.originalIndex, b.originalIndex);
        }
        
        // 计算每个点沿多段线的累积距离
        double distanceA = calculateCumulativeDistance(a.point);
        double distanceB = calculateCumulativeDistance(b.point);
        
        return Double.compare(distanceA, distanceB);
    }
    
    /**
     * 计算点沿多段线的累积距离
     */
    private double calculateCumulativeDistance(Vec2d point) {
        double cumulativeDistance = 0.0;
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get(i + 1);
            
            // 检查点是否在当前线段上
            if (isPointOnSegment(point, p1, p2)) {
                cumulativeDistance += p1.distance(point);
                return cumulativeDistance;
            }
            
            cumulativeDistance += p1.distance(p2);
        }
        
        // 如果是闭合的，检查最后一条线段
        if (closed && points.size() > 2) {
            Vec2d p1 = points.getLast();
            Vec2d p2 = points.getFirst();
            
            if (isPointOnSegment(point, p1, p2)) {
                cumulativeDistance += p1.distance(point);
                return cumulativeDistance;
            }
        }
        
        return cumulativeDistance;
    }
    
    /**
     * 检查点是否在线段上
     */
    private boolean isPointOnSegment(Vec2d point, Vec2d segStart, Vec2d segEnd) {
        if (segStart.equals(segEnd)) return point.equals(segStart);
        
        Vec2d v = segEnd.subtract(segStart);
        Vec2d w = point.subtract(segStart);
        
        double c1 = w.dot(v);
        double c2 = v.dot(v);
        
        if (c1 < 0 || c1 > c2) return false;
        
        double b = c1 / c2;
        Vec2d projection = segStart.add(v.multiply(b));
        
        return projection.distance(point) < 1e-8;
    }
    
    @Override
    public Shape extend(Vec2d point, double distance) {
        List<Vec2d> newPoints = new ArrayList<>(points);
        
        // 找到最近的端点
        if (!closed && !points.isEmpty()) {
            Vec2d firstPoint = points.get(0);
            Vec2d lastPoint = points.getLast();
            
            if (point.distance(firstPoint) < point.distance(lastPoint)) {
                // 延伸起点
                Vec2d direction = firstPoint.subtract(points.get(1)).normalize();
                newPoints.set(0, firstPoint.add(direction.multiply(distance)));
            } else {
                // 延伸终点
                Vec2d direction = lastPoint.subtract(points.get(points.size() - 2)).normalize();
                newPoints.set(points.size() - 1, lastPoint.add(direction.multiply(distance)));
            }
        }
        
        return new PolylineShape(newPoints, closed);
    }
    
    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        List<Vec2d> newPoints = new ArrayList<>(points);
        
        // 找到最近的端点
        if (!closed && !points.isEmpty()) {
            Vec2d firstPoint = points.getFirst();
            Vec2d lastPoint = points.getLast();
            
            if (point.distance(firstPoint) < point.distance(lastPoint)) {
                // 延伸起点：直接设置到目标点
                newPoints.set(0, toPoint);
            } else {
                // 延伸终点：直接设置到目标点
                newPoints.set(points.size() - 1, toPoint);
            }
        }
        
        PolylineShape extended = new PolylineShape(newPoints, closed);
        
        // 复制样式和其他属性
        if (getStyle() != null) {
            extended.setStyle(getStyle().clone());
        }
        
        return extended;
    }
    
    @Override
    public Shape trimToPoint(Vec2d point) {
        // 找到最近的线段
        int nearestSegment = -1;
        double minDistance = Double.POSITIVE_INFINITY;
        Vec2d closestPointOnSegment = null;
        
        List<LineShape> segments = getSegments();
        for (int i = 0; i < segments.size(); i++) {
            LineShape segment = segments.get(i);
            Vec2d segmentClosest = segment.getClosestPoint(point);
            double distance = segmentClosest.distance(point);
            if (distance < minDistance) {
                minDistance = distance;
                nearestSegment = i;
                closestPointOnSegment = segmentClosest;
            }
        }
        
        if (nearestSegment == -1) return clone();
        
        // 创建新的点列表，使用投影点而不是原始点
        List<Vec2d> newPoints = new ArrayList<>();
        for (int i = 0; i <= nearestSegment; i++) {
            newPoints.add(points.get(i));
        }
        newPoints.add(closestPointOnSegment); // 使用投影点
        
        return new PolylineShape(newPoints, false);
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

        PolylineShape offsetPolyline = new PolylineShape(offsetPoints, closed);
        if (getStyle() != null) {
            offsetPolyline.setStyle(getStyle().clone());
        }
        return offsetPolyline;
    }

    
    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> polylinePoints) {
        List<Vec2d> result = new ArrayList<>();
        
        for (LineShape segment : getSegments()) {
            result.addAll(segment.getIntersectionsWithPolyline(polylinePoints));
        }
        
        return result;
    }
    
    @Override
    public boolean intersectsPolyline(List<Vec2d> polylinePoints) {
        return !getIntersectionsWithPolyline(polylinePoints).isEmpty();
    }
    
    @Override
    public Shape clone() {
        Shape shape = super.clone();
        PolylineShape clone = new PolylineShape(new ArrayList<>(points), closed);
        clone.setTransform(getTransform().clone());
        clone.setStyle(getStyle().clone());
        clone.setSelected(isSelected());
        clone.setVisible(isVisible());
        return clone;
    }
    
    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(closed ? "POLYGON " : "POLYLINE ");
        for (Vec2d point : points) {
            sb.append(String.format("%.2f,%.2f ", point.x, point.y));
        }
        return sb.toString().trim();
    }
    
    @Override
    public void deserialize(String data) {
        String[] parts = data.split(" ");
        if (parts.length >= 2) {
            closed = parts[0].equals("POLYGON");
            points.clear();
            
            for (int i = 1; i < parts.length; i++) {
                String[] coords = parts[i].split(",");
                if (coords.length == 2) {
                    points.add(new Vec2d(
                        Double.parseDouble(coords[0]),
                        Double.parseDouble(coords[1])
                    ));
                }
            }
            markSegmentsDirty();
        }
    }
    
    @Override
    public void draw(DrawContext context) {
        if (points.size() < 2) return;
        
        // 应用变换矩阵到所有点
        List<Vec2d> transformedPoints = new ArrayList<>();
        for (Vec2d point : points) {
            transformedPoints.add(getTransform().transform(point));
        }
        
        // 优先使用DrawContext中设置的样式（用于选中和高亮状态）
        ShapeStyle activeStyle = context.getCurrentStyle();
        if (activeStyle == null) {
            // 如果DrawContext没有设置样式，使用图形自己的样式
            activeStyle = (ShapeStyle) getStyle();
        }
        
        // 绘制线段
        if (activeStyle != null && activeStyle.getLineStyle() != null && activeStyle.getLineStyle().isVisible()) {
            // 使用LineStyle绘制线段
            if (activeStyle.getLineStyle() instanceof LineStyle lineStyle) {

                // 绘制线段
                for (int i = 0; i < transformedPoints.size() - 1; i++) {
                    context.drawLine(transformedPoints.get(i), transformedPoints.get(i + 1), lineStyle);
                }
                
                // 如果是闭合的，绘制最后一条线段
                if (closed && transformedPoints.size() > 2) {
                    context.drawLine(
                        transformedPoints.getLast(),
                        transformedPoints.getFirst(),
                        lineStyle
                    );
                }
            } else {
                // 如果没有LineStyle，使用默认颜色绘制
                Color color = new Color(activeStyle.getLineStyle().getColor().getRGB());
                
                // 绘制线段
                for (int i = 0; i < transformedPoints.size() - 1; i++) {
                    context.drawLine(transformedPoints.get(i), transformedPoints.get(i + 1), color);
                }
                
                // 如果是闭合的，绘制最后一条线段
                if (closed && transformedPoints.size() > 2) {
                    context.drawLine(
                        transformedPoints.getLast(),
                        transformedPoints.getFirst(),
                        color
                    );
                }
            }
        }
        
        // 注意：移除了本地填充算法
        // 扫描线算法对复杂多边形（如自相交）可能会失败
        // 正确的填充应该由渲染引擎提供，使用 Ear Clipping 或 Tesselation 算法
        // 如果需要填充，应该调用 context.fillPolygon() 或类似的方法
    }
    

    @Override
    public boolean contains(Vec2d point) {
        // 如果是闭合的多边形，使用射线法判断点是否在内部
        if (closed && points.size() >= 3) {
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
            
            if (inside) return true;
        }
        
        // 检查点是否在任何线段上
        for (LineShape segment : getSegments()) {
            if (segment.contains(point)) {
                return true;
            }
        }
        
        return false;
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
            if ("SINGLE_POINT".equals(breakMode)) {
                // 单点打断：在打断点处分割多段线
                int segmentIndex = GeometryUtils.findSegmentContainingPoint(points, lp1);
                if (segmentIndex >= 0) {
                    // 将断点精确投影到该线段上，避免出现“延伸/偏移”
                    Vec2d segStart = points.get(segmentIndex);
                    Vec2d segEnd = points.get(segmentIndex + 1);
                    Vec2d pOnSeg = projectPointOnSegment(lp1, segStart, segEnd);
                    if (closed && points.size() > 2) {
                        // 修复：闭合多段线单点打断 → 确保不"跳过"任何边
                        // 生成一条从断点开始，沿着原路径一直绕回到断点的开口多段线
                        
                        List<Vec2d> openPolyline = new ArrayList<>();
                        openPolyline.add(pOnSeg);  // 起始点：断点
                        
                        // 添加被断开线段的终点
                        if (segmentIndex + 1 < points.size()) {
                            openPolyline.add(points.get(segmentIndex + 1));
                        }
                        
                        // 添加后续所有点（如果存在）
                        if (segmentIndex + 2 < points.size()) {
                            openPolyline.addAll(points.subList(segmentIndex + 2, points.size()));
                        }
                        
                        // 添加前面的所有点（绕回到开始）
                        openPolyline.addAll(points.subList(0, segmentIndex + 1));
                        
                        // 最后回到断点，形成完整的开口线条
                        openPolyline.add(pOnSeg);

                        PolylineShape openPoly = new PolylineShape(openPolyline, false);
                        openPoly.setStyle(getStyle().clone());
                        if (getTransform() != null) openPoly.setTransform(getTransform().clone());
                        newShapes.add(openPoly);
                    } else {
                        // 对于非闭合多段线，按原来的逻辑处理
                        // 创建第一段多段线
                        List<Vec2d> firstPoints = new ArrayList<>(points.subList(0, segmentIndex + 1));
                        firstPoints.add(pOnSeg);
                        
                        if (firstPoints.size() >= 2) {
                            PolylineShape firstPolyline = new PolylineShape(firstPoints, false);
                            firstPolyline.setStyle(getStyle().clone());
                            if (getTransform() != null) firstPolyline.setTransform(getTransform().clone());
                            newShapes.add(firstPolyline);
                        }
                        
                        // 创建第二段多段线
                        List<Vec2d> secondPoints = new ArrayList<>();
                        secondPoints.add(pOnSeg);
                        secondPoints.addAll(points.subList(segmentIndex + 1, points.size()));
                        
                        if (secondPoints.size() >= 2) {
                            PolylineShape secondPolyline = new PolylineShape(secondPoints, false);
                            secondPolyline.setStyle(getStyle().clone());
                            if (getTransform() != null) secondPolyline.setTransform(getTransform().clone());
                            newShapes.add(secondPolyline);
                        }
                    }
                }
            } else if ("TWO_POINT".equals(breakMode) && lp2 != null) {
                // 两点打断：移除两点间的部分
                int firstSegmentIndex = GeometryUtils.findSegmentContainingPoint(points, lp1);
                int secondSegmentIndex = GeometryUtils.findSegmentContainingPoint(points, lp2);
                
                if (firstSegmentIndex >= 0 && secondSegmentIndex >= 0) {
                    // 精确投影两断点
                    Vec2d fSegStart = points.get(firstSegmentIndex);
                    Vec2d fSegEnd = points.get(firstSegmentIndex + 1);
                    Vec2d p1OnSeg = projectPointOnSegment(lp1, fSegStart, fSegEnd);
                    Vec2d sSegStart = points.get(secondSegmentIndex);
                    Vec2d sSegEnd = points.get(secondSegmentIndex + 1);
                    Vec2d p2OnSeg = projectPointOnSegment(lp2, sSegStart, sSegEnd);
                    if (closed && points.size() > 2) {
                        // 修复：闭合图形两点打断 - 正确处理用户点击顺序
                        // 计算两种可能的路径，移除较短的一段（用户想要删除的部分）
                        
                        // 路径1：从第一断点到第二断点（顺着路径方向）
                        List<Vec2d> path1 = new ArrayList<>();
                        path1.add(p1OnSeg);
                        
                        if (firstSegmentIndex == secondSegmentIndex) {
                            // 两个断点在同一线段上
                            // 检查在线段上的位置关系
                            Vec2d segStart = points.get(firstSegmentIndex);
                            Vec2d segEnd = points.get(firstSegmentIndex + 1);
                            double pos1 = GeometryUtils.getDistanceFromStart(segStart, segEnd, p1OnSeg);
                            double pos2 = GeometryUtils.getDistanceFromStart(segStart, segEnd, p2OnSeg);
                            
                            if (pos1 < pos2) {
                                // 第一个点在前，第二个点在后，直接连接（这是要移除的部分）
                                path1.add(p2OnSeg);
                            } else {
                                // 需要绕一圈
                                if (firstSegmentIndex + 1 < points.size()) {
                                    path1.addAll(points.subList(firstSegmentIndex + 1, points.size()));
                                }
                                path1.addAll(points.subList(0, secondSegmentIndex + 1));
                                path1.add(p2OnSeg);
                            }
                        } else if (firstSegmentIndex < secondSegmentIndex) {
                            // 正常情况：第一断点在前，第二断点在后
                            if (firstSegmentIndex + 1 <= secondSegmentIndex) {
                                path1.addAll(points.subList(firstSegmentIndex + 1, secondSegmentIndex + 1));
                            }
                            path1.add(p2OnSeg);
                        } else {
                            // firstSegmentIndex > secondSegmentIndex：需要绕一圈
                            if (firstSegmentIndex + 1 < points.size()) {
                                path1.addAll(points.subList(firstSegmentIndex + 1, points.size()));
                            }
                            path1.addAll(points.subList(0, secondSegmentIndex + 1));
                            path1.add(p2OnSeg);
                        }
                        
                        // 路径2：从第二断点到第一断点（反向）
                        List<Vec2d> path2 = new ArrayList<>();
                        path2.add(p2OnSeg);
                        
                        if (firstSegmentIndex == secondSegmentIndex) {
                            Vec2d segStart = points.get(firstSegmentIndex);
                            Vec2d segEnd = points.get(firstSegmentIndex + 1);
                            double pos1 = GeometryUtils.getDistanceFromStart(segStart, segEnd, p1OnSeg);
                            double pos2 = GeometryUtils.getDistanceFromStart(segStart, segEnd, p2OnSeg);
                            
                            if (pos2 < pos1) {
                                // 第二个点在前，第一个点在后
                                path2.add(p1OnSeg);
                            } else {
                                // 需要绕一圈
                                if (secondSegmentIndex + 1 < points.size()) {
                                    path2.addAll(points.subList(secondSegmentIndex + 1, points.size()));
                                }
                                path2.addAll(points.subList(0, firstSegmentIndex + 1));
                                path2.add(p1OnSeg);
                            }
                        } else if (secondSegmentIndex < firstSegmentIndex) {
                            // 第二断点在前，第一断点在后
                            if (secondSegmentIndex + 1 <= firstSegmentIndex) {
                                path2.addAll(points.subList(secondSegmentIndex + 1, firstSegmentIndex + 1));
                            }
                            path2.add(p1OnSeg);
                        } else {
                            // secondSegmentIndex > firstSegmentIndex：需要绕一圈
                            if (secondSegmentIndex + 1 < points.size()) {
                                path2.addAll(points.subList(secondSegmentIndex + 1, points.size()));
                            }
                            path2.addAll(points.subList(0, firstSegmentIndex + 1));
                            path2.add(p1OnSeg);
                        }
                        
                        // 对于闭合多段线，两点打断应当产生两条开口段（path1 和 path2），
                        // 分别表示绕行两种路径的两段，直接将这两条路径作为结果返回。
                        PolylineShape poly1 = new PolylineShape(new ArrayList<>(path1), false);
                        poly1.setStyle(getStyle().clone());
                        if (getTransform() != null) poly1.setTransform(getTransform().clone());
                        newShapes.add(poly1);
                        PolylineShape poly2 = new PolylineShape(new ArrayList<>(path2), false);
                        poly2.setStyle(getStyle().clone());
                        if (getTransform() != null) poly2.setTransform(getTransform().clone());
                        newShapes.add(poly2);
                    } else {
                        // 对于非闭合多段线，需要确保第一个点在第二个点之前
                        if (firstSegmentIndex > secondSegmentIndex || 
                            (firstSegmentIndex == secondSegmentIndex && 
                             GeometryUtils.getDistanceFromStart(points.get(firstSegmentIndex), points.get(firstSegmentIndex + 1), p1OnSeg) >
                             GeometryUtils.getDistanceFromStart(points.get(secondSegmentIndex), points.get(secondSegmentIndex + 1), p2OnSeg))) {
                            
                            Vec2d temp = p1OnSeg;
                            p1OnSeg = p2OnSeg;
                            p2OnSeg = temp;
                            
                            int tempIndex = firstSegmentIndex;
                            firstSegmentIndex = secondSegmentIndex;
                            secondSegmentIndex = tempIndex;
                        }
                        
                        // 创建第一段多段线
                        List<Vec2d> firstPoints = new ArrayList<>(points.subList(0, firstSegmentIndex + 1));
                        firstPoints.add(p1OnSeg);
                        
                        if (firstPoints.size() >= 2) {
                            PolylineShape firstPolyline = new PolylineShape(firstPoints, false);
                            firstPolyline.setStyle(getStyle().clone());
                            if (getTransform() != null) firstPolyline.setTransform(getTransform().clone());
                            newShapes.add(firstPolyline);
                        }
                        
                        // 创建第三段多段线
                        List<Vec2d> thirdPoints = new ArrayList<>();
                        thirdPoints.add(p2OnSeg);
                        thirdPoints.addAll(points.subList(secondSegmentIndex + 1, points.size()));
                        
                        if (thirdPoints.size() >= 2) {
                            PolylineShape thirdPolyline = new PolylineShape(thirdPoints, false);
                            thirdPolyline.setStyle(getStyle().clone());
                            if (getTransform() != null) thirdPolyline.setTransform(getTransform().clone());
                            newShapes.add(thirdPolyline);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常，返回空列表
            LOGGER.error("打断多段线时发生错误: {}", e.getMessage(), e);
        }
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        return GeometryUtils.getDistanceToPolyline(points, point);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
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
            
            // 绘制多段线
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
            LOGGER.error("渲染多段线ImGui时发生错误: {}", e.getMessage(), e);
        }
    }
}