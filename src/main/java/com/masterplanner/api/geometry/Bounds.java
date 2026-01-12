package com.masterplanner.api.geometry;

/**
 * 表示形状的边界矩形
 */
public class Bounds {
    private final double x;      // 左上角 x 坐标
    private final double y;      // 左上角 y 坐标
    private final double width;  // 宽度
    private final double height; // 高度
    
    public Bounds(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public double getX() {
        return x;
    }
    
    public double getY() {
        return y;
    }
    
    public double getWidth() {
        return width;
    }
    
    public double getHeight() {
        return height;
    }
    
    public Vec2d getPosition() {
        return new Vec2d(x, y);
    }
    
    public Vec2d getCenter() {
        return new Vec2d(x + width/2, y + height/2);
    }
    
    public boolean contains(Vec2d point) {
        return point.x >= x && point.x <= x + width &&
               point.y >= y && point.y <= y + height;
    }
    
    public boolean intersects(Bounds other) {
        return !(other.x > x + width ||
                other.x + other.width < x ||
                other.y > y + height ||
                other.y + other.height < y);
    }
    
    public Bounds expand(double margin) {
        return new Bounds(
            x - margin,
            y - margin,
            width + 2 * margin,
            height + 2 * margin
        );
    }
    
    @Override
    public String toString() {
        return String.format("Bounds[x=%.1f, y=%.1f, w=%.1f, h=%.1f]",
            x, y, width, height);
    }
} 