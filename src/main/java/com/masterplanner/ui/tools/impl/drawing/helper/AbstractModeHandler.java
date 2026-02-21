package com.masterplanner.ui.tools.impl.drawing.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模式处理器抽象基类
 * 
 * 提供所有模式处理器的通用功能，包括：
 * - 通用的鼠标事件处理逻辑（右键取消、基础状态管理）
 * - 通用的键盘事件处理（ESC取消）
 * - 共同的状态字段和样式处理
 * 
 * 子类只需要实现具体的绘制逻辑和渲染方法。
 */
public abstract class AbstractModeHandler implements IModeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractModeHandler.class);
    
    // 通用状态字段
    protected Vec2d currentMousePoint;
    protected boolean isDrawing = false;
    protected long lastClickTime = 0;
    protected final StyleHandler styleHandler;
    
    // 通用常量（从 PolylineTool.Constants 导入）
    protected static final long DOUBLE_CLICK_TIME = 500; // 双击时间阈值（毫秒）
    protected static final double POSITION_TOLERANCE = 8.0; // 双击位置容差
    
    protected AbstractModeHandler(StyleHandler styleHandler) {
        this.styleHandler = styleHandler;
    }
    
    /**
     * 实现通用的鼠标按下逻辑
     * 处理右键取消和非左键事件，然后委托给子类处理左键点击
     */
    @Override
    public IInteractionStrategy.InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
        LOGGER.debug("AbstractModeHandler.onMouseDown: 位置={}, 按钮={}", pos, button);
        
        if (button != 0) { // 非左键
            if (button == 1) { // 右键取消
                LOGGER.debug("右键取消绘制");
                reset();
                context.resetDrawing("右键取消");
                return IInteractionStrategy.InteractionResult.CANCEL;
            }
            LOGGER.debug("忽略非左键按钮: {}", button);
            return IInteractionStrategy.InteractionResult.IGNORED;
        }
        
        // 委托给子类处理左键点击逻辑
        LOGGER.debug("委托给子类处理左键点击");
        IInteractionStrategy.InteractionResult result = handleLeftMouseDown(pos, context);
        LOGGER.debug("子类返回结果: {}", result);
        return result;
    }
    
    /**
     * 子类需要实现的具体左键点击逻辑
     * 
     * @param pos 鼠标位置（世界坐标）
     * @param context 绘制工具上下文
     * @return 交互结果
     */
    protected abstract IInteractionStrategy.InteractionResult handleLeftMouseDown(Vec2d pos, DrawingToolContext context);
    
    /**
     * 通用的鼠标移动处理
     * 更新当前鼠标位置，子类可以重写以添加额外逻辑
     */
    @Override
    public IInteractionStrategy.InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
        currentMousePoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        
        // 如果正在绘制，更新预览
        if (isDrawing) {
            notifyPreviewUpdate(context);
            return IInteractionStrategy.InteractionResult.CONTINUE;
        }
        
        return IInteractionStrategy.InteractionResult.IGNORED;
    }
    
    /**
     * 通用的鼠标释放处理
     * 默认实现不做特殊处理，子类可以重写
     */
    @Override
    public IInteractionStrategy.InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
        return IInteractionStrategy.InteractionResult.CONTINUE;
    }
    
    /**
     * 通用的键盘事件处理
     * 处理 ESC 键取消操作，子类可以重写以添加更多键盘快捷键
     */
    @Override
    public IInteractionStrategy.InteractionResult onKeyDown(int key, DrawingToolContext context) {
        if (key == 27) { // ESC键：总是取消绘制
            reset();
            context.resetDrawing("ESC键取消");
            return IInteractionStrategy.InteractionResult.CANCEL;
        }
        return IInteractionStrategy.InteractionResult.IGNORED;
    }
    
    /**
     * 通用的绘制状态检查
     */
    @Override
    public boolean isDrawing() {
        return isDrawing;
    }
    
    /**
     * 通用的状态重置
     * 清除基础状态，子类需要重写以清除特定状态
     */
    @Override
    public void reset() {
        currentMousePoint = null;
        isDrawing = false;
        lastClickTime = 0;
        LOGGER.debug("{} 状态已重置", this.getClass().getSimpleName());
    }
    
    /**
     * 检查双击条件的辅助方法
     * 
     * @param currentTime 当前时间戳
     * @param currentPos 当前鼠标位置
     * @param lastPos 上次点击位置
     * @return 是否满足双击条件
     */
    protected boolean isDoubleClick(long currentTime, Vec2d currentPos, Vec2d lastPos) {
        return currentTime - lastClickTime <= DOUBLE_CLICK_TIME &&
               currentPos.distance(lastPos) < POSITION_TOLERANCE;
    }
    
    /**
     * 更新点击时间的辅助方法
     * 
     * @param currentTime 当前时间戳
     */
    protected void updateClickTime(long currentTime) {
        this.lastClickTime = currentTime;
    }
    
    /**
     * 获取样式处理器的辅助方法
     */
    protected StyleHandler getStyleHandler() {
        return styleHandler;
    }
    
    /**
     * 获取世界坐标点的辅助方法
     */
    protected Vec2d getWorldPoint(Vec2d screenPos, DrawingToolContext context) {
        return context.getSnapHandler().getSnappedWorldPoint(screenPos, context.getCamera());
    }
    
    /**
     * 通知工具更新预览的辅助方法
     * 通过上下文接口通知工具更新预览
     */
    protected void notifyPreviewUpdate(DrawingToolContext context) {
        try {
            // 刷新为当前模式的状态提示，避免“预览已更新”覆盖实际操作引导
            context.updateStatusMessage(getStatusMessage());
            LOGGER.debug("已通知工具更新预览");
        } catch (Exception e) {
            LOGGER.warn("无法通知工具更新预览: {}", e.getMessage());
        }
    }
}