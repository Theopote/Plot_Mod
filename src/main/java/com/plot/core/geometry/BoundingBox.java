package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.api.geometry.IBoundingBox;

/**
 * 边界框类，用于表示一个矩形区域
 * 实现API包中的IBoundingBox接口
 */
public class BoundingBox implements IBoundingBox {
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;
    
    public BoundingBox(double minX, double minY, double maxX, double maxY) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
    }
    
    public BoundingBox(Vec2d point1, Vec2d point2) {
        this(point1.x, point1.y, point2.x, point2.y);
    }
    
    /**
     * 创建一个包含给定点的最小边界框
     * @param points 点数组
     * @return 包含所有点的边界框
     */
    public static BoundingBox fromPoints(Vec2d... points) {
        if (points == null || points.length == 0) {
            throw new IllegalArgumentException("Points array cannot be empty");
        }
        
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
        
        return new BoundingBox(minX, minY, maxX, maxY);
    }
    
    /**
     * 获取边界框的中心点
     * @return 中心点坐标
     */
    @Override
    public Vec2d getCenter() {
        return new Vec2d(
            (minX + maxX) / 2,
            (minY + maxY) / 2
        );
    }
    
    /**
     * 获取边界框的四个角点
     * @return 角点数组，顺序为：左上、右上、右下、左下
     */
    public Vec2d[] getCorners() {
        return new Vec2d[] {
            new Vec2d(minX, maxY), // 左上
            new Vec2d(maxX, maxY), // 右上
            new Vec2d(maxX, minY), // 右下
            new Vec2d(minX, minY)  // 左下
        };
    }
    
    /**
     * 获取边界框的中点
     * @return 中点数组，顺序为：上中、右中、下中、左中
     */
    public Vec2d[] getMidPoints() {
        Vec2d center = getCenter();
        return new Vec2d[] {
            new Vec2d(center.x, maxY), // 上中
            new Vec2d(maxX, center.y), // 右中
            new Vec2d(center.x, minY), // 下中
            new Vec2d(minX, center.y)  // 左中
        };
    }
    
    /**
     * 获取边界框的宽度
     * @return 宽度
     */
    @Override
    public double getWidth() {
        return maxX - minX;
    }
    
    /**
     * 获取边界框的高度
     * @return 高度
     */
    @Override
    public double getHeight() {
        return maxY - minY;
    }

    /**
     * 获取边界框的最小点(左下角)
     * @return 最小点坐标
     */
    @Override
    public Vec2d getMin() {
        return new Vec2d(minX, minY);
    }

    /**
     * 获取边界框的最大点(右上角)
     * @return 最大点坐标
     */
    @Override
    public Vec2d getMax() {
        return new Vec2d(maxX, maxY);
    }
    
    /**
     * 判断点是否在边界框内
     * @param point 要判断的点
     * @return 是否在边界框内
     */
    @Override
    public boolean contains(Vec2d point) {
        return point.x >= minX && point.x <= maxX &&
               point.y >= minY && point.y <= maxY;
    }
    
    /**
     * 获取点到边界框的最短距离
     * @param point 要计算的点
     * @return 最短距离
     */
    @Override
    public double distanceTo(Vec2d point) {
        double dx = Math.max(minX - point.x, Math.max(0, point.x - maxX));
        double dy = Math.max(minY - point.y, Math.max(0, point.y - maxY));
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 判断是否与另一个边界框相交
     * @param other 另一个边界框
     * @return 是否相交
     */
    @Override
    public boolean intersects(IBoundingBox other) {
        return !(other.getMin().x > maxX ||
                other.getMax().x < minX ||
                other.getMin().y > maxY ||
                other.getMax().y < minY);
    }
    
    /**
     * 获取与另一个边界框的并集
     * @param other 另一个边界框
     * @return 并集边界框
     */
    public BoundingBox union(IBoundingBox other) {
        return new BoundingBox(
            Math.min(minX, other.getMin().x),
            Math.min(minY, other.getMin().y),
            Math.max(maxX, other.getMax().x),
            Math.max(maxY, other.getMax().y)
        );
    }
    
    /**
     * 获取与另一个边界框的交集
     * @param other 另一个边界框
     * @return 交集边界框，如果不相交则返回null
     */
    public BoundingBox intersection(IBoundingBox other) {
        if (!intersects(other)) {
            return null;
        }
        
        return new BoundingBox(
            Math.max(minX, other.getMin().x),
            Math.max(minY, other.getMin().y),
            Math.min(maxX, other.getMax().x),
            Math.min(maxY, other.getMax().y)
        );
    }
    
    /**
     * 扩展边界框
     * @param margin 扩展的边距
     * @return 扩展后的边界框
     */
    @Override
    public BoundingBox expand(double margin) {
        return new BoundingBox(
            minX - margin, minY - margin,
            maxX + margin, maxY + margin
        );
    }
    
    /**
     * 创建一个空的边界框
     * @return 空边界框
     */
    public static BoundingBox empty() {
        return new BoundingBox(0, 0, 0, 0);
    }
    
    /**
     * 创建一个包含所有给定边界框的最小边界框
     * @param boxes 边界框数组
     * @return 包含所有边界框的最小边界框
     */
    public static BoundingBox merge(IBoundingBox... boxes) {
        if (boxes == null || boxes.length == 0) {
            return empty();
        }
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        
        for (IBoundingBox box : boxes) {
            minX = Math.min(minX, box.getMin().x);
            minY = Math.min(minY, box.getMin().y);
            maxX = Math.max(maxX, box.getMax().x);
            maxY = Math.max(maxY, box.getMax().y);
        }
        
        return new BoundingBox(minX, minY, maxX, maxY);
    }
    
    /**
     * 从图形列表创建总包围盒
     * @param shapes 图形列表
     * @return 包含所有图形的总包围盒，如果列表为空则返回null
     */
    public static BoundingBox createFromShapes(java.util.List<? extends com.plot.core.model.Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return null;
        }
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (com.plot.core.model.Shape shape : shapes) {
            BoundingBox bounds = shape.getBoundingBox();
            if (bounds != null) {
                minX = Math.min(minX, bounds.getMin().x);
                minY = Math.min(minY, bounds.getMin().y);
                maxX = Math.max(maxX, bounds.getMax().x);
                maxY = Math.max(maxY, bounds.getMax().y);
            }
        }
        
        return new BoundingBox(minX, minY, maxX, maxY);
    }
    
    /**
     * 获取边界框上距离给定点最近的点
     * @param point 给定点
     * @return 边界框上最近的点
     */
    public Vec2d getClosestPoint(Vec2d point) {
        // 如果点在边界框内，直接返回该点
        if (contains(point)) {
            return new Vec2d(point.x, point.y);
        }
        
        // 计算点到边界框各边的最近点
        double x = Math.max(minX, Math.min(maxX, point.x));
        double y = Math.max(minY, Math.min(maxY, point.y));
        
        return new Vec2d(x, y);
    }
    
    @Override
    public String toString() {
        return String.format("BoundingBox[min=(%f,%f), max=(%f,%f)]", minX, minY, maxX, maxY);
    }
    
    // Getters
    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
}
