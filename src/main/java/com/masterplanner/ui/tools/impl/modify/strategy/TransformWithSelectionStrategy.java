package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.model.Shape;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.ui.tools.impl.modify.helper.TransformHandler;
import com.masterplanner.ui.tools.impl.modify.helper.BoundingBoxControlManager;
import com.masterplanner.ui.tools.impl.modify.helper.BoundingBoxControlManager.ControlPointType;
import com.masterplanner.ui.tools.impl.modify.helper.DragSession;
import com.masterplanner.ui.tools.impl.modify.enums.TransformMode;
import com.masterplanner.ui.tools.impl.modify.enums.TransformState;
import com.masterplanner.ui.tools.impl.modify.dto.TransformParams;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.core.command.commands.ModifyCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 变换策略 - 双模式交互设计
 * 
 * <p>工作流程：</p>
 * <ul>
 *   <li><strong>选择模式</strong>：左键选择图形，右键确认进入变换模式</li>
 *   <li><strong>变换模式</strong>：拖拽控制点进行变换，右键返回选择模式</li>
 * </ul>
 * 
 * <p>交互细节：</p>
 * <ul>
 *   <li>左键单击：点选图形（支持Ctrl加选/减选）</li>
 *   <li>左键拖拽：框选图形（支持Ctrl加选/减选）</li>
 *   <li>右键单击：模式切换（选择↔变换）</li>
 *   <li>ESC键：取消所有操作，清空选择</li>
 * </ul>
 */
