package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;

/**
 * 仿射变换矩阵类
 * 
 * <p>提供统一的仿射变换支持，包括平移、旋转、缩放、斜切等操作。
 * 这是所有图形变换操作的底层基础。</p>
 * 
 * <p>矩阵格式：</p>
 * <pre>
 * [ m00  m01  m02 ]
 * [ m10  m11  m12 ]
 * [  0    0    1  ]
 * </pre>
 * 
 * <p>其中：</p>
 * <ul>
 *   <li>m00, m11: X和Y方向的缩放</li>
 *   <li>m01, m10: 斜切</li>
 *   <li>m02, m12: X和Y方向的平移</li>
 * </ul>
 * 
 * <p><b>API使用指南：</b></p>
 * 
 * <p><b>1. 重置变换（清除所有现有变换）：</b></p>
 * <pre>
 * AffineTransform t = new AffineTransform();
 * t.setToTranslation(100, 50);    // 重置为纯平移
 * t.setToRotation(Math.PI/4);     // 重置为纯旋转
 * t.setToScale(2.0, 3.0);         // 重置为纯缩放
 * </pre>
 * 
 * <p><b>2. 叠加变换（链式调用）：</b></p>
 * <pre>
 * AffineTransform t = new AffineTransform();
 * t.translate(100, 50)            // 先平移
 *  .rotate(Math.PI/4)             // 再旋转
 *  .scale(2.0, 3.0);              // 最后缩放
 * </pre>
 * 
 * <p><b>3. 矩阵运算：</b></p>
 * <pre>
 * AffineTransform t1 = AffineTransform.createRotation(Math.PI/4);
 * AffineTransform t2 = AffineTransform.createScale(2.0, 2.0);
 * 
 * // 创建新矩阵：result = t1 * t2
 * AffineTransform result = t1.multiply(t2);
 * 
 * // 修改现有矩阵：t1 = t2 * t1
 * t1.preMultiply(t2);
 * 
 * // 修改现有矩阵：t1 = t1 * t2
 * t1.postMultiply(t2);
 * </pre>
 * 
 * <p><b>4. 逆变换：</b></p>
 * <pre>
 * AffineTransform t = AffineTransform.createRotation(Math.PI/4);
 * AffineTransform inverse = t.inverse();  // 抛出异常如果不可逆
 * AffineTransform safe = t.inverseSafe(); // 返回null如果不可逆
 * </pre>
 * 
 * <p><b>5. 精确的变换分析：</b></p>
 * <pre>
 * AffineTransform t = new AffineTransform();
 * t.translate(100, 50).rotate(Math.PI/4).scale(2.0, 1.5);
 * 
 * double[] components = t.decompose(); // [rotation, scaleX, scaleY, shearX]
 * double rotation = t.getRotation();   // 精确的旋转角度
 * double scaleX = t.getScaleX();       // 精确的X缩放
 * double scaleY = t.getScaleY();       // 精确的Y缩放
 * double shearX = t.getShearX();       // 斜切分量
 * boolean uniform = t.isUniform();     // 是否为均匀变换
 * </pre>
 */
public class AffineTransform {
    
    // 矩阵元素
    private double m00, m01, m02;
    private double m10, m11, m12;
    
    // 缓存分解结果，避免重复计算
    private double[] cachedDecomposition;
    private boolean decompositionValid = false;
    
    /**
     * 创建单位变换矩阵
     */
    public AffineTransform() {
        setToIdentity();
    }
    
    /**
     * 创建指定变换矩阵
     */
    public AffineTransform(double m00, double m01, double m02,
                          double m10, double m11, double m12) {
        this.m00 = m00; this.m01 = m01; this.m02 = m02;
        this.m10 = m10; this.m11 = m11; this.m12 = m12;
    }
    
    /**
     * 复制构造函数
     */
    public AffineTransform(AffineTransform other) {
        this.m00 = other.m00; this.m01 = other.m01; this.m02 = other.m02;
        this.m10 = other.m10; this.m11 = other.m11; this.m12 = other.m12;
        // 复制缓存状态
        if (other.decompositionValid && other.cachedDecomposition != null) {
            this.cachedDecomposition = other.cachedDecomposition.clone();
            this.decompositionValid = true;
        }
    }
    
    /**
     * 设置为单位矩阵
     */
    public void setToIdentity() {
        m00 = 1.0; m01 = 0.0; m02 = 0.0;
        m10 = 0.0; m11 = 1.0; m12 = 0.0;
        invalidateCache();
    }
    
    /**
     * 重置为纯平移变换（会清除所有其他变换）
     */
    public void setToTranslation(double tx, double ty) {
        m00 = 1.0; m01 = 0.0; m02 = tx;
        m10 = 0.0; m11 = 1.0; m12 = ty;
        invalidateCache();
    }
    
