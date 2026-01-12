package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.command.commands.CopyMirrorCommand;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.geometry.shapes.RectangleShape;
import com.masterplanner.core.geometry.shapes.CircleShape;
import com.masterplanner.core.geometry.shapes.EllipseShape;
import com.masterplanner.core.geometry.shapes.LineShape;
import com.masterplanner.core.geometry.shapes.ArcShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @author MasterPlanner Team
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
        
        // 检查必需参数
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            ValidationResult paramValidation = concreteParams.validateRequired(
                com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START,
                com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END
            );
            if (!paramValidation.isValid()) {
                return paramValidation;
            }
        }
        
        // 检查镜像轴
        Vec2d axisStart = null;
        Vec2d axisEnd = null;
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            axisStart = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START);
            axisEnd = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END);
        }
        
        if (axisStart == null || axisEnd == null) {
            return ValidationResult.invalid("镜像轴起点或终点无效");
        }
        
        // 检查镜像轴长度
        if (axisStart.distance(axisEnd) < MIN_AXIS_LENGTH) {
            return ValidationResult.invalid("镜像轴长度太短");
        }
        
        return ValidationResult.valid();
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
        boolean copyMode = false;
        
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            axisStart = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START);
            axisEnd = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END);
            copyMode = concreteParams.getBoolean("copyMode", false);
        }
        
        List<Shape> modifiedShapes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        // 修复：如果是复制模式，添加原始图形的克隆而不是引用
        if (copyMode) {
            for (Shape shape : shapes) {
                try {
                    Shape clonedShape = shape.clone();
                    modifiedShapes.add(clonedShape);
                } catch (Exception e) {
                    LOGGER.error("克隆图形失败: {}", e.getMessage(), e);
                    errors.add("图形 " + shape.getId() + " 克隆失败: " + e.getMessage());
                }
            }
        }
        
        // 添加镜像图形
        for (Shape shape : shapes) {
            try {
                Shape mirroredShape = shape.clone();
                mirrorShape(mirroredShape, axisStart, axisEnd);
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
                    try { preview.setStyle(original.getStyle().clone()); } catch (Exception ignore) {}
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
        // 检查镜像模式
        String mirrorMode = "MIRROR"; // 默认模式
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            String mode = concreteParams.getString("mirrorMode", "MIRROR");
            if (mode != null) {
                mirrorMode = mode;
            }
        }
        
        if ("COPY_MIRROR".equals(mirrorMode)) {
            // 复制镜像模式：只添加新图形，不删除原图形
            return new CopyMirrorCommand(originalShapes, modifiedShapes, appState);
        } else {
            // 正常镜像模式：替换原图形
            return new ModifyCommand(originalShapes, modifiedShapes, appState);
        }
    }
    
    @Override
    public IModifyHandler.ModifyParameters applyConstraints(IModifyHandler.ModifyParameters parameters, 
                                                          IModifyHandler.ModifyConstraints constraints) {
        if (constraints == null || !(parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams)) {
            return parameters;
        }

        Vec2d axisStart = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START);
        Vec2d axisEnd = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END);
        
        if (axisStart == null || axisEnd == null) {
            return parameters;
        }
        
        // 克隆参数以避免修改原始参数
        com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters constrainedParameters = concreteParams.clone();
        
        // 检查是否启用正交约束
        if (constraints.isConstraintEnabled("orthogonalConstraint")) {
            Vec2d constrainedAxisEnd = applyOrthogonalConstraint(axisStart, axisEnd);
            constrainedParameters.setVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END, constrainedAxisEnd);
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
            case null, default ->
                // 通用镜像处理：镜像所有控制点
                    mirrorShapeByControlPoints(shape, axisStart, axisEnd);
        }
        
        LOGGER.debug("图形 {} 镜像完成", shape.getId());
    }
    
    /**
     * 镜像矩形
     */
    private void mirrorRectangleShape(RectangleShape rectangle, Vec2d axisStart, Vec2d axisEnd) {
        // 获取矩形的原始属性
        Vec2d originalCorner = rectangle.getCorner();
        double originalWidth = rectangle.getWidth();
        double originalHeight = rectangle.getHeight();
        double originalRotation = rectangle.getRotation();
        
        // 计算矩形的中心点
        Vec2d center = new Vec2d(
            originalCorner.x + originalWidth / 2,
            originalCorner.y + originalHeight / 2
        );
        
        // 镜像中心点
        Vec2d mirroredCenter = mirrorPoint(center, axisStart, axisEnd);
        
        // 计算镜像轴的角度
        Vec2d axisVector = axisEnd.subtract(axisStart);
        double axisAngle = Math.atan2(axisVector.y, axisVector.x);
        
        // 镜像矩形的旋转角度
        double newRotation = 2 * axisAngle - originalRotation;
        
        // 规范化角度到 [0, 2π) 范围
        while (newRotation < 0) {
            newRotation += 2 * Math.PI;
        }
        while (newRotation >= 2 * Math.PI) {
            newRotation -= 2 * Math.PI;
        }
        
        // 计算新的左下角位置
        Vec2d newCorner = new Vec2d(
            mirroredCenter.x - originalWidth / 2,
            mirroredCenter.y - originalHeight / 2
        );
        
        // 更新矩形属性，保持原有的宽度和高度
        rectangle.setCorner(newCorner);
        rectangle.setWidth(originalWidth);
        rectangle.setHeight(originalHeight);
        rectangle.setRotation(newRotation);
        
        LOGGER.debug("矩形镜像: 中心点({}, {}) -> ({}, {}), 角点({}, {}) -> ({}, {}), 尺寸保持不变 {:.2f}x{:.2f}, 旋转角度 {:.2f} -> {:.2f}", 
                    center.x, center.y, mirroredCenter.x, mirroredCenter.y,
                    originalCorner.x, originalCorner.y, newCorner.x, newCorner.y,
                    originalWidth, originalHeight,
                    Math.toDegrees(originalRotation), Math.toDegrees(newRotation));
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
     * 通用镜像处理：通过控制点镜像
     */
    private void mirrorShapeByControlPoints(Shape shape, Vec2d axisStart, Vec2d axisEnd) {
        // 获取图形的所有控制点（顶点）
        List<Vec2d> controlPoints = shape.getControlPoints();
        if (controlPoints.isEmpty()) {
            LOGGER.warn("图形没有控制点，无法进行镜像变换");
            return;
        }
        
        // 对每个控制点进行镜像变换
        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d originalPoint = controlPoints.get(i);
            Vec2d mirroredPoint = mirrorPoint(originalPoint, axisStart, axisEnd);
            shape.setControlPoint(i, mirroredPoint);
        }
        
        // 计算镜像轴的角度
        Vec2d axisVector = axisEnd.subtract(axisStart);
        double axisAngle = Math.atan2(axisVector.y, axisVector.x);
        
        // 镜像旋转角度
        double currentRotation = shape.getRotation();
        double newRotation = 2 * axisAngle - currentRotation;
        
        // 规范化角度到 [0, 2π) 范围
        while (newRotation < 0) {
            newRotation += 2 * Math.PI;
        }
        while (newRotation >= 2 * Math.PI) {
            newRotation -= 2 * Math.PI;
        }
        
        // 设置新的旋转角度
        shape.setRotation(newRotation);
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
        
        if (parameters instanceof com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            axisStart = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_START);
            axisEnd = concreteParams.getVec2d(com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters.MIRROR_AXIS_END);
            copyMode = concreteParams.getBoolean("copyMode", false);
        }
        
        if (axisStart != null && axisEnd != null) {
            double length = axisStart.distance(axisEnd);
            double angle = Math.toDegrees(Math.atan2(axisEnd.y - axisStart.y, axisEnd.x - axisStart.x));
            
            String modeText = copyMode ? "复制镜像" : "镜像";
            String constraintText = "";
            
            // 检查是否为正交约束（水平或垂直）
            if (isOrthogonalAxis(axisStart, axisEnd)) {
                if (Math.abs(angle) < 45 || Math.abs(angle - 180) < 45) {
                    constraintText = " (水平镜像)";
                } else {
                    constraintText = " (垂直镜像)";
                }
            }
            
            return String.format("%s: 轴长=%.1f, 角度=%.1f°%s", modeText, length, angle, constraintText);
        } else {
            return copyMode ? "复制镜像模式" : "镜像模式";
        }
    }

}
