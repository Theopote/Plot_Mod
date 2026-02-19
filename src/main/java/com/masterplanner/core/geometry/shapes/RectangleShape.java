package com.masterplanner.core.geometry.shapes;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.render.IRenderVisitor;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.geometry.GeometryUtils;
import com.masterplanner.core.geometry.AffineTransform;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.ui.tools.impl.modify.helper.IShapeVisitor;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import com.masterplanner.core.geometry.RasterizationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 矩形形状
 */
public class RectangleShape extends Shape {
    private static final Logger LOGGER = LoggerFactory.getLogger(RectangleShape.class);
    private Vec2d corner;     // 左下角
    private double width;     // 宽度
    private double height;    // 高度
    private double rotation;  // 旋转角度（弧度）
    private double cornerRadius; // 圆角半径
    
    private List<Vec2d> cachedCorners = null;
    private List<LineShape> cachedEdges = null;
    private BoundingBox cachedBoundingBox = null;
    private boolean cacheValid = false;
    
    public RectangleShape(Vec2d corner, double width, double height, double cornerRadius) {
        super(corner != null ? corner : new Vec2d(0, 0));
        this.corner = corner != null ? corner : new Vec2d(0, 0);
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.rotation = 0;
        this.cornerRadius = Math.max(0, cornerRadius);
        clampCornerRadiusToSize();
        
        // 验证参数
        if (this.width <= 0 || this.height <= 0) {
            LOGGER.warn("创建矩形时尺寸无效: width={}, height={}", width, height);
        }
    }
    
    public RectangleShape(Vec2d corner, double width, double height, double cornerRadius, double rotation) {
        super(corner != null ? corner : new Vec2d(0, 0));
        this.corner = corner != null ? corner : new Vec2d(0, 0);
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.rotation = rotation;
        this.cornerRadius = Math.max(0, cornerRadius);
        clampCornerRadiusToSize();
        
        // 验证参数
        if (this.width <= 0 || this.height <= 0) {
            LOGGER.warn("创建矩形时尺寸无效: width={}, height={}", width, height);
        }
    }
    
    public Vec2d getCorner() { return corner; }
    public void setCorner(Vec2d corner) { 
        this.corner = corner; 
        invalidateCache();
    }
    public double getWidth() { return width; }
    public void setWidth(double width) { 
        if (width < 0) {
            LOGGER.warn("设置矩形宽度为负值: {}", width);
        }
        this.width = Math.max(0, width); 
        clampCornerRadiusToSize();
        invalidateCache();
    }
    public double getHeight() { return height; }
    public void setHeight(double height) { 
        if (height < 0) {
            LOGGER.warn("设置矩形高度为负值: {}", height);
        }
        this.height = Math.max(0, height); 
        clampCornerRadiusToSize();
        invalidateCache();
    }
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { 
        this.rotation = rotation; 
        invalidateCache();
    }
    
    // 圆角半径的getter和setter
    public double getCornerRadius() { return cornerRadius; }
    public void setCornerRadius(double cornerRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
        clampCornerRadiusToSize();
        invalidateCache();
    }

    private void clampCornerRadiusToSize() {
        double maxRadius = Math.max(0.0, Math.min(width, height) / 2.0);
        cornerRadius = Math.min(cornerRadius, maxRadius);
    }
    
    /**
     * 使所有缓存失效
     */
    private void invalidateCache() {
        cachedCorners = null;
        cachedEdges = null;
        cachedBoundingBox = null;
        cacheValid = false;
    }
    
    // 获取四个角点
    private List<Vec2d> getCorners() {
        if (!cacheValid || cachedCorners == null) {
            updateCorners();
        }
        return new ArrayList<>(cachedCorners); // 返回副本以防止外部修改
    }
    
    // 获取四条边
    private List<LineShape> getEdges() {
        if (!cacheValid || cachedEdges == null) {
            List<Vec2d> corners = getCorners();
            cachedEdges = new ArrayList<>();
            
            for (int i = 0; i < 4; i++) {
                cachedEdges.add(new LineShape(
                    corners.get(i),
                    corners.get((i + 1) % 4)
                ));
            }
        }
        
        return new ArrayList<>(cachedEdges); // 返回副本以防止外部修改
    }
    
