package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.model.Shape;
import com.plot.core.tool.BaseTool;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ToolStyle;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.snap.SnapManager;
import com.plot.core.shortcut.ShortcutManager;
import com.plot.core.state.AppState;

import net.minecraft.util.Identifier;
import imgui.ImDrawList;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.infrastructure.event.tool.ToolStatusEvent;
import com.plot.infrastructure.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.api.model.IDirty;
import com.plot.ui.canvas.Canvas;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 导入策略模式相关类
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import com.plot.ui.tools.snap.SnapHandler;
import com.plot.ui.tools.impl.drawing.strategy.IShapeFactory;

// 导入辅助类
import com.plot.ui.tools.impl.drawing.helper.ISnapHandler;
import com.plot.ui.tools.impl.drawing.helper.IStyleHandler;
import com.plot.ui.tools.impl.drawing.helper.StyleHandler;

/**
 * 绘图工具基类 - 纯粹策略模式版本
 * 
 * <p>这是一个采用纯粹策略模式的绘图工具基类，作为上下文(Context)角色：</p>
 * <ul>
 *   <li><strong>职责单一</strong>：只负责协调策略和提供公共服务</li>
 *   <li><strong>完全委托</strong>：所有交互逻辑都委托给IInteractionStrategy实现</li>
 *   <li><strong>无状态污染</strong>：不包含任何具体的交互处理代码</li>
 *   <li><strong>高度可扩展</strong>：新增交互模式只需添加策略类</li>
 * </ul>
 * 
 * <p><strong>架构优势：</strong></p>
 * <ul>
 *   <li>消除了新旧逻辑冲突</li>
 *   <li>代码流程单一且可预测</li>
 *   <li>策略间完全隔离，互不影响</li>
 *   <li>极易测试和维护</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 7.0 - 纯粹策略模式
 */
public abstract class DrawingTool extends BaseTool implements IDirty, IInteractionStrategy.DrawingToolContext {
    protected static final Logger LOGGER = LoggerFactory.getLogger(DrawingTool.class);

    private static volatile EventBus sharedEventBus = EventBus.getInstance();
    private static volatile ShortcutManager sharedShortcutManager = ShortcutManager.getInstance();

    /**
     * 在组合根配置共享依赖，避免各绘图工具构造器内直接调用单例。
     */
    public static void configureSharedDependencies(EventBus eventBus, ShortcutManager shortcutManager) {
        sharedEventBus = Objects.requireNonNull(eventBus, "EventBus 不能为空");
        sharedShortcutManager = Objects.requireNonNull(shortcutManager, "ShortcutManager 不能为空");
    }
    
    /**
     * 绘图工具交互模式枚举
     */
    protected enum InteractionType {
        DRAG_AND_DROP("拖放模式", "按下鼠标拖动绘制，松开完成"),
        CLICK_AND_CLICK("点击模式", "点击设置控制点，移动显示预览，再次点击完成");
        
        private final String displayName;
        private final String description;
        
        InteractionType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        
        @Override
        public String toString() {
            return displayName + ": " + description;
        }
    }

    /**
     * 工具状态枚举
     */
    public enum ToolState {
        IDLE("空闲", "工具处于空闲状态，等待用户操作"),
        DRAWING("绘制中", "正在进行绘制操作，显示实时预览");
        
        private final String displayName;
        private final String description;
        
        ToolState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 鼠标按键常量
    protected static final int MOUSE_LEFT = 0;
    protected static final int MOUSE_RIGHT = 1;
    protected static final int MOUSE_MIDDLE = 2;
    
    // 核心组件引用
    protected final String toolId;
    protected final String toolName;
    protected final Identifier toolIcon;
    protected final String toolDescription;
    protected final ISnapManager snapManager;
    private final InteractionType interactionType;
    protected final ToolStyle toolStyle;
    
    // 策略模式组件
    protected IInteractionStrategy interactionStrategy;
    protected final IShapeFactory shapeFactory;
    
    // 辅助类组件
    protected final ISnapHandler snapHandler;
    protected final IStyleHandler styleHandler;
    
