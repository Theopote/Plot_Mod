package com.plot.api.render;

import com.plot.api.geometry.Vec2d;
import com.plot.api.geometry.Matrix3d;

/**
 * 变换接口，定义坐标变换操作
 */
public interface ITransform {
    /**
     * 平移
     * @param dx X方向偏移
     * @param dy Y方向偏移
     */
    void translate(double dx, double dy);

    /**
     * 旋转
     * @param angle 旋转角度（弧度）
     */
    void rotate(double angle);

    /**
     * 缩放
     * @param sx X方向缩放
     * @param sy Y方向缩放
     */
    void scale(double sx, double sy);

    /**
     * 统一缩放
     * @param scale 缩放比例
     */
    void scale(double scale);

    /**
     * 获取变换矩阵
     * @return 变换矩阵
     */
    Matrix3d getMatrix();

    /**
     * 设置变换矩阵
     * @param matrix 变换矩阵
     */
    void setMatrix(Matrix3d matrix);

    /**
     * 重置变换
     */
    void reset();

    /**
     * 变换点
     * @param point 要变换的点
     * @return 变换后的点
     */
    Vec2d transformPoint(Vec2d point);

    /**
     * 变换向量
     * @param vector 要变换的向量
     * @return 变换后的向量
     */
    Vec2d transformVector(Vec2d vector);

    /**
     * 获取逆变换
     * @return 逆变换
     */
    ITransform inverse();

    /**
     * 组合变换
     * @param other 其他变换
     * @return 组合后的变换
     */
    ITransform combine(ITransform other);

    /**
     * 克隆变换
     * @return 变换副本
     */
    ITransform clone();
}
