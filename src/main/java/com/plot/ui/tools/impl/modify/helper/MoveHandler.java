package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.command.commands.CopyMoveCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;

import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 移动操作处理器
 * 
 * <p>专门处理图形移动操作的逻辑，包括：</p>
 * <ul>
 *   <li>移动向量计算</li>
 *   <li>约束应用（网格吸附、正交移动等）</li>
 *   <li>预览图形生成</li>
 *   <li>移动命令创建</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 移动处理器
 */
public class MoveHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveHandler.class);
    
    private final AppState appState;
    
    /**
     * 构造函数
     * @param appState 应用状态管理器
     */
    public MoveHandler(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.MOVE;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 检查图形列表
        if (shapes == null || shapes.isEmpty()) {
            return ValidationResult.invalid("status.plot.move.no_selection");
        }
        
        // 检查必需参数
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            ValidationResult paramValidation = concreteParams.validateRequired(
                com.plot.ui.tools.impl.modify.dto.ModifyParameters.START_POINT,
                com.plot.ui.tools.impl.modify.dto.ModifyParameters.END_POINT
            );
            if (!paramValidation.isValid()) {
                return paramValidation;
            }
        }
        
        // 检查移动向量
        Vec2d startPoint = null;
        Vec2d endPoint = null;
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            startPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.START_POINT);
            endPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.END_POINT);
        }
        
        if (startPoint == null || endPoint == null) {
            return ValidationResult.invalid("status.plot.move.invalid_points");
        }
        
        // 检查是否有实际移动
        Vec2d offset = endPoint.subtract(startPoint);
        if (offset.length() < 0.001) {
            return ValidationResult.invalid("status.plot.move.too_small");
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        Vec2d startPoint = null;
        Vec2d endPoint = null;
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            startPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.START_POINT);
            endPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.END_POINT);
        }
        
        if (startPoint == null || endPoint == null) {
            return new ArrayList<>(shapes); // 返回原图形
        }
        
        // 计算鼠标移动的偏移量（统一对几何应用平移）
        Vec2d mouseOffset = endPoint.subtract(startPoint);
        List<Shape> modifiedShapes = new ArrayList<>();
        
        for (Shape shape : shapes) {
            try {
                Shape moved = shape.clone();
                if (moved == null) {
                    LOGGER.error("克隆图形失败: {}", shape.getId());
                    modifiedShapes.add(shape);
                    continue;
                }
                // 使用 translate 确保各图形类型（如悬链线、正弦曲线）正确移动其内部控制点
                moved.translate(mouseOffset);
                modifiedShapes.add(moved);
            } catch (Exception e) {
                LOGGER.error("移动图形失败: {}", e.getMessage(), e);
                modifiedShapes.add(shape);
            }
        }
        
        return modifiedShapes;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        Vec2d startPoint = null;
        Vec2d endPoint = null;
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            startPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.START_POINT);
            endPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.END_POINT);
        }
        
        if (startPoint == null || endPoint == null) {
            return new ArrayList<>(shapes); // 返回原图形
        }
        
        // 计算偏移量，预览对克隆应用 translate
        Vec2d mouseOffset = endPoint.subtract(startPoint);
        List<Shape> previewShapes = new ArrayList<>();
        
        for (Shape shape : shapes) {
            try {
                // 克隆原图形用于预览，不修改原图形
                Shape previewShape = shape.clone();
                
                // 直接对克隆应用平移
                previewShape.translate(mouseOffset);
                
                // 预览样式应与原图形一致
                if (shape.getStyle() != null) {
                    try { previewShape.setStyle(shape.getStyle().clone()); } catch (Exception e) { ExceptionDebug.log("MoveHandler: clone style for preview", e); }
                }
                
                // 修复：清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
                previewShape.setSelected(false);
                previewShape.setHighlighted(false);
                
                previewShapes.add(previewShape);
                LOGGER.debug("创建预览图形 {}: 偏移 {}", shape.getId(), mouseOffset);
            } catch (Exception e) {
                LOGGER.error("创建预览图形失败: {}", e.getMessage(), e);
                // 如果预览创建失败，使用原图形
                previewShapes.add(shape);
            }
        }
        
        return previewShapes;
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes, 
                                           IModifyHandler.ModifyParameters parameters) {
        // 检查是否为复制模式
        boolean isCopyMode = false;
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            isCopyMode = concreteParams.isCopyMode();
        }
        
        if (isCopyMode) {
            // 复制模式：只添加新图形，不删除原图形
            return new CopyMoveCommand(originalShapes, modifiedShapes, appState);
        } else {
            // 移动模式：替换原图形
            return new ModifyCommand(originalShapes, modifiedShapes, appState, "history.plot.op.move");
        }
    }
    
    @Override
    public IModifyHandler.ModifyParameters applyConstraints(IModifyHandler.ModifyParameters parameters, 
                                                          IModifyHandler.ModifyConstraints constraints) {
        if (constraints == null || !(parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams)) {
            return parameters;
        }

        Vec2d startPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.START_POINT);
        Vec2d endPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.END_POINT);
        
        if (startPoint == null || endPoint == null) {
            return parameters;
        }

        // 计算原始位移向量
        Vec2d delta = endPoint.subtract(startPoint);

        // 应用正交约束（Shift）
        if (constraints instanceof com.plot.ui.tools.impl.modify.constants.ModifyConstraints mc && mc.isOrthogonalConstraintEnabled()) {
            delta = mc.applyOrthogonalConstraint(delta);
            endPoint = startPoint.add(delta);
        }

        // 可选：应用网格吸附到终点
        if (constraints instanceof com.plot.ui.tools.impl.modify.constants.ModifyConstraints mc2 && mc2.isGridSnapEnabled()) {
            endPoint = mc2.applyGridSnapConstraint(endPoint);
        }

        // 创建新的参数对象
        com.plot.ui.tools.impl.modify.dto.ModifyParameters constrainedParameters = concreteParams.clone();
        constrainedParameters.setEndPoint(endPoint);
        
        return constrainedParameters;
    }
    
    /**
     * 计算移动向量
     * @param parameters 修改参数
     * @return 移动向量
     */
    public Vec2d calculateMoveVector(IModifyHandler.ModifyParameters parameters) {
        Vec2d startPoint = null;
        Vec2d endPoint = null;
        if (parameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters concreteParams) {
            startPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.START_POINT);
            endPoint = concreteParams.getVec2d(com.plot.ui.tools.impl.modify.dto.ModifyParameters.END_POINT);
        }
        
        if (startPoint == null || endPoint == null) {
            return new Vec2d(0, 0);
        }
        
        return endPoint.subtract(startPoint);
    }

    /**
     * 计算移动角度（弧度）
     * @param parameters 修改参数
     * @return 移动角度
     */
    public double calculateMoveAngle(IModifyHandler.ModifyParameters parameters) {
        Vec2d moveVector = calculateMoveVector(parameters);
        return Math.atan2(moveVector.y, moveVector.x);
    }

    /**
     * 获取移动操作的状态消息
     * @param parameters 修改参数
     * @return 状态消息
     */
    public String getStatusMessage(IModifyHandler.ModifyParameters parameters) {
        Vec2d moveVector = calculateMoveVector(parameters);
        double distance = moveVector.length();
        double angle = Math.toDegrees(calculateMoveAngle(parameters));
        
        return PlotI18n.status("status.plot.move.handler",
                moveVector.x, moveVector.y, distance, angle);
    }
}