    // 工具状态
    private ToolState currentState = ToolState.IDLE;
    private final AtomicBoolean isDirtyFlag = new AtomicBoolean(false);
    
    // 绘制状态
    protected Shape previewShape;
    protected String statusMessage = "";
    protected Canvas canvas;
    protected CanvasCamera camera;

    /**
     * 构造函数（推荐方式 - 依赖注入 + 交互模式）
     */
    protected DrawingTool(String id, String name, Identifier icon, String description,
                         IAppState appState, ISnapManager snapManager, InteractionType interactionType) {
        this(id, name, icon, description, appState, snapManager,
                sharedEventBus, sharedShortcutManager, interactionType);
    }

    protected DrawingTool(String id, String name, Identifier icon, String description,
                         IAppState appState, ISnapManager snapManager,
                         EventBus eventBus, ShortcutManager shortcutManager,
                         InteractionType interactionType) {
        super(id, description, icon, name, appState, eventBus, shortcutManager);

        this.toolId = id;
        this.toolName = name;
        this.toolIcon = icon;
        this.toolDescription = description;
        this.snapManager = snapManager;
        this.interactionType = Objects.requireNonNull(interactionType, "InteractionType 不能为空");
        this.toolStyle = new ToolStyle();

        // 初始化辅助类
        this.snapHandler = new SnapHandler(appState, snapManager, id);
        this.styleHandler = new StyleHandler(appState, id);
        
        // 创建内部图形工厂
        this.shapeFactory = new InternalShapeFactory();
        
        // 在构造时就创建策略
        this.interactionStrategy = createStrategy(interactionType);
        
        LOGGER.debug("DrawingTool [{}] 初始化完成（交互模式: {}）", id, interactionType.getDisplayName());
    }
    
    /**
     * 构造函数（向后兼容方式 - 默认拖放模式）
     */
    protected DrawingTool(String id, String name, Identifier icon, String description, 
                         IAppState appState, ISnapManager snapManager) {
        this(id, name, icon, description, appState, snapManager, InteractionType.DRAG_AND_DROP);
    }
    
    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    protected DrawingTool(String id, String name, Identifier icon, String description) {
        this(id, name, icon, description, AppState.getInstance(), SnapManager.getInstance(), InteractionType.DRAG_AND_DROP);
    }

    /**
     * 允许子类安全设置自定义交互策略，替代反射
     */
    protected void setInteractionStrategy(IInteractionStrategy strategy) {
        this.interactionStrategy = strategy;
    }

    // ====== 鼠标事件处理（完全委托给策略） ======

    @Override
    public final boolean onMouseDown(Vec2d pos, int button) {
        LOGGER.debug("DrawingTool [{}] onMouseDown: 位置={}, 按钮={}", toolId, pos, button);
        
        // 修改：支持所有鼠标按钮，让交互策略决定如何处理
        if (interactionStrategy == null) {
            LOGGER.warn("DrawingTool [{}] 没有交互策略", toolId);
            return false;
        }
        
        try {
            LOGGER.debug("DrawingTool [{}] 调用交互策略: {}", toolId, interactionStrategy.getClass().getSimpleName());
            IInteractionStrategy.InteractionResult result = 
                interactionStrategy.onMouseDown(pos, button, this);
            
            LOGGER.debug("DrawingTool [{}] 交互结果: {}", toolId, result);
            return handleInteractionResult(result, "鼠标按下");
            
        } catch (Exception e) {
            LOGGER.error("工具 [{}] 鼠标按下处理失败: {}", toolId, e.getMessage(), e);
            resetDrawing("鼠标按下处理异常");
            return false;
        }
    }

