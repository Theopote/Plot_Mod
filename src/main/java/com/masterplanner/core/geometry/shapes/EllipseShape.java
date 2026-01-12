package com.masterplanner.core.geometry.shapes;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.render.IRenderVisitor;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.geometry.GeometryUtils;
import com.masterplanner.core.geometry.Matrix2d;
import com.masterplanner.core.geometry.AffineTransform;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.ui.tools.impl.modify.helper.IShapeVisitor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 椭圆形状
 */
public class EllipseShape extends Shape {
    private static final Logger LOGGER = LoggerFactory.getLogger(EllipseShape.class);
    private Vec2d center;      // 中心点
    private double radiusX;    // X轴半径
    private double radiusY;    // Y轴半径
    private double rotation;   // 旋转角度（弧度）
    
    // 缓存旋转矩阵的值，避免重复计算
    private double cosRotation;  // 旋转角度的余弦值
    private double sinRotation;  // 旋转角度的正弦值
    
    public EllipseShape(Vec2d center, double radiusX, double radiusY, double rotation) {
        super(center != null ? center : new Vec2d(0, 0));
        this.center = center != null ? center : new Vec2d(0, 0);
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.rotation = rotation;
        // 初始化缓存的旋转矩阵值
        updateRotationCache();
    }
    
    // 更新旋转缓存
    private void updateRotationCache() {
        this.cosRotation = Math.cos(rotation);
        this.sinRotation = Math.sin(rotation);
    }
    
    public Vec2d getCenter() { return center; }
    public void setCenter(Vec2d center) { this.center = center; }
    public double getRadiusX() { return radiusX; }
    public void setRadiusX(double radiusX) { this.radiusX = radiusX; }
    public double getRadiusY() { return radiusY; }
    public void setRadiusY(double radiusY) { this.radiusY = radiusY; }
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { 
        this.rotation = rotation; 
        // 更新旋转缓存
        updateRotationCache();
    }
    
    /**
     * 计算椭圆上指定角度的点坐标
     * @param angle 角度（弧度制）
     * @return 椭圆上的点坐标
     */
    public Vec2d getPointAtAngle(double angle) {
        // 计算未旋转椭圆上的点坐标
        double x = radiusX * Math.cos(angle);
        double y = radiusY * Math.sin(angle);
        
        // 应用旋转变换
        double rotatedX = x * cosRotation - y * sinRotation;
        double rotatedY = x * sinRotation + y * cosRotation;
        
        // 返回世界坐标
        return new Vec2d(
            center.x + rotatedX,
            center.y + rotatedY
        );
    }
    
    // 将全局坐标转换为椭圆局部坐标 - 使用矩阵变换
    private Vec2d toLocal(Vec2d point) {
        Vec2d local = point.subtract(center);
        // 使用缓存的旋转矩阵值进行反向旋转
        // 注意：这里手动计算矩阵乘法以优化性能，避免创建Matrix2d对象
        return new Vec2d(
            local.x * cosRotation + local.y * sinRotation,
            -local.x * sinRotation + local.y * cosRotation
        );
        // 等效于：Matrix2d.rotation(-rotation).transform(local);
    }
    
    // 将局部坐标转换为全局坐标 - 使用矩阵变换
    private Vec2d toGlobal(Vec2d point) {
        // 使用缓存的旋转矩阵值进行旋转
        // 注意：这里手动计算矩阵乘法以优化性能，避免创建Matrix2d对象
        return new Vec2d(
            center.x + point.x * cosRotation - point.y * sinRotation,
            center.y + point.x * sinRotation + point.y * cosRotation
        );
        // 等效于：Matrix2d.rotation(rotation).transform(point).add(center);
    }
    
    @Override
    public void translate(Vec2d offset) {
        center = center.add(offset);
    }
    
    @Override
    public void rotate(double angle, Vec2d rotationCenter) {
        // 使用矩阵变换旋转中心点
        Matrix2d rotMatrix = Matrix2d.rotation(angle);
        center = rotMatrix.transform(center.subtract(rotationCenter)).add(rotationCenter);
        
        // 更新旋转角度
        rotation += angle;
        // 更新缓存的旋转矩阵值
        updateRotationCache();
    }
    
    @Override
    public Shape transform(AffineTransform transformMatrix) {
        // 椭圆变换后仍然是椭圆，但需要组合变换矩阵
        Vec2d newCenter = transformMatrix.transform(this.center);
        
        // 通过变换x轴和y轴的单位向量来计算新椭圆的半轴和旋转角
        Vec2d xAxis = transformMatrix.transformVector(new Vec2d(this.radiusX, 0));
        Vec2d yAxis = transformMatrix.transformVector(new Vec2d(0, this.radiusY));
        
        double newRadiusX = xAxis.length();
        double newRadiusY = yAxis.length();
        double newRotation = this.rotation + transformMatrix.getRotation();
        
        // 更新椭圆参数
        this.center = newCenter;
        this.radiusX = newRadiusX;
        this.radiusY = newRadiusY;
        this.rotation = newRotation;
        
        // 更新缓存的旋转矩阵值
        updateRotationCache();
        
        return this; // 椭圆变换后仍然是椭圆
    }
    
    /**
     * 对椭圆进行缩放变换
     * 
     * 对于旋转椭圆的非均匀缩放，这是一个复杂的数学问题，需要二次型变换。
     * 本实现支持以下情况：
     * 1. 均匀缩放：直接缩放半径
     * 2. 未旋转椭圆的非均匀缩放：直接缩放对应轴的半径
     * 3. 旋转椭圆的非均匀缩放：使用二次型矩阵变换（精确实现）
     *
     * @param scale 缩放比例向量
     * @param scaleCenter 缩放中心点
     */
    @Override
    public void scale(Vec2d scale, Vec2d scaleCenter) {
        // 1. 缩放中心点
        if (!center.equals(scaleCenter)) {
            Vec2d centerOffset = center.subtract(scaleCenter);
            centerOffset = new Vec2d(
                centerOffset.x * scale.x,
                centerOffset.y * scale.y
            );
            center = scaleCenter.add(centerOffset);
        }
        
        // 2. 检查是否为均匀缩放
        if (Math.abs(scale.x - scale.y) < 1e-6) {
            // 均匀缩放：直接缩放半径
            radiusX *= scale.x;
            radiusY *= scale.y;
            return;
        }
        
        // 3. 检查椭圆是否未旋转
        if (Math.abs(rotation % Math.PI) < 1e-6) {
            // 未旋转椭圆：直接缩放对应轴的半径
            radiusX *= scale.x;
            radiusY *= scale.y;
            return;
        }
        
        // 4. 旋转椭圆的非均匀缩放：使用二次型矩阵变换
        try {
            scaleRotatedEllipse(scale);
        } catch (Exception e) {
            LOGGER.error("旋转椭圆缩放时发生错误，回退到简单缩放", e);
            // 回退到简单缩放（可能不精确）
            radiusX *= scale.x;
            radiusY *= scale.y;
        }
    }
    