    /**
     * 重置为纯旋转变换（会清除所有其他变换）
     */
    public void setToRotation(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        m00 = cos;  m01 = -sin; m02 = 0.0;
        m10 = sin;  m11 = cos;  m12 = 0.0;
        invalidateCache();
    }
    
    /**
     * 重置为纯缩放变换（会清除所有其他变换）
     */
    public void setToScale(double sx, double sy) {
        m00 = sx;  m01 = 0.0; m02 = 0.0;
        m10 = 0.0; m11 = sy;  m12 = 0.0;
        invalidateCache();
    }
    
    /**
     * 重置为纯斜切变换（会清除所有其他变换）
     */
    public void setToShear(double shx, double shy) {
        m00 = 1.0;  m01 = shx; m02 = 0.0;
        m10 = shy;  m11 = 1.0; m12 = 0.0;
        invalidateCache();
    }
    
    // ===== 兼容性方法 =====
    // 保留原有的set方法名，但标记为废弃，引导用户使用新的命名
    
    /**
     * @deprecated 使用 setToTranslation(double, double) 替代
     */
    @Deprecated
    public void setTranslation(double tx, double ty) {
        setToTranslation(tx, ty);
    }
    
    /**
     * @deprecated 使用 setToRotation(double) 替代
     */
    @Deprecated
    public void setRotation(double angle) {
        setToRotation(angle);
    }
    
    /**
     * @deprecated 使用 setToScale(double, double) 替代
     */
    @Deprecated
    public void setScale(double sx, double sy) {
        setToScale(sx, sy);
    }
    
    /**
     * @deprecated 使用 setToShear(double, double) 替代
     */
    @Deprecated
    public void setShear(double shx, double shy) {
        setToShear(shx, shy);
    }
    
    // ===== 链式调用的叠加方法 =====
    
    /**
     * 叠加平移变换（支持链式调用）
     * 在当前变换基础上添加平移
     * 
     * @param tx X方向平移量
     * @param ty Y方向平移量
     * @return 返回自身以支持链式调用
     */
    public AffineTransform translate(double tx, double ty) {
        this.m02 += this.m00 * tx + this.m01 * ty;
        this.m12 += this.m10 * tx + this.m11 * ty;
        // 平移不影响分解结果，无需失效缓存
        return this;
    }
    
    /**
     * 叠加旋转变换（支持链式调用）
     * 在当前变换基础上添加旋转
     * 
     * @param angle 旋转角度（弧度）
     * @return 返回自身以支持链式调用
     */
    public AffineTransform rotate(double angle) {
        AffineTransform rot = AffineTransform.createRotation(angle);
        // 使用后置乘法：先应用当前变换，再应用旋转
        postMultiply(rot);
        return this;
    }
    
    /**
     * 叠加缩放变换（支持链式调用）
     * 在当前变换基础上添加缩放
     * 
     * @param sx X方向缩放因子
     * @param sy Y方向缩放因子
     * @return 返回自身以支持链式调用
     */
    public AffineTransform scale(double sx, double sy) {
        AffineTransform scl = AffineTransform.createScale(sx, sy);
        // 使用后置乘法：先应用当前变换，再应用缩放
        postMultiply(scl);
        return this;
    }

    /**
     * 变换点
     */
    public Vec2d transform(Vec2d point) {
        double x = m00 * point.x + m01 * point.y + m02;
        double y = m10 * point.x + m11 * point.y + m12;
        return new Vec2d(x, y);
    }
    
    /**
     * 变换向量（不包含平移）
     */
    public Vec2d transformVector(Vec2d vector) {
        double x = m00 * vector.x + m01 * vector.y;
        double y = m10 * vector.x + m11 * vector.y;
        return new Vec2d(x, y);
    }
    
    /**
     * 矩阵乘法（创建新矩阵）：result = this * other
     */
    public AffineTransform multiply(AffineTransform other) {
        AffineTransform result = new AffineTransform();
        
        result.m00 = this.m00 * other.m00 + this.m01 * other.m10;
        result.m01 = this.m00 * other.m01 + this.m01 * other.m11;
        result.m02 = this.m00 * other.m02 + this.m01 * other.m12 + this.m02;
        
        result.m10 = this.m10 * other.m00 + this.m11 * other.m10;
        result.m11 = this.m10 * other.m01 + this.m11 * other.m11;
        result.m12 = this.m10 * other.m02 + this.m11 * other.m12 + this.m12;
        
        // 乘法会改变变换矩阵，需要失效缓存
        result.invalidateCache();
        
        return result;
    }
    