    @Override
    public void translate(Vec2d offset) {
        corner = corner.add(offset);
        invalidateCache();
    }
    
    @Override
    public void rotate(double angle, Vec2d center) {
        corner = GeometryUtils.rotate(corner.subtract(center), angle).add(center);
        rotation += angle;
        invalidateCache();
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 检查是否为均匀变换
        if (transformMatrix.isUniform()) {
            // 均匀变换：可以保持为矩形
            Vec2d newCorner = transformMatrix.transform(corner);
            double scaleFactor = transformMatrix.getScaleX();
            double newRotation = rotation + transformMatrix.getRotation();
            
            // 更新矩形参数
            this.corner = newCorner;
            this.width *= scaleFactor;
            this.height *= scaleFactor;
            this.rotation = newRotation;
            this.cornerRadius *= Math.abs(scaleFactor);
            clampCornerRadiusToSize();
            
            invalidateCache();
            return this; // 返回自身
        } else {
            // 非均匀变换：必须转换为多边形
            List<Vec2d> corners = getCorners();
            List<Vec2d> transformedCorners = new ArrayList<>();
            
            for (Vec2d corner : corners) {
                transformedCorners.add(transformMatrix.transform(corner));
            }
            
            // 用变换后的四个角点创建一个新的多边形
            Polygon result = new Polygon(transformedCorners);
            if (this.getStyle() != null) {
                result.setStyle(this.getStyle().clone());
            }
            return result; // 返回新类型的对象
        }
    }
    
    @Override
    public void scale(Vec2d scale, Vec2d center) {
        corner = center.add(corner.subtract(center).multiply(scale));
        width *= Math.abs(scale.x);
        height *= Math.abs(scale.y);
        cornerRadius *= Math.min(Math.abs(scale.x), Math.abs(scale.y));
        clampCornerRadiusToSize();
        invalidateCache();
    }
    
    @Override
    public BoundingBox getBoundingBox() {
        if (!cacheValid || cachedBoundingBox == null) {
            List<Vec2d> corners = getCorners();
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            
            for (Vec2d point : corners) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }
            
            cachedBoundingBox = new BoundingBox(
                new Vec2d(minX, minY),
                new Vec2d(maxX, maxY)
            );
        }
        