    /**
     * 使用二次型矩阵变换对旋转椭圆进行非均匀缩放
     * 这是数学上正确的实现
     * 
     * @param scale 缩放比例向量
     */
    private void scaleRotatedEllipse(Vec2d scale) {
        // 椭圆的二次型矩阵形式：x^T * A * x = 1
        // 其中 A = R^T * D * R，R是旋转矩阵，D是对角矩阵
        
        // 构建原始椭圆的二次型矩阵
        double cos = cosRotation;
        double sin = sinRotation;
        
        // 原始椭圆的二次型矩阵（在标准坐标系中）
        double a11 = 1.0 / (radiusX * radiusX);
        double a22 = 1.0 / (radiusY * radiusY);
        // 标准椭圆无交叉项，a12 = 0.0
        
        // 旋转到世界坐标系
        double A11 = a11 * cos * cos + a22 * sin * sin;
        double A12 = (a11 - a22) * cos * sin;
        double A22 = a11 * sin * sin + a22 * cos * cos;
        
        // 应用缩放变换：S * A * S^T
        // 其中 S 是缩放矩阵
        double s11 = 1.0 / scale.x;
        double s22 = 1.0 / scale.y;
        
        double newA11 = s11 * s11 * A11;
        double newA12 = s11 * s22 * A12;
        double newA22 = s22 * s22 * A22;
        
        // 从新的二次型矩阵中提取椭圆参数
        // 求解特征值和特征向量
        double trace = newA11 + newA22;
        double det = newA11 * newA22 - newA12 * newA12;
        
        // 特征值
        double discriminant = trace * trace - 4 * det;
        if (discriminant < 0) {
            throw new IllegalArgumentException("缩放后的二次型不是椭圆");
        }
        
        double lambda1 = (trace + Math.sqrt(discriminant)) / 2.0;
        double lambda2 = (trace - Math.sqrt(discriminant)) / 2.0;
        
        // 新的半径
        double newRadiusX = 1.0 / Math.sqrt(lambda1);
        double newRadiusY = 1.0 / Math.sqrt(lambda2);
        
        // 新的旋转角度（从特征向量计算）
        double newRotation;
        if (Math.abs(newA12) < 1e-10) {
            // 无交叉项，主轴与坐标轴对齐
            newRotation = 0.0;
        } else {
            // 计算主轴方向
            newRotation = 0.5 * Math.atan2(2 * newA12, newA11 - newA22);
        }
        
        // 更新椭圆参数
        radiusX = newRadiusX;
        radiusY = newRadiusY;
        rotation = newRotation;
        
        // 更新旋转缓存
        updateRotationCache();
        
        LOGGER.debug("旋转椭圆缩放完成: radiusX={}, radiusY={}, rotation={}", 
                    radiusX, radiusY, Math.toDegrees(rotation));
    }
    
    
    /**
     * 计算椭圆的边界框
     * 对于旋转的椭圆，我们通过以下步骤计算精确的轴对齐边界框：
     * 1. 计算椭圆在x和y方向上的极值点，这些点是椭圆与其边界框相切的点
     * 2. 对于旋转椭圆，极值点出现在参数方程对x或y求导等于0的位置
     * 3. 通过这些极值点确定边界框的范围
     * 
     * 注意：这个方法返回的是轴对齐的边界框（Axis-Aligned Bounding Box, AABB）
     * 对于旋转的椭圆，这个边界框可能会比最小面积的旋转边界框（Oriented Bounding Box, OBB）大一些
     * 
     * @return 包含椭圆的轴对齐边界框
     */
    @Override
    public BoundingBox getBoundingBox() {
        // 如果椭圆没有旋转，直接返回轴对齐的边界框
        if (Math.abs(rotation % (Math.PI * 2)) < 1e-6) {
            return new BoundingBox(
                new Vec2d(center.x - radiusX, center.y - radiusY),
                new Vec2d(center.x + radiusX, center.y + radiusY)
            );
        }
        
        // 对于旋转的椭圆，我们需要找到x和y方向的极值点
        // 这些点出现在参数方程对x或y求导等于0的位置
        
        // 计算旋转角度的三角函数值（使用缓存）
        double cos = cosRotation;
        double sin = sinRotation;
        
        // 计算x方向的极值点的参数方程角度
        double tx1 = Math.atan2(-radiusY * sin, radiusX * cos);
        double tx2 = tx1 + Math.PI;
        
        // 计算y方向的极值点的参数方程角度
        double ty1 = Math.atan2(radiusY * cos, radiusX * sin);
        double ty2 = ty1 + Math.PI;
        
        // 计算这些角度对应的点
        Vec2d[] extremePoints = new Vec2d[4];
        extremePoints[0] = getPointAtAngle(tx1);
        extremePoints[1] = getPointAtAngle(tx2);
        extremePoints[2] = getPointAtAngle(ty1);
        extremePoints[3] = getPointAtAngle(ty2);
        
        // 找到x和y的最小最大值
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        
        for (Vec2d point : extremePoints) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        
        // 返回精确的轴对齐边界框
        return new BoundingBox(
            new Vec2d(minX, minY),
            new Vec2d(maxX, maxY)
        );
    }

    @Override
    public boolean containsPoint(Vec2d point, double tolerance) {
        Vec2d local = toLocal(point);
        double x = local.x / radiusX;
        double y = local.y / radiusY;
        return Math.abs(x * x + y * y - 1) <= tolerance;
    }
    
