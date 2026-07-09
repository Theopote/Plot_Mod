package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.DeleteShapesCommand;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.ThemeManager;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;
import com.plot.utils.PlotI18n;

/**
 * 橡皮擦策略实现 - 策略模式版本
 * 
 * <p>处理图形删除的交互逻辑，支持：</p>
 * <ul>
 *   <li>点击删除模式：点击图形直接删除</li>
 *   <li>拖拽删除模式：拖拽删除路径上的图形</li>
 *   <li>实时预览删除效果</li>
 *   <li>可调节的橡皮擦半径</li>
 * </ul>
 * 
 * <p><strong>交互流程：</strong></p>
 * <ol>
 *   <li>鼠标移动时显示橡皮擦光标</li>
 *   <li>点击或拖拽时查找范围内的图形</li>
 *   <li>高亮显示即将删除的图形</li>
 *   <li>执行删除操作</li>
 * </ol>
 * 
 * @author Plot Team
 * @version 1.0 - 橡皮擦策略
 */
public class EraserStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(EraserStrategy.class);
    
    // 鼠标按键常量
    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    
    // 键盘按键常量
    private static final int ESC_KEY = 27;
    
    /**
     * 橡皮擦模式枚举
     */
    public enum EraserMode {
        CLICK_DELETE("mode.plot.eraser.click", "mode.plot.eraser.click.desc"),
        DRAG_DELETE("mode.plot.eraser.drag", "mode.plot.eraser.drag.desc");

        private final String nameKey;
        private final String descKey;

        EraserMode(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }
    
    /**
     * 橡皮擦状态枚举
     */
    private enum EraserState {
        IDLE("mode.plot.common.idle", "mode.plot.eraser.state.idle.desc"),
        ERASING("mode.plot.eraser.state.erasing", "mode.plot.eraser.state.erasing.desc");

        private final String nameKey;
        private final String descKey;

        EraserState(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }
    
    // 策略状态
    private EraserMode currentMode = EraserMode.CLICK_DELETE;
    private EraserState currentState = EraserState.IDLE;
    
    // 橡皮擦配置
    private float eraserRadius = 15.0f;
    private final float minRadius = 5.0f;
    private final float maxRadius = 50.0f;
    
    // 交互状态
    private Vec2d currentMousePosition;
    private boolean isDragging = false;
    private final Set<Shape> shapesToDelete = new HashSet<>();
    private final List<Vec2d> dragPath = new ArrayList<>();
    private final int maxDragPathSize = 10;
    
    // 命令状态
    private ModifyCommand pendingCommand;
    
    /**
     * 默认构造函数
     */
    public EraserStrategy() {
        LOGGER.debug("EraserStrategy 已创建，模式: {}", currentMode.getDisplayName());
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT) {
            if (button == MOUSE_RIGHT && currentState != EraserState.IDLE) {
                // 右键取消删除
                reset();
                context.setStatusMessage("status.plot.eraser.cancelled");
                return ModifyResult.CANCEL;
            }
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            currentMousePosition = snappedPoint;
            
            switch (currentMode) {
                case CLICK_DELETE -> {
                    return performClickDelete(snappedPoint, context);
                }
                case DRAG_DELETE -> {
                    return startDragDelete(snappedPoint, context);
                }
                default -> {
                    return ModifyResult.IGNORED;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("橡皮擦策略鼠标按下处理失败: {}", e.getMessage(), e);
            reset();
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        try {
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            currentMousePosition = snappedPoint;
            
            if (isDragging && currentMode == EraserMode.DRAG_DELETE) {
                return continueDragDelete(snappedPoint, context);
            }
            
            // 更新预览
            context.setPreviewEnabled(true);
            
            return ModifyResult.CONTINUE;
            
        } catch (Exception e) {
            LOGGER.error("橡皮擦策略鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE; // 移动失败不应该取消操作
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT) {
            return ModifyResult.IGNORED;
        }
        
        try {
            if (isDragging && currentMode == EraserMode.DRAG_DELETE) {
                return finishDragDelete(context);
            }
            
            return ModifyResult.CONTINUE;
            
        } catch (Exception e) {
            LOGGER.error("橡皮擦策略鼠标释放处理失败: {}", e.getMessage(), e);
            reset();
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        if (keyCode == ESC_KEY) {
            reset();
            context.setStatusMessage("status.plot.eraser.cancelled");
            return ModifyResult.CANCEL;
        }
        return ModifyResult.IGNORED;
    }
    
    /**
     * 执行点击删除
     */
    private ModifyResult performClickDelete(Vec2d point, ModifyToolContext context) {
        // 查找橡皮擦范围内的图形
        Set<Shape> shapesInRange = findShapesInEraserArea(point, context);
        
        if (shapesInRange.isEmpty()) {
            context.setStatusMessage("status.plot.eraser.not_found");
            return ModifyResult.CONTINUE;
        }
        
        // 创建删除命令
        shapesToDelete.clear();
        shapesToDelete.addAll(shapesInRange);
        pendingCommand = new DeleteShapesCommand(new ArrayList<>(shapesToDelete));
        
        context.setStatusMessage(PlotI18n.status("status.plot.common.deleted_count", shapesToDelete.size()));
        return ModifyResult.COMPLETE;
    }
    
    /**
     * 开始拖拽删除
     */
    private ModifyResult startDragDelete(Vec2d point, ModifyToolContext context) {
        isDragging = true;
        currentState = EraserState.ERASING;
        
        // 初始化拖拽路径
        dragPath.clear();
        dragPath.add(point);
        
        // 查找起始点的图形
        Set<Shape> shapesInRange = findShapesInEraserArea(point, context);
        shapesToDelete.clear();
        shapesToDelete.addAll(shapesInRange);
        
        context.setStatusMessage("status.plot.eraser.dragging");
        context.setPreviewEnabled(true);
        
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 继续拖拽删除
     */
    private ModifyResult continueDragDelete(Vec2d point, ModifyToolContext context) {
        // 更新拖拽路径
        dragPath.add(point);
        if (dragPath.size() > maxDragPathSize) {
            dragPath.removeFirst();
        }
        
        // 查找路径上的图形
        Set<Shape> shapesInRange = findShapesAlongPath(dragPath, context);
        shapesToDelete.addAll(shapesInRange);
        
        context.setStatusMessage(PlotI18n.status("status.plot.common.drag_delete_count", shapesToDelete.size()));
        
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 完成拖拽删除
     */
    private ModifyResult finishDragDelete(ModifyToolContext context) {
        isDragging = false;
        currentState = EraserState.IDLE;
        
        if (shapesToDelete.isEmpty()) {
            context.setStatusMessage("status.plot.eraser.not_found");
            reset();
            return ModifyResult.CANCEL;
        }
        
        // 创建删除命令
        pendingCommand = new DeleteShapesCommand(new ArrayList<>(shapesToDelete));
        
        context.setStatusMessage(PlotI18n.status("status.plot.common.deleted_count", shapesToDelete.size()));
        return ModifyResult.COMPLETE;
    }
    
    /**
     * 查找橡皮擦区域内的图形
     */
    private Set<Shape> findShapesInEraserArea(Vec2d point, ModifyToolContext context) {
        Set<Shape> result = new HashSet<>();
        
        // 从所有图层获取图形
        List<Shape> allShapes = getAllShapes(context);
        
        for (Shape shape : allShapes) {
            if (shape == null) continue;
            
            try {
                double distance = shape.distanceTo(point);
                if (distance <= eraserRadius) {
                    result.add(shape);
                }
            } catch (Exception e) {
                LOGGER.warn("计算图形距离失败: {}", e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * 查找拖拽路径上的图形
     */
    private Set<Shape> findShapesAlongPath(List<Vec2d> path, ModifyToolContext context) {
        Set<Shape> result = new HashSet<>();
        
        for (Vec2d point : path) {
            result.addAll(findShapesInEraserArea(point, context));
        }
        
        return result;
    }
    
    /**
     * 获取所有图形
     */
    private List<Shape> getAllShapes(ModifyToolContext context) {
        List<Shape> allShapes = new ArrayList<>();
        
        // 优先从选中的图形中获取
        List<Shape> selectedShapes = context.getSelectedShapes();
        if (selectedShapes != null && !selectedShapes.isEmpty()) {
            allShapes.addAll(selectedShapes);
        } else {
            // 如果没有选中图形，从画布获取所有图形
            try {
                // 这里需要通过context获取所有图形
                // 具体实现取决于ModifyToolContext的接口
                allShapes.addAll(context.findShapesInArea(
                    new Vec2d(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY),
                    new Vec2d(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
                ));
            } catch (Exception e) {
                LOGGER.warn("获取所有图形失败: {}", e.getMessage());
            }
        }
        
        return allShapes;
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }
    
    @Override
    public void reset() {
        currentState = EraserState.IDLE;
        isDragging = false;
        shapesToDelete.clear();
        dragPath.clear();
        pendingCommand = null;
        currentMousePosition = null;
        LOGGER.debug("橡皮擦策略已重置");
    }
    
    @Override
    public String getStrategyName() {
        return "橡皮擦策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return "用于删除图形的策略，支持点击删除和拖拽删除模式";
    }
    
    @Override
    public boolean requiresSelection() {
        return false; // 橡皮擦工具不需要预先选择图形
    }
    
    @Override
    public int getMinimumSelectionCount() {
        return 0;
    }
    
    @Override
    public int getMaximumSelectionCount() {
        return -1; // 无限制
    }
    
    // ====== 配置方法 ======
    
    /**
     * 设置橡皮擦模式
     */
    public void setEraserMode(EraserMode mode) {
        if (this.currentMode != mode) {
            reset(); // 切换模式时重置状态
            this.currentMode = mode;
            LOGGER.debug("橡皮擦模式已切换为: {}", mode.getDisplayName());
        }
    }
    
    /**
     * 获取当前模式
     */
    public EraserMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * 设置橡皮擦半径
     */
    public void setEraserRadius(float radius) {
        this.eraserRadius = Math.max(minRadius, Math.min(maxRadius, radius));
        LOGGER.debug("橡皮擦半径已设置为: {}", this.eraserRadius);
    }
    
    /**
     * 获取橡皮擦半径
     */
    public float getEraserRadius() {
        return eraserRadius;
    }

    // ====== 渲染方法 ======
    
    /**
     * 渲染预览（DrawContext版本）
     */
    public void renderPreview(DrawContext context) {
        if (currentMousePosition == null || context == null) {
            return;
        }
        
        try {
            // 渲染橡皮擦光标
            renderEraserCursor(context);
            
            // 高亮显示即将删除的图形
            if (!shapesToDelete.isEmpty()) {
                renderHighlightShapes(context);
            }
            
        } catch (Exception e) {
            LOGGER.warn("橡皮擦预览渲染失败: {}", e.getMessage());
        }
    }
    
    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentMousePosition == null || drawList == null || camera == null) {
            return;
        }
        
        try {
            var theme = ThemeManager.getInstance().getCurrentTheme();
            // 转换到屏幕坐标
            Vec2d screenPos = camera.worldToScreen(currentMousePosition);
            float screenRadius = eraserRadius * camera.getZoom();
            
            // 渲染橡皮擦光标
            int redColor = theme.errorText;
            int whiteColor = theme.text;
            
            // 填充圆形
            drawList.addCircleFilled(
                (float) screenPos.x, (float) screenPos.y,
                screenRadius, withAlpha(redColor, 0x80)
            );
            
            // 轮廓圆形
            drawList.addCircle(
                (float) screenPos.x, (float) screenPos.y,
                screenRadius, whiteColor, 0, 2.0f
            );
            
            // 中心点
            drawList.addCircleFilled(
                (float) screenPos.x, (float) screenPos.y,
                4.0f, whiteColor
            );
            
        } catch (Exception e) {
            LOGGER.warn("橡皮擦ImGui预览渲染失败: {}", e.getMessage());
        }
    }
    
    /**
     * 渲染橡皮擦光标
     */
    private void renderEraserCursor(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        Color fillColor = withAlpha(toColor(theme.errorText), 120);
        Color strokeColor = toColor(theme.text);

        // 渲染半透明红色填充圆形
        context.drawCircleFilled(currentMousePosition, eraserRadius, fillColor);
        
        // 渲染白色轮廓
        context.drawCircleOutline(currentMousePosition, eraserRadius, strokeColor);
        
        // 渲染中心点
        context.drawCircleFilled(currentMousePosition, 3.0f, strokeColor);
        
        // 渲染十字线
        float crossSize = Math.max(8.0f, eraserRadius * 0.3f);
        context.drawLine(
            new Vec2d(currentMousePosition.x - crossSize, currentMousePosition.y),
            new Vec2d(currentMousePosition.x + crossSize, currentMousePosition.y),
            strokeColor
        );
        context.drawLine(
            new Vec2d(currentMousePosition.x, currentMousePosition.y - crossSize),
            new Vec2d(currentMousePosition.x, currentMousePosition.y + crossSize),
            strokeColor
        );
    }
    
    /**
     * 渲染高亮图形
     */
    private void renderHighlightShapes(DrawContext context) {
        Color highlightColor = withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().errorText), 150);
        
        for (Shape shape : shapesToDelete) {
            if (shape != null) {
                try {
                    // 使用高亮颜色渲染图形
                    // 这里需要根据具体的Shape接口来实现
                    // shape.drawHighlight(context, highlightColor);
                } catch (Exception e) {
                    LOGGER.warn("高亮图形渲染失败: {}", e.getMessage());
                }
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new Color(red, green, blue, alpha);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
