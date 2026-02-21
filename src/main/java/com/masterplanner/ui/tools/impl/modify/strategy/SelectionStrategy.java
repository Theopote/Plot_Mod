package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.tools.impl.modify.helper.GeometricSelectionHelper;
import com.masterplanner.ui.tools.impl.modify.ControlPointEditTool;
import imgui.ImColor;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.masterplanner.core.state.AppState;
// import com.masterplanner.core.command.commands.DeleteShapesCommand; // 未使用

/**
 * 选择策略实现 - 完整版本
 * 
 * <p>处理图形选择的交互逻辑，支持多种选择模式：</p>
 * <ul>
 *   <li>点击选择：单击图形进行选择，支持Ctrl键加选/减选</li>
 *   <li>框选模式：拖动矩形框选择多个图形</li>
 *   <li>  - 从左到右：实线框，只选择完全包含的图形</li>
 *   <li>  - 从右到左：虚线框，选择相交的图形</li>
 *   <li>套索选择：自由绘制选择区域</li>
 *   <li>组合选择：Ctrl键配合进行添加/移除选择</li>
 * </ul>
 * 
 * <p><strong>策略特点：</strong></p>
 * <ul>
 *   <li>不需要预选择图形 - 自管理选择状态</li>
 *   <li>支持多种选择模式动态切换</li>
 *   <li>智能选择方向检测（窗口选择 vs 交叉选择）</li>
 *   <li>完整的键盘辅助支持</li>
 *   <li>实时渲染预览</li>
 * </ul>
 * 
 * <p><strong>选择清除逻辑（已修复）：</strong></p>
 * <ul>
 *   <li><strong>点选模式</strong>：普通点击时先清除所有选择，然后只选中当前图形</li>
 *   <li><strong>框选模式</strong>：非Ctrl模式下在应用新选择前清除旧选择，确保替换而非添加</li>
 *   <li><strong>套索模式</strong>：非Ctrl模式下在应用新选择前清除旧选择</li>
 *   <li><strong>Ctrl模式</strong>：保持添加/移除选择的逻辑不变</li>
 *   <li><strong>空白点击</strong>：点击空白区域时清除所有选择</li>
 * </ul>
 * 
 * <p><strong>DRY原则优化：</strong></p>
 * <ul>
 *   <li><strong>职责分离</strong>：onMouseDown只处理需要立即响应的Ctrl+点击逻辑</li>
 *   <li><strong>统一处理</strong>：所有点选的最终确定逻辑都在onMouseUp中完成</li>
 *   <li><strong>避免重复</strong>：消除了onMouseDown和onMouseUp之间的逻辑重复</li>
 *   <li><strong>逻辑集中</strong>：选择状态的最终修改都在鼠标释放时进行</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 2.2 - DRY原则优化
 */
