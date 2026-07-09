package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.command.commands.TransformCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.dto.TransformParams;
import com.plot.ui.tools.impl.modify.exception.InvalidTransformException;
import com.plot.ui.tools.impl.modify.helper.BoundingBoxControlManager.ControlPointType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 变换操作处理器
 * 
 * <p>简化的变换处理器，专注于参数转换和命令创建：</p>
 * <ul>
 *   <li>参数验证和转换</li>
 *   <li>预览图形生成</li>
 *   <li>变换命令创建</li>
 * </ul>
 * 
 * <p>所有几何变换逻辑已统一到TransformCommand中</p>
 * 
 * @author Plot Team
 * @version 2.0 - 简化设计，统一变换逻辑
 */
public class TransformHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformHandler.class);
    
    private final AppState appState;
    
    /**
     * 构造函数
     * @param appState 应用状态管理器
     */
    public TransformHandler(AppState appState) {
        this.appState = Objects.requireNonNull(appState, "AppState不能为空");
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.TRANSFORM;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        try {
            // 使用Objects.requireNonNull进行空值检查
            Objects.requireNonNull(shapes, PlotI18n.error("error.plot.validation.shapes_null"));
            Objects.requireNonNull(parameters, PlotI18n.error("error.plot.validation.modify_params_null"));
            
            // 检查图形列表
            if (shapes.isEmpty()) {
                return ValidationResult.invalid(PlotI18n.status("status.plot.transform.need_shapes"));
            }
            
            // 检查图形对象
            for (Shape shape : shapes) {
                Objects.requireNonNull(shape, PlotI18n.error("error.plot.validation.shape_null"));
            }
            
            return ValidationResult.valid();
            
        } catch (Exception e) {
            LOGGER.error("变换操作验证时发生错误: {}", e.getMessage(), e);
            return ValidationResult.invalid(PlotI18n.status("status.plot.transform.validation_failed", e.getMessage()));
        }
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 这个方法现在主要用于向后兼容
        // 新的实现应该使用 TransformParams 版本
        LOGGER.warn("使用了旧的参数接口，建议使用 TransformParams 版本");
        return new ArrayList<>(shapes);
    }
    
    /**
     * 使用类型安全的TransformParams计算修改后的图形
     * 
     * @param shapes 原始图形列表
     * @param transformParams 变换参数
     * @return 修改后的图形列表
     */
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, TransformParams transformParams) {
        if (shapes.isEmpty()) {
            LOGGER.warn("变换操作需要至少一个图形，但提供了空列表");
            return new ArrayList<>();
        }
        
        // 参数验证在validateTransformParams中进行
        
        try {
            // 使用 TransformCommand 生成预览图形
            return TransformCommand.generatePreviewShapes(shapes, transformParams);
            
        } catch (Exception e) {
            LOGGER.error("计算修改后的图形失败", e);
            return new ArrayList<>(shapes);
        }
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
        
        // 应用预览样式
        for (int i = 0; i < modifiedShapes.size(); i++) {
            Shape preview = modifiedShapes.get(i);
            Shape original = i < shapes.size() ? shapes.get(i) : null;
            
            if (original != null && original.getStyle() != null) {
                try { 
                    preview.setStyle(original.getStyle().clone());
                    // 应用预览样式（使用类型安全的方法）
                    applyPreviewStyle(preview);
                } catch (Exception e) {
                    LOGGER.warn("应用预览样式失败: {}", e.getMessage());
                }
            }
        }
        
        return modifiedShapes;
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes,
                                           IModifyHandler.ModifyParameters parameters) {
        // 这个方法现在主要用于向后兼容
        // 新的实现应该使用 TransformParams 版本
        LOGGER.warn("使用了旧的参数接口创建命令，建议使用 TransformParams 版本");
        return null;
    }
    
    /**
     * 使用类型安全的TransformParams创建修改命令
     * 
     * @param originalShapes 原始图形列表
     * @param modifiedShapes 修改后的图形列表
     * @param transformParams 变换参数
     * @return 修改命令
     */
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes,
                                           TransformParams transformParams) {
        try {
            // 使用Objects.requireNonNull进行空值检查
            Objects.requireNonNull(originalShapes, PlotI18n.error("error.plot.validation.shapes_null"));
            Objects.requireNonNull(transformParams, PlotI18n.error("error.plot.validation.transform_params_null"));
            
            if (originalShapes.isEmpty()) {
                throw new InvalidTransformException(
                    PlotI18n.status("status.plot.transform.empty_shapes"),
                    InvalidTransformException.ErrorCode.INVALID_SHAPE,
                    InvalidTransformException.Context.TRANSFORM_HANDLER
                );
            }
            
            // 验证TransformParams参数
            if (!validateTransformParams(transformParams)) {
                throw new InvalidTransformException(
                    PlotI18n.status("status.plot.transform.params_invalid"),
                    InvalidTransformException.ErrorCode.INVALID_DRAG_VECTOR,
                    InvalidTransformException.Context.TRANSFORM_HANDLER
                );
            }
            
            // 创建变换命令
            return new TransformCommand(originalShapes, transformParams, appState);
            
        } catch (InvalidTransformException e) {
            LOGGER.error("创建变换命令失败: {}", e.getFullErrorMessage());
            return null;
        } catch (Exception e) {
            LOGGER.error("创建变换命令时发生未知错误: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 验证TransformParams参数
     * 
     * <p>针对新的TransformParams DTO的专门验证方法</p>
     */
    private boolean validateTransformParams(TransformParams params) {
        try {
            // 使用Objects.requireNonNull进行空值检查
            Objects.requireNonNull(params, PlotI18n.error("error.plot.validation.transform_params_null"));
            Objects.requireNonNull(params.getDragVector(), PlotI18n.error("error.plot.validation.drag_vector_null"));
            Objects.requireNonNull(params.getMode(), PlotI18n.error("error.plot.validation.transform_mode_null"));
            
            // 验证拖拽向量
            Vec2d dragVector = params.getDragVector();
            if (Double.isNaN(dragVector.x) || Double.isNaN(dragVector.y) ||
                Double.isInfinite(dragVector.x) || Double.isInfinite(dragVector.y)) {
                throw new InvalidTransformException(
                    PlotI18n.status("status.plot.transform.invalid_drag_vector", dragVector.x, dragVector.y),
                    InvalidTransformException.ErrorCode.INVALID_DRAG_VECTOR,
                    InvalidTransformException.Context.TRANSFORM_HANDLER
                );
            }
            
            // 验证控制点索引（动态验证，基于枚举定义）
            int controlPointIndex = params.getControlPointIndex();
            int numControlPoints = ControlPointType.values().length;
            if (controlPointIndex < 0 || controlPointIndex >= numControlPoints) {
                throw new InvalidTransformException(
                    PlotI18n.status("status.plot.transform.invalid_control_index",
                        controlPointIndex, numControlPoints - 1),
                    InvalidTransformException.ErrorCode.INVALID_CONTROL_POINT_INDEX,
                    InvalidTransformException.Context.TRANSFORM_HANDLER
                );
            }
            
            // 验证控制点类型
            if (params.getControlPointType() == null) {
                throw new InvalidTransformException(
                    PlotI18n.status("status.plot.transform.control_type_required"),
                    InvalidTransformException.ErrorCode.INVALID_CONTROL_POINT,
                    InvalidTransformException.Context.TRANSFORM_HANDLER
                );
            }
            
            return true;
            
        } catch (InvalidTransformException e) {
            LOGGER.error("TransformParams验证失败: {}", e.getFullErrorMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("TransformParams验证时发生未知错误: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 应用预览样式（类型安全的方法）
     * 
     * @param shape 要应用样式的图形
     */
    private void applyPreviewStyle(Shape shape) {
        try {
            // 这里可以应用预览样式，比如半透明、虚线等
            // 具体实现取决于 Shape 类的样式系统
            LOGGER.debug("应用预览样式到图形: {}", shape.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.warn("应用预览样式失败: {}", e.getMessage());
        }
    }
}