    @Override
    public Vec2d getClosestPoint(Vec2d point) {
        Vec2d local = toLocal(point);
        
        // 使用迭代法找到最近点，但动态调整迭代次数
        double angle = Math.atan2(local.y * radiusX, local.x * radiusY);
        
        // 初始误差和目标精度
        double error = Double.MAX_VALUE;
        double targetPrecision = 1e-6;
        int maxIterations = 8;  // 最大迭代次数
        
        for (int i = 0; i < maxIterations && error > targetPrecision; i++) {
            double cosA = Math.cos(angle);
            double sinA = Math.sin(angle);
            double x = radiusX * cosA;
            double y = radiusY * sinA;
            
            double ex = -radiusX * sinA;
            double ey = radiusY * cosA;
            
            double numerator = (x - local.x) * ex + (y - local.y) * ey;
            double denominator = ex * ex + ey * ey;
            
            // 计算角度调整量
            double deltaAngle = numerator / denominator;
            angle -= deltaAngle;
            
            // 更新误差
            error = Math.abs(deltaAngle);
        }
        
        // 使用优化后的toGlobal方法
        double x = radiusX * Math.cos(angle);
        double y = radiusY * Math.sin(angle);
        return toGlobal(new Vec2d(x, y));
    }
    
    /**
     * 设置椭圆的控制点
     * 控制点的定义如下：
     * - 索引0：中心点
     * - 索引1：右点（主轴正方向）
     * - 索引2：上点（次轴正方向）
     * - 索引3：左点（主轴负方向）
     * - 索引4：下点（次轴负方向）
     * 
     * 对于旋转的椭圆，我们通过以下步骤更新参数：
     * 1. 中心点直接更新位置
     * 2. 主轴点（右/左）用于更新radiusX和旋转角度
     * 3. 次轴点（上/下）用于更新radiusY，同时保持旋转角度不变
     * 
     * @param index 控制点索引
     * @param point 新的控制点位置
     * @throws IllegalArgumentException 如果索引无效
     */
    @Override
    public void setControlPoint(int index, Vec2d point) {
        if (index < 0 || index > 4) {
            throw new IllegalArgumentException("Control point index out of bounds: " + index);
        }
        
        switch (index) {
            case 0: // 中心点
                setCenter(point);
                break;
                
            case 1: // 右点（主轴正方向）
            case 3: // 左点（主轴负方向）
                updateMainAxisPoint(point);
                break;
                
            case 2: // 上点（次轴正方向）
            case 4: // 下点（次轴负方向）
                updateSecondaryAxisPoint(point);
                break;
        }
    }
    
    /**
     * 更新主轴控制点（右点或左点）
     * 这个方法会同时更新radiusX和旋转角度
     * 
     * @param point 新的控制点位置
     */
    private void updateMainAxisPoint(Vec2d point) {
        // 计算从中心点到控制点的向量
        Vec2d vector = point.subtract(center);
        
        // 更新radiusX（使用向量长度）
        radiusX = vector.length();
        
        // 更新旋转角度（使用向量方向）
        if (radiusX > 1e-6) {  // 避免除以零
            rotation = Math.atan2(vector.y, vector.x);
            // 更新旋转缓存
            updateRotationCache();
        }
    }
    
    /**
     * 更新次轴控制点（上点或下点）
     * 这个方法只更新radiusY，保持旋转角度不变
     * 
     * @param point 新的控制点位置
     */
    private void updateSecondaryAxisPoint(Vec2d point) {
        // 将点转换到椭圆的局部坐标系
        Vec2d local = toLocal(point);
        
        // 计算新的radiusY
        // 使用局部坐标系中的y值，因为这是垂直于主轴的分量
        radiusY = Math.abs(local.y);
    }
    
    /**
     * 获取椭圆的控制点列表
     * 返回的控制点按照以下顺序排列：
     * 0: 中心点
     * 1: 右点（主轴正方向）
     * 2: 上点（次轴正方向）
     * 3: 左点（主轴负方向）
     * 4: 下点（次轴负方向）
     * 
     * @return 控制点列表
     */
    @Override
    public List<Vec2d> getControlPoints() {
        List<Vec2d> points = new ArrayList<>();
        points.add(center);                       // 中心点 (索引0)
        points.add(getPointAtAngle(0));           // 右点 (索引1)
        points.add(getPointAtAngle(Math.PI / 2)); // 上点 (索引2)
        points.add(getPointAtAngle(Math.PI));     // 左点 (索引3)
        points.add(getPointAtAngle(3 * Math.PI / 2)); // 下点 (索引4)
        return points;
    }
    
    @Override
    public boolean intersects(Shape other) {
        return !getIntersectionPoints(other).isEmpty();
    }
    
    @Override
    public List<Vec2d> getIntersectionPoints(Shape other) {
        // 将问题转化为标准椭圆
        if (other instanceof LineShape) {
            return getLineIntersections((LineShape) other);
        }
        return other.getIntersectionPoints(this);
    }
    
    /**
     * 求解二次方程 ax² + bx + c = 0
     * @param a 二次项系数
     * @param b 一次项系数
     * @param c 常数项
     * @return 方程的实数根列表，如果没有实数根则返回空列表
     */
    private List<Double> solveQuadratic(double a, double b, double c) {
        List<Double> roots = new ArrayList<>();
        
        // 计算判别式
        double discriminant = b * b - 4 * a * c;
        
        // 如果判别式小于0，方程没有实数根
        if (discriminant < 0) return roots;
        
        // 计算两个根
        double sqrtDiscriminant = Math.sqrt(discriminant);
        roots.add((-b + sqrtDiscriminant) / (2 * a));
        roots.add((-b - sqrtDiscriminant) / (2 * a));
        
        return roots;
    }
    
    private List<Vec2d> getLineIntersections(LineShape line) {
        List<Vec2d> result = new ArrayList<>();
        
        // 将线段转换到椭圆局部坐标系
        Vec2d start = toLocal(line.getStart());
        Vec2d end = toLocal(line.getEnd());
        
        // 求解二次方程
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double a = dx * dx / (radiusX * radiusX) + dy * dy / (radiusY * radiusY);
        double b = 2 * (start.x * dx / (radiusX * radiusX) + start.y * dy / (radiusY * radiusY));
        double c = start.x * start.x / (radiusX * radiusX) + start.y * start.y / (radiusY * radiusY) - 1;
        
        // 使用二次方程求解器获取参数t的值
        List<Double> tValues = solveQuadratic(a, b, c);
        
        // 检查每个t值是否在[0,1]范围内，如果是则计算交点
        for (double t : tValues) {
            if (t >= 0 && t <= 1) {
                result.add(toGlobal(new Vec2d(
                    start.x + t * dx,
                    start.y + t * dy
                )));
            }
        }
        
        return result;
    }
    