public class SelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionStrategy.class);
    
    // 选择模式枚举
    public enum SelectionMode {
        NORMAL("普通选择", "点击选择图形，拖动框选多个图形"),
        LASSO("套索选择", "按住鼠标绘制自由选择区域");
        
        private final String displayName;
        private final String description;
        
        SelectionMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 常量
    private static final double SELECTION_TOLERANCE = 5.0;
    private static final double DRAG_THRESHOLD = 4.0; // 拖动阈值，小于此值视为点选
    private static final double LASSO_MIN_DISTANCE = 3.0; // 套索点最小间距
    
    // 渲染常量（画布固定色，与UI主题解耦）
    private static final int SELECTION_ALPHA = 255;
    private static final Color CANVAS_SELECTION_COLOR = new Color(255, 215, 0, 255);
    private static final int IMGUI_SELECTION_COLOR = ImColor.rgba(255, 215, 0, 255);
    private static final float SELECTION_LINE_WIDTH = 1.5f;
    // 预留：选中高亮色（当前未直接使用，由Shape自身渲染样式处理）
    
    // 策略状态
    private SelectionMode currentMode = SelectionMode.NORMAL;
    private boolean isSelecting = false;
    private boolean isLassoSelecting = false;
    private boolean isCtrlPressed = false;
    private boolean isPointSelecting = false; // 是否为点选模式
    private boolean isLeftToRight = true; // 选择方向：true=从左到右，false=从右到左
    
    // 坐标状态
    private Vec2d startPoint;
    private Vec2d currentPoint;
    private Vec2d initialClickPoint;
    
    // 选择数据
    private final List<Vec2d> lassoPoints = new ArrayList<>();
    private final Set<String> selectedShapeIds = new HashSet<>();
    private final Set<String> tempSelectedShapeIds = new HashSet<>(); // 临时选择（预览用）
    
    // 鼠标悬停状态
    private Shape hoveredShape = null; // 当前鼠标悬停的图形
    
    // 控制点编辑策略缓存
    private com.masterplanner.ui.tools.impl.modify.strategy.ControlPointEditStrategy cachedEditStrategy = null;
    
    /**
     * 默认构造函数
     */
    public SelectionStrategy() {
        // 策略模式下，依赖通过上下文提供
    }
    
    /**
     * 设置选择模式
     */
    public void setSelectionMode(SelectionMode mode) {
        LOGGER.info("SelectionStrategy.setSelectionMode: 收到模式设置请求: {}", mode);
        if (mode == null) {
            LOGGER.warn("尝试设置null选择模式，忽略");
            return;
        }
        
        if (this.currentMode != mode) {
            LOGGER.info("SelectionStrategy: 选择模式变更 {} -> {}", this.currentMode, mode);
            reset(); // 切换模式时重置状态
            this.currentMode = mode;
        } else {
            LOGGER.debug("SelectionStrategy: 模式没有变化，当前已经是: {}", this.currentMode);
        }
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != 0) { // 只处理左键
            return ModifyResult.IGNORED;
        }
        
        // 检查是否在控制点编辑模式下
        ControlPointEditTool editTool = context.getControlPointEditTool();
        if (editTool != null && editTool.isActive()) {
            // 在控制点编辑模式下，完全委托给控制点编辑策略处理
            LOGGER.debug("SelectionStrategy: 在控制点编辑模式下，委托给控制点编辑策略处理鼠标按下");
            if (cachedEditStrategy == null) {
                cachedEditStrategy = new com.masterplanner.ui.tools.impl.modify.strategy.ControlPointEditStrategy(editTool);
            }
            return cachedEditStrategy.onMouseDown(pos, button, context);
        }
        
        try {
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            startPoint = snappedPoint;
            currentPoint = snappedPoint;
            initialClickPoint = snappedPoint;
            isSelecting = true;
            isPointSelecting = true; // 初始为点选模式
            
            LOGGER.info("SelectionStrategy: 鼠标按下 at {}, 模式: {}, isSelecting设置为true", snappedPoint, currentMode);
            
            // 修复：移除过早的清除选择逻辑，让具体的选择方法决定何时清除
            // 原来的代码：if (!isCtrlPressed) { clearSelection(context); }
            
            switch (currentMode) {
                case NORMAL -> {
                    return handleNormalModeMouseDown(snappedPoint, context);
                }
                case LASSO -> {
                    return handleLassoModeMouseDown(snappedPoint, context);
                }
                default -> {
                    return ModifyResult.IGNORED;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("选择策略鼠标按下处理失败: {}", e.getMessage(), e);
            reset();
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        // 检查是否在控制点编辑模式下
        ControlPointEditTool editTool = context.getControlPointEditTool();
        if (editTool != null && editTool.isActive()) {
            // 在控制点编辑模式下，完全委托给控制点编辑策略处理
            LOGGER.debug("SelectionStrategy: 在控制点编辑模式下，委托给控制点编辑策略处理鼠标移动");
            if (cachedEditStrategy == null) {
                cachedEditStrategy = new com.masterplanner.ui.tools.impl.modify.strategy.ControlPointEditStrategy(editTool);
            }
            return cachedEditStrategy.onMouseMove(pos, context);
        }
        
        if (!isSelecting) {
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            currentPoint = snappedPoint;
            
            // 检查是否从点选模式切换到框选模式
            if (isPointSelecting && initialClickPoint != null) {
                double moveDistance = initialClickPoint.distance(snappedPoint);
                if (moveDistance > DRAG_THRESHOLD) {
                    // 超过拖动阈值，切换到框选模式
                    isPointSelecting = false;
                    LOGGER.info("SelectionStrategy: 从点选切换到框选模式，移动距离: {}", moveDistance);
                }
            }
            
            LOGGER.debug("SelectionStrategy: 鼠标移动 to {}, isSelecting={}, isPointSelecting={}", 
                        snappedPoint, isSelecting, isPointSelecting);
            
            switch (currentMode) {
                case NORMAL -> {
                    return handleNormalModeMouseMove(snappedPoint, context);
                }
                case LASSO -> {
                    return handleLassoModeMouseMove(snappedPoint, context);
                }
                default -> {
                    return ModifyResult.CONTINUE;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("选择策略鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE; // 移动失败不应该取消选择
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 检查是否在控制点编辑模式下
        ControlPointEditTool editTool = context.getControlPointEditTool();
        if (editTool != null && editTool.isActive()) {
            // 在控制点编辑模式下，完全委托给控制点编辑策略处理
            LOGGER.debug("SelectionStrategy: 在控制点编辑模式下，委托给控制点编辑策略处理鼠标释放");
            if (cachedEditStrategy == null) {
                cachedEditStrategy = new com.masterplanner.ui.tools.impl.modify.strategy.ControlPointEditStrategy(editTool);
            }
            return cachedEditStrategy.onMouseUp(pos, button, context);
        }
        
        if (button != 0 || !isSelecting) {
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            currentPoint = snappedPoint;
            
            LOGGER.debug("SelectionStrategy: 鼠标释放 at {}, 点选模式: {}", snappedPoint, isPointSelecting);
            
            switch (currentMode) {
                case NORMAL -> {
                    return handleNormalModeMouseUp(snappedPoint, context);
                }
                case LASSO -> {
                    return handleLassoModeMouseUp(snappedPoint, context);
                }
                default -> {
                    isSelecting = false;
                    return ModifyResult.COMPLETE;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("选择策略鼠标释放处理失败: {}", e.getMessage(), e);
            reset();
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        LOGGER.debug("SelectionStrategy.onKeyDown: keyCode={}, selectedShapeIds.size={}", keyCode, selectedShapeIds.size());
        
        switch (keyCode) {
            case 17, 341, 345 -> { // Ctrl键（17=AWT, 341/345=GLFW 左/右Ctrl）
                isCtrlPressed = true;
                LOGGER.debug("SelectionStrategy: Ctrl键按下");
                return ModifyResult.CONTINUE;
            }
            case 27 -> { // Esc键
                reset();
                context.clearSelection();
                context.setStatusMessage("选择已取消");
                LOGGER.debug("SelectionStrategy: Esc键按下，取消选择");
                return ModifyResult.CANCEL;
            }
            case 65 -> { // A键 - 全选
                if (isCtrlPressed) {
                    selectAll(context);
                    LOGGER.debug("SelectionStrategy: Ctrl+A 全选");
                    return ModifyResult.COMPLETE;
                }
                LOGGER.debug("SelectionStrategy: A键按下（非Ctrl）");
                return ModifyResult.IGNORED;
            }
            case 46, 261 -> { // Delete键（46=AWT, 261=GLFW）
                LOGGER.debug("SelectionStrategy: Delete键按下，selectedShapeIds.size={}", selectedShapeIds.size());
                if (!selectedShapeIds.isEmpty()) {
                    deleteSelectedShapes(context);
                    LOGGER.debug("SelectionStrategy: 执行删除操作");
                    return ModifyResult.COMPLETE;
                }
                LOGGER.debug("SelectionStrategy: Delete键按下，但没有选中的图形");
                return ModifyResult.IGNORED;
            }
            default -> {
                LOGGER.debug("SelectionStrategy: 未处理的按键 keyCode={}", keyCode);
                return ModifyResult.IGNORED;
            }
        }
    }
    
    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        if (keyCode == 17 || keyCode == 341 || keyCode == 345) { // Ctrl键（AWT/GLFW）
            isCtrlPressed = false;
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }
    
    // ====== 模式特定的处理方法 ======
    
    private ModifyResult handleNormalModeMouseDown(Vec2d pos, ModifyToolContext context) {
        // 优化：只处理Ctrl键的逻辑，因为它需要立即响应
        // 普通点击的决定权留给onMouseUp，避免重复逻辑
        if (isCtrlPressed) {
            Shape clickedShape = context.findShapeAt(pos, SELECTION_TOLERANCE);
            if (clickedShape != null) {
                LOGGER.debug("SelectionStrategy: Ctrl+点击到图形 {}", clickedShape.getId());
                if (selectedShapeIds.contains(clickedShape.getId())) {
                    selectedShapeIds.remove(clickedShape.getId());
                    updateShapeSelection(clickedShape, false, context);
                    LOGGER.debug("SelectionStrategy: 取消选择图形 {}", clickedShape.getId());
                } else {
                    selectedShapeIds.add(clickedShape.getId());
                    updateShapeSelection(clickedShape, true, context);
                    LOGGER.debug("SelectionStrategy: 选中图形 {}", clickedShape.getId());
                }
                context.setStatusMessage(String.format("已选择 %d 个图形", selectedShapeIds.size()));
            }
        }
        // 普通点击不做任何处理，等待onMouseUp统一处理
        return ModifyResult.CONTINUE;
    }
    
    private ModifyResult handleNormalModeMouseMove(Vec2d pos, ModifyToolContext context) {
        currentPoint = pos;
        
        if (isSelecting) {
            // 框选模式：确定选择方向并更新临时选择
                if (startPoint != null) {
                // 仅以水平拖动方向决定窗口/交叉选择（CAD约定）：
                // true = 左到右（窗口选择，完全包含），false = 右到左（交叉选择，相交即选）
                isLeftToRight = pos.x >= startPoint.x;
                
                context.setStatusMessage(isLeftToRight ? 
                    "从左到右选择：只选择完全包含的图形" : 
                    "从右到左选择：选择相交的图形");
                
                updateTemporarySelection(context);
                LOGGER.trace("SelectionStrategy: 框选方向 {}", isLeftToRight ? "左到右" : "右到左");
            }
        } else {
            // 非选择模式：处理鼠标悬停高亮
            Shape shapeUnderMouse = context.findShapeAt(pos, SELECTION_TOLERANCE);
            updateHoverHighlight(shapeUnderMouse);
        }
        return ModifyResult.CONTINUE;
    }
    
    private ModifyResult handleNormalModeMouseUp(Vec2d pos, ModifyToolContext context) {
        if (isPointSelecting) {
            // 优化：点选模式的最终处理，统一处理所有点选逻辑
            if (!isCtrlPressed) {
                // 如果不是Ctrl模式，先清除所有之前的选择
                clearSelection(context);
                
                Shape clickedShape = context.findShapeAt(pos, SELECTION_TOLERANCE);
                if (clickedShape != null) {
                    // 如果点到了图形，就选中它
                    selectedShapeIds.add(clickedShape.getId());
                    updateShapeSelection(clickedShape, true, context);
                    LOGGER.debug("SelectionStrategy: 完成点选，选中图形 {}", clickedShape.getId());
                    
                    // 激活控制点编辑模式
                    activateControlPointEdit(clickedShape, context);
                } else {
                    // 如果点在空白处，选择集已在上面被清空
                    LOGGER.debug("SelectionStrategy: 点击空白区域，清除所有选择");
                }
            }
            // 如果是Ctrl模式，由于MouseDown已经处理了，这里不需要做任何事
            
            LOGGER.debug("SelectionStrategy: 完成点选，选中 {} 个形状", selectedShapeIds.size());
        } else {
            // 框选模式：在finalizeBoxSelection中处理选择清除逻辑
            finalizeBoxSelection(context);
            LOGGER.debug("SelectionStrategy: 完成框选，选中 {} 个形状", selectedShapeIds.size());
            
            // 如果框选只选中了一个图形，也激活控制点编辑模式
            if (selectedShapeIds.size() == 1) {
                context.getSelectedShapes().stream().findFirst().ifPresent(selectedShape -> activateControlPointEdit(selectedShape, context));
            }
        }
        
        // 重置状态
        isSelecting = false;
        isPointSelecting = false;
        clearTemporarySelection(context);
        
        return ModifyResult.COMPLETE;
    }
    
    private ModifyResult handleLassoModeMouseDown(Vec2d pos, ModifyToolContext context) {
        // 开始套索选择
        lassoPoints.clear();
        lassoPoints.add(pos);
        isLassoSelecting = true;
        LOGGER.debug("SelectionStrategy: 开始套索选择，起始点: {}", pos);
        return ModifyResult.CONTINUE;
    }
    
    private ModifyResult handleLassoModeMouseMove(Vec2d pos, ModifyToolContext context) {
        if (isLassoSelecting && !lassoPoints.isEmpty()) {
            // 优化点采样：添加距离阈值，仅当新点与上一点距离超过一定值时才加入
            Vec2d lastPoint = lassoPoints.getLast();
            double distance = lastPoint.distance(pos);
            
            if (distance > LASSO_MIN_DISTANCE) {
                lassoPoints.add(pos);
                LOGGER.trace("SelectionStrategy: 添加套索点 #{} at {}", lassoPoints.size(), pos);
            }
        }
        return ModifyResult.CONTINUE;
    }
    
    private ModifyResult handleLassoModeMouseUp(Vec2d pos, ModifyToolContext context) {
        if (isLassoSelecting && lassoPoints.size() > 2) {
            // 完成套索选择
            finalizeLassoSelection(context);
            LOGGER.debug("SelectionStrategy: 完成套索选择，选中 {} 个形状", selectedShapeIds.size());
        }
        
        // 重置状态
        isSelecting = false;
        isLassoSelecting = false;
        lassoPoints.clear();
        
        return ModifyResult.COMPLETE;
    }
    
    // ====== 辅助方法 ======
    
    
    /**
     * 激活控制点编辑模式
     */
    private void activateControlPointEdit(Shape shape, ModifyToolContext context) {
        try {
            // 获取控制点编辑工具
            com.masterplanner.ui.tools.impl.modify.ControlPointEditTool editTool = 
                context.getControlPointEditTool();
            
            if (editTool != null) {
                // 检查图形是否有控制点
                List<Vec2d> controlPoints = shape.getControlPoints();
                if (controlPoints != null && !controlPoints.isEmpty()) {
                    editTool.activate(shape);
                    context.setStatusMessage("已进入控制点编辑模式，拖拽控制点调整图形");
                    LOGGER.debug("SelectionStrategy: 激活控制点编辑模式，图形: {}", shape.getClass().getSimpleName());
                } else {
                    LOGGER.debug("SelectionStrategy: 图形没有控制点，跳过控制点编辑模式");
                }
            } else {
                LOGGER.warn("SelectionStrategy: 无法获取控制点编辑工具");
            }
        } catch (Exception e) {
            LOGGER.error("SelectionStrategy: 激活控制点编辑模式失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 清除所有选择
     */
    private void clearSelection(ModifyToolContext context) {
        // 清除所有已选中图形的选中状态（跨图层）
        try {
            List<Shape> allShapes = AppState.getInstance().getShapes();
            for (Shape shape : allShapes) {
                if (selectedShapeIds.contains(shape.getId())) {
                    shape.setSelected(false);
                    shape.setHighlighted(false);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("清除选择状态时出错: {}", e.getMessage());
        }
        
        // 清除选择状态
        context.clearSelection();
        selectedShapeIds.clear();
        LOGGER.debug("SelectionStrategy: 已清除所有选择");
        
        // 更新状态消息
        context.setStatusMessage("已取消所有选择");
    }
    
    /**
     * 更新图形选择状态
     */
    private void updateShapeSelection(Shape shape, boolean selected, ModifyToolContext context) {
        if (shape != null) {
            shape.setSelected(selected);
            
            // 修复：被选中的图形不需要额外设置highlighted状态
            // Shape.render()方法会根据isSelected()状态自动应用SELECTED样式
            if (selected) {
                LOGGER.debug("图形 {} 已选中", shape.getId());
                context.addSelectedShape(shape);
            } else {
                // 取消选择时，确保清除所有高亮状态
                shape.setHighlighted(false);
                LOGGER.debug("图形 {} 已取消选中", shape.getId());
                context.removeSelectedShape(shape);
    }
        }
    }
    
    /**
     * 更新鼠标悬停高亮
     */
    private void updateHoverHighlight(Shape newHoveredShape) {
        if (hoveredShape != newHoveredShape) {
            // 清除之前的悬停高亮
            if (hoveredShape != null && !hoveredShape.isSelected()) {
                hoveredShape.setHighlighted(false);
            }
            
            // 设置新的悬停高亮
            hoveredShape = newHoveredShape;
            if (hoveredShape != null && !hoveredShape.isSelected()) {
                hoveredShape.setHighlighted(true);
                LOGGER.trace("图形 {} 悬停高亮", hoveredShape.getId());
            }
        }
    }
    
    /**
     * 更新临时选择（框选预览）
     */
    private void updateTemporarySelection(ModifyToolContext context) {
        if (startPoint == null || currentPoint == null) return;
        
        // 清除之前的临时选择高亮
        clearTemporarySelection(context);
        
        // 计算选择框范围内的形状
        List<Shape> shapesInArea = context.findShapesInArea(startPoint, currentPoint);
        
        for (Shape shape : shapesInArea) {
            if (isShapeInSelection(shape, startPoint, currentPoint, isLeftToRight)) {
                tempSelectedShapeIds.add(shape.getId());
                // 为临时选择的图形添加高亮效果（但不覆盖已选中的图形）
                if (!shape.isSelected()) {
                    shape.setHighlighted(true);
                }
            }
        }
    }
    
    /**
     * 清除临时选择
     */
    private void clearTemporarySelection(ModifyToolContext context) {
        // 清除之前临时选择图形的高亮（跨图层）
        if (!tempSelectedShapeIds.isEmpty()) {
            try {
                List<Shape> allShapes = AppState.getInstance().getShapes();
                for (Shape shape : allShapes) {
                    if (tempSelectedShapeIds.contains(shape.getId()) && !shape.isSelected()) {
                        shape.setHighlighted(false);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("清除临时选择高亮时出错: {}", e.getMessage());
            }
        }
        tempSelectedShapeIds.clear();
    }
    
    /**
     * 完成框选
     */
    private void finalizeBoxSelection(ModifyToolContext context) {
        if (startPoint == null || currentPoint == null) return;
        
        // 修复：如果不是Ctrl多选模式，则在应用新选择前清除旧选择
        if (!isCtrlPressed) {
            clearSelection(context);
            LOGGER.debug("SelectionStrategy: 框选前清除旧选择（非Ctrl模式）");
        }
        
        List<Shape> shapesInArea = context.findShapesInArea(startPoint, currentPoint);
        
        for (Shape shape : shapesInArea) {
            if (isShapeInSelection(shape, startPoint, currentPoint, isLeftToRight)) {
                if (isCtrlPressed && selectedShapeIds.contains(shape.getId())) {
                    // Ctrl+框选已选中形状，取消选择
                    selectedShapeIds.remove(shape.getId());
                    updateShapeSelection(shape, false, context);
                } else if (!selectedShapeIds.contains(shape.getId())) {
                    // 新选中的形状
                    selectedShapeIds.add(shape.getId());
                    updateShapeSelection(shape, true, context);
                }
            }
        }
        
        context.setStatusMessage(String.format("框选完成，已选择 %d 个图形", selectedShapeIds.size()));
    }
    
    /**
     * 完成套索选择
     */
    private void finalizeLassoSelection(ModifyToolContext context) {
        // 闭合套索
        if (lassoPoints.size() > 2) {
            performLassoContainmentSelection(context);
            context.setStatusMessage(String.format("套索（完全包含）选择完成，已选择 %d 个图形", selectedShapeIds.size()));
        }
    }

    /**
     * 套索选择采用完全包含规则：图形需完全位于套索内部且不与套索边界相交
     */
    private void performLassoContainmentSelection(ModifyToolContext context) {
        // 如果不是Ctrl多选模式，则在应用新选择前清除旧选择
        if (!isCtrlPressed) {
            clearSelection(context);
            LOGGER.debug("SelectionStrategy: 套索选择前清除旧选择（非Ctrl模式）");
        }

        // 确保套索闭合
        if (lassoPoints.size() > 2) {
            Vec2d firstPoint = lassoPoints.getFirst();
            Vec2d lastPoint = lassoPoints.getLast();
            if (firstPoint.distance(lastPoint) > LASSO_MIN_DISTANCE) {
                lassoPoints.add(firstPoint);
            }
        }

        // 候选图形（先按边界框过滤）
        BoundingBox lassoBounds = calculateLassoBounds();
        List<Shape> candidateShapes = context.findShapesInArea(lassoBounds.getMin(), lassoBounds.getMax());

        for (Shape shape : candidateShapes) {
            if (isShapeFullyInsideLasso(shape, lassoPoints)) {
                if (isCtrlPressed && selectedShapeIds.contains(shape.getId())) {
                    selectedShapeIds.remove(shape.getId());
                    updateShapeSelection(shape, false, context);
                } else if (!selectedShapeIds.contains(shape.getId())) {
                    selectedShapeIds.add(shape.getId());
                    updateShapeSelection(shape, true, context);
                }
            }
        }
    }
    
    private boolean isShapeInSelection(Shape shape, Vec2d start, Vec2d end, boolean leftToRight) {
        try {
            // 使用精确的几何选择逻辑，而不是包围框检测
            boolean result = GeometricSelectionHelper.isShapeInRectangleSelection(shape, start, end, leftToRight);
            
            LOGGER.trace("精确框选择检测: 图形 {} {} {}选中 (方向: {})", 
                        shape.getClass().getSimpleName(), 
                        shape.getId(),
                        result ? "被" : "未被",
                        leftToRight ? "左到右" : "右到左");
            
            return result;
            
        } catch (Exception e) {
            LOGGER.warn("精确几何选择检测失败，回退到包围框检测: {}", e.getMessage());
            
            // 回退到原始的包围框检测逻辑
            BoundingBox shapeBounds = shape.getBoundingBox();
            if (shapeBounds == null) {
                return false;
            }
            
            // 计算选择区域
            double minX = Math.min(start.x, end.x);
            double minY = Math.min(start.y, end.y);
            double maxX = Math.max(start.x, end.x);
            double maxY = Math.max(start.y, end.y);
            
            if (leftToRight) {
                // 从左到右：窗口选择，图形必须完全包含在选择框内
                return shapeBounds.getMinX() >= minX && shapeBounds.getMinY() >= minY &&
                       shapeBounds.getMaxX() <= maxX && shapeBounds.getMaxY() <= maxY;
            } else {
                // 从右到左：交叉选择，图形与选择框相交即可
                return !(shapeBounds.getMaxX() < minX || shapeBounds.getMinX() > maxX ||
                        shapeBounds.getMaxY() < minY || shapeBounds.getMinY() > maxY);
            }
        }
    }
    
    private boolean isShapeFullyInsideLasso(Shape shape, List<Vec2d> lasso) {
        try {
            // 规则：完全包含 + 不与套索边界相交
            // 1) 如与套索边界相交，则不是完全包含
            if (com.masterplanner.ui.tools.impl.modify.helper.GeometricSelectionHelper.isLassoIntersectsShape(shape, lasso)) {
                return false;
            }

            // 2) 所有代表性点（优先控制点，退化用包围框角点）均在套索内
            List<Vec2d> reps = shape.getControlPoints();
            if (reps == null || reps.isEmpty()) {
                BoundingBox bounds = shape.getBoundingBox();
                if (bounds != null) {
                    reps = java.util.Arrays.asList(bounds.getCorners());
                }
            }
            if (reps == null || reps.isEmpty()) {
                return false;
            }

            for (Vec2d p : reps) {
                if (!com.masterplanner.ui.tools.impl.modify.helper.GeometricSelectionHelper.isPointInPolygon(p, lasso)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("套索完全包含检测失败: {}", e.getMessage());
            return false;
        }
    }
    
    private BoundingBox calculateLassoBounds() {
        if (lassoPoints.isEmpty()) {
            return new BoundingBox(new Vec2d(0, 0), new Vec2d(0, 0));
        }
        
        double minX = lassoPoints.stream().mapToDouble(p -> p.x).min().orElse(0);
        double maxX = lassoPoints.stream().mapToDouble(p -> p.x).max().orElse(0);
        double minY = lassoPoints.stream().mapToDouble(p -> p.y).min().orElse(0);
        double maxY = lassoPoints.stream().mapToDouble(p -> p.y).max().orElse(0);
        
        return new BoundingBox(new Vec2d(minX, minY), new Vec2d(maxX, maxY));
    }
    
    private void selectAll(ModifyToolContext context) {
        try {
            // 清空现有选择
            selectedShapeIds.clear();
            context.clearSelection();

            // 选择所有可见图形（遍历所有图层的可见、未删除图形）
            List<Shape> allShapes = AppState.getInstance().getShapes();
            for (Shape shape : allShapes) {
                if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                    selectedShapeIds.add(shape.getId());
                    updateShapeSelection(shape, true, context);
                }
            }

            context.setStatusMessage(String.format("已全选 %d 个图形", selectedShapeIds.size()));
        } catch (Exception e) {
            LOGGER.error("执行全选时出错: {}", e.getMessage(), e);
            context.setStatusMessage("全选失败: " + e.getMessage());
        }
    }
    
    private void deleteSelectedShapes(ModifyToolContext context) {
        // 实现删除选中图形的逻辑
        if (!selectedShapeIds.isEmpty()) {
            LOGGER.debug("SelectionStrategy: 删除 {} 个选中图形", selectedShapeIds.size());
            
            try {
                // 获取AppState的选中图形列表
                AppState appState = AppState.getInstance();
                List<Shape> selectedShapes = appState.getSelectedShapes();
                
                if (!selectedShapes.isEmpty()) {
                    // 使用CommandManager执行删除命令，确保命令历史和事件的完整性
                    com.masterplanner.core.command.CommandManager commandManager = 
                        com.masterplanner.core.command.CommandManager.getInstance();
                    
                    com.masterplanner.core.command.commands.DeleteShapesCommand deleteCommand = 
                        new com.masterplanner.core.command.commands.DeleteShapesCommand(
                            new java.util.ArrayList<>(selectedShapes));
                    
                    commandManager.executeCommand(deleteCommand);
                    
                    LOGGER.debug("SelectionStrategy: Delete命令执行成功");
                    context.setStatusMessage(String.format("已删除 %d 个图形", selectedShapes.size()));
                } else {
                    LOGGER.debug("SelectionStrategy: 没有选中的图形需要删除");
                    context.setStatusMessage("没有选中的图形");
                }
                
            } catch (Exception e) {
                LOGGER.error("SelectionStrategy: 执行删除命令时出错: {}", e.getMessage(), e);
                context.setStatusMessage("删除失败: " + e.getMessage());
            }
            
            // 清空选择状态
            selectedShapeIds.clear();
            context.clearSelection();
        } else {
            LOGGER.debug("SelectionStrategy: 没有选中的图形");
            context.setStatusMessage("没有选中的图形");
        }
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        // 选择工具通常不产生修改命令
        return null;
    }
    
    @Override
    public void reset() {
        LOGGER.debug("选择策略重置");
        isSelecting = false;
        isLassoSelecting = false;
        isCtrlPressed = false;
        isPointSelecting = false;
        isLeftToRight = true;
        startPoint = null;
        currentPoint = null;
        initialClickPoint = null;
        lassoPoints.clear();
        selectedShapeIds.clear();
        tempSelectedShapeIds.clear();
        
        // 清除悬停高亮
        if (hoveredShape != null) {
            hoveredShape.setHighlighted(false);
            hoveredShape = null;
        }
        
        // 清理缓存的编辑策略
        cachedEditStrategy = null;
    }
    
    @Override
    public String getStrategyName() {
        return "选择策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
    }
    
    @Override
    public boolean requiresSelection() {
        return false; // 选择工具本身不需要预选择
    }
    
    @Override
    public int getMinimumSelectionCount() {
        return 0; // 选择工具可以从空选择开始
    }
    
    @Override
    public int getMaximumSelectionCount() {
        return -1; // 无限制
    }
    
    // ====== 渲染方法 ======
    
    @Override
    public void renderPreview(DrawContext context) {
        if (!isSelecting) return;
        
        switch (currentMode) {
            case NORMAL -> renderNormalSelectionPreview(context);
            case LASSO -> renderLassoSelectionPreview(context);
        }
    }
    
    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        LOGGER.debug("SelectionStrategy.renderPreview(ImDrawList): isSelecting={}, currentMode={}", isSelecting, currentMode);
        
        if (!isSelecting) {
            LOGGER.debug("SelectionStrategy.renderPreview(ImDrawList): 不在选择状态，跳过渲染");
            return;
        }
        
        LOGGER.debug("SelectionStrategy.renderPreview(ImDrawList): 开始渲染预览，模式: {}", currentMode);
        try {
            switch (currentMode) {
                case NORMAL -> {
                    LOGGER.debug("SelectionStrategy.renderPreview(ImDrawList): 渲染普通选择预览");
                    renderNormalSelectionPreviewImGui(drawList, camera);
                }
                case LASSO -> {
                    LOGGER.debug("SelectionStrategy.renderPreview(ImDrawList): 渲染套索选择预览");
                    renderLassoSelectionPreviewImGui(drawList, camera);
                }
            }
        } catch (Exception e) {
            LOGGER.error("SelectionStrategy.renderPreview(ImDrawList): 渲染预览时出错", e);
        }
    }
    
    /**
     * 渲染普通选择预览（DrawContext版本）
     */
    private void renderNormalSelectionPreview(DrawContext context) {
        if (startPoint == null || currentPoint == null || isPointSelecting) {
            return;
        }
        Color selectionColor = getSelectionColor();
        
        // 根据选择方向绘制不同样式的选择框
        if (isLeftToRight) {
            // 从左到右：实线框
            context.drawRect(startPoint, currentPoint, selectionColor);
        } else {
            // 从右到左：虚线框
            drawDashedRect(context, startPoint, currentPoint);
        }
    }
    
    /**
     * 渲染套索选择预览（DrawContext版本）
     */
    private void renderLassoSelectionPreview(DrawContext context) {
        if (!isLassoSelecting || lassoPoints.size() < 2) {
            return;
        }
        Color lassoColor = getSelectionColor();
        
        // 绘制已确定的套索线段
        for (int i = 1; i < lassoPoints.size(); i++) {
            context.drawLine(lassoPoints.get(i - 1), lassoPoints.get(i), lassoColor);
        }
        
        // 绘制当前鼠标位置到最后一个点的线段
        if (currentPoint != null && !lassoPoints.isEmpty()) {
            Vec2d lastPoint = lassoPoints.getLast();
            context.drawLine(lastPoint, currentPoint, lassoColor);
        }
        
        // 如果有两个以上的点，绘制回到起点的虚线（预览闭合）
        if (lassoPoints.size() > 2 && currentPoint != null) {
            context.drawDashedLine(currentPoint, lassoPoints.getFirst(), lassoColor);
        }
    }
    
    /**
     * 渲染普通选择预览（ImGui版本）
     */
    private void renderNormalSelectionPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        LOGGER.debug("SelectionStrategy.renderNormalSelectionPreviewImGui: startPoint={}, currentPoint={}, isPointSelecting={}", 
                    startPoint, currentPoint, isPointSelecting);
        
        if (startPoint == null || currentPoint == null || isPointSelecting) {
            LOGGER.debug("SelectionStrategy.renderNormalSelectionPreviewImGui: 跳过渲染 - startPoint={}, currentPoint={}, isPointSelecting={}", 
                        startPoint, currentPoint, isPointSelecting);
            return;
        }
        
        try {
            // 转换为屏幕坐标（不加窗口偏移，保持与Canvas图形渲染路径一致）
            Vec2d screenStart = camera.worldToScreen(startPoint);
            Vec2d screenEnd = camera.worldToScreen(currentPoint);
            
            LOGGER.debug("SelectionStrategy.renderNormalSelectionPreviewImGui: 世界坐标 {} -> 屏幕坐标 {}", startPoint, screenStart);
            LOGGER.debug("SelectionStrategy.renderNormalSelectionPreviewImGui: 世界坐标 {} -> 屏幕坐标 {}", currentPoint, screenEnd);
            
            // 直接使用屏幕坐标，不添加额外偏移（ImDrawList已经处理了窗口偏移）
            float minX = (float) Math.min(screenStart.x, screenEnd.x);
            float minY = (float) Math.min(screenStart.y, screenEnd.y);
            float maxX = (float) Math.max(screenStart.x, screenEnd.x);
            float maxY = (float) Math.max(screenStart.y, screenEnd.y);
            
            // 修复：将LOGGER.info改为LOGGER.debug，避免每帧渲染时产生大量日志
            LOGGER.debug("SelectionStrategy.renderNormalSelectionPreviewImGui: 绘制选择框 ({},{}) -> ({},{})", 
                       minX, minY, maxX, maxY);
            
            // 使用与主题无关的画布固定高对比颜色
            int color = IMGUI_SELECTION_COLOR;
            float lineWidth = SELECTION_LINE_WIDTH;
            
            if (isLeftToRight) {
                // 实线框（窗口选择）
                LOGGER.debug("SelectionStrategy.renderNormalSelectionPreviewImGui: 绘制实线框（窗口选择），颜色=0x{}", Integer.toHexString(color));
                drawList.addRect(minX, minY, maxX, maxY, color, 0.0f, 0, lineWidth);
            } else {
                // 虚线框（交叉选择）
                LOGGER.debug("SelectionStrategy.renderNormalSelectionPreviewImGui: 绘制虚线框（交叉选择），颜色=0x{}", Integer.toHexString(color));
                drawDashedRectImGui(drawList, minX, minY, maxX, maxY, color);
            }
            
        } catch (Exception e) {
            LOGGER.error("渲染选择框时出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 渲染套索选择预览（ImGui版本）
     */
    private void renderLassoSelectionPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        if (!isLassoSelecting || lassoPoints.size() < 2) {
            return;
        }
        
        try {
            // 使用与主题无关的画布固定高对比颜色
            int color = IMGUI_SELECTION_COLOR;
            float lineWidth = SELECTION_LINE_WIDTH;
            
            // 绘制已确定的套索线段（不添加窗口偏移）
            for (int i = 1; i < lassoPoints.size(); i++) {
                Vec2d screenStart = camera.worldToScreen(lassoPoints.get(i - 1));
                Vec2d screenEnd = camera.worldToScreen(lassoPoints.get(i));
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    color, lineWidth
                );
            }
            
            // 绘制当前鼠标位置到最后一个点的线段
            if (currentPoint != null && !lassoPoints.isEmpty()) {
                Vec2d lastPoint = lassoPoints.getLast();
                Vec2d screenLast = camera.worldToScreen(lastPoint);
                Vec2d screenCurrent = camera.worldToScreen(currentPoint);
                drawList.addLine(
                    (float) screenLast.x, (float) screenLast.y,
                    (float) screenCurrent.x, (float) screenCurrent.y,
                    color, lineWidth
                );
            }
            
            // 如果有两个以上的点，绘制回到起点的虚线（预览闭合）
            if (lassoPoints.size() > 2 && currentPoint != null) {
                Vec2d screenFirst = camera.worldToScreen(lassoPoints.getFirst());
                Vec2d screenCurrent = camera.worldToScreen(currentPoint);
                // 虚线效果（通过多条短线段模拟），不添加偏移
                drawDashedLineImGui(drawList, screenCurrent, screenFirst, color);
            }
            
        } catch (Exception e) {
            LOGGER.warn("渲染套索选择时出错: {}", e.getMessage());
        }
    }
    
    /**
     * 绘制虚线矩形（DrawContext版本）
     */
    private void drawDashedRect(DrawContext context, Vec2d start, Vec2d end) {
        Vec2d topLeft = new Vec2d(Math.min(start.x, end.x), Math.min(start.y, end.y));
        Vec2d topRight = new Vec2d(Math.max(start.x, end.x), Math.min(start.y, end.y));
        Vec2d bottomLeft = new Vec2d(Math.min(start.x, end.x), Math.max(start.y, end.y));
        Vec2d bottomRight = new Vec2d(Math.max(start.x, end.x), Math.max(start.y, end.y));
        Color dashedColor = getSelectionColor();
        
        context.drawDashedLine(topLeft, topRight, dashedColor);
        context.drawDashedLine(topRight, bottomRight, dashedColor);
        context.drawDashedLine(bottomRight, bottomLeft, dashedColor);
        context.drawDashedLine(bottomLeft, topLeft, dashedColor);
    }

    private Color getSelectionColor() {
        return withAlpha(CANVAS_SELECTION_COLOR, SELECTION_ALPHA);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
    
    /**
     * 绘制虚线矩形（ImGui版本）
     */
    private void drawDashedRectImGui(ImDrawList drawList, float minX, float minY, float maxX, float maxY, int color) {
        // 通过多条短线段模拟虚线效果
        float dashLength = 8.0f;  // 稍微长一点的虚线段
        float gapLength = 4.0f;   // 适中的间隔
        
        // 顶边
        drawDashedLineImGui(drawList, minX, minY, maxX, minY, color, dashLength, gapLength);
        // 右边
        drawDashedLineImGui(drawList, maxX, minY, maxX, maxY, color, dashLength, gapLength);
        // 底边
        drawDashedLineImGui(drawList, maxX, maxY, minX, maxY, color, dashLength, gapLength);
        // 左边
        drawDashedLineImGui(drawList, minX, maxY, minX, minY, color, dashLength, gapLength);
    }
    
    /**
     * 绘制虚线（ImGui版本）
     */
    private void drawDashedLineImGui(ImDrawList drawList, Vec2d start, Vec2d end, int color) {
        drawDashedLineImGui(drawList, (float) start.x, (float) start.y, (float) end.x, (float) end.y, color, 5.0f, 3.0f);
    }
    
    /**
     * 绘制虚线（ImGui版本）- 具体实现
     */
    private void drawDashedLineImGui(ImDrawList drawList, float x1, float y1, float x2, float y2, int color, float dashLength, float gapLength) {
        float totalLength = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        if (totalLength < 0.1f) return;
        
        float dx = (x2 - x1) / totalLength;
        float dy = (y2 - y1) / totalLength;
        float lineWidth = 1.0f; // 与实线框保持一致的线宽
        
        float currentPos = 0;
        boolean drawing = true;
        
        while (currentPos < totalLength) {
            float segmentLength = drawing ? dashLength : gapLength;
            float nextPos = Math.min(currentPos + segmentLength, totalLength);
            
            if (drawing) {
                float startX = x1 + dx * currentPos;
                float startY = y1 + dy * currentPos;
                float endX = x1 + dx * nextPos;
                float endY = y1 + dy * nextPos;
                drawList.addLine(startX, startY, endX, endY, color, lineWidth);
            }
            
            currentPos = nextPos;
            drawing = !drawing;
        }
    }
    
    public SelectionMode getCurrentMode() {
        return currentMode;
    }
    
    public boolean isSelecting() {
        return isSelecting;
    }
    
    public Set<String> getSelectedShapeIds() {
        return new HashSet<>(selectedShapeIds);
    }
    
    public int getSelectedCount() {
        return selectedShapeIds.size();
    }

} 