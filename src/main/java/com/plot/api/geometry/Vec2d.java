package com.plot.api.geometry;

import com.plot.utils.PlotI18n;
import java.util.Objects;

/**
 * 二维向量类
 */
public class Vec2d implements Cloneable {
    public final double x;
    public final double y;
    
    public Vec2d(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    /**
     * 创建向量的深拷贝
     * @return 新的向量实例
     */
    @Override
    public Vec2d clone() {
        try {
            return (Vec2d) super.clone();
        } catch (CloneNotSupportedException e) {
            // 由于我们实现了 Cloneable，这种情况不应该发生
            return new Vec2d(this.x, this.y);
        }
    }
    
    public Vec2d add(Vec2d other) {
        return new Vec2d(x + other.x, y + other.y);
    }
    
    public Vec2d subtract(Vec2d other) {
        return new Vec2d(x - other.x, y - other.y);
    }
    
    public Vec2d multiply(double scalar) {
        return new Vec2d(x * scalar, y * scalar);
    }
    
    public Vec2d multiply(Vec2d other) {
        return new Vec2d(x * other.x, y * other.y);
    }
    
    public Vec2d divide(double scalar) {
        if (scalar == 0) throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.divisor_zero"));
        return new Vec2d(x / scalar, y / scalar);
    }
    
    public double dot(Vec2d other) {
        return x * other.x + y * other.y;
    }
    
    public double cross(Vec2d other) {
        return x * other.y - y * other.x;
    }
    
    public double length() {
        return Math.sqrt(x * x + y * y);
    }
    
    public double lengthSquared() {
        return x * x + y * y;
    }
    
    public Vec2d normalize() {
        double len = length();
        if (len == 0) return new Vec2d(0, 0);
        return divide(len);
    }
    
    public Vec2d rotate(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec2d(
            x * cos - y * sin,
            x * sin + y * cos
        );
    }
    
    public Vec2d perpendicular() {
        return new Vec2d(-y, x);
    }
    
    public double angle() {
        return Math.atan2(y, x);
    }
    
    public double angleTo(Vec2d other) {
        return other.angle() - this.angle();
    }
    
    public double distance(Vec2d other) {
        return subtract(other).length();
    }
    
    public double distanceSquared(Vec2d other) {
        return subtract(other).lengthSquared();
    }
    
    public Vec2d lerp(Vec2d other, double t) {
        return new Vec2d(
            x + (other.x - x) * t,
            y + (other.y - y) * t
        );
    }
    
    public Vec2d clamp(double min, double max) {
        return new Vec2d(
            Math.min(Math.max(x, min), max),
            Math.min(Math.max(y, min), max)
        );
    }
    
    public Vec2d round() {
        return new Vec2d(Math.round(x), Math.round(y));
    }
    
    public Vec2d floor() {
        return new Vec2d(Math.floor(x), Math.floor(y));
    }
    
    public Vec2d ceil() {
        return new Vec2d(Math.ceil(x), Math.ceil(y));
    }
    
    public boolean isZero() {
        return x == 0 && y == 0;
    }
    
    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y);
    }
    
    /**
     * 创建一个新的向量实例
     * @return 新的向量
     */
    public Vec2d copy() {
        return new Vec2d(x, y);
    }
    
    // ====== 性能优化方法 ======
    
    /**
     * 静态方法：计算两个向量的距离平方
     * 避免创建临时向量对象，提高性能
     * 
     * @param a 第一个向量
     * @param b 第二个向量
     * @return 距离的平方
     */
    public static double distanceSquared(Vec2d a, Vec2d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return dx * dx + dy * dy;
    }
    
    /**
     * 静态方法：计算两个向量的距离
     * 避免创建临时向量对象，提高性能
     * 
     * @param a 第一个向量
     * @param b 第二个向量
     * @return 距离
     */
    public static double distance(Vec2d a, Vec2d b) {
        return Math.sqrt(distanceSquared(a, b));
    }
    
    /**
     * 静态方法：计算两个向量的点积
     * 避免创建临时向量对象，提高性能
     * 
     * @param a 第一个向量
     * @param b 第二个向量
     * @return 点积
     */
    public static double dot(Vec2d a, Vec2d b) {
        return a.x * b.x + a.y * b.y;
    }
    
    /**
     * 静态方法：计算两个向量的叉积
     * 避免创建临时向量对象，提高性能
     * 
     * @param a 第一个向量
     * @param b 第二个向量
     * @return 叉积
     */
    public static double cross(Vec2d a, Vec2d b) {
        return a.x * b.y - a.y * b.x;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vec2d)) return false;
        Vec2d other = (Vec2d) obj;
        return Double.compare(x, other.x) == 0 
            && Double.compare(y, other.y) == 0;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
    
    @Override
    public String toString() {
        return String.format("Vec2d(%.2f, %.2f)", x, y);
    }
}