    @Override
    public List<Vec2d> getIntersectionsWith(Shape other) {
        // 首先检查边界框是否相交
        if (!getBoundingBox().intersects(other.getBoundingBox())) {
            return new ArrayList<>();
        }

        // 如果是直线，直接调用辅助方法，避免重复代码
        switch (other) {
            case LineShape line -> {
                return getLineIntersections(line);
            }


            // 如果是圆形，使用椭圆-圆形相交算法
            case CircleShape circle -> {
                return getCircleIntersections(circle);
            }


            // 如果是另一个椭圆，使用椭圆-椭圆相交算法
            case EllipseShape ellipse -> {
                return getEllipseIntersections(ellipse);
            }
            default -> {
            }
        }

        // 对于其他形状，明确表示不支持精确相交检测
        LOGGER.warn("椭圆与 {} 的相交检测不支持，返回空结果", other.getClass().getSimpleName());
        return new ArrayList<>();
    }
    
    /**
     * 计算椭圆与圆形的交点
     * 使用解析几何方法
     */
    private List<Vec2d> getCircleIntersections(CircleShape circle) {
        List<Vec2d> result = new ArrayList<>();
        
        // 将圆形转换到椭圆的局部坐标系
        Vec2d circleCenter = toLocal(circle.getCenter());
        double circleRadius = circle.getRadius();
        
        // 在局部坐标系中，椭圆方程为 x²/a² + y²/b² = 1
        // 圆形方程为 (x-cx)² + (y-cy)² = r²
        
        // 展开圆形方程并代入椭圆方程
        // 这将产生一个四次方程，需要数值求解
        // 为了简化，我们使用迭代方法找到交点
        
        // 在椭圆上采样点，找到与圆形距离最近的点
        int samples = 64;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < samples; i++) {
            double angle = 2 * Math.PI * i / samples;
            Vec2d ellipsePoint = new Vec2d(
                radiusX * Math.cos(angle),
                radiusY * Math.sin(angle)
            );
            
            double distance = ellipsePoint.distance(circleCenter) - circleRadius;
            if (Math.abs(distance) < minDistance) {
                minDistance = Math.abs(distance);
            }
        }
        
        // 如果找到足够接近的点，使用牛顿法精确求解
        if (minDistance < circleRadius * 0.1) {
            // 使用数值方法精确求解交点
            result.addAll(solveEllipseCircleIntersection(circleCenter, circleRadius));
        }
        
        // 转换回世界坐标
        List<Vec2d> worldResult = new ArrayList<>();
        for (Vec2d point : result) {
            worldResult.add(toGlobal(point));
        }
        