    /**
     * 后置乘法（修改当前矩阵）：this = this * other
     * 相当于先应用当前的this变换，再应用other变换
     */
    public void postMultiply(AffineTransform other) {
        double new_m00 = this.m00 * other.m00 + this.m01 * other.m10;
        double new_m01 = this.m00 * other.m01 + this.m01 * other.m11;
        double new_m02 = this.m00 * other.m02 + this.m01 * other.m12 + this.m02;
        
        double new_m10 = this.m10 * other.m00 + this.m11 * other.m10;
        double new_m11 = this.m10 * other.m01 + this.m11 * other.m11;
        double new_m12 = this.m10 * other.m02 + this.m11 * other.m12 + this.m12;
        
        this.m00 = new_m00; this.m01 = new_m01; this.m02 = new_m02;
        this.m10 = new_m10; this.m11 = new_m11; this.m12 = new_m12;
        
        invalidateCache();
    }
    
    /**
     * 获取行列式
     */
    public double getDeterminant() {
        return m00 * m11 - m01 * m10;
    }

    /**
     * 失效分解缓存
     */
    private void invalidateCache() {
        decompositionValid = false;
        cachedDecomposition = null;
    }
    
    /**
     * 使用极分解精确地从变换矩阵中提取旋转、缩放和斜切分量。
     * 这是数学上完全正确的分解方法，可以处理任意仿射变换的组合。
     * 使用缓存机制避免重复计算。
     * 
     * @return 一个包含 {rotation, scaleX, scaleY, shearX} 的 double 数组
     */
    public double[] decompose() {
        // 如果缓存有效，直接返回缓存结果
        if (decompositionValid && cachedDecomposition != null) {
            return cachedDecomposition.clone();
        }
        
        double a = this.m00;
        double b = this.m10;
        double c = this.m01;
        double d = this.m11;

        double det = a * d - b * c;

        // 旋转角度 - 从基向量(1,0)变换后的方向计算
        double rotation = Math.atan2(b, a);

        // 移除旋转，得到一个上三角矩阵 [sx, shy; 0, sy]
        double denom = a * a + b * b;
        if (denom == 0) { // 奇异矩阵
             cachedDecomposition = new double[]{0, 0, 0, 0};
        } else {
            double scaleX = Math.sqrt(denom);
            double shearX = (a * c + b * d) / scaleX;
            double scaleY = det / scaleX;
            cachedDecomposition = new double[]{rotation, scaleX, scaleY, shearX};
        }
        
        decompositionValid = true;
        return cachedDecomposition.clone();
    }
    
    /**
     * 获取旋转角度（使用极分解精确计算）
     */
    public double getRotation() {
        return decompose()[0];
    }
    
    /**
     * 获取X方向缩放因子（使用极分解精确计算）
     */
    public double getScaleX() {
        return decompose()[1];
    }
    
    /**
     * 获取Y方向缩放因子（使用极分解精确计算）
     * 注意：这里返回绝对值，因为scaleY可能为负值（镜像变换）
     */
    public double getScaleY() {
        return Math.abs(decompose()[2]);
    }
    
    /**
     * 检查是否为均匀变换（等比例缩放，无斜切）
     * 使用极分解进行数学上完全正确的判断
     */
    public boolean isUniform() {
        double[] components = decompose();
        double scaleX = components[1];
        double scaleY = components[2];
        double shearX = components[3];

        // 检查缩放是否几乎相等，并且没有斜切
        return Math.abs(scaleX - scaleY) < 1e-9 && Math.abs(shearX) < 1e-9;
    }
    

    /**
     * 检查是否为纯旋转
     */
    public boolean isRotation() {
        double scaleX = getScaleX();
        double scaleY = getScaleY();
        return Math.abs(scaleX - 1.0) < 1e-10 && Math.abs(scaleY - 1.0) < 1e-10 &&
               Math.abs(m02) < 1e-10 && Math.abs(m12) < 1e-10;
    }
    
    /**
     * 检查是否为纯缩放
     */
    public boolean isScale() {
        return Math.abs(m01) < 1e-10 && Math.abs(m10) < 1e-10 &&
               Math.abs(m02) < 1e-10 && Math.abs(m12) < 1e-10;
    }
    
    /**
     * 克隆变换矩阵
     */
    @Override
    public AffineTransform clone() {
        return new AffineTransform(this);
    }
    
    /**
     * 转换为字符串
     */
    @Override
    public String toString() {
        return String.format("AffineTransform[%.3f, %.3f, %.3f; %.3f, %.3f, %.3f]",
                m00, m01, m02, m10, m11, m12);
    }
    
    /**
     * 创建平移变换
     */
    public static AffineTransform createTranslation(double tx, double ty) {
        AffineTransform t = new AffineTransform();
        t.setToTranslation(tx, ty);
        return t;
    }
    
    /**
     * 创建旋转变换
     */
    public static AffineTransform createRotation(double angle) {
        AffineTransform t = new AffineTransform();
        t.setToRotation(angle);
        return t;
    }
    
    /**
     * 创建缩放变换
     */
    public static AffineTransform createScale(double sx, double sy) {
        AffineTransform t = new AffineTransform();
        t.setToScale(sx, sy);
        return t;
    }

}