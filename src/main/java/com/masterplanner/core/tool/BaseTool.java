package com.masterplanner.core.tool;

import com.masterplanner.api.graphics.IShapeStyle;
import com.masterplanner.api.tool.ITool;
import com.masterplanner.api.tool.IToolConfig;
import com.masterplanner.api.tool.ToolGroup;
import com.masterplanner.api.tool.ToolState;
import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.shortcut.IShortcutListener;
import com.masterplanner.core.log.LogManager;
import com.masterplanner.core.shortcut.ShortcutManager;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.Events;
import com.masterplanner.infrastructure.event.tool.ToolStateChangedEvent;
import com.masterplanner.core.graphics.DrawContext;
import net.minecraft.util.Identifier;
import com.masterplanner.core.model.Shape;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.graphics.style.ShapeStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基础工具类，实现了ITool接口的通用功能
 */
public abstract class BaseTool implements ITool, IShortcutListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseTool.class);
    
    protected final String id;
    protected final String name;
    protected final String description;
    protected final Identifier icon;
    protected final AppState appState;
    protected final EventBus eventBus;
    protected final ShortcutManager shortcutManager;
    
    protected ToolState state;
    protected IToolConfig config;
    protected ToolGroup group;
    protected String shortcutKey;
    protected int priority;
    
    // 工具状态
    protected Vec2d lastPoint;
    protected Vec2d currentPoint;
    protected boolean isDragging;
    protected boolean isShiftDown;
    protected boolean isControlDown;
    protected boolean isAltDown;
    protected boolean isActive = false;
    protected boolean isDirty = false;
    protected Shape previewShape;

    protected BaseTool(String id, String description, Identifier icon, String name) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = description;
        this.state = ToolState.INACTIVE;
        this.config = new ToolConfig();
        this.priority = 0;
        
        // 获取单例实例
        this.appState = AppState.getInstance();
        this.eventBus = EventBus.getInstance();
        this.shortcutManager = ShortcutManager.getInstance();
        
        // 注册为快捷键监听器
        shortcutManager.addListener(this);
    }

    // ITool 实现
    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getIcon() {
        return icon.toString();
    }

    @Override
    public ToolState getState() {
        return state;
    }

    @Override
    public void setState(ToolState state) {
        if (this.state != state) {
            ToolState oldState = this.state;
            this.state = state;
            LogManager.getInstance().debug(String.format("Tool %s state changed: %s -> %s", 
                getName(), oldState, state));
            
            eventBus.publish(new ToolStateChangedEvent(this, oldState, state));
        }
    }

    @Override
    public IToolConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(IToolConfig config) {
        this.config = config;
    }

    @Override
    public ToolGroup getGroup() {
        return group;
    }

    @Override
    public void setGroup(ToolGroup group) {
        this.group = group;
    }

    // IShortcutListener 实现
    @Override
    public boolean onShortcutTriggered(String shortcut) {
        if (shortcut != null && shortcut.equals(shortcutKey)) {
            if (getState() != ToolState.ACTIVE) {
                setState(ToolState.ACTIVE);
                onActivate();
                return true;
            }
        }
        return false;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isEnabled() {
        return getState() != ToolState.DISABLED;
    }

    /**
     * 工具激活时调用
     */
    protected void onActivate() {
        setState(ToolState.ACTIVE);
        LogManager.getInstance().debug("Tool activated: " + getName());
        isActive = true;
        clearPreview();
    }

    /**
     * 工具停用时调用
     */
    protected void onDeactivate() {
        setState(ToolState.INACTIVE);
        LogManager.getInstance().debug("Tool deactivated: " + getName());
        isActive = false;
        clearPreview();
    }

    @Override
    public boolean onMouseDown(Vec2d pos, int button) {
        lastPoint = currentPoint = pos;
        isDragging = true;
        return true;
    }

    @Override
    public boolean onMouseMove(Vec2d pos) {
        lastPoint = currentPoint;
        currentPoint = pos;
        return isDragging;
    }

    @Override
    public boolean onMouseUp(Vec2d pos, int button) {
        isDragging = false;
        return true;
    }

    @Override
    public boolean onMouseDoubleClick(Vec2d pos, int button) {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        updateModifierKeys(keyCode, true);
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode) {
        updateModifierKeys(keyCode, false);
        return false;
    }

    @Override
    public boolean onKeyTyped(char character) {
        return false;
    }

    @Override
    public boolean onMouseWheel(Vec2d pos, double delta) {
        return false;
    }

    /**
     * 工具取消时调用
     */
    protected void onCancel() {
        isDragging = false;
        setState(ToolState.INACTIVE);
    }

    /**
     * 完成绘制操作
     * 由子类实现具体的绘制完成逻辑
     */
    protected void completeDrawing() {
        // 默认实现为空，由子类根据需要重写
        if (previewShape != null) {
            // 如果有预览图形，尝试使用新的参数化方法
            try {
                // 使用反射调用子类可能实现的带参数的completeDrawing方法
                java.lang.reflect.Method method = this.getClass().getDeclaredMethod("completeDrawing", Shape.class);
                method.invoke(this, previewShape);
                LOGGER.debug("使用参数化completeDrawing方法完成绘制");
                return;
            } catch (NoSuchMethodException e) {
                // 子类没有实现带参数的方法，继续使用默认行为
                LOGGER.debug("子类未实现参数化completeDrawing方法，使用默认行为");
            } catch (Exception e) {
                LOGGER.error("调用参数化completeDrawing方法失败", e);
            }
            
                        // 默认行为：如果有活动图层，将预览图形添加到图层
                        // 从 AppState 获取活动图层，确保使用最新的活动图层状态
                        ILayer layer = appState.getActiveLayer();
                        if (layer != null) {
                try {
                    Shape finalShape = previewShape.clone();
                    
                    // 设置样式
                    IShapeStyle style = appState.getCurrentShapeStyle();
                    if (style != null) {
                        finalShape.setStyle(style.clone());
                    } else {
                        finalShape.setStyle(ShapeStyle.DEFAULT.clone());
                    }
                    
                    // 添加到图层
                    layer.addShape(finalShape);
                                        LOGGER.info("图形已添加到图层: {}, 图层名称: {}", layer.getName(), layer.getName());
                    
                    // 确保图层可见
                    if (!layer.isVisible()) {
                        layer.setVisible(true);
                    }
                    
                    // 标记为脏并刷新
                    markDirty();
                    
                    // 通知AppState
                    appState.addShape(finalShape);
                    
                    // 清理预览
                    previewShape = null;
                } catch (Exception e) {
                    LOGGER.error("完成绘制时出错", e);
                }
            }
        }
    }

    @Override
    public void onComplete() {
        // 记录调用以便追踪
        LOGGER.debug("BaseTool.onComplete() 被调用");
    
        // 如果正在拖拽，先停止拖拽
        isDragging = false;
        
        // 检查是否有正在处理的操作
        if (isActive) {
            // 如果previewShape为空，说明可能已经处理过，则不再调用completeDrawing
            if (previewShape != null) {
                LOGGER.debug("BaseTool.onComplete: 执行completeDrawing");
                completeDrawing();  // 调用完成绘制方法
            } else {
                LOGGER.debug("BaseTool.onComplete: previewShape已为空，跳过处理");
            }
        } else {
            LOGGER.debug("BaseTool.onComplete: 工具未处于活动状态，跳过处理");
        }
        
        // 无论如何，都设置为非活动状态
        setState(ToolState.INACTIVE);
    }

    /**
     * 渲染工具的预览效果
     * @param context 绘图上下文
     */
    protected void renderPreview(DrawContext context) {
        // 默认实现为空，由子类根据需要重写
    }

    @Override
    public void render(DrawContext context) {
        // 调用预览渲染
        renderPreview(context);
    }

    @Override
    public void dispose() {
        // 注销快捷键监听器
        if (shortcutKey != null) {
            shortcutManager.unregisterShortcut(shortcutKey, this);
        }
        shortcutManager.removeListener(this);
    }

    // 辅助方法
    protected void updateModifierKeys(int keyCode, boolean pressed) {
        switch (keyCode) {
            case 340: // Left Shift
            case 344: // Right Shift
                isShiftDown = pressed;
                break;
            case 341: // Left Control
            case 345: // Right Control
                isControlDown = pressed;
                break;
            case 342: // Left Alt
            case 346: // Right Alt
                isAltDown = pressed;
                break;
        }
    }

    protected void updateStatusMessage(String message) {
        eventBus.publish(new Events.StatusMessageEvent(message));
    }

    protected void setCursor(String cursorType) {
        eventBus.publish(new Events.CursorChangedEvent(cursorType));
    }

    @Override
    public String getTooltip() {
        return description;
    }

    @Override
    public void setEnabled(boolean enabled) {
        setState(enabled ? ToolState.INACTIVE : ToolState.DISABLED);
    }

    @Override
    public void reset() {
        isDragging = false;
        lastPoint = null;
        currentPoint = null;
        isShiftDown = false;
        isControlDown = false;
        isAltDown = false;
    }

    @Override
    public boolean isAvailable() {
        return getState() != ToolState.DISABLED;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public void activate() {
        if (isAvailable()) {
            setState(ToolState.ACTIVE);
            onActivate();
        }
    }

    @Override
    public void deactivate() {
        if (isActive()) {
            setState(ToolState.INACTIVE);
            onDeactivate();
        }
    }

    @Override
    public void execute() {
        if (isActive()) {
            onComplete();
        }
    }

    @Override
    public void cancel() {
        if (isActive()) {
            onCancel();
        }
    }

    /**
     * 提交当前操作到活动图层
     * 修复：移除ILayer参数，统一使用AppState管理的活动图层，确保API设计一致性
     * 
     * <p>此方法现在完全依赖AppState的状态管理，确保：</p>
     * <ul>
     *   <li>使用正确的活动图层</li>
     *   <li>触发完整的业务逻辑（命令历史、事件等）</li>
     *   <li>API签名与实现逻辑完全一致</li>
     * </ul>
     */
    public void commit() {
        ILayer activeLayer = appState.getActiveLayer();
        
        if (previewShape == null) {
            LOGGER.debug("没有预览图形需要提交");
            return;
        }
        
        if (activeLayer == null) {
            LOGGER.error("没有活动图层，无法提交图形");
            return;
        }
        
        if (activeLayer.isLocked()) {
            LOGGER.warn("活动图层 '{}' 已锁定，无法提交图形", activeLayer.getName());
            return;
        }
        
        try {
            LOGGER.debug("开始提交图形到活动图层 '{}'", activeLayer.getName());
            
            // 克隆预览图形以避免引用问题
            Shape finalShape = previewShape.clone();
            
            // 确保图形有样式
            if (finalShape.getStyle() == null) {
                IShapeStyle style = appState.getCurrentShapeStyle();
                if (style != null) {
                    finalShape.setStyle(style.clone());
                } else {
                    // 创建默认样式
                    ShapeStyle defaultStyle = new ShapeStyle();
                    defaultStyle.setStrokeColor(java.awt.Color.BLACK);
                    defaultStyle.setStrokeWidth(2.0f);
                    finalShape.setStyle(defaultStyle);
                }
            }
            
            // 修复：统一通过AppState提交图形，确保完整的业务逻辑
            // 这样可以确保命令历史、事件发布、插件触发等所有逻辑都被正确执行
            appState.addShape(finalShape);
            
            LOGGER.debug("图形已通过AppState提交到活动图层 '{}'", activeLayer.getName());
            
            // 清理预览状态
            clearPreview();
            markDirty();
            
        } catch (Exception e) {
            LOGGER.error("提交图形失败", e);
        }
    }
    
    /**
     * 向后兼容的commit方法
     * @param layer 目标图层（此参数现在被忽略，始终使用活动图层）
     * @deprecated 推荐使用无参数的 {@link #commit()} 方法，
     *             该方法使用AppState管理的活动图层，确保API设计一致性
     */
    @Deprecated
    public void commit(ILayer layer) {
        if (layer != null) {
            ILayer activeLayer = appState.getActiveLayer();
            if (!layer.equals(activeLayer)) {
                LOGGER.warn("传入的目标图层 '{}' 与活动图层 '{}' 不一致，使用活动图层", 
                           layer.getName(), 
                           activeLayer != null ? activeLayer.getName() : "null");
            }
        }
        
        // 委托给新的无参数方法
        commit();
    }

    /**
     * 清除预览状态
     */
    protected void clearPreview() {
        LOGGER.debug("清除预览状态");
        previewShape = null;
        isDirty = true;
    }
    
    /**
     * 标记工具状态为脏
     */
    protected void markDirty() {
        isDirty = true;
    }
    
    /**
     * 清除脏标记
     */
    public void clearDirty() {
        isDirty = false;
    }
    
    /**
     * 检查工具是否处于脏状态
     */
    public boolean isDirty() {
        return isDirty;
    }

    /**
     * 获取预览图形
     */
    public Shape getPreviewShape() {
        return previewShape;
    }

    /**
     * 判断工具是否处于选择模式
     * @return true表示工具当前处于选择模式，false表示非选择模式
     */
    public boolean isSelecting() {
        // 默认实现为false，由子类根据需要重写
        return false;
    }

    /**
     * 获取默认光标类型
     * @return 默认光标类型
     */
    public String getDefaultCursor() {
        return "default";
    }

}