        return worldResult;
    }
    
    /**
     * 计算椭圆与椭圆的交点
     * 这是一个复杂的四次方程求解问题
     */
    private List<Vec2d> getEllipseIntersections(EllipseShape other) {
        // 椭圆-椭圆相交是一个四次方程问题，需要复杂的数值求解
        // 为了简化，我们使用边界框相交检测和近似方法
        LOGGER.debug("椭圆-椭圆相交检测使用近似方法");
        
        // 检查两个椭圆的边界框是否相交
        if (!getBoundingBox().intersects(other.getBoundingBox())) {
            return new ArrayList<>();
        }
        
        // 使用采样方法近似检测
        List<Vec2d> intersections = new ArrayList<>();
        List<Vec2d> otherPoints = other.getPoints();
        
        for (Vec2d point : otherPoints) {
            if (contains(point)) {
                intersections.add(point);
            }
        }
        
        return intersections;
    }
    
    /**
     * 使用数值方法求解椭圆与圆形的交点
     */
    private List<Vec2d> solveEllipseCircleIntersection(Vec2d circleCenter, double circleRadius) {
        List<Vec2d> result = new ArrayList<>();
        
        // 使用牛顿法在椭圆上寻找与圆形距离为零的点
        int maxIterations = 20;
        double tolerance = 1e-6;
        
        // 在椭圆上选择几个起始点
        for (int i = 0; i < 8; i++) {
            double startAngle = 2 * Math.PI * i / 8;
            Vec2d startPoint = new Vec2d(
                radiusX * Math.cos(startAngle),
                radiusY * Math.sin(startAngle)
            );
            
            Vec2d solution = newtonMethodForIntersection(startPoint, circleCenter, circleRadius, maxIterations, tolerance);
            if (solution != null) {
                // 检查解是否有效且不重复
                boolean isDuplicate = false;
                for (Vec2d existing : result) {
                    if (solution.distance(existing) < tolerance) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (!isDuplicate) {
                    result.add(solution);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 牛顿法求解椭圆与圆形的交点
     */
    private Vec2d newtonMethodForIntersection(Vec2d startPoint, Vec2d circleCenter, double circleRadius, 
                                            int maxIterations, double tolerance) {
        Vec2d current = new Vec2d(startPoint.x, startPoint.y);
        
        for (int i = 0; i < maxIterations; i++) {
            // 计算目标函数：椭圆约束和圆形约束
            double ellipseConstraint = (current.x * current.x) / (radiusX * radiusX) + 
                                     (current.y * current.y) / (radiusY * radiusY) - 1.0;
            double circleConstraint = (current.x - circleCenter.x) * (current.x - circleCenter.x) + 
                                    (current.y - circleCenter.y) * (current.y - circleCenter.y) - circleRadius * circleRadius;
            
            // 检查收敛
            if (Math.abs(ellipseConstraint) < tolerance && Math.abs(circleConstraint) < tolerance) {
                return current;
            }
            
            // 计算雅可比矩阵
            double j11 = 2 * current.x / (radiusX * radiusX);
            double j12 = 2 * current.y / (radiusY * radiusY);
            double j21 = 2 * (current.x - circleCenter.x);
            double j22 = 2 * (current.y - circleCenter.y);
            
            // 求解线性系统 J * delta = -f
            double det = j11 * j22 - j12 * j21;
            if (Math.abs(det) < 1e-10) {
                break; // 奇异矩阵，无法继续
            }
            
            double deltaX = -(j22 * ellipseConstraint - j12 * circleConstraint) / det;
            double deltaY = -(-j21 * ellipseConstraint + j11 * circleConstraint) / det;
            
            current = new Vec2d(current.x + deltaX, current.y + deltaY);
        }
        
        return null; // 未收敛
    }
    
    @Override
    public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
        // 椭圆不支持延伸
        return new ArrayList<>();
    }
    
    @Override
    public List<Vec2d> getEndpoints() {
        // 椭圆没有端点
        return new ArrayList<>();
    }
    
    @Override
    public Vec2d getTangentAt(Vec2d point) {
        Vec2d local = toLocal(point);
        double angle = Math.atan2(local.y * radiusX, local.x * radiusY);
        Vec2d tangent = new Vec2d(
            -radiusX * Math.sin(angle),
            radiusY * Math.cos(angle)
        );
        return GeometryUtils.rotate(tangent, rotation).normalize();
    }
    
    @Override
    public double getSignedDistance(Vec2d point) {
        Vec2d local = toLocal(point);
        double x = local.x / radiusX;
        double y = local.y / radiusY;
        return Math.sqrt(x * x + y * y) - 1;
    }
    
    /**
     * 将椭圆分割为一系列椭圆弧
     * 这个方法使用以下策略来确保分割的精确性：
     * 1. 对于每个分割区间，保持原始椭圆的参数（半径和旋转角度）
     * 2. 仅调整起始和结束角度来定义分割段
     * 3. 使用椭圆参数方程确保分割点在原始椭圆上
     * 
     * @param points 分割点列表，这些点应该位于椭圆上或非常接近椭圆
     * @param pickPoint 用于确定分割方向的参考点
     * @return 分割后的椭圆弧列表
     */
    @Override
    public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
        List<Shape> result = new ArrayList<>();
        if (points.size() < 2) return result;
        
        // 将分割点转换为参数角度
        List<Double> angles = getDoubles(points);

        // 根据pickPoint确定分割方向
        Vec2d localPick = new Vec2d(
            pickPoint.x - center.x,
            pickPoint.y - center.y
        );
        double pickX = localPick.x * Math.cos(-rotation) - localPick.y * Math.sin(-rotation);
        double pickY = localPick.x * Math.sin(-rotation) + localPick.y * Math.cos(-rotation);
        double pickAngle = Math.atan2(pickY / radiusY, pickX / radiusX);
        if (pickAngle < 0) pickAngle += 2 * Math.PI;
        
        // 对角度进行排序
        List<Double> sortedAngles = new ArrayList<>(angles);
        Collections.sort(sortedAngles);
        
        // 找到包含pickPoint的分割段
        int startIndex = 0;
        for (int i = 0; i < sortedAngles.size(); i++) {
            double currentAngle = sortedAngles.get(i);
            double nextAngle = sortedAngles.get((i + 1) % sortedAngles.size());
            if (nextAngle < currentAngle) nextAngle += 2 * Math.PI;
            
            if (isAngleBetween(pickAngle, currentAngle, nextAngle)) {
                startIndex = i;
                break;
            }
        }
        
        // 创建椭圆弧段
        for (int i = 0; i < sortedAngles.size(); i++) {
            EllipticalArcShape arc = getEllipticalArcShape(startIndex, i, sortedAngles);
            // 复制样式和变换
            if (getStyle() != null) {
                arc.setStyle(getStyle().clone());
            }
            if (getTransform() != null) {
                arc.setTransform(getTransform().clone());
            }
            result.add(arc);
        }
        
        return result;
    }

    private @NotNull EllipticalArcShape getEllipticalArcShape(int startIndex, int i, List<Double> sortedAngles) {
        int currentIndex = (startIndex + i) % sortedAngles.size();
        int nextIndex = (startIndex + i + 1) % sortedAngles.size();

        double startAngle = sortedAngles.get(currentIndex);
        double endAngle = sortedAngles.get(nextIndex);

        // 处理跨越2π的情况
        if (endAngle < startAngle) endAngle += 2 * Math.PI;

        // 创建新的椭圆弧
        return new EllipticalArcShape(
            center,
            radiusX,
            radiusY,
            rotation,
            startAngle,
            endAngle
        );
    }

    private @NotNull List<Double> getDoubles(List<Vec2d> points) {
        List<Double> angles = new ArrayList<>();
        for (Vec2d point : points) {
            // 将点转换到椭圆的局部坐标系
            Vec2d local = new Vec2d(
                point.x - center.x,
                point.y - center.y
            );

            // 应用反向旋转
            double x = local.x * Math.cos(-rotation) - local.y * Math.sin(-rotation);
            double y = local.x * Math.sin(-rotation) + local.y * Math.cos(-rotation);

            // 计算参数角度
            double angle = Math.atan2(y / radiusY, x / radiusX);
            // 确保角度在[0, 2π]范围内
            if (angle < 0) angle += 2 * Math.PI;
            angles.add(angle);
        }
        return angles;
    }

    /**
     * 判断一个角度是否在给定的角度范围内
     * 考虑了角度的循环性质
     * 
     * @param angle 要检查的角度
     * @param start 范围的起始角度
     * @param end 范围的结束角度
     * @return 如果角度在范围内则返回true
     */
    private boolean isAngleBetween(double angle, double start, double end) {
        // 确保所有角度在[0, 2π]范围内
        angle = normalizeAngle(angle);
        start = normalizeAngle(start);
        end = normalizeAngle(end);
        
        if (start <= end) {
            return angle >= start && angle <= end;
        } else {
            // 处理跨越0/2π的情况
            return angle >= start || angle <= end;
        }
    }
    
    /**
     * 将角度标准化到[0, 2π]范围内
     * 
     * @param angle 要标准化的角度
     * @return 标准化后的角度
     */
    private double normalizeAngle(double angle) {
        angle = angle % (2 * Math.PI);
        if (angle < 0) angle += 2 * Math.PI;
        return angle;
    }

    @Override
    public Shape extend(Vec2d point, double distance) {
        // 椭圆不支持延伸
        return clone();
    }
    
    @Override
    public Shape extend(Vec2d point, Vec2d toPoint) {
        // 椭圆不支持延伸
        return clone();
    }
    
    @Override
    public Shape trimToPoint(Vec2d point) {
        // 椭圆不支持修剪
        return clone();
    }
    
    @Override
    public Shape createOffset(double distance) {
        // 创建一个更大或更小的椭圆（半轴均偏移 distance，确保为正）
        double newRx = radiusX + distance;
        double newRy = radiusY + distance;
        if (newRx <= 0) newRx = Math.max(0.1, newRx);
        if (newRy <= 0) newRy = Math.max(0.1, newRy);
        return new EllipseShape(center, newRx, newRy, rotation);
    }
    
    @Override
    public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
        List<Vec2d> result = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            LineShape line = new LineShape(points.get(i), points.get(i + 1));
            result.addAll(getIntersectionsWith(line));
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
        EllipseShape clone = new EllipseShape(
            new Vec2d(center.x, center.y),
            radiusX,
            radiusY,
            rotation
        );
        clone.setStyle(getStyle().clone());
        return clone;
    }
    
    @Override
    public String serialize() {
        return String.format("ELLIPSE %.2f,%.2f %.2f,%.2f %.2f",
            center.x, center.y, radiusX, radiusY, rotation);
    }
    
    @Override
    public void deserialize(String data) {
        String[] parts = data.split(" ");
        if (parts.length >= 4 && parts[0].equals("ELLIPSE")) {
            String[] c = parts[1].split(",");
            center = new Vec2d(
                Double.parseDouble(c[0]),
                Double.parseDouble(c[1])
            );
            String[] r = parts[2].split(",");
            radiusX = Double.parseDouble(r[0]);
            radiusY = Double.parseDouble(r[1]);
            rotation = Double.parseDouble(parts[3]);
        }
    }
    
    /**
     * 计算椭圆的最佳段数
     * 根据椭圆大小和变换比例动态调整段数，以平衡性能和视觉效果
     * @param transformedRadiusX 变换后的X半径
     * @param transformedRadiusY 变换后的Y半径
     * @return 建议的段数
     */
    private int calculateSegments(double transformedRadiusX, double transformedRadiusY) {
        // 基于椭圆周长的近似计算
        double avgRadius = Math.sqrt(transformedRadiusX * transformedRadiusY);
        // 最小16段，最大128段
        return Math.max(16, Math.min(128, (int)(Math.sqrt(avgRadius) * 4)));
    }
    
    @Override
    public void draw(DrawContext context) {
        // 调用带参数的draw方法，默认不显示辅助线
        draw(context, false);
    }
    
    /**
     * 绘制椭圆，可选择是否显示辅助线
     * @param context 绘图上下文
     * @param showAxes 是否显示辅助线（焦点、主轴、次轴）
     */
    public void draw(DrawContext context, boolean showAxes) {
        // 优先使用DrawContext中设置的样式（用于选中和高亮状态）
        ShapeStyle activeStyle = context.getCurrentStyle();
        if (activeStyle == null) {
            // 如果DrawContext没有设置样式，使用图形自己的样式
            activeStyle = (ShapeStyle) getStyle();
        }
        
        if (!activeStyle.getLineStyle().isVisible()) return;
        
        // 动态计算椭圆细分段数
        int segments = calculateSegments(radiusX, radiusY);
        List<Vec2d> points = new ArrayList<>();
        
        // 生成椭圆上的点，正确考虑 rotation
        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            // 先获得带旋转的世界坐标点
            Vec2d point = getPointAtAngle(angle);
            // 应用全局变换（如有）
            points.add(getTransform().transform(point));
        }
        
        // 使用LineStyle绘制线段
        if (activeStyle.getLineStyle() instanceof LineStyle lineStyle) {

            // 绘制线段
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                context.drawLine(p1, p2, lineStyle);
            }
            
            // 连接最后一点和第一点，闭合椭圆
            if (points.size() > 1) {
                context.drawLine(points.getLast(), points.getFirst(), lineStyle);
            }
        } else {
            // 如果没有LineStyle，使用默认颜色绘制
            Color color = new Color(activeStyle.getLineStyle().getColor().getRGB());
            
            // 绘制线段
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d p1 = points.get(i);
                Vec2d p2 = points.get(i + 1);
                context.drawLine(p1, p2, color);
            }
            
            // 连接最后一点和第一点，闭合椭圆
            if (points.size() > 1) {
                context.drawLine(points.getLast(), points.getFirst(), color);
            }
        }
        
        // 如果有填充样式，绘制填充
        if (activeStyle.getFillStyle() != null && activeStyle.getFillStyle().isVisible()) {
            Color fillColor = activeStyle.getFillStyle().getColor();
            // 使用 DrawContext 的标准填充方法
            // 注意：这里需要根据实际的 DrawContext API 调整
            // 暂时使用简单的多边形填充实现
            fillPolygonSimple(context, points, fillColor);
        }
        
        // 绘制辅助线
        if (showAxes) {
            // 创建辅助线样式
            LineStyle dashedStyle = new LineStyle(LineStyle.LineType.DASHED, 1.0f);
            dashedStyle.setColor(new Color(100, 100, 255, 180)); // 半透明蓝色
            
            LineStyle solidStyle = new LineStyle(LineStyle.LineType.SOLID, 1.0f);
            solidStyle.setColor(new Color(255, 100, 100, 180)); // 半透明红色
            
            // 应用变换到焦点和轴端点
            List<Vec2d> foci = getFoci();
            Vec2d focus1 = getTransform().transform(foci.get(0));
            Vec2d focus2 = getTransform().transform(foci.get(1));
            
            // 绘制焦点连线（虚线）
            context.drawLine(focus1, focus2, dashedStyle);
            
            // 绘制主轴（实线）
            boolean isXMajor = radiusX >= radiusY;
            if (isXMajor) {
                // X轴是主轴
                Vec2d rightPoint = getTransform().transform(getPointAtAngle(0));
                Vec2d leftPoint = getTransform().transform(getPointAtAngle(Math.PI));
                context.drawLine(rightPoint, leftPoint, solidStyle);
                
                // 次轴（虚线）
                Vec2d topPoint = getTransform().transform(getPointAtAngle(Math.PI / 2));
                Vec2d bottomPoint = getTransform().transform(getPointAtAngle(3 * Math.PI / 2));
                context.drawLine(topPoint, bottomPoint, dashedStyle);
            } else {
                // Y轴是主轴
                Vec2d topPoint = getTransform().transform(getPointAtAngle(Math.PI / 2));
                Vec2d bottomPoint = getTransform().transform(getPointAtAngle(3 * Math.PI / 2));
                context.drawLine(topPoint, bottomPoint, solidStyle);
                
                // 次轴（虚线）
                Vec2d rightPoint = getTransform().transform(getPointAtAngle(0));
                Vec2d leftPoint = getTransform().transform(getPointAtAngle(Math.PI));
                context.drawLine(rightPoint, leftPoint, dashedStyle);
            }
            
            // 绘制焦点标记
            float focusSize = 3.0f;
            context.drawCircleOutline(focus1, focusSize, dashedStyle.getColor());
            context.drawCircleOutline(focus2, focusSize, dashedStyle.getColor());
        }
    }
    
    /**
     * 简单的多边形填充实现
     * 这是一个临时实现，理想情况下应该使用 DrawContext 的标准方法
     */
    private void fillPolygonSimple(DrawContext context, List<Vec2d> points, Color color) {
        if (points.size() < 3) return;
        
        // 找到多边形的边界
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Vec2d point : points) {
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        
        // 对每一行进行扫描线填充
        for (int y = (int)minY; y <= (int)maxY; y++) {
            List<Double> intersections = getDoubles(points, y);

            // 对交点进行排序
            intersections.sort(Double::compareTo);
            
            // 在交点之间填充
            for (int i = 0; i < intersections.size() - 1; i += 2) {
                int x1 = intersections.get(i).intValue();
                int x2 = intersections.get(i + 1).intValue();
                context.fill(x1, y, x2, y, color.getRGB());
            }
        }
    }

    private static @NotNull List<Double> getDoubles(List<Vec2d> points, int y) {
        List<Double> intersections = new ArrayList<>();

        // 计算扫描线与多边形边的交点
        for (int i = 0; i < points.size(); i++) {
            Vec2d p1 = points.get(i);
            Vec2d p2 = points.get((i + 1) % points.size());

            if ((p1.y > y && p2.y <= y) || (p2.y > y && p1.y <= y)) {
                if (Math.abs(p2.y - p1.y) > 1e-10) { // 避免除零
                    double x = p1.x + (y - p1.y) * (p2.x - p1.x) / (p2.y - p1.y);
                    intersections.add(x);
                }
            }
        }
        return intersections;
    }


    @Override
    public boolean contains(Vec2d point) {
        // 将点转换到椭圆局部坐标系
        Vec2d local = toLocal(point);
        double x = local.x / radiusX;
        double y = local.y / radiusY;

        // 类型安全的样式处理
        boolean hasFill = false;
        double lineWidth = 1.0;
        if (getStyle() instanceof ShapeStyle shapeStyle) {
            hasFill = shapeStyle.getFillStyle() != null && shapeStyle.getFillStyle().isVisible();
            if (shapeStyle.getLineStyle() != null) {
                lineWidth = shapeStyle.getLineStyle().getWidth();
            }
        }

        // 如果是填充的椭圆，检查点是否在椭圆内
        if (hasFill) {
            return x * x + y * y <= 1;
        }

        // 否则检查点是否在椭圆轮廓上（考虑线宽）
        double distance = Math.abs(x * x + y * y - 1);
        return distance <= lineWidth / (2 * Math.min(radiusX, radiusY));
    }
    
    @Override
    public List<Vec2d> getPoints() {
        List<Vec2d> points = new ArrayList<>();
        
        // 动态计算椭圆细分段数
        int segments = calculateSegments(radiusX, radiusY);
        
        // 生成椭圆上的点
        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            points.add(getPointAtAngle(angle));
        }
        
        return points;
    }

    /**
     * 计算椭圆的焦点坐标
     * 椭圆有两个焦点，它们位于椭圆的长轴上
     * @return 包含两个焦点坐标的列表
     */
    public List<Vec2d> getFoci() {
        // 计算焦距c，c² = |a² - b²|，其中a是长半轴，b是短半轴
        double majorRadius = Math.max(radiusX, radiusY);
        double minorRadius = Math.min(radiusX, radiusY);
        double c = Math.sqrt(Math.abs(majorRadius * majorRadius - minorRadius * minorRadius));
        
        // 确定焦点方向
        double focusAngle = rotation;
        if (radiusY > radiusX) {
            // 如果Y轴半径大于X轴半径，焦点在垂直方向
            focusAngle += Math.PI / 2;
        }
        
        // 计算两个焦点的坐标
        Vec2d focus1 = new Vec2d(
            center.x + c * Math.cos(focusAngle), 
            center.y + c * Math.sin(focusAngle)
        );
        Vec2d focus2 = new Vec2d(
            center.x - c * Math.cos(focusAngle), 
            center.y - c * Math.sin(focusAngle)
        );
        
        return List.of(focus1, focus2);
    }
    
    /**
     * 判断点是否在椭圆内部
     * @param point 要检测的点
     * @return 如果点在椭圆内部则返回true，否则返回false
     */
    public boolean isPointInside(Vec2d point) {
        // 将点转换到椭圆的局部坐标系
        Vec2d localPoint = new Vec2d(
            point.x - center.x,
            point.y - center.y
        );
        
        // 应用反向旋转变换
        double rotatedX = localPoint.x * Math.cos(-rotation) - localPoint.y * Math.sin(-rotation);
        double rotatedY = localPoint.x * Math.sin(-rotation) + localPoint.y * Math.cos(-rotation);
        
        // 使用椭圆方程判断点是否在椭圆内部
        // (x/a)² + (y/b)² <= 1
        double normalizedX = rotatedX / radiusX;
        double normalizedY = rotatedY / radiusY;
        
        return (normalizedX * normalizedX + normalizedY * normalizedY) <= 1.0;
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
            if (firstBreakPoint == null) return newShapes;
            // 计算参数角度（将世界坐标点映射到椭圆参数角）
            java.util.function.Function<Vec2d, Double> toParamAngle = (pt) -> {
                Vec2d worldLocal = (getTransform() != null) ? getTransform().inverseTransform(pt) : pt;
                Vec2d local = new Vec2d(worldLocal.x - center.x, worldLocal.y - center.y);
                double x = local.x * Math.cos(-rotation) - local.y * Math.sin(-rotation);
                double y = local.x * Math.sin(-rotation) + local.y * Math.cos(-rotation);
                double ang = Math.atan2(y / radiusY, x / radiusX);
                if (ang < 0) ang += 2 * Math.PI;
                return ang;
            };

            if ("SINGLE_POINT".equals(breakMode)) {
                // 单点断开但不删除：分成 [0->a] 与 [a->2π]，避免整圈重复
                double a = toParamAngle.apply(firstBreakPoint);
                EllipticalArcShape arc1 = new EllipticalArcShape(new Vec2d(center.x, center.y), radiusX, radiusY, rotation, 0.0, a);
                EllipticalArcShape arc2 = new EllipticalArcShape(new Vec2d(center.x, center.y), radiusX, radiusY, rotation, a, 2 * Math.PI);
                if (getStyle() != null) { arc1.setStyle(getStyle().clone()); arc2.setStyle(getStyle().clone()); }
                if (getTransform() != null) { arc1.setTransform(getTransform().clone()); arc2.setTransform(getTransform().clone()); }
                newShapes.add(arc1);
                newShapes.add(arc2);
            } else if ("TWO_POINT".equals(breakMode) && secondBreakPoint != null) {
                // 修复：椭圆两点打断 - 正确处理用户点击顺序
                double a1 = toParamAngle.apply(firstBreakPoint);
                double a2 = toParamAngle.apply(secondBreakPoint);
                
                // 计算两种可能的弧长，移除较短的一段
                double arcLength1, arcLength2;

                // 从a2到a1的弧长
                if (a2 >= a1) {
                    arcLength1 = a2 - a1;  // 从a1到a2的弧长
                } else {
                    arcLength1 = (2 * Math.PI - a1) + a2;  // 从a1到a2的弧长（跨越0度）
                }
                arcLength2 = 2 * Math.PI - arcLength1;  // 从a2到a1的弧长

                // 移除较短的弧段，保留较长的弧段
                if (arcLength1 <= arcLength2) {
                    // 移除从a1到a2的弧段，保留从a2到a1的弧段
                    double endAngle = a1 + 2 * Math.PI;
                    EllipticalArcShape remain = new EllipticalArcShape(new Vec2d(center.x, center.y), radiusX, radiusY, rotation, a2, endAngle);
                    if (getStyle() != null) remain.setStyle(getStyle().clone());
                    if (getTransform() != null) remain.setTransform(getTransform().clone());
                    newShapes.add(remain);
                } else {
                    // 移除从a2到a1的弧段，保留从a1到a2的弧段
                    double endAngle = a2;
                    if (endAngle <= a1) endAngle += 2 * Math.PI;
                    EllipticalArcShape remain = new EllipticalArcShape(new Vec2d(center.x, center.y), radiusX, radiusY, rotation, a1, endAngle);
                    if (getStyle() != null) remain.setStyle(getStyle().clone());
                    if (getTransform() != null) remain.setTransform(getTransform().clone());
                    newShapes.add(remain);
                }
            }
        } catch (Exception e) {
            LOGGER.error("打断椭圆时发生错误", e);
        }
        return newShapes;
    }
    
    @Override
    public double getDistanceToPoint(Vec2d point) {
        return GeometryUtils.getDistanceToEllipse(center, radiusX, radiusY, point);
    }
    
    @Override
    protected void drawImGui(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        try {
            // 获取变换后的椭圆属性
            Vec2d transformedCenter = getTransform().transform(center);
            double transformedRadiusX = radiusX * getTransform().getScale().x;
            double transformedRadiusY = radiusY * getTransform().getScale().y;
            double transformedRotation = rotation;
            
            // 转换到屏幕坐标（用于后续计算）
            // Vec2d screenCenter = camera.worldToScreen(transformedCenter);
            
            // 根据选中状态和高亮状态确定颜色和线宽
            int lineColor;
            float lineWidth;
            int fillColor;
            
            if (isSelected()) {
                // 选中状态：亮黄色，加粗
                lineColor = 0xFFFFD700; // 亮黄色
                lineWidth = 2.5f;
                fillColor = 0x50FFD700; // 半透明亮黄色
            } else if (isHighlighted()) {
                // 高亮状态：橙色
                lineColor = 0xFFFF8C00; // 橙色
                lineWidth = 2.0f;
                fillColor = 0x40FF8C00; // 半透明橙色
            } else {
                // 正常状态：使用图形自身样式
                if (getStyle() != null && getStyle().getLineStyle() != null) {
                    java.awt.Color styleColor = getStyle().getLineStyle().getColor();
                    lineColor = styleColor.getRGB();
                    lineWidth = getStyle().getLineStyle().getWidth();
                } else {
                    lineColor = 0xFFFFFFFF; // 默认白色
                    lineWidth = 1.0f;
                }
                fillColor = 0x80FFFFFF; // 默认半透明白色
            }
            
            // 检查是否为填充椭圆
            boolean isFilled = getStyle() != null && 
                             getStyle().getFillStyle() != null && 
                             getStyle().getFillStyle().isVisible();
            
            // 对于椭圆，我们使用多边形近似绘制
            // 生成椭圆上的点
            int segments = 36; // 36段近似椭圆
            List<Vec2d> ellipsePoints = new ArrayList<>();
            
            for (int i = 0; i < segments; i++) {
                double angle = 2 * Math.PI * i / segments;
                double x = transformedRadiusX * Math.cos(angle);
                double y = transformedRadiusY * Math.sin(angle);
                
                // 应用旋转
                double cos = Math.cos(transformedRotation);
                double sin = Math.sin(transformedRotation);
                double rotatedX = x * cos - y * sin;
                double rotatedY = x * sin + y * cos;
                
                Vec2d worldPoint = new Vec2d(transformedCenter.x + rotatedX, transformedCenter.y + rotatedY);
                Vec2d screenPoint = camera.worldToScreen(worldPoint);
                ellipsePoints.add(screenPoint);
            }
            
            if (isFilled && ellipsePoints.size() >= 3) {
                // 填充椭圆 - 使用三角形扇形填充
                Vec2d center = ellipsePoints.getFirst();
                for (int i = 1; i < ellipsePoints.size() - 1; i++) {
                    Vec2d p1 = ellipsePoints.get(i);
                    Vec2d p2 = ellipsePoints.get(i + 1);
                    
                    drawList.addTriangleFilled(
                        (float) center.x, (float) center.y,
                        (float) p1.x, (float) p1.y,
                        (float) p2.x, (float) p2.y,
                        fillColor
                    );
                }
            }
            
            // 绘制椭圆轮廓
            for (int i = 0; i < ellipsePoints.size(); i++) {
                Vec2d p1 = ellipsePoints.get(i);
                Vec2d p2 = ellipsePoints.get((i + 1) % ellipsePoints.size());
                drawList.addLine(
                    (float) p1.x, (float) p1.y,
                    (float) p2.x, (float) p2.y,
                    lineColor, lineWidth
                );
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常
            LOGGER.error("渲染椭圆ImGui时发生错误", e);
        }
    }
}