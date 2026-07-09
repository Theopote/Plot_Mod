package com.plot.core.command.commands;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.SpiralShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.utils.PlotI18n;
import com.plot.ui.tools.impl.modify.dto.TransformParams;
import com.plot.ui.tools.impl.modify.enums.TransformMode;
import com.plot.ui.tools.impl.modify.helper.BoundingBoxControlManager.ControlPointType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 变换命令 - 通用变换操作
 * 支持平移、缩放、旋转等变换操作
 * 
 * <p>参考专业软件的设计：</p>
 * <ul>
 *   <li>支持多种变换模式（自由、水平、垂直、等比、旋转）</li>
 *   <li>支持中心缩放和锚点变换</li>
 *   <li>精确的几何变换计算</li>
 *   <li>保持图形质量</li>
 *   <li>支持撤销/重做</li>
 * </ul>
 */
public class TransformCommand extends ModifyCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformCommand.class);

    private final List<Shape> originalShapes;
    private final TransformParams transformParams;
    
    // 保存原始状态用于撤销
    private List<Shape> originalShapeCopies;
    private List<Shape> transformedShapes;
    
    public TransformCommand(List<Shape> originalShapes, TransformParams transformParams, AppState appState) {
        super(originalShapes, new ArrayList<>(), appState, "history.plot.op.transform");
        this.originalShapes = Objects.requireNonNull(originalShapes, "原始图形列表不能为空");
        this.transformParams = Objects.requireNonNull(transformParams, "变换参数不能为空");
        this.originalShapeCopies = new ArrayList<>();
        this.transformedShapes = new ArrayList<>();
    }
    
    @Override
    public void execute() {
        try {
            LOGGER.debug("执行变换命令: 模式={}, 中心缩放={}, 旋转模式={}", 
                transformParams.getMode(), transformParams.isCenterScale(), transformParams.isRotationMode());
            
            // 保存原始状态
            saveOriginalState();
            
            // 应用变换
            applyTransform();
            
            // 调用父类的execute方法来实际替换图形
            super.execute();
            
            LOGGER.info("变换命令执行成功，变换了 {} 个图形", originalShapes.size());
            
        } catch (Exception e) {
            LOGGER.error("执行变换命令失败: {}", e.getMessage(), e);
            throw new RuntimeException("变换命令执行失败", e);
        }
    }
    
    @Override
    public void undo() {
        try {
            LOGGER.debug("撤销变换命令");
            
            // 调用父类的undo方法来恢复原始图形
            super.undo();
            
            LOGGER.info("变换命令撤销成功");
            
        } catch (Exception e) {
            LOGGER.error("撤销变换命令失败: {}", e.getMessage(), e);
            throw new RuntimeException("变换命令撤销失败", e);
        }
    }
    
    @Override
    public void redo() {
        execute();
    }
    
    /**
     * 保存原始状态
     */
    private void saveOriginalState() {
        originalShapeCopies.clear();
        for (Shape shape : originalShapes) {
            if (shape != null) {
                originalShapeCopies.add(shape.clone());
            }
        }
    }
    
    /**
     * 应用变换
     */
    private void applyTransform() {
        transformedShapes.clear();

        BoundingBox selectionBounds = calculateCombinedBounds(originalShapes);

        for (Shape originalShape : originalShapes) {
            if (originalShape == null) {
                continue;
            }

            Shape transformedShape = (originalShapes.size() > 1)
                    ? transformShapeAsGroup(originalShape, transformParams, selectionBounds)
                    : transformShape(originalShape, transformParams);
            transformedShapes.add(transformedShape);
        }
        
        // 设置变换后的图形为新的图形列表
        setTargetShapes(transformedShapes);
    }

    /**
     * 多选时按统一组参数变换（共享同一锚点/中心与缩放系数），确保被选图形作为整体变换。
     */
    private Shape transformShapeAsGroup(Shape shape, TransformParams params, BoundingBox selectionBounds) {
        if (params == null || selectionBounds == null) {
            return transformShape(shape, params);
        }

        Shape workingShape = shape.clone();

        if (shape.getTransform() != null) {
            workingShape.setTransform(shape.getTransform().clone());
        }
        if (shape.getStyle() != null) {
            workingShape.setStyle(shape.getStyle().clone());
        }
        workingShape.setVisible(true);

        Vec2d dragVector = params.getDragVector();
        if (dragVector == null) {
            return workingShape;
        }

        ControlPointType controlPointType = params.getControlPointType();
        TransformMode mode = params.getMode();

        if (mode == TransformMode.ROTATION) {
            double angle = params.getRotationAngle();
            Vec2d center = params.getAnchorPoint() != null ? params.getAnchorPoint() : selectionBounds.getCenter();
            workingShape.rotate(angle, center);
            return workingShape;
        }

        if (controlPointType == null) {
            if (mode == TransformMode.HORIZONTAL) {
                workingShape.translate(new Vec2d(dragVector.x, 0.0));
            } else if (mode == TransformMode.VERTICAL) {
                workingShape.translate(new Vec2d(0.0, dragVector.y));
            } else {
                workingShape.translate(dragVector);
            }
            return workingShape;
        }

        Vec2d scaleFactors;
        if (controlPointType == ControlPointType.TOP_CENTER || controlPointType == ControlPointType.BOTTOM_CENTER) {
            double originalHeight = selectionBounds.getHeight();
            double scaleY = 1.0;
            if (originalHeight > 0) {
                if (controlPointType == ControlPointType.TOP_CENTER) {
                    scaleY = (originalHeight + dragVector.y) / originalHeight;
                } else {
                    scaleY = (originalHeight - dragVector.y) / originalHeight;
                }
            }
            scaleFactors = new Vec2d(1.0, Math.max(0.01, scaleY));
        } else if (controlPointType == ControlPointType.CENTER_LEFT || controlPointType == ControlPointType.CENTER_RIGHT) {
            double originalWidth = selectionBounds.getWidth();
            double scaleX = 1.0;
            if (originalWidth > 0) {
                if (controlPointType == ControlPointType.CENTER_LEFT) {
                    scaleX = (originalWidth - dragVector.x) / originalWidth;
                } else {
                    scaleX = (originalWidth + dragVector.x) / originalWidth;
                }
            }
            scaleFactors = new Vec2d(Math.max(0.01, scaleX), 1.0);
        } else {
            if (params.isMaintainAspectRatio() || mode == TransformMode.UNIFORM) {
                double uniformScale = Math.max(0.01, calculateUniformScaleFactor(controlPointType, dragVector, selectionBounds));
                scaleFactors = new Vec2d(uniformScale, uniformScale);
            } else {
                scaleFactors = calculateScaleFactors(controlPointType, dragVector, selectionBounds);
            }
        }

        Vec2d scaleCenter = params.isCenterScale()
                ? selectionBounds.getCenter()
                : calculateAnchorPoint(controlPointType, selectionBounds);

        boolean nonUniformScale = Math.abs(scaleFactors.x - scaleFactors.y) > 1e-10;
        boolean useAffineForTypeConversion = nonUniformScale
                && (workingShape instanceof CircleShape || workingShape instanceof ArcShape || workingShape instanceof SpiralShape);

        if (useAffineForTypeConversion) {
            AffineTransform toOrigin = AffineTransform.createTranslation(-scaleCenter.x, -scaleCenter.y);
            AffineTransform scale = AffineTransform.createScale(scaleFactors.x, scaleFactors.y);
            AffineTransform fromOrigin = AffineTransform.createTranslation(scaleCenter.x, scaleCenter.y);
            AffineTransform transform = fromOrigin.multiply(scale).multiply(toOrigin);

            Shape transformed = workingShape.transform(transform);
            if (transformed != workingShape && workingShape.getStyle() != null) {
                transformed.setStyle(workingShape.getStyle().clone());
            }
            return transformed;
        }

        workingShape.scale(scaleFactors, scaleCenter);
        return workingShape;
    }

    private BoundingBox calculateCombinedBounds(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return null;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        boolean hasValid = false;

        for (Shape shape : shapes) {
            if (shape == null) {
                continue;
            }

            BoundingBox bounds = shape.getBoundingBox();
            if (bounds == null) {
                continue;
            }

            minX = Math.min(minX, bounds.getMinX());
            minY = Math.min(minY, bounds.getMinY());
            maxX = Math.max(maxX, bounds.getMaxX());
            maxY = Math.max(maxY, bounds.getMaxY());
            hasValid = true;
        }

        if (!hasValid) {
            return null;
        }

        return new BoundingBox(minX, minY, maxX, maxY);
    }
    
    /**
     * 变换单个图形
     */
    private Shape transformShape(Shape shape, TransformParams params) {
        // 在任何变换前先克隆，避免预览阶段修改原始对象导致累积漂移
        Shape workingShape = shape.clone();

        // 统一继承原图样式/变换，并保证预览副本可见。
        // 原因：预览阶段原图会被临时隐藏，部分图形clone会继承visible=false或丢失样式/transform，
        // 导致“无预览”或“预览线变黑”。
        if (shape.getTransform() != null) {
            workingShape.setTransform(shape.getTransform().clone());
        }
        if (shape.getStyle() != null) {
            workingShape.setStyle(shape.getStyle().clone());
        }
        workingShape.setVisible(true);

        // 检查是否为圆形或圆弧，且需要进行非等比缩放
        // 如果是，使用transform()方法转换为椭圆/椭圆弧
        ControlPointType controlPointType = params.getControlPointType();
        boolean isNonUniformScale = false;
        Vec2d scaleFactors = null;
        
        if (controlPointType != null) {
            if (controlPointType == ControlPointType.TOP_CENTER || 
                controlPointType == ControlPointType.BOTTOM_CENTER) {
                // 垂直缩放：检查是否为非等比
                com.plot.core.geometry.BoundingBox bounds = workingShape.getBoundingBox();
                if (bounds != null) {
                    Vec2d dragVector = params.getDragVector();
                    double originalHeight = bounds.getHeight();
                    double scaleY = 1.0;
                    if (originalHeight > 0) {
                        if (controlPointType == ControlPointType.TOP_CENTER) {
                            scaleY = (originalHeight + dragVector.y) / originalHeight;
                        } else {
                            scaleY = (originalHeight - dragVector.y) / originalHeight;
                        }
                    }
                    scaleFactors = new Vec2d(1.0, scaleY);
                    isNonUniformScale = Math.abs(scaleY - 1.0) > 1e-10;
                }
            } else if (controlPointType == ControlPointType.CENTER_LEFT || 
                       controlPointType == ControlPointType.CENTER_RIGHT) {
                // 水平缩放：检查是否为非等比
                com.plot.core.geometry.BoundingBox bounds = workingShape.getBoundingBox();
                if (bounds != null) {
                    Vec2d dragVector = params.getDragVector();
                    double originalWidth = bounds.getWidth();
                    double scaleX = 1.0;
                    if (originalWidth > 0) {
                        if (controlPointType == ControlPointType.CENTER_LEFT) {
                            scaleX = (originalWidth - dragVector.x) / originalWidth;
                        } else {
                            scaleX = (originalWidth + dragVector.x) / originalWidth;
                        }
                    }
                    scaleFactors = new Vec2d(scaleX, 1.0);
                    isNonUniformScale = Math.abs(scaleX - 1.0) > 1e-10;
                }
            } else if (controlPointType == ControlPointType.TOP_LEFT || 
                       controlPointType == ControlPointType.TOP_RIGHT ||
                       controlPointType == ControlPointType.BOTTOM_LEFT || 
                       controlPointType == ControlPointType.BOTTOM_RIGHT) {
                // 角点缩放：检查是否为非等比（且未按住Shift）
                if (!params.isMaintainAspectRatio()) {
                    com.plot.core.geometry.BoundingBox bounds = workingShape.getBoundingBox();
                    if (bounds != null) {
                        scaleFactors = calculateScaleFactors(controlPointType, params.getDragVector(), bounds);
                        isNonUniformScale = Math.abs(scaleFactors.x - scaleFactors.y) > 1e-10;
                    }
                }
            }
        }
        
        // 对于圆形、圆弧和螺旋形的非等比缩放，使用transform()方法进行变换
        // 注意：EllipseShape和EllipticalArcShape已经支持非等比缩放，不需要特殊处理
        // 对于SpiralShape，使用transform()方法可以支持单轴缩放（通过AffineTransform变换点）
        if (isNonUniformScale && (workingShape instanceof CircleShape || workingShape instanceof ArcShape || workingShape instanceof SpiralShape)) {
            Vec2d scaleCenter;
            com.plot.core.geometry.BoundingBox bounds = workingShape.getBoundingBox();
            if (bounds == null) {
                // 如果无法获取边界框，回退到普通变换
            } else {
                if (controlPointType == ControlPointType.TOP_CENTER || 
                    controlPointType == ControlPointType.BOTTOM_CENTER) {
                    scaleCenter = getVec2d(params, bounds);
                } else if (controlPointType == ControlPointType.CENTER_LEFT || 
                           controlPointType == ControlPointType.CENTER_RIGHT) {
                    scaleCenter = getHorizontalScaleCenter(params, bounds);
                } else {
                    scaleCenter = params.isCenterScale() ? bounds.getCenter() : calculateAnchorPoint(controlPointType, bounds);
                }
                
                // 创建以指定中心点进行缩放的变换矩阵
                // 变换顺序：1. 平移到原点 2. 缩放 3. 平移回去
                // 注意：AffineTransform的translate()直接修改矩阵（不是矩阵乘法），
                // 而scale()使用postMultiply（后置乘法：this = this * scale）
                // 所以我们需要使用multiply()方法手动组合变换矩阵
                // 最终效果：T(scaleCenter) * S(scaleFactors) * T(-scaleCenter)
                AffineTransform translate1 = AffineTransform.createTranslation(-scaleCenter.x, -scaleCenter.y);
                AffineTransform scaleTransform = AffineTransform.createScale(scaleFactors.x, scaleFactors.y);
                AffineTransform translate2 = AffineTransform.createTranslation(scaleCenter.x, scaleCenter.y);
                
                // 组合变换：translate2 * scaleTransform * translate1
                AffineTransform transform = translate2.multiply(scaleTransform).multiply(translate1);
                
                // 使用transform()方法进行变换
                // 对于CircleShape和ArcShape，transform()可能返回新类型（EllipseShape/EllipticalArcShape）
                // 对于SpiralShape，transform()返回this，但会正确应用非等比缩放（包括单轴缩放）
                Shape transformed = workingShape.transform(transform);
                if (transformed != workingShape) {
                    // 如果返回了新图形类型，复制样式
                    if (workingShape.getStyle() != null) {
                        transformed.setStyle(workingShape.getStyle().clone());
                    }
                    return transformed;
                } else if (workingShape instanceof SpiralShape) {
                    // SpiralShape.transform()返回this，但已经应用了变换，直接返回
                    return transformed;
                }
            }
        }
        
        // 其他情况，使用原来的逻辑

        // 根据变换模式应用不同的变换
        TransformMode mode = params.getMode();
        
        switch (mode) {
            case FREE -> applyFreeTransform(workingShape, params);
            case HORIZONTAL -> applyHorizontalTransform(workingShape, params);
            case VERTICAL -> applyVerticalTransform(workingShape, params);
            case UNIFORM -> applyUniformTransform(workingShape, params);
            case ROTATION -> applyRotationTransform(workingShape, params);
        }
        
        return workingShape;
    }
    
    /**
     * 应用自由变换
     */
    private void applyFreeTransform(Shape shape, TransformParams params) {
        Vec2d dragVector = params.getDragVector();
        ControlPointType controlPointType = params.getControlPointType();
        
        if (controlPointType == null) {
            // 如果没有指定控制点类型，进行平移
            shape.translate(dragVector);
        } else {
            // 根据控制点类型进行相应的变换
            applyControlPointTransform(shape, params);
        }
    }
    
    /**
     * 应用水平变换
     */
    private void applyHorizontalTransform(Shape shape, TransformParams params) {
        Vec2d dragVector = params.getDragVector();
        // 只应用X方向的变换
        shape.translate(new Vec2d(dragVector.x, 0));
    }
    
    /**
     * 应用垂直变换
     */
    private void applyVerticalTransform(Shape shape, TransformParams params) {
        Vec2d dragVector = params.getDragVector();
        // 只应用Y方向的变换
        shape.translate(new Vec2d(0, dragVector.y));
    }
    
    /**
     * 应用等比变换
     */
    private void applyUniformTransform(Shape shape, TransformParams params) {
        ControlPointType controlPointType = params.getControlPointType();
        Vec2d dragVector = params.getDragVector();
        com.plot.core.geometry.BoundingBox bounds = shape.getBoundingBox();
        
        if (bounds == null) {
            LOGGER.warn("无法获取图形边界框，跳过等比变换");
            return;
        }
        
        // 计算等比缩放因子
        double scaleFactor = calculateUniformScaleFactor(controlPointType, dragVector, bounds);
        
        // 确定缩放中心
        // 关键修复：对于SpiralShape，应该使用getPosition()而不是bounds.getCenter()
        // 因为SpiralShape的transform矩阵可能包含旋转/非等比缩放，导致bounds.getCenter()与center不一致
        Vec2d scaleCenter;
        if (params.isCenterScale()) {
            // 对于SpiralShape，使用getPosition()返回center，而不是bounds.getCenter()
            // 这样可以避免位置漂移问题
            if (shape instanceof SpiralShape) {
                scaleCenter = shape.getPosition();
            } else {
                scaleCenter = bounds.getCenter();
            }
        } else {
            scaleCenter = calculateAnchorPoint(controlPointType, bounds);
        }
        
        // 应用等比缩放
        shape.scale(new Vec2d(scaleFactor, scaleFactor), scaleCenter);
        
        LOGGER.debug("等比变换: 缩放因子={}, 中心={}", scaleFactor, scaleCenter);
    }
    
    /**
     * 计算等比缩放因子
     */
    private double calculateUniformScaleFactor(ControlPointType controlPointType, 
                                            Vec2d dragVector, 
                                            com.plot.core.geometry.BoundingBox bounds) {
        if (controlPointType == null) {
            // 如果没有控制点类型，使用拖拽向量的长度
            double dragDistance = dragVector.length();
            return 1.0 + dragDistance / 100.0;
        }
        
        double originalWidth = bounds.getWidth();
        double originalHeight = bounds.getHeight();
        
        if (originalWidth <= 0 || originalHeight <= 0) {
            return 1.0;
        }
        
        // 根据控制点类型计算缩放因子
        double scaleX;
        double scaleY;
        
        switch (controlPointType) {
            case TOP_LEFT, BOTTOM_LEFT -> {
                scaleX = (originalWidth - dragVector.x) / originalWidth;
                scaleY = (originalHeight - dragVector.y) / originalHeight;
            }
            case TOP_RIGHT, BOTTOM_RIGHT -> {
                scaleX = (originalWidth + dragVector.x) / originalWidth;
                scaleY = (originalHeight + dragVector.y) / originalHeight;
            }
            case TOP_CENTER, BOTTOM_CENTER -> {
                scaleX = 1.0;
                scaleY = (originalHeight + dragVector.y) / originalHeight;
            }
            case CENTER_LEFT, CENTER_RIGHT -> {
                scaleX = (originalWidth + dragVector.x) / originalWidth;
                scaleY = 1.0;
            }
            default -> {
                // 使用拖拽向量的平均长度
                double avgDrag = (Math.abs(dragVector.x) + Math.abs(dragVector.y)) / 2.0;
                double avgSize = (originalWidth + originalHeight) / 2.0;
                return 1.0 + avgDrag / avgSize;
            }
        }
        
        // 对于角点，使用两个方向缩放因子的平均值来保持等比
        if (isCornerPoint(controlPointType)) {
            return (scaleX + scaleY) / 2.0;
        }
        
        // 对于边中点，使用对应方向的缩放因子
        return Math.max(scaleX, scaleY);
    }
    
    /**
     * 判断是否为角点
     */
    private boolean isCornerPoint(ControlPointType controlPointType) {
        return controlPointType == ControlPointType.TOP_LEFT ||
               controlPointType == ControlPointType.TOP_RIGHT ||
               controlPointType == ControlPointType.BOTTOM_LEFT ||
               controlPointType == ControlPointType.BOTTOM_RIGHT;
    }
    
    /**
     * 应用旋转变换
     */
    private void applyRotationTransform(Shape shape, TransformParams params) {
        double rotationAngle = params.getRotationAngle();
        Vec2d rotationCenter = params.getAnchorPoint();
        
        if (rotationCenter == null) {
            // 如果没有指定旋转中心，使用图形中心
            // 关键修复：对于SpiralShape，使用getPosition()返回center，而不是bounds.getCenter()
            // 因为SpiralShape的transform矩阵可能包含旋转/非等比缩放，导致bounds.getCenter()与center不一致
            if (shape instanceof SpiralShape) {
                rotationCenter = shape.getPosition();
            } else {
                com.plot.core.geometry.BoundingBox bounds = shape.getBoundingBox();
                if (bounds != null) {
                    rotationCenter = bounds.getCenter();
                } else {
                    LOGGER.warn("无法获取旋转中心，跳过旋转变换");
                    return;
                }
            }
        }
        
        shape.rotate(rotationAngle, rotationCenter);
        
        LOGGER.debug("旋转变换: 角度={}, 中心={}", rotationAngle, rotationCenter);
    }
    
    /**
     * 根据控制点类型应用变换
     */
    private void applyControlPointTransform(Shape shape, TransformParams params) {
        ControlPointType controlPointType = params.getControlPointType();
        Vec2d dragVector = params.getDragVector();
        
        switch (controlPointType) {
            case TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT -> // 角点：缩放变换
                    applyCornerTransform(shape, params);
            case TOP_CENTER, BOTTOM_CENTER -> // 上下中点：垂直缩放
                    applyVerticalScale(shape, params);
            case CENTER_LEFT, CENTER_RIGHT -> // 左右中点：水平缩放
                    applyHorizontalScale(shape, params);
            default -> // 默认：平移
                    shape.translate(dragVector);
        }
    }
    
    /**
     * 应用角点变换（缩放）
     */
    private void applyCornerTransform(Shape shape, TransformParams params) {
        ControlPointType controlPointType = params.getControlPointType();
        Vec2d dragVector = params.getDragVector();
        
        if (controlPointType == null) {
            shape.translate(dragVector);
            return;
        }
        
        // 获取图形的原始边界框
        com.plot.core.geometry.BoundingBox originalBounds = shape.getBoundingBox();
        if (originalBounds == null) {
            LOGGER.warn("无法获取图形边界框，跳过角点变换");
            return;
        }
        
        // 计算缩放中心：Alt(中心缩放)时使用边界框中心，否则使用对角锚点
        Vec2d anchorPoint = params.isCenterScale()
            ? originalBounds.getCenter()
            : calculateAnchorPoint(controlPointType, originalBounds);
        
        // 检查是否按住Shift键（等比缩放）
        Vec2d scaleFactors;
        if (params.isMaintainAspectRatio()) {
            // 按住Shift键：等比缩放
            double uniformScale = calculateUniformScaleFactor(controlPointType, dragVector, originalBounds);
            scaleFactors = new Vec2d(uniformScale, uniformScale);
            LOGGER.debug("角点变换（等比缩放）: 控制点={}, 缩放因子={}", controlPointType, uniformScale);
        } else {
            // 自由缩放
            scaleFactors = calculateScaleFactors(controlPointType, dragVector, originalBounds);
            LOGGER.debug("角点变换（自由缩放）: 控制点={}, 缩放因子={}", controlPointType, scaleFactors);
        }
        
        // 应用缩放变换
        shape.scale(scaleFactors, anchorPoint);
        
        LOGGER.debug("角点变换: 控制点={}, 锚点={}, 缩放因子={}", 
            controlPointType, anchorPoint, scaleFactors);
    }
    
    
    /**
     * 应用垂直缩放
     */
    private void applyVerticalScale(Shape shape, TransformParams params) {
        Vec2d dragVector = params.getDragVector();
        com.plot.core.geometry.BoundingBox bounds = shape.getBoundingBox();
        
        if (bounds == null) {
            LOGGER.warn("无法获取图形边界框，跳过垂直缩放");
            return;
        }
        
        // 计算垂直缩放因子
        // 注意：世界坐标系Y轴向上，屏幕坐标系Y轴向下
        // TOP_CENTER 在 maxY（顶部），锚点在 minY（底部中点）
        // BOTTOM_CENTER 在 minY（底部），锚点在 maxY（顶部中点）
        double originalHeight = bounds.getHeight();
        ControlPointType controlPointType = params.getControlPointType();
        double scaleY;
        if (originalHeight > 0) {
            if (controlPointType == ControlPointType.TOP_CENTER) {
                // 拖拽顶部中点，锚点在底部中点
                // 向上拖动（dragVector.y > 0，世界坐标Y轴向上）增加高度
                // 向下拖动（dragVector.y < 0）减少高度
                scaleY = (originalHeight + dragVector.y) / originalHeight;
            } else { // BOTTOM_CENTER
                // 拖拽底部中点，锚点在顶部中点
                // 向下拖动（dragVector.y < 0，世界坐标Y轴向上）增加高度
                // 向上拖动（dragVector.y > 0）减少高度
                scaleY = (originalHeight - dragVector.y) / originalHeight;
            }
        } else {
            scaleY = 1.0;
        }
        
        // 确定缩放中心
        Vec2d scaleCenter = getVec2d(params, bounds);

        // 直接使用scale方法（圆形和圆弧的非等比缩放转换已在transformShape中处理）
        shape.scale(new Vec2d(1.0, scaleY), scaleCenter);
        
        LOGGER.debug("垂直缩放: 控制点={}, 缩放因子={}, 中心={}", controlPointType, scaleY, scaleCenter);
    }

    private static Vec2d getVec2d(TransformParams params, BoundingBox bounds) {
        Vec2d scaleCenter;
        if (params.isCenterScale()) {
            scaleCenter = bounds.getCenter();
        } else {
            // 根据控制点类型确定锚点（修正：与TransformWithSelectionStrategy保持一致）
            ControlPointType controlPointType = params.getControlPointType();
            Vec2d center = bounds.getCenter();
            if (controlPointType == ControlPointType.TOP_CENTER) {
                // 拖拽顶部中点时，锚点应该在底部中点
                scaleCenter = new Vec2d(center.x, bounds.getMinY());
            } else { // BOTTOM_CENTER
                // 拖拽底部中点时，锚点应该在顶部中点
                scaleCenter = new Vec2d(center.x, bounds.getMaxY());
            }
        }
        return scaleCenter;
    }

    /**
     * 应用水平缩放
     */
    private void applyHorizontalScale(Shape shape, TransformParams params) {
        Vec2d dragVector = params.getDragVector();
        com.plot.core.geometry.BoundingBox bounds = shape.getBoundingBox();
        
        if (bounds == null) {
            LOGGER.warn("无法获取图形边界框，跳过水平缩放");
            return;
        }
        
        // 计算水平缩放因子
        // CENTER_LEFT 在 minX（左侧），锚点在 maxX（右侧中点）
        // CENTER_RIGHT 在 maxX（右侧），锚点在 minX（左侧中点）
        // 参考角点的计算：左侧角点用减法，右侧角点用加法
        double originalWidth = bounds.getWidth();
        ControlPointType controlPointType = params.getControlPointType();
        double scaleX;
        if (originalWidth > 0) {
            if (controlPointType == ControlPointType.CENTER_LEFT) {
                // 拖拽左侧中点，锚点在右侧中点
                // 向右拖动（dragVector.x > 0）增加宽度，向左拖动（dragVector.x < 0）减少宽度
                // 参考左侧角点的计算方式，使用减法
                scaleX = (originalWidth - dragVector.x) / originalWidth;
            } else { // CENTER_RIGHT
                // 拖拽右侧中点，锚点在左侧中点
                // 向右拖动（dragVector.x > 0）增加宽度，向左拖动（dragVector.x < 0）减少宽度
                // 参考右侧角点的计算方式，使用加法
                scaleX = (originalWidth + dragVector.x) / originalWidth;
            }
        } else {
            scaleX = 1.0;
        }
        
        // 确定缩放中心
        Vec2d scaleCenter = getHorizontalScaleCenter(params, bounds);

        // 直接使用scale方法（圆形和圆弧的非等比缩放转换已在transformShape中处理）
        shape.scale(new Vec2d(scaleX, 1.0), scaleCenter);
        
        LOGGER.debug("水平缩放: 控制点={}, 缩放因子={}, 中心={}", controlPointType, scaleX, scaleCenter);
    }

    /**
     * 获取水平缩放的缩放中心点
     */
    private static Vec2d getHorizontalScaleCenter(TransformParams params, BoundingBox bounds) {
        Vec2d scaleCenter;
        if (params.isCenterScale()) {
            scaleCenter = bounds.getCenter();
        } else {
            // 根据控制点类型确定锚点（修正：与TransformWithSelectionStrategy保持一致）
            ControlPointType controlPointType = params.getControlPointType();
            Vec2d center = bounds.getCenter();
            if (controlPointType == ControlPointType.CENTER_LEFT) {
                // 拖拽左侧中点时，锚点应该在右侧中点
                scaleCenter = new Vec2d(bounds.getMaxX(), center.y);
            } else { // CENTER_RIGHT
                // 拖拽右侧中点时，锚点应该在左侧中点
                scaleCenter = new Vec2d(bounds.getMinX(), center.y);
            }
        }
        return scaleCenter;
    }

    
    /**
     * 计算锚点（对角点）
     */
    private Vec2d calculateAnchorPoint(ControlPointType controlPointType, 
                                     com.plot.core.geometry.BoundingBox bounds) {
        double minX = bounds.getMinX();
        double minY = bounds.getMinY();
        double maxX = bounds.getMaxX();
        double maxY = bounds.getMaxY();
        Vec2d center = bounds.getCenter();
        double centerX = center.x;
        double centerY = center.y;
        
        return switch (controlPointType) {
            // 角点的锚点是对角顶点
            // 控制点位置：TOP_LEFT=(minX,maxY), TOP_RIGHT=(maxX,maxY), BOTTOM_RIGHT=(maxX,minY), BOTTOM_LEFT=(minX,minY)
            case TOP_LEFT -> new Vec2d(maxX, minY);     // 拖拽左上角，锚点在右下角
            case TOP_RIGHT -> new Vec2d(minX, minY);    // 拖拽右上角，锚点在左下角
            case BOTTOM_LEFT -> new Vec2d(maxX, maxY);  // 拖拽左下角，锚点在右上角
            case BOTTOM_RIGHT -> new Vec2d(minX, maxY); // 拖拽右下角，锚点在左上角
            
            // 边中点的锚点是相对的另一条边中点
            case TOP_CENTER -> new Vec2d(centerX, minY);      // 拖拽顶部中点，锚点在底部中点
            case BOTTOM_CENTER -> new Vec2d(centerX, maxY);   // 拖拽底部中点，锚点在顶部中点
            case CENTER_LEFT -> new Vec2d(maxX, centerY);      // 拖拽左侧中点，锚点在右侧中点
            case CENTER_RIGHT -> new Vec2d(minX, centerY);     // 拖拽右侧中点，锚点在左侧中点
        };
    }
    
    /**
     * 计算缩放因子
     */
    private Vec2d calculateScaleFactors(ControlPointType controlPointType, 
                                      Vec2d dragVector, 
                                      com.plot.core.geometry.BoundingBox bounds) {
        double originalWidth = bounds.getWidth();
        double originalHeight = bounds.getHeight();
        
        double scaleX = 1.0;
        double scaleY = 1.0;
        
        if (originalWidth > 0) {
            scaleX = switch (controlPointType) {
                // 左侧角点：TOP_LEFT 和 BOTTOM_LEFT 在 minX，锚点在 maxX，向右拖动（dragVector.x > 0）增加宽度
                case TOP_LEFT, BOTTOM_LEFT -> (originalWidth - dragVector.x) / originalWidth;
                // 右侧角点：TOP_RIGHT 和 BOTTOM_RIGHT 在 maxX，锚点在 minX，向右拖动（dragVector.x > 0）增加宽度
                case TOP_RIGHT, BOTTOM_RIGHT -> (originalWidth + dragVector.x) / originalWidth;
                default -> 1.0;
            };
        }
        
        if (originalHeight > 0) {
            scaleY = switch (controlPointType) {
                // 世界坐标系Y轴向上
                // TOP_LEFT 和 TOP_RIGHT 在 maxY（顶部），向上拖动（dragVector.y > 0）增加高度
                case TOP_LEFT, TOP_RIGHT -> (originalHeight + dragVector.y) / originalHeight;
                // BOTTOM_LEFT 和 BOTTOM_RIGHT 在 minY（底部），向下拖动（dragVector.y < 0）增加高度
                case BOTTOM_LEFT, BOTTOM_RIGHT -> (originalHeight - dragVector.y) / originalHeight;
                default -> 1.0;
            };
        }
        
        // 确保缩放因子为正数
        scaleX = Math.max(0.01, scaleX);
        scaleY = Math.max(0.01, scaleY);
        
        return new Vec2d(scaleX, scaleY);
    }
    
    /**
     * 恢复原始状态
     */
    private void restoreOriginalState() {
        for (int i = 0; i < originalShapes.size(); i++) {
            Shape originalShape = originalShapes.get(i);
            Shape originalCopy = originalShapeCopies.get(i);
            
            if (originalShape != null && originalCopy != null) {
                // 恢复原始形状 - 使用clone方式
                Shape restoredShape = originalCopy.clone();
                // 这里需要更复杂的恢复逻辑，暂时简化
                LOGGER.debug("恢复原始形状: {}", originalShape.getId());
            }
        }
    }
    
    /**
     * 更新应用状态
     */
    private void updateAppState() {
        // 这里可以更新应用状态，比如刷新UI等
        LOGGER.debug("更新应用状态");
    }
    
    /**
     * 生成预览图形
     */
    public static List<Shape> generatePreviewShapes(List<Shape> shapes, TransformParams params) {
        if (shapes == null || shapes.isEmpty()) {
            return new ArrayList<>();
        }

        List<Shape> validShapes = new ArrayList<>();
        for (Shape shape : shapes) {
            if (shape != null) {
                validShapes.add(shape);
            }
        }

        if (validShapes.isEmpty()) {
            return new ArrayList<>();
        }

        // 关键：多选预览必须整组计算，不能逐图形单独计算
        TransformCommand tempCommand = new TransformCommand(validShapes, params, null);
        tempCommand.applyTransform();
        return new ArrayList<>(tempCommand.transformedShapes);
    }
    
    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.transform", originalShapes.size());
    }
}