public class TransformWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    
    /**
     * 交互模式枚举
     */
    private enum InteractionMode {
        SELECTING("选择模式", "选择图形，右键确认进入变换"),
        TRANSFORMING("变换模式", "拖拽控制点进行变换，右键返回选择");
        
        private final String displayName;

        InteractionMode(String displayName, String description) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformWithSelectionStrategy.class);
    
    // 核心组件
    private final TransformHandler transformHandler;
    private final BoundingBoxControlManager controlManager;
    @SuppressWarnings("unused") // 预留字段，用于未来扩展（如事件发布）
    private final EventBus eventBus;
    
    // 双模式状态管理
    private InteractionMode currentMode = InteractionMode.SELECTING;
    private TransformMode transformMode = TransformMode.FREE;
    
    // 变换状态
    private TransformState transformState = TransformState.IDLE;
    private boolean isDragging = false;
    private ControlPointType primaryControlPoint;
    private DragSession currentDragSession;
    private boolean rotationEnabled = true;
    
    // 预览状态
    private List<Shape> previewShapes = new ArrayList<>();
    private boolean previewEnabled = false;
    private final Map<String, Boolean> hiddenShapeVisibility = new HashMap<>();
    
    // 常量 - 使用基类的常量，这里只定义变换特有的常量
    
    // 变换工具常量定义
    public static final String CONFIG_KEY_MODE = "transform.mode";
    public static final double CONTROL_POINT_SIZE = 6.0;
    public static final int ESC_KEY = 27;

    // 鼠标按钮常量 - 使用基类的常量
    
    public TransformWithSelectionStrategy(TransformHandler transformHandler, 
                                       BoundingBoxControlManager controlManager,
                                       EventBus eventBus) {
        this.transformHandler = Objects.requireNonNull(transformHandler, "TransformHandler不能为null");
        this.controlManager = Objects.requireNonNull(controlManager, "BoundingBoxControlManager不能为null");
        this.eventBus = Objects.requireNonNull(eventBus, "EventBus不能为null");
        
        LOGGER.info("变换策略初始化完成，当前模式: {}", currentMode.getDisplayName());
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        LOGGER.debug("鼠标按下: 位置=({}, {}), 按钮={}, 模式={}", pos.x, pos.y, button, currentMode);
        
        if (button == MOUSE_LEFT) {
            if (currentMode == InteractionMode.SELECTING) {
                // 使用基类的选择逻辑
                return super.handleSelectionMouseDown(pos, context);
            } else { // TRANSFORMING
                return handleTransformMouseDown(pos, context);
            }
        } else if (button == MOUSE_RIGHT) {
            return handleRightMouseDown(pos, context);
        }
        
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentMode == InteractionMode.SELECTING) {
            // 使用基类的选择逻辑
            return super.handleSelectionMouseMove(pos, context);
        } else { // TRANSFORMING
            return handleTransformMouseMove(pos, context);
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        LOGGER.debug("鼠标释放: 位置=({}, {}), 按钮={}, 模式={}", pos.x, pos.y, button, currentMode);
        
        if (button == MOUSE_LEFT) {
            if (currentMode == InteractionMode.SELECTING) {
                // 使用基类的选择逻辑
                return super.handleSelectionMouseUp(pos, context);
            } else { // TRANSFORMING
                return handleTransformMouseUp(pos, context);
            }
        }
        
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        if (keyCode == ESC_KEY) {
            if (currentMode == InteractionMode.TRANSFORMING) {
                // 在变换模式下按ESC，返回选择模式并清除变换框
                currentMode = InteractionMode.SELECTING;
                transformState = TransformState.IDLE;
                clearControlsAndPreview();
                context.setStatusMessage("已返回选择模式");
                LOGGER.info("ESC键按下，从变换模式返回选择模式");
            } else {
                // 在选择模式下按ESC，清除所有选择
                reset();
                context.clearSelection();
                context.setStatusMessage("选择已取消");
                LOGGER.info("ESC键按下，清除所有选择");
            }
            return ModifyResult.CANCEL;
        }
        
        return ModifyResult.IGNORED;
    }
    
    // 选择相关方法已由BaseSelectionStrategy提供，无需重复实现
    
    // onKeyPressed 不是 IModifyStrategy 接口的一部分，移除了
    // ESC键处理逻辑保留在其他地方
    
    // ==================== 核心处理方法 ====================
    
    /**
     * 处理右键点击 - 模式切换的核心逻辑
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == InteractionMode.SELECTING) {
            // 从选择模式 -> 变换模式
            if (!selectedShapeIds.isEmpty()) {
                // 确保选中的图形列表是最新的
                updateSelectedShapesFromIds(context);
                
                if (selectedShapes != null && !selectedShapes.isEmpty()) {
                    currentMode = InteractionMode.TRANSFORMING;
                    transformState = TransformState.SHOWING_BOUNDING_BOX;
                    
                    // 计算并显示变换框
                    recalculateBoundingBoxAndControls();
                    
                    context.setStatusMessage("进入变换模式，拖拽控制点进行操作，右键返回选择。");
                    LOGGER.info("切换到变换模式，选中了 {} 个图形", selectedShapes.size());
                    return ModifyResult.CONTINUE;
                } else {
                    context.setStatusMessage("选中的图形无效，请重新选择。");
                    clearSelection(context);
                    return ModifyResult.IGNORED;
                }
            } else {
                context.setStatusMessage("请先选择图形，然后右键确认。");
                return ModifyResult.IGNORED;
            }
        } else { // TRANSFORMING
            // 从变换模式 -> 选择模式
            currentMode = InteractionMode.SELECTING;
            transformState = TransformState.IDLE;
            
            // 隐藏变换框
            clearControlsAndPreview();
            
            context.setStatusMessage("返回选择模式，当前已选择 " + selectedShapeIds.size() + " 个图形。");
            LOGGER.info("从变换模式返回选择模式。");
            return ModifyResult.CANCEL; // 使用CANCEL可以很好地结束任何正在进行的预览
        }
    }

    // ==================== 选择模式处理方法 ====================
    // 选择相关方法已由BaseSelectionStrategy提供，无需重复实现
    
    /**
     * 从选中的图形ID更新图形列表
     */
    private void updateSelectedShapesFromIds(ModifyToolContext context) {
        selectedShapes = new ArrayList<>();
        
        if (selectedShapeIds.isEmpty()) {
            return;
        }
        
        // 从应用状态中获取所有图形
        List<Shape> allShapes = context.getAppState().getActiveLayer().getShapes();
        
        // 根据ID找到对应的图形
        for (String shapeId : selectedShapeIds) {
            for (Shape shape : allShapes) {
                if (shape != null && shape.getId().equals(shapeId) && shape.isVisible() && !shape.isDeleted()) {
                    selectedShapes.add(shape);
                    break;
                }
            }
        }
        
        LOGGER.debug("更新选中图形列表，ID数量: {}, 实际图形数量: {}", selectedShapeIds.size(), selectedShapes.size());
    }
    
    // ==================== 变换模式处理方法 ====================
    
    /**
     * 处理变换模式下的鼠标按下
     */
    private ModifyResult handleTransformMouseDown(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("变换模式 - 鼠标按下: ({}, {})", pos.x, pos.y);
        
        // 最高优先级：检查是否点击了控制点
        ControlPointType clickedControlPoint = controlManager.findClickedControlPoint(pos);
        if (clickedControlPoint != null) {
            return startTransformDrag(clickedControlPoint, pos, context);
        }
        
        // 次高优先级：检查是否点击了已选中的图形
        if (isClickingOnSelectedShapes(pos, context)) {
            return startMoveDrag(pos, context);
        }
        
        // 其他情况：无操作，防止用户误触
        LOGGER.debug("点击在空白区域，无操作");
        return ModifyResult.IGNORED;
    }
    
    /**
     * 处理变换模式下的鼠标移动
     */
    private ModifyResult handleTransformMouseMove(Vec2d pos, ModifyToolContext context) {
        if (isDragging) {
            return updateTransformDrag(pos, context);
        }

        // 非拖拽时更新控制点悬停状态，用于角点附近显示旋转图示
        controlManager.updateHoveredControlPoint(pos);
        
        return ModifyResult.IGNORED;
    }
    
    /**
     * 处理变换模式下的鼠标释放
     */
    private ModifyResult handleTransformMouseUp(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("变换模式 - 鼠标释放: ({}, {})", pos.x, pos.y);
        
        if (isDragging) {
            return finishTransformDrag(pos, context);
        }
        
        return ModifyResult.IGNORED;
    }
    
    // ==================== 变换拖拽处理方法 ====================
    
    /**
     * 开始变换拖拽
     */
    private ModifyResult startTransformDrag(ControlPointType controlPoint, Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("开始变换拖拽: 控制点={}, 位置=({}, {})", controlPoint, pos.x, pos.y);
        
        primaryControlPoint = controlPoint;
        isDragging = true;
        transformState = TransformState.DRAGGING_CONTROL_POINT;
        
        // 创建拖拽会话
        currentDragSession = new DragSession(pos, controlPoint, DRAG_THRESHOLD);
        currentDragSession.activate();
        
        // 启用预览状态，确保变换框在拖拽时正确显示
        previewEnabled = true;
        hideSelectedShapesForPreview();
        
        // 立即计算一次预览包围盒，确保变换框在拖拽开始时就能正确显示
        if (!selectedShapes.isEmpty()) {
            // 创建一个初始的变换参数（无变换）
            TransformParams initialParams = createTransformParameters(pos, context);
            updateTransformPreview(initialParams, context);
        }
        
        context.setStatusMessage("拖拽控制点进行变换");
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 开始移动拖拽
     */
    private ModifyResult startMoveDrag(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("开始移动拖拽: 位置=({}, {})", pos.x, pos.y);
        
        isDragging = true;
        transformState = TransformState.DRAGGING_CONTROL_POINT; // 复用状态
        
        // 创建拖拽会话
        currentDragSession = new DragSession(pos, null, DRAG_THRESHOLD);
        currentDragSession.activate();
        
        // 启用预览状态，确保变换框在拖拽时正确显示
        previewEnabled = true;
        hideSelectedShapesForPreview();
        
        context.setStatusMessage("拖拽移动选择集");
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 更新变换拖拽
     */
    private ModifyResult updateTransformDrag(Vec2d pos, ModifyToolContext context) {
        if (currentDragSession == null) {
            return ModifyResult.IGNORED;
        }
        
        // 更新拖拽会话
        currentDragSession.updateCurrentPoint(pos);
        
        if (currentDragSession.shouldStartDrag()) {
            // 计算变换参数
            TransformParams params = createTransformParameters(pos, context);
            
            // 更新预览
            updateTransformPreview(params, context);
            
            context.setStatusMessage("变换预览中...");
            return ModifyResult.CONTINUE;
        }
        
        return ModifyResult.IGNORED;
    }
    
    /**
     * 完成变换拖拽
     */
    private ModifyResult finishTransformDrag(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("完成变换拖拽: 位置=({}, {})", pos.x, pos.y);
        restoreHiddenShapesAfterPreview();
        
        if (currentDragSession != null && currentDragSession.shouldStartDrag()) {
            // 执行变换
            TransformParams params = createTransformParameters(pos, context);
            ModifyResult result = performTransform(params, context);
            
            // 如果变换成功，更新选中图形列表并重新计算边界框
            if (result == ModifyResult.COMPLETE) {
                // 更新选中图形列表，确保指向变换后的图形
                // 注意：此时selectedShapeIds应该已经在executeModifyCommand中更新为新图形的ID了
                updateSelectedShapesFromIds(context);
                
                // 重新计算边界框和控制点，使用变换后的新图形
                recalculateBoundingBoxAndControls();
                
                LOGGER.debug("变换完成，已更新选中图形列表和变换框，图形数量: {}", selectedShapes.size());
            }
            
            // 重置拖拽状态，但保持变换框显示
            isDragging = false;
            transformState = TransformState.SHOWING_BOUNDING_BOX;
            currentDragSession = null;
            primaryControlPoint = null;
            
            // 清除预览图形，但保持变换框显示
            previewShapes.clear();
            previewEnabled = false;
            controlManager.clearPreviewState();
            
            context.setStatusMessage("变换完成，变换框继续显示");
            return result;
        }
        
        // 重置拖拽状态，但保持变换框显示
        isDragging = false;
        transformState = TransformState.SHOWING_BOUNDING_BOX;
        currentDragSession = null;
        primaryControlPoint = null;
        
        // 清除预览图形，但保持变换框显示
        previewShapes.clear();
        previewEnabled = false;
        controlManager.clearPreviewState();
        
        context.setStatusMessage("变换完成，变换框继续显示");
        return ModifyResult.CONTINUE;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 检查是否点击了已选中的图形
     */
    private boolean isClickingOnSelectedShapes(Vec2d pos, ModifyToolContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            return false;
        }
        
        // 获取吸附后的点
        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        
        // 遍历所有选中的图形，检查点击点是否落在其中任何一个内部
        for (Shape shape : selectedShapes) {
            if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                // 使用图形的contains方法检查点击点是否在图形内部
                if (shape.contains(snappedPoint)) {
                    LOGGER.debug("点击在选中图形上: {}", shape.getId());
                    return true;
                }
                
                // 如果contains方法不可靠，使用距离检测作为备选
                double distance = shape.getDistanceToPoint(snappedPoint);
                if (distance <= BaseSelectionStrategy.SELECTION_TOLERANCE) {
                    LOGGER.debug("点击在选中图形附近: {}, 距离: {}", shape.getId(), distance);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 重新计算边界框和控制点
     */
    private void recalculateBoundingBoxAndControls() {
        if (selectedShapes.isEmpty()) {
            return;
        }
        
        LOGGER.debug("重新计算边界框和控制点，图形数量: {}", selectedShapes.size());
        
        try {
            // 计算所有选中图形的联合边界框
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE;
            double maxY = Double.MIN_VALUE;
            
            for (Shape shape : selectedShapes) {
                if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                    // 获取图形的边界框
                    BoundingBox shapeBounds = shape.getBoundingBox();
                    if (shapeBounds != null) {
                        minX = Math.min(minX, shapeBounds.getMinX());
                        minY = Math.min(minY, shapeBounds.getMinY());
                        maxX = Math.max(maxX, shapeBounds.getMaxX());
                        maxY = Math.max(maxY, shapeBounds.getMaxY());
                    }
                }
            }
            
            // 更新控制管理器
            if (minX != Double.MAX_VALUE) {
                controlManager.calculateBoundingBox(selectedShapes);
                
                LOGGER.debug("边界框计算完成: ({}, {}) 到 ({}, {})", minX, minY, maxX, maxY);
            } else {
                LOGGER.warn("无法计算边界框，没有有效的图形");
            }
        } catch (Exception e) {
            LOGGER.error("计算边界框时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 创建变换参数
     */
    private TransformParams createTransformParameters(Vec2d currentPos, ModifyToolContext context) {
        if (currentDragSession == null) {
            return null;
        }
        
        Vec2d dragVector = currentDragSession.getDragVector();
        
        // 获取按键状态（使用解耦的方法）
        boolean isShiftPressed = context.isShiftKeyDown();
        boolean isAltPressed = context.isAltKeyDown();
        
        // 根据按键状态确定变换参数
        // Shift = 等比缩放
        // Alt = 中心缩放

        // 计算锚点（如果需要）
        Vec2d anchorPoint = null;
        if (primaryControlPoint != null && !isAltPressed) {
            anchorPoint = calculateAnchorPointForControlPoint(primaryControlPoint);
        }
        
        return TransformParams.builder()
            .mode(transformMode)
            .dragVector(dragVector)
            .controlPointIndex(primaryControlPoint != null ? primaryControlPoint.ordinal() : 0)
            .controlPointType(primaryControlPoint)
            .maintainAspectRatio(isShiftPressed)
            .centerScale(isAltPressed)
            .anchorPoint(anchorPoint)
            .build();
    }
    
    
    /**
     * 为控制点计算锚点
     * 
     * <p>锚点是变换过程中保持不动的固定点。当拖拽一个控制点时，
     * 锚点应该设置在相对的另一侧，以确保变换按预期方向进行。</p>
     * 
     * <p>修正说明：</p>
     * <ul>
     *   <li>拖拽左侧中点时，锚点应该在右侧中点</li>
     *   <li>拖拽右侧中点时，锚点应该在左侧中点</li>
     *   <li>拖拽顶部中点时，锚点应该在底部中点</li>
     *   <li>拖拽底部中点时，锚点应该在顶部中点</li>
     *   <li>拖拽角点时，锚点在对角顶点</li>
     * </ul>
     */
    private Vec2d calculateAnchorPointForControlPoint(ControlPointType controlPointType) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            return null;
        }
        
        // 计算所有选中图形的联合边界框
        com.masterplanner.core.geometry.BoundingBox combinedBounds = calculateCombinedBoundingBox();
        if (combinedBounds == null) {
            return null;
        }
        
        Vec2d center = combinedBounds.getCenter();
        double minX = combinedBounds.getMinX();
        double minY = combinedBounds.getMinY();
        double maxX = combinedBounds.getMaxX();
        double maxY = combinedBounds.getMaxY();
        
        return switch (controlPointType) {
            // 角点的锚点是对角顶点
            // 控制点位置：TOP_LEFT=(minX,maxY), TOP_RIGHT=(maxX,maxY), BOTTOM_RIGHT=(maxX,minY), BOTTOM_LEFT=(minX,minY)
            case TOP_LEFT -> new Vec2d(maxX, minY);     // 拖拽左上角，锚点在右下角
            case TOP_RIGHT -> new Vec2d(minX, minY);    // 拖拽右上角，锚点在左下角
            case BOTTOM_LEFT -> new Vec2d(maxX, maxY);  // 拖拽左下角，锚点在右上角
            case BOTTOM_RIGHT -> new Vec2d(minX, maxY); // 拖拽右下角，锚点在左上角

            // 边中点的锚点是相对的另一条边中点
            case TOP_CENTER -> new Vec2d(center.x, minY);      // 拖拽顶部中点，锚点在底部中点
            case BOTTOM_CENTER -> new Vec2d(center.x, maxY);   // 拖拽底部中点，锚点在顶部中点
            case CENTER_LEFT -> new Vec2d(maxX, center.y);      // 拖拽左侧中点，锚点在右侧中点
            case CENTER_RIGHT -> new Vec2d(minX, center.y);     // 拖拽右侧中点，锚点在左侧中点
        };
    }
    
    /**
     * 计算所有选中图形的联合边界框
     */
    private com.masterplanner.core.geometry.BoundingBox calculateCombinedBoundingBox() {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            return null;
        }
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (Shape shape : selectedShapes) {
            if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                com.masterplanner.core.geometry.BoundingBox bounds = shape.getBoundingBox();
                if (bounds != null) {
                    minX = Math.min(minX, bounds.getMinX());
                    minY = Math.min(minY, bounds.getMinY());
                    maxX = Math.max(maxX, bounds.getMaxX());
                    maxY = Math.max(maxY, bounds.getMaxY());
                }
            }
        }
        
        if (minX == Double.MAX_VALUE) {
            return null;
        }
        
        return new com.masterplanner.core.geometry.BoundingBox(minX, minY, maxX, maxY);
    }
    
    /**
     * 更新变换预览
     */
    private void updateTransformPreview(TransformParams params, ModifyToolContext context) {
        if (params == null || selectedShapes.isEmpty()) {
            return;
        }
        
        // 使用TransformHandler计算预览图形
        previewShapes = transformHandler.calculateModifiedShapes(selectedShapes, params);
        previewEnabled = true;
        
        // 更新预览包围盒和控制点，确保变换框在拖拽时正确显示
        controlManager.calculatePreviewBoundingBoxAndControlPoints(previewShapes);
        
        LOGGER.debug("更新变换预览，图形数量: {}", previewShapes.size());
    }
    
    /**
     * 执行变换
     */
    private ModifyResult performTransform(TransformParams params, ModifyToolContext context) {
        if (params == null || selectedShapes.isEmpty()) {
            return ModifyResult.IGNORED;
        }
        
        try {
            // 使用TransformHandler创建变换命令
            ModifyCommand command = transformHandler.createModifyCommand(selectedShapes, new ArrayList<>(), params);
            if (command != null) {
                // 通过context执行命令，这样会被全局的撤销/重做系统正确记录
                context.executeModifyCommand(command);
                LOGGER.info("变换命令已提交执行");
                return ModifyResult.COMPLETE; // 返回COMPLETE表示操作已完成
            }
        } catch (Exception e) {
            LOGGER.error("变换执行失败: {}", e.getMessage(), e);
            context.setStatusMessage("变换执行失败: " + e.getMessage());
        }
        
        return ModifyResult.IGNORED;
    }
    
    /**
     * 清空控制和预览
     */
    private void clearControlsAndPreview() {
        // 清空预览
        previewShapes.clear();
        previewEnabled = false;
        restoreHiddenShapesAfterPreview();
        
        // TODO: 清空控制管理器
        // controlManager.clear();
    }
    
    @Override
    public void reset() {
        LOGGER.debug("重置变换策略状态");
        
        // 重置基类状态
        isSelecting = false;
        isPointSelecting = false;
        selectedShapeIds.clear();
        tempSelectedShapeIds.clear();
        selectedShapes = null;
        
        // 重置模式
        currentMode = InteractionMode.SELECTING;
        transformState = TransformState.IDLE;
        isDragging = false;
        
        // 清空拖拽状态
        currentDragSession = null;
        primaryControlPoint = null;
        
        // 清空预览
        clearControlsAndPreview();
        
        LOGGER.debug("变换策略状态重置完成，模式: {}", currentMode.getDisplayName());
    }

    private void hideSelectedShapesForPreview() {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            return;
        }

        for (Shape shape : selectedShapes) {
            if (shape == null || shape.isDeleted()) {
                continue;
            }

            hiddenShapeVisibility.putIfAbsent(shape.getId(), shape.isVisible());
            shape.setVisible(false);
        }
    }

    private void restoreHiddenShapesAfterPreview() {
        if (hiddenShapeVisibility.isEmpty()) {
            return;
        }

        if (selectedShapes != null) {
            for (Shape shape : selectedShapes) {
                if (shape == null) {
                    continue;
                }

                Boolean originalVisible = hiddenShapeVisibility.get(shape.getId());
                if (originalVisible != null) {
                    shape.setVisible(originalVisible);
                }
            }
        }

        hiddenShapeVisibility.clear();
    }
    
    @Override
    public String getStrategyName() {
        return "变换策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return "双模式变换策略 - 支持选择和变换两种交互模式";
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        // 这个方法在策略中通常不需要实现
        return null;
    }
    
    // ==================== 配置方法 ====================
    
    /**
     * 设置变换模式
     */
    public void setTransformMode(TransformMode mode) {
        this.transformMode = Objects.requireNonNull(mode, "变换模式不能为null");
        LOGGER.debug("变换模式设置为: {}", mode);
    }

    /**
     * 设置是否启用旋转图示
     */
    public void setRotationEnabled(boolean enabled) {
        this.rotationEnabled = enabled;
        controlManager.setRotationIconsEnabled(enabled);
        LOGGER.debug("旋转图示已{}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 获取当前交互模式
     */
    public InteractionMode getCurrentMode() {
        return currentMode;
    }

    /**
     * 获取选中的图形
     */
    public List<Shape> getSelectedShapes() {
        return selectedShapes != null ? new ArrayList<>(selectedShapes) : new ArrayList<>();
    }
    
    /**
     * 变换完成后更新选中图形ID列表
     * 将旧图形的ID替换为新图形的ID，确保变换框能正确更新到新图形的位置
     */
    public void updateSelectedShapeIdsAfterTransform(List<Shape> oldShapes, List<Shape> newShapes) {
        if (oldShapes == null || newShapes == null || oldShapes.size() != newShapes.size()) {
            LOGGER.warn("更新选中图形ID列表失败：旧图形和新图形数量不匹配");
            return;
        }
        
        // 创建旧图形ID到新图形ID的映射
        java.util.Map<String, String> idMapping = new java.util.HashMap<>();
        for (int i = 0; i < oldShapes.size(); i++) {
            Shape oldShape = oldShapes.get(i);
            Shape newShape = newShapes.get(i);
            if (oldShape != null && newShape != null) {
                idMapping.put(oldShape.getId(), newShape.getId());
            }
        }
        
        // 更新selectedShapeIds，将旧ID替换为新ID
        java.util.List<String> updatedIds = new java.util.ArrayList<>();
        for (String oldId : selectedShapeIds) {
            String newId = idMapping.get(oldId);
            if (newId != null) {
                updatedIds.add(newId);
                LOGGER.debug("更新图形ID: {} -> {}", oldId, newId);
            } else {
                // 如果找不到映射，保留原ID（可能这个图形没有被变换）
                updatedIds.add(oldId);
                LOGGER.debug("保留图形ID: {} (未找到映射)", oldId);
            }
        }
        
        selectedShapeIds.clear();
        selectedShapeIds.addAll(updatedIds);
        
        LOGGER.info("已更新选中图形ID列表，更新了 {} 个ID", updatedIds.size());
    }
    
    /**
     * 渲染预览
     */
    public void renderPreview(com.masterplanner.core.graphics.DrawContext context) {
        if (currentMode == InteractionMode.SELECTING) {
            // 渲染选择预览（包括选框和选中的图形）
            renderSelectionPreview(context);
        } else if (currentMode == InteractionMode.TRANSFORMING) {
            // 渲染变换框和控制点（变换模式）
            // 在变换模式下，变换框应该始终显示，包括拖拽时
            if (transformState == TransformState.SHOWING_BOUNDING_BOX || 
                transformState == TransformState.DRAGGING_CONTROL_POINT) {
                // 在拖拽状态下显示预览变换框，否则显示普通变换框
                boolean showPreview = transformState == TransformState.DRAGGING_CONTROL_POINT;
                controlManager.render(context, showPreview);
            }
            
            // 渲染变换预览
            if (previewEnabled && !previewShapes.isEmpty()) {
                for (Shape shape : previewShapes) {
                    if (shape != null) {
                        // 使用预览样式渲染
                        shape.render(context);
                    }
                }
            }
        }
    }
    
    /**
     * 渲染选择预览（包括选框和选中的图形）
     */
    public void renderSelectionPreview(com.masterplanner.core.graphics.DrawContext context) {
        // 渲染选框预览（如果正在选择）
        if (isSelecting && !isPointSelecting && startPoint != null && currentPoint != null) {
            renderSelectionBox(context);
        }
        
        // 渲染选中的图形
        if (!selectedShapeIds.isEmpty()) {
            renderSelectedShapes(context);
        }
    }
    
    /**
     * 渲染选择框
     */
    private void renderSelectionBox(com.masterplanner.core.graphics.DrawContext context) {
        if (startPoint == null || currentPoint == null) {
            return;
        }
        
        // 使用基类的渲染逻辑，根据选择方向显示不同样式的框
        super.renderSelectionPreview(context);
    }
    
    /**
     * 渲染选中的图形
     */
    private void renderSelectedShapes(com.masterplanner.core.graphics.DrawContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            return;
        }
        
        for (Shape shape : selectedShapes) {
            if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                // 使用选中样式渲染图形
                shape.render(context);
                
                // 可以在这里添加额外的选中效果，比如高亮边框
                renderSelectionHighlight(shape, context);
            }
        }
    }
    
    /**
     * 渲染选择高亮效果
     */
    private void renderSelectionHighlight(Shape shape, com.masterplanner.core.graphics.DrawContext context) {
        try {
            // 获取图形的边界框
            BoundingBox bounds = shape.getBoundingBox();
            if (bounds != null) {
                // 创建高亮颜色（蓝色半透明）
                java.awt.Color highlightColor = new java.awt.Color(0.0f, 0.5f, 1.0f, 0.3f);
                
                // 设置线宽
                context.setLineWidth(2.0f);
                
                // 绘制高亮边框
                Vec2d topLeft = new Vec2d(bounds.getMinX(), bounds.getMinY());
                Vec2d bottomRight = new Vec2d(bounds.getMaxX(), bounds.getMaxY());
                
                context.drawRect(topLeft, bottomRight, highlightColor);
            }
        } catch (Exception e) {
            LOGGER.debug("渲染选择高亮时发生错误: {}", e.getMessage());
        }
    }
    
    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        if (currentMode == InteractionMode.SELECTING) {
            // 渲染选择预览（包括选框和选中的图形）
            renderSelectionPreviewImGui(drawList, camera);
        } else if (currentMode == InteractionMode.TRANSFORMING) {
            // 渲染变换框和控制点（变换模式）
            // 在变换模式下，变换框应该始终显示，包括拖拽时
            if (transformState == TransformState.SHOWING_BOUNDING_BOX || 
                transformState == TransformState.DRAGGING_CONTROL_POINT) {
                // 在拖拽状态下显示预览变换框，否则显示普通变换框
                boolean showPreview = transformState == TransformState.DRAGGING_CONTROL_POINT;
                controlManager.renderImGui(drawList, camera, showPreview);
            }
            
            // 渲染变换预览
            if (previewEnabled && !previewShapes.isEmpty()) {
                for (Shape shape : previewShapes) {
                    if (shape != null) {
                        // 使用预览样式渲染
                        shape.renderImGui(drawList, camera);
                    }
                }
            }
        }
    }
    
    /**
     * 渲染选择预览（ImGui版本）
     */
    public void renderSelectionPreviewImGui(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        // 渲染选框预览（如果正在选择）
        if (isSelecting && !isPointSelecting && startPoint != null && currentPoint != null) {
            renderSelectionBoxImGui(drawList, camera);
        }
        
        // 渲染选中的图形
        if (!selectedShapeIds.isEmpty()) {
            renderSelectedShapesImGui(drawList, camera);
        }
    }
    
    /**
     * 渲染选择框（ImGui版本）
     */
    private void renderSelectionBoxImGui(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        if (startPoint == null || currentPoint == null) {
            return;
        }
        
        // 使用基类的ImGui渲染逻辑，根据选择方向显示不同样式的框
        super.renderSelectionPreviewImGui(drawList, camera);
    }
    
    /**
     * 渲染选中的图形（ImGui版本）
     */
    private void renderSelectedShapesImGui(imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            return;
        }
        
        for (Shape shape : selectedShapes) {
            if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                // 使用选中样式渲染图形
                shape.renderImGui(drawList, camera);
                
                // 可以在这里添加额外的选中效果，比如高亮边框
                renderSelectionHighlightImGui(shape, drawList, camera);
            }
        }
    }
    
    /**
     * 渲染选择高亮效果（ImGui版本）
     */
    private void renderSelectionHighlightImGui(Shape shape, imgui.ImDrawList drawList, com.masterplanner.ui.canvas.CanvasCamera camera) {
        try {
            // 获取图形的边界框
            BoundingBox bounds = shape.getBoundingBox();
            if (bounds != null) {
                // 将世界坐标转换为屏幕坐标
                Vec2d topLeft = camera.worldToScreen(new Vec2d(bounds.getMinX(), bounds.getMinY()));
                Vec2d bottomRight = camera.worldToScreen(new Vec2d(bounds.getMaxX(), bounds.getMaxY()));
                
                // 创建高亮颜色：使用主题强调色并应用固定半透明度
                int accentColor = ThemeManager.getInstance().getCurrentTheme().accent;
                int highlightColor = (accentColor & 0x00FFFFFF) | 0x4D000000;
                
                // 绘制高亮边框
                float minX = (float) Math.min(topLeft.x, bottomRight.x);
                float minY = (float) Math.min(topLeft.y, bottomRight.y);
                float maxX = (float) Math.max(topLeft.x, bottomRight.x);
                float maxY = (float) Math.max(topLeft.y, bottomRight.y);
                
                drawList.addRect(minX, minY, maxX, maxY, highlightColor, 0.0f, 0, 2.0f);
            }
        } catch (Exception e) {
            LOGGER.debug("渲染选择高亮时发生错误: {}", e.getMessage());
        }
    }
}