package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.tools.impl.modify.helper.FillHandler;
import com.masterplanner.ui.tools.impl.modify.helper.IModifyHandler;
import com.masterplanner.ui.tools.impl.modify.constants.ModifyConstraints;
import com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

/**
 * 填充策略
 * 实现填充工具的具体逻辑，支持点击填充和边界填充两种模式
 * 
 * <p><strong>功能特点：</strong></p>
 * <ul>
 *   <li><strong>点击填充模式</strong>：直接在点击位置进行填充，自动检测封闭区域</li>
 *   <li><strong>边界填充模式</strong>：先选中边界，再在区域内点击确认填充</li>
 *   <li><strong>射线检测算法</strong>：使用射线法确定封闭边界，提高填充准确性</li>
 *   <li><strong>智能边界选择</strong>：支持直接选中边界图形，无需依赖选择工具</li>
 *   <li><strong>连续填充模式</strong>：支持连续多次填充操作</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 2.1 - 完善边界填充模式的选择功能
 */
public class FillStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(FillStrategy.class);
    
    public enum FillState {
        SELECTING_TARGET,    // 选择目标状态
        READY_TO_FILL,      // 准备填充状态
        SELECTING_BOUNDARY  // 选择边界状态（边界填充模式）
    }
    
    public enum FillMode {
        POINT_FILL("点击填充", "点击要填充的区域，自动检测封闭边界"),
        BOUNDARY_FILL("边界填充", "先选择边界，再点击区域内部确认填充");
        
        private final String displayName;
        private final String description;
        
        FillMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    private FillState currentState;
    private FillMode currentMode;
    private Shape targetShape;
    private Vec2d fillPoint;
    private float fillOpacity;
    private boolean multipleMode;
    private ModifyCommand modifyCommand;
    
    // 边界填充模式相关字段
    private List<Shape> selectedBoundaryShapes;
    private boolean isBoundarySelectionMode;
    
    // Handler相关字段
    private FillHandler fillHandler;
    private ModifyParameters fillParameters;
    // 当前实现不需要额外的约束，保留以兼容接口
    @SuppressWarnings("unused")
    private ModifyConstraints fillConstraints;

    /**
     * 默认构造函数 - 兼容版本
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public FillStrategy() {
        this.fillHandler = new FillHandler(AppState.getInstance());
        this.currentMode = FillMode.POINT_FILL;
        this.fillOpacity = 1.0f; // 默认不透明
        this.multipleMode = false;
        this.selectedBoundaryShapes = new ArrayList<>();
        this.isBoundarySelectionMode = false;
        reset();
    }
    
    @Override
    public void reset() {
        currentState = FillState.SELECTING_TARGET;
        if (targetShape != null) {
            targetShape.setHighlighted(false);
        }
        targetShape = null;
        fillPoint = null;
        modifyCommand = null;
        selectedBoundaryShapes.clear();
        isBoundarySelectionMode = false;
        
        // 清除所有边界选择的高亮
        clearBoundaryHighlights();
        
        // 重置选择状态
        resetSelectionState();
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != 0) return ModifyResult.IGNORED; // 只响应左键
        
        if (currentMode == FillMode.BOUNDARY_FILL) {
            return handleBoundaryFillMouseDown(pos, context);
        } else {
            // 点击填充模式：直接在点击位置填充
            fillPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            currentState = FillState.READY_TO_FILL;
            return performFill(context);
        }
    }
    
    /**
     * 处理边界填充模式的鼠标按下事件
     */
    private ModifyResult handleBoundaryFillMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentState == FillState.SELECTING_TARGET) {
            // 第一步：使用BaseSelectionStrategy的选择逻辑
            ModifyResult selectionResult = super.handleSelectionMouseDown(pos, context);
            
            // 如果选择完成，更新状态
            if (selectionResult == ModifyResult.COMPLETE) {
                selectedBoundaryShapes = getSelectedShapesFromIds(context);
                if (!selectedBoundaryShapes.isEmpty()) {
                    currentState = FillState.READY_TO_FILL;
                    context.setStatusMessage("已选择边界，点击区域内部确认填充");
                    return ModifyResult.CONTINUE;
                }
            }
            
            return selectionResult;
        } else if (currentState == FillState.READY_TO_FILL) {
            // 第二步：在区域内点击确认填充
            fillPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            return performFill(context);
        }
        
        return ModifyResult.IGNORED;
    }
    
    /**
     * 清除所有边界选择的高亮
     */
    private void clearBoundaryHighlights() {
        for (Shape shape : selectedBoundaryShapes) {
            try {
                shape.setHighlighted(false);
            } catch (Exception ignored) {}
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentMode == FillMode.BOUNDARY_FILL && currentState == FillState.SELECTING_TARGET) {
            // 使用BaseSelectionStrategy的鼠标移动逻辑
            return super.handleSelectionMouseMove(pos, context);
        }
        
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        if (currentMode == FillMode.BOUNDARY_FILL && currentState == FillState.SELECTING_TARGET) {
            // 使用BaseSelectionStrategy的鼠标释放逻辑
            return super.handleSelectionMouseUp(pos, context);
        }
        
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case 27: // ESC
                reset();
                context.setStatusMessage(getStatusMessage());
                return ModifyResult.CANCEL;
                
            case 66: // B键 - 切换填充模式
                currentMode = (currentMode == FillMode.POINT_FILL) ? 
                             FillMode.BOUNDARY_FILL : FillMode.POINT_FILL;
                reset();
                context.setStatusMessage(getStatusMessage());
                return ModifyResult.CONTINUE;
                
            case 77: // M键 - 切换连续模式
                multipleMode = !multipleMode;
                context.setStatusMessage(String.format("连续填充模式: %s", 
                    multipleMode ? "开启" : "关闭"));
                return ModifyResult.CONTINUE;
                
            case 13: // Enter键 - 确认边界选择
                if (currentMode == FillMode.BOUNDARY_FILL && currentState == FillState.SELECTING_TARGET) {
                    if (hasSelection()) {
                        selectedBoundaryShapes = getSelectedShapesFromIds(context);
                        currentState = FillState.READY_TO_FILL;
                        context.setStatusMessage("已选择边界，点击区域内部确认填充");
                        return ModifyResult.CONTINUE;
                    } else {
                        context.setStatusMessage("请先选择至少一个边界");
                        return ModifyResult.IGNORED;
                    }
                }
                break;
        }
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        // 处理Ctrl键释放
        if (keyCode == 17 || keyCode == 341 || keyCode == 345) { // Ctrl键（AWT/GLFW）
            isCtrlPressed = false;
            return ModifyResult.CONTINUE;
        }
        return IModifyStrategy.super.onKeyUp(keyCode, context);
    }
    
    @Override
    public String getStrategyName() {
        return "FillStrategy";
    }
    
    @Override
    public String getStrategyDescription() {
        return "填充策略 - " + currentMode.getDescription();
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        return modifyCommand;
    }
    
    @Override
    public void renderPreview(DrawContext context) {
        // 渲染选择预览
        if (currentMode == FillMode.BOUNDARY_FILL && currentState == FillState.SELECTING_TARGET) {
            super.renderSelectionPreview(context);
        }
        
        // 渲染边界选择的高亮效果
        if (currentMode == FillMode.BOUNDARY_FILL && !selectedBoundaryShapes.isEmpty()) {
            // 这里可以添加特殊的渲染效果
        }
    }
    
    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        // ImGui预览渲染实现
        if (currentMode == FillMode.BOUNDARY_FILL && currentState == FillState.SELECTING_TARGET) {
            super.renderSelectionPreviewImGui(drawList, camera);
        }
        
        if (currentMode == FillMode.BOUNDARY_FILL && !selectedBoundaryShapes.isEmpty()) {
            // 渲染选中的边界
            for (Shape shape : selectedBoundaryShapes) {
                // 这里可以添加特殊的ImGui渲染效果
            }
        }
    }
    
    public String getStatusMessage() {
        switch (currentMode) {
            case POINT_FILL:
                return "点击要填充的区域，按B键切换模式，按M键切换连续模式";
            case BOUNDARY_FILL:
                if (currentState == FillState.SELECTING_TARGET) {
                    if (hasSelection()) {
                        return String.format("已选择 %d 个边界，继续选择或按Enter确认，按B键切换模式", getSelectedCount());
                    } else {
                        return "选择要填充的边界，按B键切换模式，按M键切换连续模式";
                    }
                } else {
                    return "点击确认填充位置";
                }
            default:
                return "填充工具";
        }
    }
    
    private ModifyResult performFill(ModifyToolContext context) {
        try {
            if (fillPoint == null) {
                context.setStatusMessage("错误：未指定填充点");
                return ModifyResult.IGNORED;
            }
            
            // 创建填充参数
            fillParameters = FillHandler.createFillParameters(fillPoint, fillOpacity);
            fillConstraints = new ModifyConstraints();
            
            // 获取要检查的图形列表
            List<Shape> shapesInput = getShapesForFill(context);
            
            // 使用handler执行填充操作
            IModifyHandler.ModifyResult result = fillHandler.performModification(shapesInput, fillParameters);
            
            if (!result.isSuccess()) {
                context.setStatusMessage(result.getMessage());
                return ModifyResult.IGNORED;
            }
            
            // 获取修改命令
            modifyCommand = result.getCommand();
            if (modifyCommand == null) {
                context.setStatusMessage("创建填充命令失败");
                return ModifyResult.IGNORED;
            }
            
            context.setStatusMessage(String.format("填充完成，应用了%s样式", getFillStyleName()));
            
            if (multipleMode) {
                // 连续模式：维持策略状态，继续下一次
                prepareForNextOperation();
                return ModifyResult.CONTINUE;
            } else {
                // 单次模式：交给调用方在执行命令后进行重置
                return ModifyResult.COMPLETE;
            }
        } catch (Exception e) {
            LOGGER.error("填充操作失败: {}", e.getMessage(), e);
            context.setStatusMessage("填充操作失败: " + e.getMessage());
            return ModifyResult.IGNORED;
        }
    }
    
    /**
     * 获取用于填充的图形列表
     */
    private List<Shape> getShapesForFill(ModifyToolContext context) {
        com.masterplanner.core.state.AppState appState =
            (com.masterplanner.core.state.AppState) context.getAppState();
        
        if (currentMode == FillMode.BOUNDARY_FILL && !selectedBoundaryShapes.isEmpty()) {
            // 边界填充模式：使用选中的边界图形
            return new ArrayList<>(selectedBoundaryShapes);
        } else {
            // 点击填充模式：使用活动图层的所有图形
            return appState.getActiveLayer() != null
                    ? appState.getActiveLayer().getShapes()
                    : appState.getShapes();
        }
    }
    
    /**
     * 获取填充样式名称
     */
    private String getFillStyleName() {
        return "实体"; // 统一使用实体填充
    }
    
    private void prepareForNextOperation() {
        if (targetShape != null) {
            targetShape.setHighlighted(false);
        }
        targetShape = null;
        fillPoint = null;
        currentState = FillState.SELECTING_TARGET;
        
        // 边界填充模式保持边界选择状态
        if (currentMode == FillMode.POINT_FILL) {
            selectedBoundaryShapes.clear();
            clearBoundaryHighlights();
            resetSelectionState();
        }
    }
    
    // 配置方法
    public void updateConfig(String key, Object value) {
        switch (key) {
            case "fillOpacity":
                try {
                    // 在策略内部进行类型转换
                    float opacity = Float.parseFloat(String.valueOf(value));
                    this.fillOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
                } catch (NumberFormatException e) {
                    LOGGER.warn("无效的透明度值: {}", value);
                }
                break;
            case "multipleMode":
                this.multipleMode = Boolean.parseBoolean(String.valueOf(value));
                break;
            case "mode":
                // 处理模式切换
                if (value instanceof String modeStr) {
                    try {
                        this.currentMode = FillMode.valueOf(modeStr);
                        reset(); // 切换模式时重置状态
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("无效的填充模式: {}", modeStr);
                    }
                }
                break;
        }
    }
} 