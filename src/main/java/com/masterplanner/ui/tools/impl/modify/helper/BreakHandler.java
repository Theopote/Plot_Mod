package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 打断处理器 - 专门处理图形打断操作
 * 
 * <p>支持以下图形类型的打断：</p>
 * <ul>
 *   <li>直线 (LineShape)</li>
 *   <li>圆形 (CircleShape)</li>
 *   <li>多段线 (PolylineShape)</li>
 *   <li>矩形 (RectangleShape)</li>
 *   <li>椭圆 (EllipseShape)</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 打断处理器
 */
public class BreakHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BreakHandler.class);

    @Override
    public ModifyType getModifyType() {
        return ModifyType.BREAK;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, ModifyParameters parameters) {
        // 检查必需参数
        if (!parameters.hasParameter("targetShape")) {
            return ValidationResult.invalid("缺少目标图形");
        }
        
        if (!parameters.hasParameter("firstBreakPoint")) {
            return ValidationResult.invalid("缺少第一个打断点");
        }
        
        if (!parameters.hasParameter("breakMode")) {
            return ValidationResult.invalid("缺少打断模式");
        }
        
        // 检查两点模式是否需要第二个打断点
        Object breakMode = parameters.getParameter("breakMode");
        if (breakMode != null && "TWO_POINT".equals(breakMode.toString())) {
            if (!parameters.hasParameter("secondBreakPoint")) {
                return ValidationResult.invalid("两点打断模式需要第二个打断点");
            }
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, ModifyParameters parameters) {
        try {
            // 获取参数
            Shape targetShape = (Shape) parameters.getParameter("targetShape");
            Vec2d firstBreakPoint = getVec2dFromParameters(parameters, "firstBreakPoint");
            Vec2d secondBreakPoint = getVec2dFromParameters(parameters, "secondBreakPoint");
            Object breakMode = parameters.getParameter("breakMode");
            
            if (targetShape == null || firstBreakPoint == null) {
                LOGGER.warn("打断操作参数不完整");
                return new ArrayList<>();
            }
            
            // 使用多态方法执行打断
            String breakModeStr = breakMode != null ? breakMode.toString() : "SINGLE_POINT";
            List<Shape> newShapes = targetShape.breakShape(firstBreakPoint, secondBreakPoint, breakModeStr);
            
            if (newShapes.isEmpty()) {
                LOGGER.warn("无法生成打断后的图形");
                return new ArrayList<>();
            }
            
            LOGGER.debug("打断操作成功: 原始图形=1, 新图形={}", newShapes.size());
            return newShapes;
            
        } catch (Exception e) {
            LOGGER.error("执行打断操作时发生错误: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, ModifyParameters parameters) {
        // 预览图形与最终图形相同
        List<Shape> previewShapes = calculateModifiedShapes(shapes, parameters);
        
        // 修复：清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
        for (Shape preview : previewShapes) {
            preview.setSelected(false);
            preview.setHighlighted(false);
        }
        
        return previewShapes;
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, List<Shape> modifiedShapes, ModifyParameters parameters) {
        try {
            AppState appState = (AppState) parameters.getParameter("appState");
            if (appState == null) {
                LOGGER.warn("无法创建打断命令：缺少AppState");
                // 新增：增强反馈，如果有上下文
                Object context = parameters.getParameter("context");
                if (context instanceof ModifyToolContext) {
                    ((ModifyToolContext) context).setStatusMessage("打断操作失败：系统状态错误");
                }
                return null;
            }
            // 若没有生成任何新图形，则不执行删除原图形的命令，避免"打断失败导致对象被删除"
            if (modifiedShapes == null || modifiedShapes.isEmpty()) {
                LOGGER.warn("打断未生成新图形，跳过命令创建以避免删除原图形");
                // 新增：增强反馈
                Object context = parameters.getParameter("context");
                if (context instanceof ModifyToolContext) {
                    ((ModifyToolContext) context).setStatusMessage("打断操作失败：无法生成新图形");
                }
                return null;
            }
            
            ModifyCommand command = new ModifyCommand(originalShapes, modifiedShapes, appState, "打断");
            LOGGER.debug("成功创建打断命令");
            return command;
            
        } catch (Exception e) {
            LOGGER.error("创建打断命令时发生错误: {}", e.getMessage(), e);
            // 新增：增强错误反馈
            Object context = parameters.getParameter("context");
            if (context instanceof ModifyToolContext) {
                ((ModifyToolContext) context).setStatusMessage("打断命令创建失败: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * 从参数中获取Vec2d值
     */
    private Vec2d getVec2dFromParameters(ModifyParameters parameters, String key) {
        Object value = parameters.getParameter(key);
        if (value instanceof Vec2d) {
            return (Vec2d) value;
        }
        return null;
    }
} 