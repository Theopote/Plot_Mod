package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;

/**
 * 2x2矩阵类，用于二维几何变换
 */
public class Matrix2d {
    private double a, b, c, d;
    
    /**
     * 创建一个2x2矩阵
     * [a b]
     * [c d]
     */
    public Matrix2d(double a, double b, double c, double d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }
    
    /**
     * 创建单位矩阵
     * [1 0]
     * [0 1]
     */
    public static Matrix2d identity() {
        return new Matrix2d(1, 0, 0, 1);
    }
    
    /**
     * 创建旋转矩阵
     * [cos(angle) -sin(angle)]
     * [sin(angle)  cos(angle)]
     */
    public static Matrix2d rotation(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Matrix2d(cos, -sin, sin, cos);
    }
    
    /**
     * 创建缩放矩阵
     * [sx  0]
     * [0  sy]
     */
    public static Matrix2d scaling(double sx, double sy) {
        return new Matrix2d(sx, 0, 0, sy);
    }
    
    /**
     * 矩阵乘法
     */
    public Matrix2d multiply(Matrix2d other) {
        return new Matrix2d(
            a * other.a + b * other.c, a * other.b + b * other.d,
            c * other.a + d * other.c, c * other.b + d * other.d
        );
    }
    
    /**
     * 变换向量
     */
    public Vec2d transform(Vec2d v) {
        return new Vec2d(
            a * v.x + b * v.y,
            c * v.x + d * v.y
        );
    }
    
    /**
     * 计算矩阵行列式
     */
    public double determinant() {
        return a * d - b * c;
    }
    
    /**
     * 计算矩阵的逆
     */
    public Matrix2d inverse() {
        double det = determinant();
        if (Math.abs(det) < 1e-10) {
            throw new IllegalStateException("矩阵不可逆");
        }
        return new Matrix2d(
            d / det, -b / det,
            -c / det, a / det
        );
    }
    
    /**
     * 获取矩阵元素
     */
    public double getA() { return a; }
    public double getB() { return b; }
    public double getC() { return c; }
    public double getD() { return d; }
    
    @Override
    public String toString() {
        return String.format("[%.2f %.2f; %.2f %.2f]", a, b, c, d);
    }
} 