        return cachedBoundingBox;
    }
    
    @Override
    public boolean containsPoint(Vec2d point, double tolerance) {
        boolean hasFill = getStyle() != null && getStyle().getFillStyle() != null && getStyle().getFillStyle().isVisible();
        return containsPoint(point, tolerance, hasFill);
    }

    public boolean containsPoint(Vec2d point, double tolerance, boolean checkInterior) {
        if (checkInterior) {
            Vec2d localPoint = GeometryUtils.rotate(point.subtract(corner), -rotation);
            return localPoint.x >= 0 && localPoint.x <= width &&
                   localPoint.y >= 0 && localPoint.y <= height;
        } else {
            for (LineShape edge : getEdges()) {
                if (edge.containsPoint(point, tolerance)) return true;
            }
            return false;
        }
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        if (point == null) {
            LOGGER.warn("getClosestPoint传入空点");
            return new Vec2d(0, 0);
        }
        
        try {
            // 利用组合模式的优势：遍历四条边，找到距离最小的点
            List<LineShape> edges = getEdges();
            if (edges.isEmpty()) {
                LOGGER.warn("矩形没有边，返回输入点");
                return point;
            }
            
            Vec2d closestPoint = null;
            double minDistance = Double.POSITIVE_INFINITY;
            
            for (LineShape edge : edges) {
                Vec2d edgeClosestPoint = edge.getClosestPoint(point);
                if (edgeClosestPoint != null) {
                    double distance = point.distance(edgeClosestPoint);
                    
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestPoint = edgeClosestPoint;
                    }
                }
            }
            
            return closestPoint != null ? closestPoint : point;
        } catch (Exception e) {
            LOGGER.error("计算最近点时发生错误", e);
            return point;
        }
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        return getCorners();
    }
    
    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (point == null) {
            LOGGER.warn("设置控制点时传入空点");
            return;
        }
        
        List<Vec2d> corners = getCorners();
        if (index < 0 || index >= corners.size()) {
            LOGGER.warn("控制点索引超出范围: index={}, 有效范围=[0, {}]", index, corners.size() - 1);
            return;
        }
        
        try {
            // 将目标点转换到矩形的局部坐标系
            Vec2d localPoint = point.subtract(corner).rotate(-rotation);
            
            switch (index) {
                case 0: // 左下角 - 移动整个矩形
                    corner = point;
                    break;
                case 1: // 右下角 - 调整宽度，保持高度和旋转
                    width = Math.max(0, localPoint.x);
                    break;
                case 2: // 右上角 - 调整宽度和高度，保持旋转
                    width = Math.max(0, localPoint.x);
                    height = Math.max(0, localPoint.y);
                    break;
                case 3: // 左上角 - 调整高度，保持宽度和旋转
                    height = Math.max(0, localPoint.y);
                    break;
            }
            
            // 清除缓存
            invalidateCache();
        } catch (Exception e) {
            LOGGER.error("设置控制点时发生错误: index={}, point={}", index, point, e);
        }
    }
    
    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }
    
    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        List<Vec2d> intersections = new ArrayList<>();
        
        for (LineShape edge : getEdges()) {
            intersections.addAll(edge.getIntersectionPoints(other));
        }
        
        return intersections;
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        return getIntersectionPoints(other);
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 矩形不支持延伸
        return new ArrayList<>();
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        return getCorners();
    }

    @Override
    public List<Vec2d> getKeyPoints() {
        List<Vec2d> keyPoints = new ArrayList<>();
        
        try {
            // 获取四个角点（已考虑旋转）
            List<Vec2d> corners = getCorners();
            keyPoints.addAll(corners);
            
            // 获取四条边的中点（已考虑旋转）
            List<LineShape> edges = getEdges();
            for (LineShape edge : edges) {
                Vec2d start = edge.getStart();
                Vec2d end = edge.getEnd();
                Vec2d midpoint = new Vec2d(
                    (start.x + end.x) * 0.5,
                    (start.y + end.y) * 0.5
                );
                keyPoints.add(midpoint);
            }
            
            // 添加中心点
            Vec2d center = getPosition();
            if (center != null) {
                keyPoints.add(center);
            }
            
        } catch (Exception e) {
            // 如果计算失败，回退到默认实现
            return super.getKeyPoints();
        }
        
        return keyPoints;
    }
    
    @Override
    public Vec2d getTangentAt(Vec2d point) {
        // 找到最近的边
        LineShape nearestEdge = null;
        double minDistance = Double.POSITIVE_INFINITY;
        
        for (LineShape edge : getEdges()) {
            double distance = edge.getClosestPoint(point).distance(point);
            if (distance < minDistance) {
                minDistance = distance;
                nearestEdge = edge;
            }
        }
        
        return nearestEdge != null ? nearestEdge.getTangentAt(point) : null;
    }
    
    @Override
    public double getSignedDistance(Vec2d point) {
        // 将点转换到矩形的局部坐标系
        Vec2d localPoint = GeometryUtils.rotate(point.subtract(corner), -rotation);
        
        // 检查点是否在矩形内部
        boolean isInside = localPoint.x >= 0 && localPoint.x <= width &&
                          localPoint.y >= 0 && localPoint.y <= height;
        
        // 计算到矩形边界的最短距离
        double minDistance = Double.POSITIVE_INFINITY;
        
        for (LineShape edge : getEdges()) {
            double distance = Math.abs(edge.getSignedDistance(point));
            if (distance < minDistance) {
                minDistance = distance;
            }
        }
        
        // 如果点在内部，返回负值；如果点在外部，返回正值
        return isInside ? -minDistance : minDistance;
    }
    
    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        List<Shape> result = new ArrayList<>();
        
        // 将矩形转换为多边形进行分割
        PolylineShape polyline = new PolylineShape(getCorners(), true);
        return polyline.split(points, pickPoint);
    }
    
    @Override
    public Shape extend(Vec2d point, double distance) {
        // 矩形不支持延伸
        return clone();
    }
    
    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        // 矩形不支持延伸
        return clone();
    }
    
    @Override
    public Shape trimToPoint(Vec2d point) {
        // 矩形不支持修剪
        return clone();
    }
    
    @Override
    public Shape createOffset(double distance) {
        // 对于矩形，我们创建一个偏移后的矩形
        // 计算新的尺寸
        double newWidth = width + 2 * distance;
        double newHeight = height + 2 * distance;
        
        // 防止尺寸为非正导致几何异常
        if (newWidth <= 0 || newHeight <= 0) {
            LOGGER.warn("偏移距离 {} 导致矩形尺寸无效，返回原矩形", distance);
            return clone();
        }

        // 计算新的角点位置
        // 在局部坐标系中，角点应该向内或向外移动distance距离
        Vec2d localOffset = new Vec2d(-distance, -distance);
        
        // 将局部偏移转换到全局坐标系
        Vec2d globalOffset = localOffset.rotate(rotation);
        Vec2d newCorner = corner.add(globalOffset);
        
        // 调整圆角半径以适应新的尺寸
        double newCornerRadius = Math.max(0, cornerRadius + distance);
        
        return new RectangleShape(
            newCorner,
            newWidth,
            newHeight,
            newCornerRadius,
            rotation
        );
    }
    
    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> result = new ArrayList<>();
        
        for (LineShape edge : getEdges()) {
            result.addAll(edge.getIntersectionsWithPolyline(points));
        }
        
        return result;
    }
    
    @Override
    public boolean intersectsPolyline(List<Vec2d> points) {
        return !getIntersectionsWithPolyline(points).isEmpty();
    }
    
    @Override
    public Shape clone() {
        Shape shape = super.clone();
        RectangleShape clone = new RectangleShape(
            new Vec2d(corner.x, corner.y),
            width,
            height,
            cornerRadius,
            rotation
        );
        clone.setTransform(getTransform().clone());
        clone.setStyle(getStyle().clone());
        clone.setSelected(isSelected());
        clone.setVisible(isVisible());
        return clone;
    }
    
    @Override
    public String serialize() {
        return String.format("RECTANGLE %.2f,%.2f %.2f,%.2f %.2f",
            corner.x, corner.y, width, height, rotation);
    }
    
    @Override
    public void deserialize(String data) {
        String[] parts = data.split(" ");
        if (parts.length >= 4 && parts[0].equals("RECTANGLE")) {
            String[] c = parts[1].split(",");
            corner = new Vec2d(
                Double.parseDouble(c[0]),
                Double.parseDouble(c[1])
            );
            String[] size = parts[2].split(",");
            width = Double.parseDouble(size[0]);
            height = Double.parseDouble(size[1]);
            rotation = Double.parseDouble(parts[3]);
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
        
        // 获取变换后的角点
        List<Vec2d> transformedCorners = getCorners().stream()
            .map(p -> getTransform().transform(p))
            .toList();
        
        // 获取线条样式
        LineStyle lineStyle = (activeStyle.getLineStyle() instanceof LineStyle) 
            ? (LineStyle) activeStyle.getLineStyle() 
            : new LineStyle(LineStyle.LineType.SOLID, 1.0f).withColor(activeStyle.getLineStyle().getColor());
        
        // 矩形工具不需要填充，只绘制轮廓
        // 如果需要填充，先绘制填充
        // if (activeStyle.getFillStyle() != null && activeStyle.getFillStyle().isVisible()) {
        //     int fillColor = activeStyle.getFillStyle().getColor().getRGB();
        //     
        //     // 如果有圆角，使用特殊的填充方法
        //     if (cornerRadius > 0) {
        //         fillRoundedRectangle(context, fillColor);
        //     } else {
        //         fillPolygon(context, transformedCorners, fillColor);
        //     }
        // }
        
        // 绘制边框
        if (cornerRadius > 0) {
            // 绘制圆角矩形边框
            drawRoundedRectangle(context, lineStyle);
        } else {
            // 绘制普通矩形边框
            for (int i = 0; i < 4; i++) {
                Vec2d p1 = transformedCorners.get(i);
                Vec2d p2 = transformedCorners.get((i + 1) % 4);
                context.drawLine(p1, p2, lineStyle);
            }
        }
    }
    
    /**
     * 绘制圆角矩形边框
     */
    private void drawRoundedRectangle(DrawContext context, LineStyle lineStyle) {
        // 获取变换后的矩形属性
        Vec2d transformedCorner = getTransform().transform(corner);
        double transformedWidth = width;
        double transformedHeight = height;
        double transformedRadius = Math.min(cornerRadius, Math.min(width/2, height/2));
        
        // 如果圆角半径过大，调整为矩形短边的一半
        transformedRadius = Math.min(transformedRadius, Math.min(transformedWidth/2, transformedHeight/2));
        
        // 绘制四个圆角
        Color lineColor = lineStyle.getColor();
        float lineWidth = lineStyle.getWidth();
        
        // 计算四个角的圆心
        Vec2d bottomLeft = transformedCorner.add(new Vec2d(transformedRadius, transformedRadius));
        Vec2d bottomRight = transformedCorner.add(new Vec2d(transformedWidth - transformedRadius, transformedRadius));
        Vec2d topRight = transformedCorner.add(new Vec2d(transformedWidth - transformedRadius, transformedHeight - transformedRadius));
        Vec2d topLeft = transformedCorner.add(new Vec2d(transformedRadius, transformedHeight - transformedRadius));
        
        // 绘制四个圆弧 - 使用LineStyle确保选中样式正确应用
        context.drawArc(bottomLeft, transformedRadius, Math.PI, Math.PI * 1.5, lineStyle);
        context.drawArc(bottomRight, transformedRadius, Math.PI * 1.5, Math.PI * 2, lineStyle);
        context.drawArc(topRight, transformedRadius, 0, Math.PI * 0.5, lineStyle);
        context.drawArc(topLeft, transformedRadius, Math.PI * 0.5, Math.PI, lineStyle);
        
        // 绘制四条直线连接圆弧 - 使用LineStyle确保选中样式正确应用
        Vec2d left1 = transformedCorner.add(new Vec2d(0, transformedRadius));
        Vec2d left2 = transformedCorner.add(new Vec2d(0, transformedHeight - transformedRadius));
        
        Vec2d bottom1 = transformedCorner.add(new Vec2d(transformedRadius, 0));
        Vec2d bottom2 = transformedCorner.add(new Vec2d(transformedWidth - transformedRadius, 0));
        
        Vec2d right1 = transformedCorner.add(new Vec2d(transformedWidth, transformedRadius));
        Vec2d right2 = transformedCorner.add(new Vec2d(transformedWidth, transformedHeight - transformedRadius));
        
        Vec2d top1 = transformedCorner.add(new Vec2d(transformedRadius, transformedHeight));
        Vec2d top2 = transformedCorner.add(new Vec2d(transformedWidth - transformedRadius, transformedHeight));
        
        context.drawLine(left1, left2, lineStyle);
        context.drawLine(bottom1, bottom2, lineStyle);
        context.drawLine(right1, right2, lineStyle);
        context.drawLine(top1, top2, lineStyle);
    }
    
    /**
     * 填充圆角矩形
     */
    private void fillRoundedRectangle(DrawContext context, int color) {
        // 获取变换后的矩形属性
        Vec2d transformedCorner = getTransform().transform(corner);
        double transformedWidth = width;
        double transformedHeight = height;
        double transformedRadius = Math.min(cornerRadius, Math.min(width/2, height/2));
        
        // 如果圆角半径过大，调整为矩形短边的一半
        transformedRadius = Math.min(transformedRadius, Math.min(transformedWidth/2, transformedHeight/2));
        
        // 填充矩形主体部分（不包括圆角）
        context.fill(
            (int)(transformedCorner.x + transformedRadius), 
            (int)transformedCorner.y, 
            (int)(transformedCorner.x + transformedWidth - transformedRadius), 
            (int)(transformedCorner.y + transformedHeight), 
            color
        );
        
        context.fill(
            (int)transformedCorner.x, 
            (int)(transformedCorner.y + transformedRadius), 
            (int)(transformedCorner.x + transformedWidth), 
            (int)(transformedCorner.y + transformedHeight - transformedRadius), 
            color
        );
        
        // 填充四个圆角
        Color fillColor = new Color(color);
        
        // 计算四个角的圆心
        Vec2d bottomLeft = transformedCorner.add(new Vec2d(transformedRadius, transformedRadius));
        Vec2d bottomRight = transformedCorner.add(new Vec2d(transformedWidth - transformedRadius, transformedRadius));
        Vec2d topRight = transformedCorner.add(new Vec2d(transformedWidth - transformedRadius, transformedHeight - transformedRadius));
        Vec2d topLeft = transformedCorner.add(new Vec2d(transformedRadius, transformedHeight - transformedRadius));
        
        // 填充四个圆角
        context.fillCircle(bottomLeft, transformedRadius, color);
        context.fillCircle(bottomRight, transformedRadius, color);
        context.fillCircle(topRight, transformedRadius, color);
        context.fillCircle(topLeft, transformedRadius, color);
    }

    private static @NotNull List<Integer> getIntegers(List<Vec2d> points, int y) {
        List<Integer> intersections = new ArrayList<>();

        // 计算扫描线与矩形四条边的交点
        for (int i = 0; i < points.size(); i++) {
            Vec2d vertex1 = points.get(i);
            Vec2d vertex2 = points.get((i + 1) % points.size());
            
            // 检查扫描线是否与当前边相交
            if ((vertex1.y > y && vertex2.y <= y) || (vertex2.y > y && vertex1.y <= y)) {
                // 计算交点的x坐标
                double x = vertex1.x + (y - vertex1.y) * (vertex2.x - vertex1.x) / (vertex2.y - vertex1.y);
                intersections.add((int)x);
            }
        }
        return intersections;
    }

    @Override
    public boolean contains(Vec2d point) {
        boolean hasFill = getStyle() != null && 
                          getStyle().getFillStyle() != null && 
                          getStyle().getFillStyle().isVisible();
        
        if (hasFill) {
            // 预计算旋转矩阵的系数
            double cos = Math.cos(-rotation);
            double sin = Math.sin(-rotation);
            
            // 计算点相对于矩形左下角的偏移量
            Vec2d delta = point.subtract(corner);
            
            // 使用旋转矩阵将点转换到矩形的局部坐标系
            double localX = delta.x * cos - delta.y * sin;
            double localY = delta.x * sin + delta.y * cos;
            
            // 检查点是否在矩形内部
            return localX >= 0 && localX <= width && localY >= 0 && localY <= height;
        }
        
        // 如果没有填充，检查点是否在边上
        for (LineShape edge : getEdges()) {
            if (edge.contains(point)) return true;
        }
        return false;
    }
    
    @Override
    public List<Vec2d> getPoints() {
        List<Vec2d> localPoints = new ArrayList<>();

        double radius = Math.min(cornerRadius, Math.min(width, height) / 2.0);
        if (radius <= 1e-9) {
            localPoints.add(new Vec2d(0, 0));
            localPoints.add(new Vec2d(width, 0));
            localPoints.add(new Vec2d(width, height));
            localPoints.add(new Vec2d(0, height));
            localPoints.add(new Vec2d(0, 0));
        } else {
            int arcSegments = Math.max(8, (int) Math.ceil(radius * 6.0));

            localPoints.add(new Vec2d(radius, 0));
            localPoints.add(new Vec2d(width - radius, 0));
            addArcPoints(localPoints, new Vec2d(width - radius, radius), radius, -Math.PI / 2.0, 0.0, arcSegments);

            localPoints.add(new Vec2d(width, height - radius));
            addArcPoints(localPoints, new Vec2d(width - radius, height - radius), radius, 0.0, Math.PI / 2.0, arcSegments);

            localPoints.add(new Vec2d(radius, height));
            addArcPoints(localPoints, new Vec2d(radius, height - radius), radius, Math.PI / 2.0, Math.PI, arcSegments);

            localPoints.add(new Vec2d(0, radius));
            addArcPoints(localPoints, new Vec2d(radius, radius), radius, Math.PI, Math.PI * 1.5, arcSegments);

            localPoints.add(new Vec2d(radius, 0));
        }

        List<Vec2d> points = new ArrayList<>(localPoints.size());
        for (Vec2d local : localPoints) {
            Vec2d world = rotation != 0
                    ? local.rotate(rotation).add(corner)
                    : new Vec2d(corner.x + local.x, corner.y + local.y);
            points.add(getTransform().transform(world));
        }

        return points;
    }

    private void addArcPoints(List<Vec2d> points, Vec2d center, double radius, double startAngle, double endAngle, int segments) {
        if (segments <= 0) {
            return;
        }

        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (endAngle - startAngle) * t;
            points.add(new Vec2d(
                    center.x + radius * Math.cos(angle),
                    center.y + radius * Math.sin(angle)
            ));
        }
    }
    
    /**
     * 获取矩形光栅化后的方块位置
     * 根据是否有填充样式决定返回边框或填充的方块位置
     * 
     * @return 矩形上的方块位置列表
     */
    @Override
    public List<Vec2d> getBlockPositions() {
        // 获取变换后的角点
        List<Vec2d> transformedCorners = getCorners().stream()
            .map(p -> getTransform().transform(p))
            .toList();
        
        if (transformedCorners.size() < 4) {
            return new ArrayList<>();
        }
        
        // 检查是否有填充样式
        ShapeStyle shapeStyle = (ShapeStyle) getStyle();
        boolean hasFill = shapeStyle != null && 
                          shapeStyle.getFillStyle() != null && 
                          shapeStyle.getFillStyle().isVisible();
        
        if (hasFill) {
            // 如果有填充，返回填充矩形的方块位置
            return RasterizationUtils.rasterizeFilledPolygon(transformedCorners);
        } else {
            // 否则返回矩形边框的方块位置
            return RasterizationUtils.rasterizePolygon(transformedCorners);
        }
    }
    
    private void updateCorners() {
        cachedCorners = new ArrayList<>();
        Vec2d[] offsets = {
            new Vec2d(0, 0),         // 左下角
            new Vec2d(width, 0),     // 右下角
            new Vec2d(width, height), // 右上角
            new Vec2d(0, height)     // 左上角
        };
        for (Vec2d offset : offsets) {
            Vec2d point = rotation != 0 ? offset.rotate(rotation).add(corner) : new Vec2d(corner.x + offset.x, corner.y + offset.y);
            cachedCorners.add(point);
        }
        cacheValid = true;
    }

    /**
     * 填充多边形
     * 针对矩形的优化版本，避免逐行扫描
     */
    private void fillPolygon(DrawContext context, List<Vec2d> points, int color) {
        if (points.size() < 3) return;
        
        // 由于矩形是凸多边形，我们可以直接使用三角形填充
        // 对于矩形，我们可以将其分解为两个三角形
        
        // 获取矩形的四个顶点
        Vec2d p0 = points.get(0);
        Vec2d corner1 = points.get(1);
        Vec2d corner2 = points.get(2);
        Vec2d corner3 = points.get(3);
        
        // 使用Color对象，因为context.fill方法需要Color对象
        Color fillColor = new Color(color);
        
        // 计算矩形的边界框
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        
        for (Vec2d p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        
        // 使用边界框进行填充
        // 如果矩形没有旋转，这将是最高效的方法
        if (rotation == 0 || Math.abs(rotation) % (Math.PI/2) < 0.001) {
            // 矩形没有旋转或旋转了90度的倍数，可以直接使用fillRect
            context.fill((int)minX, (int)minY, (int)maxX, (int)maxY, color);
            return;
        }
        
        // 对于旋转的矩形，我们使用扫描线算法
        // 但我们可以优化扫描范围，只扫描边界框内的区域
        int scanMinY = (int)minY;
        int scanMaxY = (int)maxY;
        
        // 对每一行进行扫描
        for (int y = scanMinY; y <= scanMaxY; y++) {
            List<Integer> intersections = getIntersections(points, y);
            
            // 对交点进行排序
            intersections.sort(Integer::compareTo);
            
            // 在交点之间填充
            for (int i = 0; i < intersections.size() - 1; i += 2) {
                int x1 = intersections.get(i);
                int x2 = intersections.get(i + 1);
                context.fill(x1, y, x2, y, color);
            }
        }
    }
    
    /**
     * 获取多边形与水平线的交点
     */
    private List<Integer> getIntersections(List<Vec2d> points, int y) {
        List<Integer> intersections = new ArrayList<>();
        
        for (int i = 0; i < points.size(); i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get((i + 1) % points.size());
            
            // 检查线段是否与水平线相交
            if ((p1.y <= y && p2.y > y) || (p1.y > y && p2.y <= y)) {
                // 计算交点的x坐标
                int x = (int)(p1.x + (y - p1.y) * (p2.x - p1.x) / (p2.y - p1.y));
                intersections.add(x);
            }
        }
        
        return intersections;
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
            // 矩形打断：将矩形转换为多段线，然后按多段线处理
            List<Vec2d> points = getPoints();
            PolylineShape polylineShape = new PolylineShape(points, true);
            polylineShape.setStyle(getStyle().clone());
            
            newShapes = polylineShape.breakShape(firstBreakPoint, secondBreakPoint, breakMode);
            
        } catch (Exception e) {
            // 记录错误但不抛出异常，返回空列表
            System.err.println("打断矩形时发生错误: " + e.getMessage());
        }
        
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        try {
            BoundingBox bbox = getBoundingBox();
            return GeometryUtils.getDistanceToRectangle(bbox.getMin(), bbox.getMax(), point);
        } catch (Exception e) {
            // 记录错误但不抛出异常，返回最大距离
            System.err.println("计算矩形距离时发生错误: " + e.getMessage());
            return Double.MAX_VALUE;
        }
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        try {
            // 获取变换后的四个角点
            List<Vec2d> transformedCorners = getCorners().stream()
                .map(p -> getTransform().transform(p))
                .toList();
            
            if (transformedCorners.size() < 4) {
                return;
            }
            
            // 转换到屏幕坐标
            imgui.ImVec2[] screenPoints = new imgui.ImVec2[4]; // 4个点
            for (int i = 0; i < 4; i++) {
                Vec2d screenPoint = camera.worldToScreen(transformedCorners.get(i));
                screenPoints[i] = new imgui.ImVec2((float) screenPoint.x, (float) screenPoint.y);
            }
            
            // 检查是否为填充矩形
            boolean isFilled = getStyle() != null && 
                             getStyle().getFillStyle() != null && 
                             getStyle().getFillStyle().isVisible();
            
            if (isFilled) {
                // 填充多边形 - 使用凸多边形填充
                drawList.addConvexPolyFilled(
                    screenPoints, 4, // 4个顶点
                    0x80FFFFFF // 白色，半透明
                );
            } else {
                // 描边多边形 - 绘制四条边
                for (int i = 0; i < 4; i++) {
                    int nextIndex = (i + 1) % 4;
                    drawList.addLine(
                        screenPoints[i].x, screenPoints[i].y,
                        screenPoints[nextIndex].x, screenPoints[nextIndex].y,
                        0x80FFFFFF, 1.0f // 白色，半透明
                    );
                }
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常
            LOGGER.error("渲染矩形ImGui时发生错误", e);
        }
    }
} 