    @Override
    public final boolean onMouseMove(Vec2d pos) {
        if (interactionStrategy == null) {
            return false;
        }
        
        try {
            IInteractionStrategy.InteractionResult result = 
                interactionStrategy.onMouseMove(pos, this);
            
            return handleInteractionResult(result, "鼠标移动");
            
        } catch (Exception e) {
            LOGGER.error("工具 [{}] 鼠标移动处理失败: {}", toolId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public final boolean onMouseUp(Vec2d pos, int button) {
        if (button != MOUSE_LEFT || interactionStrategy == null) {
            return false;
        }
        
        try {
            IInteractionStrategy.InteractionResult result = 
                interactionStrategy.onMouseUp(pos, button, this);
            
            return handleInteractionResult(result, "鼠标释放");
            
        } catch (Exception e) {
            LOGGER.error("工具 [{}] 鼠标释放处理失败: {}", toolId, e.getMessage(), e);
            resetDrawing("鼠标释放处理异常");
            return false;
        }
    }

    /**
     * 统一处理交互结果
     * 这是状态管理的核心方法，确保职责边界清晰
     */
    private boolean handleInteractionResult(IInteractionStrategy.InteractionResult result, String eventType) {
        switch (result) {
            case CONTINUE:
                // 交互继续，设置为绘制状态以显示预览
                if (this.currentState != ToolState.DRAWING) {
                    this.currentState = ToolState.DRAWING;
                    LOGGER.debug("工具 [{}] 进入绘制状态", toolId);
                }
                LOGGER.trace("工具 [{}] {} - 交互继续", toolId, eventType);
                return true;
                
            case COMPLETE:
                // 交互完成，获取最终图形并提交
                try {
                    LOGGER.info("工具 [{}] {} - 收到 COMPLETE，准备获取最终图形", toolId, eventType);
                    Shape finalShape = interactionStrategy.getFinalShape();
                    if (finalShape != null) {
                        LOGGER.info("工具 [{}] {} - 成功获取最终图形 ID={}, 类型={}", 
                            toolId, eventType, finalShape.getId(), finalShape.getClass().getSimpleName());
                        // commitShape现在是完整的原子操作，会自动处理提交和重置
                        commitShape(finalShape);
                        LOGGER.info("工具 [{}] {} - 交互完成，图形已提交", toolId, eventType);
                    } else {
                        LOGGER.error("工具 [{}] {} - 交互完成但无法获取最终图形（getFinalShape返回null）", toolId, eventType);
                        resetDrawing("最终图形获取失败");
                    }
                } catch (Exception e) {
                    LOGGER.error("工具 [{}] {} - 处理完成结果失败: {}", toolId, eventType, e.getMessage(), e);
                    // commitShape失败时已经重置了状态，这里不需要再次重置
                }
                return true;
                
            case CANCEL:
                // 交互取消，重置状态
                resetDrawing("交互取消");
                LOGGER.debug("工具 [{}] {} - 交互取消", toolId, eventType);
                return true;
                
            case IGNORED:
                // 事件被忽略
                LOGGER.trace("工具 [{}] {} - 事件被忽略", toolId, eventType);
                return false;
                
            default:
                LOGGER.warn("工具 [{}] {} - 未知的交互结果: {}", toolId, eventType, result);
                return false;
        }
    }

    @Override
    public void onCancel() {
        resetDrawing("用户取消");
    }

    // ====== DrawingToolContext接口实现（供策略回调） ======

    @Override
    public void commitShape(Shape shape) {
        if (shape == null) {
            LOGGER.warn("工具 [{}] 尝试提交空图形", toolId);
            return;
        }
        
        try {
            // 委托给StyleHandler应用最终样式
            styleHandler.applyFinalStyle(shape);
            
            // 提交到AppState
            commitShapeToAppState(shape);
            
            LOGGER.debug("工具 [{}] 成功提交图形 [{}]", toolId, shape.getId());
            
            // 提交成功后，直接在这里重置状态
            resetDrawing("图形提交完成");
            
        } catch (Exception e) {
            LOGGER.error("工具 [{}] 提交图形失败: {}", toolId, e.getMessage(), e);
            // 失败也要重置状态，确保工具不会卡在异常状态
            resetDrawing("图形提交异常");
            throw new RuntimeException("图形提交失败", e);
        }
    }

    @Override
    public void resetDrawing(String reason) {
        this.currentState = ToolState.IDLE;
        this.previewShape = null;
        this.statusMessage = "";
        clearDirty();
        
        // 如果策略有自己的状态，也需要重置
        if (interactionStrategy != null) {
            interactionStrategy.reset();
        }
        
        LOGGER.debug("工具 [{}] 重置绘制状态: {}", toolId, reason);
    }

    @Override
    public void setPreviewShape(Shape shape) {
        this.previewShape = shape;
        markDirty();
    }

    @Override
    public void setToolState(ToolState state) {
        if (this.currentState != state) {
            ToolState oldState = this.currentState;
            this.currentState = state;
            LOGGER.debug("工具 [{}] 状态变更: {} -> {}", toolId, oldState.getDisplayName(), state.getDisplayName());
        }
    }

    @Override
    public ISnapHandler getSnapHandler() {
        return snapHandler;
    }

    @Override
    public IStyleHandler getStyleHandler() {
        return styleHandler;
    }

    @Override
    public IShapeFactory getShapeFactory() {
        return shapeFactory;
    }

    @Override
    public CanvasCamera getCamera() {
        return camera;
    }

    // ====== 内部辅助方法 ======

    /**
     * 提交图形到AppState
     */
    private void commitShapeToAppState(Shape shape) {
        try {
            if (appState instanceof AppState concreteAppState) {
                ModifyCommand command = new ModifyCommand(Collections.emptyList(), new ArrayList<>(List.of(shape)), concreteAppState);
                concreteAppState.getCommandHistory().execute(command);
                LOGGER.debug("工具 [{}] 成功提交图形 [{}] 到AppState", toolId, shape.getId());
            } else {
                appState.addShape(shape);
                LOGGER.debug("工具 [{}] 通过 IAppState 成功提交图形 [{}]", toolId, shape.getId());
            }
        } catch (Exception e) {
            LOGGER.error("工具 [{}] 提交图形失败: {}", toolId, e.getMessage(), e);
            throw new RuntimeException("图形提交失败", e);
        }
    }

    // ====== 渲染方法 ======

    @Override
    public void render(DrawContext context) {
        if (shouldShowPreview()) {
            renderPreview(context);
        }
    }

    /**
     * 渲染预览（DrawContext版本）
     * 子类必须实现此方法
     */
    protected abstract void renderPreview(DrawContext context);

    /**
     * 渲染预览（ImGui版本）
     * 子类必须实现此方法
     */
    public abstract void renderPreview(ImDrawList drawList, CanvasCamera camera);

    // ====== 状态管理方法 ======

    protected ToolState getCurrentState() {
        return currentState;
    }
    
    protected boolean isInState() {
        return getCurrentState() == ToolState.DRAWING;
    }
    
    protected boolean shouldShowPreview() {
        // 修复：在绘制状态下总是显示预览，不依赖previewShape
        // 因为有些工具（如PolylineTool）在绘制过程中使用实时渲染而不是previewShape
        return currentState == ToolState.DRAWING;
    }

    // ====== 样式管理 ======

    protected ToolStyle getStyle() {
        return toolStyle;
    }

    // ====== 状态消息管理 ======

    protected void setStatusMessage(String message) {
        this.statusMessage = message != null ? message : "";
        try {
            if (eventBus != null) {
                eventBus.publish(new ToolStatusEvent(toolId, this.statusMessage));
            }
        } catch (Exception e) {
            LOGGER.error("工具 [{}] 发布状态消息时出错: {}", toolId, e.getMessage(), e);
        }
    }

    protected void updateStatusMessage(String message) {
        setStatusMessage(message);
    }

    // ====== 相机和画布管理 ======

    public void setCamera(CanvasCamera camera) {
        this.camera = camera;
        // 如果策略尚未初始化且相机现在可用，则重新创建策略
        if (interactionStrategy == null && camera != null) {
            this.interactionStrategy = createStrategy(interactionType);
            LOGGER.debug("工具 [{}] 在设置相机后重新创建交互策略: {}", toolId, 
                        interactionStrategy != null ? interactionStrategy.getStrategyName() : "null");
        }
    }

    public void setCanvas(Canvas canvas) {
        this.canvas = canvas;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    // ====== 抽象方法（子类必须实现） ======

    /**
     * 更新配置
     * 子类应该重写此方法来处理特定的配置选项
     */
    public abstract void updateConfig(String key, String value);

    /**
     * 创建图形（新的无副作用版本）
     * 子类必须实现此方法来创建具体的图形对象
     * 
     * @param startPoint 起点坐标
     * @param endPoint 终点坐标
     * @return 创建的图形对象，如果无法创建则返回null
     */
    protected abstract Shape createShape(Vec2d startPoint, Vec2d endPoint);

    // ====== IDirty接口实现 ======

    @Override
    public boolean isDirty() {
        return isDirtyFlag.get();
    }

    @Override
    public void clearDirty() {
        isDirtyFlag.set(false);
    }

    @Override
    public void markDirty() {
        isDirtyFlag.set(true);
    }

    // ====== BaseTool接口实现 ======

    /**
     * BaseTool的commit方法实现
     * 在策略模式下，这个方法主要用于兼容性
     */
    @Override
    public void commit() {
        LOGGER.warn("工具 [{}] 的 commit() 方法被意外调用，策略模式下应由策略驱动提交。", toolId);
        if (this.previewShape != null) {
            commitShape(this.previewShape);
        }
    }

    @Override
    public boolean isSelecting() {
        return false; // 绘图工具通常不用于选择
    }

    @Override
    public void activate() {
        super.activate();
        resetDrawing("工具激活");
        LOGGER.debug("工具 [{}] 已激活", toolId);
    }
    
    @Override
    public void deactivate() {
        resetDrawing("工具停用");
        super.deactivate();
        LOGGER.debug("工具 [{}] 已停用", toolId);
    }

    @Override
    public String getDefaultCursor() {
        return "crosshair";
    }

    // ====== 资源清理 ======

    public void dispose() {
        try {
            LOGGER.debug("DrawingTool [{}] 开始资源清理", toolId);
            resetDrawing("资源清理");
            LOGGER.debug("DrawingTool [{}] 资源清理完成", toolId);
        } catch (Exception e) {
            LOGGER.error("DrawingTool [{}] 资源清理时出错: {}", toolId, e.getMessage(), e);
        }
    }

    // ====== 策略模式支持方法 (关键修改) ======

    /**
     * 创建交互策略的抽象工厂方法。
     * <p>
     * 此方法被声明为 abstract，强制每个具体的绘图工具子类（如 RectangleTool, CircleTool）
     * 必须提供它们自己的交互策略实现。这确保了每个工具都使用正确的逻辑，
     * 避免了基类中的硬编码逻辑。
     * </p>
     *
     * @param type 工具的交互类型（拖放或点击）
     * @return 适用于该工具的 IInteractionStrategy 实例。
     */
    protected abstract IInteractionStrategy createStrategy(InteractionType type);
    
    // ====== 内部类定义 ======
    
    /**
     * 内部图形工厂实现（无副作用版本）
     * 直接委托给子类的带参数createShape方法，避免状态污染
     */
    private class InternalShapeFactory implements IShapeFactory {
        @Override
        public Shape createShape(Vec2d startPoint, Vec2d endPoint) {
            // 直接调用带参数的createShape方法，无副作用
            return DrawingTool.this.createShape(startPoint, endPoint);
        }
        
        @Override
        public Shape createShapeFromPoints(List<Vec2d> points) {
            // 优先调用子类的 public createShapeFromPoints（如 StarTool）
            try {
                return (Shape) DrawingTool.this.getClass()
                    .getMethod("createShapeFromPoints", List.class)
                    .invoke(DrawingTool.this, points);
            } catch (NoSuchMethodException e) {
                // 方法不存在是正常情况，静默回退到默认实现
            } catch (Exception e) {
                // 其他异常才记录为错误
                LOGGER.error("反射调用 createShapeFromPoints 失败: 工具类={}, 错误={}",
                        DrawingTool.this.getClass().getSimpleName(), e.getMessage(), e);
            }
            // 默认实现：两点降级
            if (points == null || points.isEmpty()) return null;
            if (points.size() == 1) return createShape(points.getFirst(), points.getFirst());
            return createShape(points.getFirst(), points.getLast());
        }
    }
} 