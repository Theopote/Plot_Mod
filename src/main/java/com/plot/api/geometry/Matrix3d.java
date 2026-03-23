package com.plot.api.geometry;

/**
 * 3x3矩阵类，用于2D变换
 * 提供基本的矩阵操作和2D变换功能
 */
public class Matrix3d implements Cloneable {
    private final double[][] m;

    /**
     * 创建单位矩阵
     */
    public Matrix3d() {
        m = new double[3][3];
        setIdentity();
    }

    /**
     * 设置为单位矩阵
     */
    public void setIdentity() {
        m[0][0] = 1; m[0][1] = 0; m[0][2] = 0;
        m[1][0] = 0; m[1][1] = 1; m[1][2] = 0;
        m[2][0] = 0; m[2][1] = 0; m[2][2] = 1;
    }

    /**
     * 设置平移变换
     * @param dx X方向平移量
     * @param dy Y方向平移量
     */
    public void setTranslation(double dx, double dy) {
        setIdentity();
        m[0][2] = dx;
        m[1][2] = dy;
    }

    /**
     * 设置旋转变换
     * @param angle 旋转角度（弧度）
     */
    public void setRotation(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        setIdentity();
        m[0][0] = cos;  m[0][1] = -sin;
        m[1][0] = sin;  m[1][1] = cos;
    }

    /**
     * 设置缩放变换
     * @param sx X方向缩放比例
     * @param sy Y方向缩放比例
     */
    public void setScale(double sx, double sy) {
        setIdentity();
        m[0][0] = sx;
        m[1][1] = sy;
    }

    /**
     * 矩阵乘法
     * @param other 另一个矩阵
     * @return 乘法结果
     */
    public Matrix3d multiply(Matrix3d other) {
        Matrix3d result = new Matrix3d();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double sum = 0;
                for (int k = 0; k < 3; k++) {
                    sum += m[i][k] * other.m[k][j];
                }
                result.m[i][j] = sum;
            }
        }
        return result;
    }

    /**
     * 变换点坐标
     * @param point 要变换的点
     * @return 变换后的点
     */
    public Vec2d transform(Vec2d point) {
        return new Vec2d(
            m[0][0] * point.x + m[0][1] * point.y + m[0][2],
            m[1][0] * point.x + m[1][1] * point.y + m[1][2]
        );
    }

    /**
     * 获取矩阵元素
     * @param row 行
     * @param col 列
     * @return 矩阵元素值
     */
    public double get(int row, int col) {
        return m[row][col];
    }

    /**
     * 设置矩阵元素
     * @param row 行
     * @param col 列
     * @param value 值
     */
    public void set(int row, int col, double value) {
        m[row][col] = value;
    }

    /**
     * 获取变换矩阵的缩放因子
     * @return 包含 x 和 y 方向缩放因子的向量
     */
    public Vec2d getScale() {
        // 计算 x 和 y 方向的缩放因子
        double sx = Math.sqrt(m[0][0] * m[0][0] + m[1][0] * m[1][0]);
        double sy = Math.sqrt(m[0][1] * m[0][1] + m[1][1] * m[1][1]);
        return new Vec2d(sx, sy);
    }

    /**
     * 逆变换点坐标
     * @param point 要逆变换的点
     * @return 逆变换后的点
     */
    public Vec2d inverseTransform(Vec2d point) {
        // 计算2x2部分的行列式
        double det = m[0][0] * m[1][1] - m[0][1] * m[1][0];
        if (Math.abs(det) < 1e-10) return point; // 矩阵接近奇异，返回原点
        
        // 计算逆矩阵
        double invDet = 1.0 / det;
        
        // 先减去平移部分
        double x = point.x - m[0][2];
        double y = point.y - m[1][2];
        
        // 应用2x2部分的逆矩阵
        return new Vec2d(
            (m[1][1] * x - m[0][1] * y) * invDet,
            (-m[1][0] * x + m[0][0] * y) * invDet
        );
    }

    public Matrix3d inverse() {
        Matrix3d result = new Matrix3d();
        
        // 计算行列式
        double det = determinant();
        if (Math.abs(det) < 1e-10) {
            return null; // 矩阵不可逆
        }
        
        // 计算伴随矩阵
        double[][] adj = new double[3][3];
        adj[0][0] = +(m[1][1] * m[2][2] - m[1][2] * m[2][1]);
        adj[0][1] = -(m[0][1] * m[2][2] - m[0][2] * m[2][1]);
        adj[0][2] = +(m[0][1] * m[1][2] - m[0][2] * m[1][1]);
        adj[1][0] = -(m[1][0] * m[2][2] - m[1][2] * m[2][0]);
        adj[1][1] = +(m[0][0] * m[2][2] - m[0][2] * m[2][0]);
        adj[1][2] = -(m[0][0] * m[1][2] - m[0][2] * m[1][0]);
        adj[2][0] = +(m[1][0] * m[2][1] - m[1][1] * m[2][0]);
        adj[2][1] = -(m[0][0] * m[2][1] - m[0][1] * m[2][0]);
        adj[2][2] = +(m[0][0] * m[1][1] - m[0][1] * m[1][0]);
        
        // 计算逆矩阵
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                result.m[i][j] = adj[i][j] / det;
            }
        }
        
        return result;
    }
    
    public double determinant() {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
             - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
             + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }

    @Override
    public Matrix3d clone() {
        try {
            Matrix3d clone = (Matrix3d) super.clone();
            for (int i = 0; i < 3; i++) {
                System.arraycopy(m[i], 0, clone.m[i], 0, m[i].length);
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
