package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.command.commands.CopyMirrorCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.SpiralShape;
import com.plot.core.geometry.shapes.SineCurveShape;
import com.plot.core.geometry.shapes.CableShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.AffineTransform;
import com.plot.ui.tools.impl.modify.strategy.MirrorMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 镜像操作处理器
 * 
 * <p>专门处理图形镜像操作的逻辑，包括：</p>
 * <ul>
 *   <li>镜像轴定义和计算</li>
 *   <li>点关于直线的镜像计算</li>
 *   <li>复制镜像和原地镜像</li>
 *   <li>正交镜像约束</li>
 *   <li>预览图形生成</li>
 *   <li>镜像命令创建</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.1 - 修复异常处理和参数验证
 */
public class MirrorHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MirrorHandler.class);
    
    // 可配置的阈值常量
    private static final double MIN_AXIS_LENGTH = 0.001;
    private static final double ZERO_TOLERANCE = 0.001;
    
    private final AppState appState;
    
    /**
     * 构造函数
     * @param appState 应用状态管理器
     */
    public MirrorHandler(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.MIRROR;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 检查图形列表
        if (shapes == null || shapes.isEmpty()) {
            return ValidationResult.invalid("没有选择要镜像的图形");
        }
        
        MirrorMode mode = getMirrorMode(parameters);
        
        // 检查必需参数
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            ValidationResult paramValidation;
            if (mode == MirrorMode.CENTRAL_SYMMETRY) {
                paramValidation = concreteParams.validateRequired(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START);
            } else {
                paramValidation = concreteParams.validateRequired(
                    com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START,
                    com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END
                );
            }
            if (!paramValidation.isValid()) {
                return paramValidation;
            }
        }
        
        // 检查镜像轴/中心
        Vec2d axisStart = null;
        Vec2d axisEnd = null;
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            axisStart = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START);
            axisEnd = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END);
        }
        
        if (axisStart == null) {
            return ValidationResult.invalid("镜像参数无效：缺少轴起点/中心点");
        }
        
        if (mode != MirrorMode.CENTRAL_SYMMETRY) {
            if (axisEnd == null) {
                return ValidationResult.invalid("镜像轴终点无效");
            }
            // 检查镜像轴长度
            if (axisStart.distance(axisEnd) < MIN_AXIS_LENGTH) {
                return ValidationResult.invalid("镜像轴长度太短");
            }
        }
        
        return ValidationResult.valid();
    }
    
    private MirrorMode getMirrorMode(IModifyHandler.ModifyParameters parameters) {
        String mirrorMode = MirrorMode.AXIS_SYMMETRY.name(); // 默认：轴对称
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            mirrorMode = concreteParams.getString("mirrorMode", mirrorMode);
        }
        try {
            return MirrorMode.valueOf(mirrorMode);
        } catch (Exception e) {
            return MirrorMode.AXIS_SYMMETRY;
        }
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 首先验证参数
        ValidationResult validation = validateModification(shapes, parameters);
        if (!validation.isValid()) {
            LOGGER.warn("镜像参数无效: {}", validation.getErrorMessage());
            return new ArrayList<>(shapes); // 返回原图形
        }
        
        Vec2d axisStart = null;
        Vec2d axisEnd = null;
        MirrorMode mode = getMirrorMode(parameters);
        
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            axisStart = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START);
            axisEnd = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END);
        }
        
        List<Shape> modifiedShapes = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 计算镜像后的新图形（无论是否复制，modifiedShapes 都只包含“镜像结果”）
        for (Shape shape : shapes) {
            try {
                Shape mirroredShape = shape.clone();
                if (mode == MirrorMode.CENTRAL_SYMMETRY) {
                    centralSymmetryShape(mirroredShape, axisStart);
                } else {
                    mirrorShape(mirroredShape, axisStart, axisEnd);
                }
                modifiedShapes.add(mirroredShape);
            } catch (IllegalArgumentException e) {
                LOGGER.error("镜像参数无效: {}", e.getMessage(), e);
                errors.add("图形 " + shape.getId() + " 镜像失败: " + e.getMessage());
            } catch (Exception e) {
                LOGGER.error("镜像图形失败: {}", e.getMessage(), e);
                errors.add("图形 " + shape.getId() + " 镜像失败: " + e.getMessage());
            }
        }
        
        // 如果有错误，抛出异常通知调用者
        if (!errors.isEmpty()) {
            String errorMessage = "部分图形镜像失败: " + String.join("; ", errors);
            LOGGER.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        
        return modifiedShapes;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        try {
            List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
            
            // 预览样式应与原图形一致
            for (int i = 0; i < modifiedShapes.size(); i++) {
                Shape preview = modifiedShapes.get(i);
                Shape original = i < shapes.size() ? shapes.get(i) : null;
                if (original != null && original.getStyle() != null) {
                    try { preview.setStyle(original.getStyle().clone()); } catch (Exception e) { ExceptionDebug.log("MirrorHandler: clone style for preview", e); }
                }
                
                // 修复：清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
                preview.setSelected(false);
                preview.setHighlighted(false);
            }
            
            return modifiedShapes;
        } catch (RuntimeException e) {
            LOGGER.warn("创建预览图形失败: {}", e.getMessage());
            // 预览失败时返回空列表，不影响主操作
            return new ArrayList<>();
        }
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes, 
                                           IModifyHandler.ModifyParameters parameters) {
        boolean copyMode = false;
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            copyMode = concreteParams.getBoolean(com.plot.ui.tools.impl.modify.dto.ModifyParameters.COPY_MODE, false);
        }
        return copyMode
            ? new CopyMirrorCommand(originalShapes, modifiedShapes, appState)
            : new ModifyCommand(originalShapes, modifiedShapes, appState, "history.plot.op.mirror");
    }
    
    @Override
    public IModifyHandler.ModifyParameters applyConstraints(IModifyHandler.ModifyParameters parameters, 
                                                          IModifyHandler.ModifyConstraints constraints) {
        if (constraints == null || !(parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams)) {
            return parameters;
        }

        MirrorMode mode = getMirrorMode(parameters);
        if (mode == MirrorMode.CENTRAL_SYMMETRY) {
            // 中心对称不需要正交约束
            return parameters;
        }
        
        Vec2d axisStart = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START);
        Vec2d axisEnd = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END);
        
        if (axisStart == null || axisEnd == null) {
            return parameters;
        }
        
        // 克隆参数以避免修改原始参数
        com.plot.ui.tools.impl.modify.dto.ModifyParameters constrainedParameters = concreteParams.clone();
        
        // 检查是否启用正交约束
        if (constraints.isConstraintEnabled("orthogonalConstraint")) {
            Vec2d constrainedAxisEnd = applyOrthogonalConstraint(axisStart, axisEnd);
            constrainedParameters.setVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END, constrainedAxisEnd);
            LOGGER.debug("应用正交约束: 原始终点({}, {}) -> 约束终点({}, {})", 
                        axisEnd.x, axisEnd.y, constrainedAxisEnd.x, constrainedAxisEnd.y);
        }
        
        return constrainedParameters;
    }
    
    /**
     * 应用正交约束，将镜像轴限制为水平或垂直方向
     * @param axisStart 镜像轴起点
     * @param axisEnd 镜像轴终点
     * @return 约束后的镜像轴终点
     */
    private Vec2d applyOrthogonalConstraint(Vec2d axisStart, Vec2d axisEnd) {
        Vec2d axisVector = axisEnd.subtract(axisStart);
        double dx = axisVector.x;
        double dy = axisVector.y;
        
        // 判断是水平还是垂直方向
        if (Math.abs(dx) > Math.abs(dy)) {
            // 水平方向：保持y坐标不变
            return new Vec2d(axisEnd.x, axisStart.y);
        } else {
            // 垂直方向：保持x坐标不变
            return new Vec2d(axisStart.x, axisEnd.y);
        }
    }
    
    /**
     * 镜像图形
     */
    private void mirrorShape(Shape shape, Vec2d axisStart, Vec2d axisEnd) {
        // 验证镜像轴
        if (axisStart == null || axisEnd == null) {
            LOGGER.warn("镜像轴起点或终点为空，跳过镜像");
            return;
        }
        
        // 检查镜像轴长度
        double axisLength = axisStart.distance(axisEnd);
        if (axisLength < ZERO_TOLERANCE) {
            LOGGER.warn("镜像轴长度过短 ({}), 跳过镜像", axisLength);
            return;
        }
        
        // 根据图形类型进行专门的镜像处理
        switch (shape) {
            case RectangleShape rectangleShape -> mirrorRectangleShape(rectangleShape, axisStart, axisEnd);
            case CircleShape circleShape -> mirrorCircleShape(circleShape, axisStart, axisEnd);
            case EllipseShape ellipseShape -> mirrorEllipseShape(ellipseShape, axisStart, axisEnd);
            case LineShape lineShape -> mirrorLineShape(lineShape, axisStart, axisEnd);
            case ArcShape arcShape -> mirrorArcShape(arcShape, axisStart, axisEnd);
            case SpiralShape spiralShape -> mirrorSpiralShape(spiralShape, axisStart, axisEnd);
            case SineCurveShape sineCurveShape -> mirrorSineCurveShape(sineCurveShape, axisStart, axisEnd);
            case CableShape cableShape -> mirrorCableShape(cableShape, axisStart, axisEnd);
            case PolylineShape polylineShape -> mirrorByAffineTransform(polylineShape, axisStart, axisEnd);
            case BezierCurveShape bezierCurveShape -> mirrorByAffineTransform(bezierCurveShape, axisStart, axisEnd);
            case null, default ->
                // 关键修复：
                // 很多曲线类（如正弦/悬链线）在 getControlPoints() 内部已经应用了 getTransform()，
                // 而 setControlPoint() 期望的是“定义点”坐标。用“控制点写回”的方式会导致重复变换，
                // 从而出现“远离轴/看起来像旋转”的错误。
                // 因此默认改为：对定义数据应用一次严格的反射仿射变换。
                    mirrorByAffineTransform(shape, axisStart, axisEnd);
        }

        if (shape != null) {
            LOGGER.debug("图形 {} 镜像完成", shape.getId());
        }
    }
    
    /**
     * 轴对称：对定义数据应用一次严格的反射变换。
     * <p>使用和 Shape.mirror(...) 相同的数学构造，但作用于 Shape.transform(AffineTransform)，避免控制点坐标系错配。</p>
     */
    private void mirrorByAffineTransform(Shape shape, Vec2d axisStart, Vec2d axisEnd) {
        if (shape == null || axisStart == null || axisEnd == null) return;
        Vec2d v = axisEnd.subtract(axisStart);
        double angle = Math.atan2(v.y, v.x);
        
        // 关键：AffineTransform 的链式调用是“后置乘法”（this = this * other）。
        // 若希望点按顺序执行：
        //   1) Translate(-axisStart)
        //   2) Rotate(-angle)
        //   3) Scale(1, -1)
        //   4) Rotate(angle)
        //   5) Translate(axisStart)
        // 则最终矩阵应为：
        //   M = T(axisStart) * R(angle) * S(1,-1) * R(-angle) * T(-axisStart)
        // 因为矩阵作用在列向量上时，运算顺序是从右到左。
        AffineTransform t = new AffineTransform();
        t.translate(axisStart.x, axisStart.y)
         .rotate(angle)
         .scale(1.0, -1.0)
         .rotate(-angle)
         .translate(-axisStart.x, -axisStart.y);
        
        shape.transform(t);
    }
    
    /**
     * 镜像螺旋（SpiralShape）
     *
     * <p>螺旋由 center/rotation/clockwise 等参数生成。反射会翻转手性，因此除了中心点镜像外：</p>
     * <ul>
     *   <li>rotation 应满足：newRotation = 2*axisAngle - oldRotation</li>
     *   <li>clockwise 应翻转</li>
     * </ul>
     */
    private void mirrorSpiralShape(SpiralShape spiral, Vec2d axisStart, Vec2d axisEnd) {
        // 镜像中心点
        spiral.setCenter(mirrorPoint(spiral.getCenter(), axisStart, axisEnd));
        
        // 镜像旋转角：围绕轴角度反射
        Vec2d axisVector = axisEnd.subtract(axisStart);
        double axisAngle = Math.atan2(axisVector.y, axisVector.x);
        double newRotation = 2 * axisAngle - spiral.getRotation();
        while (newRotation < 0) newRotation += 2 * Math.PI;
        while (newRotation >= 2 * Math.PI) newRotation -= 2 * Math.PI;
        spiral.setRotation(newRotation);
        
        // 反射会翻转方向（手性）
        spiral.setClockwise(!spiral.isClockwise());
    }
    
    /**
     * 镜像正弦曲线（SineCurveShape）
     *
     * <p>正弦曲线的法线方向由基线方向的“逆时针垂直向量”定义。反射会翻转平面手性，
     * 因此仅镜像端点会导致法线方向不一致（曲线会跑到错误一侧）。
     * 解决：镜像端点 + 相位加 π（等价于 y 取反）。</p>
     */
    private void mirrorSineCurveShape(SineCurveShape sine, Vec2d axisStart, Vec2d axisEnd) {
        Vec2d newStart = mirrorPoint(sine.getStartPoint(), axisStart, axisEnd);
        Vec2d newEnd = mirrorPoint(sine.getEndPoint(), axisStart, axisEnd);
        sine.setStartPoint(newStart);
        sine.setEndPoint(newEnd);
        sine.setPhase(sine.getPhase() + Math.PI);
    }
    
    /**
     * 镜像悬链线/电缆线（CableShape）
     *
     * <p>CableShape 内部以基线的“逆时针法线”计算弧垂方向，反射会翻转手性。
     * 因此：镜像点数据 + 翻转 sagDirection，才能保证严格轴对称。</p>
     */
    private void mirrorCableShape(CableShape cable, Vec2d axisStart, Vec2d axisEnd) {
        // 先使用其自身的点镜像（包含 start/end/sagPoint/pathPoints 等）
        cable.mirror(axisStart, axisEnd);
        // 再翻转弧垂方向，补齐手性翻转
        cable.setSagDirection(-cable.getSagDirection());
    }
    
    /**
     * 中心对称：等价于绕中心点旋转180°
     */
    private void centralSymmetryShape(Shape shape, Vec2d center) {
        if (center == null) {
            LOGGER.warn("中心点为空，跳过中心对称");
            return;
        }
        // 形状类普遍实现了 rotate(angle, center)
        shape.rotate(Math.PI, center);
    }
    
    /**
     * 镜像矩形
     */
    private void mirrorRectangleShape(RectangleShape rectangle, Vec2d axisStart, Vec2d axisEnd) {
        // 矩形的几何定义：corner 为局部原点，width/height 为局部轴长度，rotation 表示局部轴旋转
        Vec2d originalCorner = rectangle.getCorner();
        double width = rectangle.getWidth();
        double height = rectangle.getHeight();
        double rotation = rectangle.getRotation();

        // 计算局部轴向量（世界坐标）
        Vec2d u = new Vec2d(width, 0).rotate(rotation);   // 宽方向
        Vec2d v = new Vec2d(0, height).rotate(rotation);  // 高方向

        // 镜像 corner 与两条局部轴的端点，以得到镜像后的轴向量
        Vec2d p0 = mirrorPoint(originalCorner, axisStart, axisEnd);
        Vec2d p1 = mirrorPoint(originalCorner.add(u), axisStart, axisEnd);
        Vec2d p3 = mirrorPoint(originalCorner.add(v), axisStart, axisEnd);

        Vec2d u2 = p1.subtract(p0);
        Vec2d v2 = p3.subtract(p0);

        // 新旋转取 u2 的方向
        double newRotation = Math.atan2(u2.y, u2.x);
        Vec2d vExpected = new Vec2d(0, height).rotate(newRotation);

        // 如果 v2 与预期的“+y”方向相反，说明发生了手性翻转：换用相邻角点作为 corner，并旋转+π
        Vec2d newCorner = p0;
        if (v2.dot(vExpected) < 0) {
            newCorner = p1; // 以宽方向相邻点作为新的 corner
            newRotation += Math.PI;
        }

        // 规范化角度到 [0, 2π) 范围
        while (newRotation < 0) newRotation += 2 * Math.PI;
        while (newRotation >= 2 * Math.PI) newRotation -= 2 * Math.PI;

        rectangle.setCorner(newCorner);
        rectangle.setWidth(width);
        rectangle.setHeight(height);
        rectangle.setRotation(newRotation);
    }
    
    /**
     * 镜像圆形
     */
    private void mirrorCircleShape(CircleShape circle, Vec2d axisStart, Vec2d axisEnd) {
        // 镜像圆心
        Vec2d mirroredCenter = mirrorPoint(circle.getCenter(), axisStart, axisEnd);
        circle.setCenter(mirroredCenter);
        // 半径保持不变
    }
    
    /**
     * 镜像椭圆
     */
    private void mirrorEllipseShape(EllipseShape ellipse, Vec2d axisStart, Vec2d axisEnd) {
        // 镜像椭圆中心
        Vec2d mirroredCenter = mirrorPoint(ellipse.getCenter(), axisStart, axisEnd);
        ellipse.setCenter(mirroredCenter);
        
        // 计算镜像轴的角度
        Vec2d axisVector = axisEnd.subtract(axisStart);
        double axisAngle = Math.atan2(axisVector.y, axisVector.x);
        
        // 镜像椭圆的旋转角度
        double currentRotation = ellipse.getRotation();
        double newRotation = 2 * axisAngle - currentRotation;
        
        // 规范化角度到 [0, 2π) 范围
        while (newRotation < 0) {
            newRotation += 2 * Math.PI;
        }
        while (newRotation >= 2 * Math.PI) {
            newRotation -= 2 * Math.PI;
        }
        
        ellipse.setRotation(newRotation);
    }
    
    /**
     * 镜像直线
     */
    private void mirrorLineShape(LineShape line, Vec2d axisStart, Vec2d axisEnd) {
        // 镜像起点和终点
        Vec2d mirroredStart = mirrorPoint(line.getStart(), axisStart, axisEnd);
        Vec2d mirroredEnd = mirrorPoint(line.getEnd(), axisStart, axisEnd);
        
        line.setStart(mirroredStart);
        line.setEnd(mirroredEnd);
    }
    
    /**
     * 镜像圆弧
     */
    private void mirrorArcShape(ArcShape arc, Vec2d axisStart, Vec2d axisEnd) {
        // 镜像圆心
        Vec2d mirroredCenter = mirrorPoint(arc.getCenter(), axisStart, axisEnd);
        arc.setCenter(mirroredCenter);
        
        // 计算镜像轴的角度
        Vec2d axisVector = axisEnd.subtract(axisStart);
        double axisAngle = Math.atan2(axisVector.y, axisVector.x);
        
        // 镜像起始角度和结束角度
        double currentStartAngle = arc.getStartAngle();
        double currentEndAngle = arc.getEndAngle();
        
        double newStartAngle = 2 * axisAngle - currentEndAngle;
        double newEndAngle = 2 * axisAngle - currentStartAngle;
        
        // 规范化角度
        while (newStartAngle < 0) newStartAngle += 2 * Math.PI;
        while (newEndAngle < 0) newEndAngle += 2 * Math.PI;
        while (newStartAngle >= 2 * Math.PI) newStartAngle -= 2 * Math.PI;
        while (newEndAngle >= 2 * Math.PI) newEndAngle -= 2 * Math.PI;
        
        arc.setStartAngle(newStartAngle);
        arc.setEndAngle(newEndAngle);
    }

    /**
     * 计算点关于直线的镜像点
     * @param point 原始点
     * @param lineStart 直线起点
     * @param lineEnd 直线终点
     * @return 镜像点
     */
    public Vec2d mirrorPoint(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        // 计算直线方向向量
        Vec2d lineVector = lineEnd.subtract(lineStart);
        double lineLength = lineVector.length();
        
        if (lineLength < ZERO_TOLERANCE) {
            return point; // 直线长度为0，返回原点
        }
        
        // 单位方向向量
        Vec2d unitVector = lineVector.divide(lineLength);
        
        // 计算点到直线起点的向量
        Vec2d pointVector = point.subtract(lineStart);
        
        // 计算点在直线上的投影
        double projection = pointVector.dot(unitVector);
        Vec2d projectionPoint = lineStart.add(unitVector.multiply(projection));
        
        // 计算镜像点
        Vec2d mirrorVector = projectionPoint.subtract(point);
        return point.add(mirrorVector.multiply(2));
    }

    /**
     * 检查镜像轴是否为水平或垂直
     */
    public boolean isOrthogonalAxis(Vec2d axisStart, Vec2d axisEnd) {
        Vec2d axisVector = axisEnd.subtract(axisStart);
        double angle = Math.atan2(axisVector.y, axisVector.x);
        
        // 检查是否接近0°, 90°, 180°, 270°
        double[] orthogonalAngles = {0, Math.PI/2, Math.PI, 3*Math.PI/2};
        double tolerance = Math.PI / 36; // 5度容差
        
        for (double orthogonalAngle : orthogonalAngles) {
            if (Math.abs(angle - orthogonalAngle) < tolerance) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 获取镜像操作的状态消息
     */
    public String getStatusMessage(IModifyHandler.ModifyParameters parameters) {
        Vec2d axisStart = null;
        Vec2d axisEnd = null;
        boolean copyMode = false;
        MirrorMode mode = getMirrorMode(parameters);

        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            axisStart = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START);
            axisEnd = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END);
            copyMode = concreteParams.getBoolean(com.plot.ui.tools.impl.modify.dto.ModifyParameters.COPY_MODE, false);
        }

        String copyText = copyMode ? PlotI18n.status("status.plot.common.copy_suffix") : "";
        if (mode == MirrorMode.CENTRAL_SYMMETRY) {
            if (axisStart != null) {
                return PlotI18n.status("status.plot.mirror.central_handler",
                        copyText, axisStart.x, axisStart.y);
            }
            return PlotI18n.status("status.plot.mirror.central_handler_simple", copyText);
        }

        // 轴对称
        if (axisStart != null && axisEnd != null) {
            double length = axisStart.distance(axisEnd);
            double angle = Math.toDegrees(Math.atan2(axisEnd.y - axisStart.y, axisEnd.x - axisStart.x));

            String constraintText = "";
            if (isOrthogonalAxis(axisStart, axisEnd)) {
                if (Math.abs(angle) < 45 || Math.abs(angle - 180) < 45) {
                    constraintText = PlotI18n.status("status.plot.mirror.constraint_horizontal");
                } else {
                    constraintText = PlotI18n.status("status.plot.mirror.constraint_vertical");
                }
            }

            return PlotI18n.status("status.plot.mirror.axis_handler",
                    copyText, length, angle, constraintText);
        }
        return PlotI18n.status("status.plot.mirror.axis_handler_simple", copyText);
    }

}
