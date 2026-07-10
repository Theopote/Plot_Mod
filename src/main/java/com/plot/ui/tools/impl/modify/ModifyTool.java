package com.plot.ui.tools.impl.modify;

import com.plot.api.geometry.Vec2d;
import com.plot.api.model.ICanvas;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.core.selection.Selection;
import com.plot.core.tool.BaseTool;
import com.plot.utils.PlotI18n;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.state.AppState;
import com.plot.core.shortcut.ShortcutManager;
import com.plot.core.snap.SnapManager;
import com.plot.core.graphics.DrawContext;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.utils.ExceptionDebug;
import com.plot.infrastructure.event.EventBus;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

// 导入策略模式相关类
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.ui.tools.snap.SnapHandler;
// 导入绘制工具的辅助类（修改工具可以复用）
import com.plot.ui.tools.impl.drawing.helper.ISnapHandler;
import com.plot.ui.tools.impl.drawing.helper.IStyleHandler;
import com.plot.ui.tools.impl.drawing.helper.StyleHandler;

// 导入修改工具的辅助类
import com.plot.ui.tools.impl.modify.helper.GeometricSelectionHelper;

/**
 * 修改工具基类 - 策略模式版本
 * 
 * <p>这是一个采用策略模式的修改工具基类，作为上下文(Context)角色：</p>
 * <ul>
 *   <li><strong>职责单一</strong>：只负责协调策略和提供公共服务</li>
 *   <li><strong>完全委托</strong>：所有修改逻辑都委托给IModifyStrategy实现</li>
 *   <li><strong>选择感知</strong>：自动管理选择状态和相关事件</li>
 *   <li><strong>命令支持</strong>：集成命令模式，支持撤销/重做</li>
 * </ul>
 * 
 * <p><strong>与绘制工具的差异：</strong></p>
 * <ul>
 *   <li>操作现有图形而非创建新图形</li>
 *   <li>依赖选择状态进行操作</li>
 *   <li>使用命令模式支持撤销</li>
 *   <li>支持预览修改效果</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 修改工具策略模式
 */
public abstract class ModifyTool extends BaseTool implements IModifyStrategy.ModifyToolContext {
    // 统一捕捉可视化
    protected final com.plot.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.plot.ui.tools.snap.SnapEnhancer("ModifyTool");
    protected static final Logger LOGGER = LoggerFactory.getLogger(ModifyTool.class);

    private static volatile EventBus sharedEventBus = EventBus.getInstance();
    private static volatile ShortcutManager sharedShortcutManager = ShortcutManager.getInstance();

    /**
     * 在组合根配置共享依赖，避免各工具构造器内直接调用单例。
     */
    public static void configureSharedDependencies(EventBus eventBus, ShortcutManager shortcutManager) {
        sharedEventBus = Objects.requireNonNull(eventBus, PlotI18n.error("error.plot.validation.event_bus_null"));
        sharedShortcutManager = Objects.requireNonNull(shortcutManager, PlotI18n.error("error.plot.validation.shortcut_manager_null"));
    }
    
    /**
     * 修改工具状态枚举
     */
    public enum ToolState {
        IDLE("mode.plot.common.idle", "mode.plot.eraser.state.idle.desc"),
        SELECTING("mode.plot.tool.state.selecting", "mode.plot.tool.state.selecting.desc"),
        MODIFYING("mode.plot.tool.state.modifying", "mode.plot.tool.state.modifying.desc"),
        PREVIEWING("mode.plot.array.state.previewing", "mode.plot.array.state.previewing.desc");

        private final String nameKey;
        private final String descKey;

        ToolState(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }
    
    // 鼠标按键常量
    protected static final int MOUSE_LEFT = 0;
    protected static final int MOUSE_RIGHT = 1;
    protected static final int MOUSE_MIDDLE = 2;
    protected static final int ALT_KEY_CODE = 18;
    
    // 核心组件引用
    protected final String toolId;
    protected final String toolName;
    protected final Identifier toolIcon;
    protected final String toolDescription;
    protected final AppState concreteAppState;
    protected final ISnapManager snapManager;
    
    // 策略模式组件
    protected IModifyStrategy modifyStrategy;
    
    // 辅助类组件（复用绘制工具的辅助类）
    protected final ISnapHandler snapHandler;
    protected final IStyleHandler styleHandler;
    
    // 工具状态
    private ToolState currentState = ToolState.IDLE;
    private final AtomicBoolean isPreviewEnabled = new AtomicBoolean(false);
    
    // 选择和修改状态
    protected Selection selection;
    protected final ShapeStyle previewStyle;
    private final Map<String, Object> options;
    
    // 画布和相机引用
    protected ICanvas canvas;
    protected CanvasCamera camera;
    
    // 控制点编辑工具引用
    private ControlPointEditTool controlPointEditTool;

    /**
     * 构造函数（推荐方式 - 依赖注入）
     */
    protected ModifyTool(String id, Identifier icon,
                        IAppState appState, ISnapManager snapManager,
                        EventBus eventBus, ShortcutManager shortcutManager) {
        this(id, id, icon, id, appState, snapManager, eventBus, shortcutManager);
    }

    protected ModifyTool(String id, Identifier icon,
                        IAppState appState, ISnapManager snapManager) {
        this(id, icon, appState, snapManager, sharedEventBus, sharedShortcutManager);
    }

    /**
     * @deprecated 推荐使用 {@link #ModifyTool(String, Identifier, IAppState, ISnapManager)}
     */
    @Deprecated
    protected ModifyTool(String id, Identifier icon) {
        this(id, icon, AppState.getInstance(), SnapManager.getInstance());
    }

    protected ModifyTool(String id, String name, Identifier icon, String description,
                        IAppState appState, ISnapManager snapManager) {
        this(id, name, icon, description, appState, snapManager,
                sharedEventBus, sharedShortcutManager);
    }

    protected ModifyTool(String id, String name, Identifier icon, String description,
                        IAppState appState, ISnapManager snapManager,
                        EventBus eventBus, ShortcutManager shortcutManager) {
        super(id, PlotI18n.toolDescription(id), icon, PlotI18n.toolLabel(id),
                appState, eventBus, shortcutManager);

        this.toolId = id;
        this.toolName = this.name;
        this.toolIcon = icon;
        this.toolDescription = this.description;
        this.concreteAppState = requireConcreteAppState(appState);
        this.snapManager = snapManager;
        this.previewStyle = ShapeStyle.PREVIEW;
        this.options = new HashMap<>();

        this.snapHandler = new SnapHandler(appState, snapManager, id);
        this.styleHandler = new StyleHandler(appState, id);

        LOGGER.debug("ModifyTool [{}] 初始化完成", id);
    }
    
    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    protected ModifyTool(String id, String name, Identifier icon, String description) {
        this(id, name, icon, description, AppState.getInstance(), SnapManager.getInstance());
    }

    // ====== 鼠标事件处理（完全委托给策略） ======

    @Override
    public final boolean onMouseDown(Vec2d pos, int button) {
        IModifyStrategy strategy = getStrategy();
        if (strategy == null) {
            LOGGER.warn("ModifyTool [{}] 策略为空", toolId);
            return false;
        }
        
        try {
            // 更新捕捉可视化状态
            try { snapEnhancer.performEnhancedSnap(pos, this); } catch (Exception e) { ExceptionDebug.log("ModifyTool: update snap on mouse down", e); }
            IModifyStrategy.ModifyResult result = 
                strategy.onMouseDown(pos, button, this);
            
            return handleModifyResult(result, "鼠标按下");
            
        } catch (Exception e) {
            LOGGER.error("ModifyTool [{}] 鼠标按下处理失败: {}", toolId, e.getMessage(), e);
            resetModification("鼠标按下异常");
            return false;
        }
    }

    @Override
    public final boolean onMouseMove(Vec2d pos) {
        IModifyStrategy strategy = getStrategy();
        if (strategy == null) {
            return false;
        }
        
        try {
            // 更新捕捉可视化状态
            try { snapEnhancer.performEnhancedSnap(pos, this); } catch (Exception e) { ExceptionDebug.log("ModifyTool: update snap on mouse move", e); }
            IModifyStrategy.ModifyResult result =
                strategy.onMouseMove(pos, this);

            return handleModifyResult(result, "鼠标移动");
            
        } catch (Exception e) {
            LOGGER.error("ModifyTool [{}] 鼠标移动处理失败: {}", toolId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public final boolean onMouseUp(Vec2d pos, int button) {
        IModifyStrategy strategy = getStrategy();
        if (strategy == null) {
            return false;
        }
        
        try {
            IModifyStrategy.ModifyResult result = 
                strategy.onMouseUp(pos, button, this);
            
            return handleModifyResult(result, "鼠标释放");
            
        } catch (Exception e) {
            LOGGER.error("ModifyTool [{}] 鼠标释放处理失败: {}", toolId, e.getMessage(), e);
            resetModification("鼠标释放异常");
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        LOGGER.debug("ModifyTool [{}] onKeyDown 被调用: keyCode={}", toolId, keyCode);
        IModifyStrategy strategy = getStrategy();
        if (strategy == null) {
            LOGGER.debug("ModifyTool [{}] 策略为空，使用父类处理", toolId);
            return super.onKeyDown(keyCode);
        }
        
        try {
            IModifyStrategy.ModifyResult result = 
                strategy.onKeyDown(keyCode, this);
            
            LOGGER.debug("ModifyTool [{}] 策略返回结果: {}", toolId, result);
            boolean handled = handleModifyResult(result, "键盘按下");
            LOGGER.debug("ModifyTool [{}] 处理结果: handled={}", toolId, handled);
            return handled || super.onKeyDown(keyCode);
            
        } catch (Exception e) {
            LOGGER.error("ModifyTool [{}] 键盘按下处理失败: {}", toolId, e.getMessage(), e);
            return super.onKeyDown(keyCode);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode) {
        IModifyStrategy strategy = getStrategy();
        if (strategy == null) {
            return super.onKeyUp(keyCode);
        }
        
        try {
            IModifyStrategy.ModifyResult result = 
                strategy.onKeyUp(keyCode, this);
            
            boolean handled = handleModifyResult(result, "键盘释放");
            return handled || super.onKeyUp(keyCode);
            
        } catch (Exception e) {
            LOGGER.error("ModifyTool [{}] 键盘释放处理失败: {}", toolId, e.getMessage(), e);
            return super.onKeyUp(keyCode);
        }
    }
    
    @Override
    public boolean onMouseWheel(Vec2d pos, double delta) {
        IModifyStrategy strategy = getStrategy();
        if (strategy == null) {
            return super.onMouseWheel(pos, delta);
        }
        
        try {
            IModifyStrategy.ModifyResult result = 
                strategy.onMouseWheel(pos, delta, this);
            
            boolean handled = handleModifyResult(result, "鼠标滚轮");
            return handled || super.onMouseWheel(pos, delta);
            
        } catch (Exception e) {
            LOGGER.error("ModifyTool [{}] 鼠标滚轮处理失败: {}", toolId, e.getMessage(), e);
            return super.onMouseWheel(pos, delta);
        }
    }

    /**
     * 处理策略返回的修改结果
     */
    private boolean handleModifyResult(IModifyStrategy.ModifyResult result, String eventType) {
        switch (result) {
            case CONTINUE -> {
                return true;
            }
            case COMPLETE -> {
                // 策略完成，尝试获取并执行修改命令
                IModifyStrategy strategy = getStrategy();
                if (strategy != null) {
                    ModifyCommand command = strategy.getModifyCommand();
                if (command != null) {
                    executeModifyCommand(command);
                    }
                }
                return true;
            }
            case CANCEL -> {
                resetModification("用户取消");
                return true;
            }
            case NEED_SELECTION -> {
                setStatusMessage("status.plot.common.select_modify_first");
                return true;
            }
            case IGNORED -> {
                return false;
            }
            default -> {
                LOGGER.warn("ModifyTool [{}] 未知的修改结果: {}", toolId, result);
                return false;
            }
        }
    }

    // ====== ModifyToolContext 接口实现 ======

    @Override
    public void executeModifyCommand(ModifyCommand command) {
        if (command != null) {
            try {
                concreteAppState.getCommandHistory().execute(command);
                LOGGER.debug("ModifyTool [{}] 执行修改命令: {}", toolId, command.getClass().getSimpleName());
                
                // 强制同步清理新旧图形的视觉状态：不选中、不高亮
                try {
                    List<Shape> oldShapes = command.getOldShapes();
                    if (oldShapes != null) {
                        for (Shape s : oldShapes) {
                            try { s.setSelected(false); s.setHighlighted(false); } catch (Exception e) { ExceptionDebug.log("ModifyTool: clear selection on command shapes", e); }
                        }
                    }
                    List<Shape> newShapes = command.getNewShapes();
                    if (newShapes != null) {
                        for (Shape s : newShapes) {
                            try { s.setSelected(false); s.setHighlighted(false); } catch (Exception e) { ExceptionDebug.log("ModifyTool: clear selection on command shapes", e); }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("清理图形视觉状态时发生异常: {}", e.getMessage());
                }

                // 需求变更：变换后的图形应为未选中状态
                // 为保持视觉与逻辑一致，执行完修改后清空选择
                try {
                    clearSelection();
                } catch (Exception e) {
                    LOGGER.debug("清空选择时发生异常: {}", e.getMessage());
                }

                // 命令执行后重置状态
                resetModification("命令执行完成");
                
            } catch (Exception e) {
                LOGGER.error("ModifyTool [{}] 执行修改命令失败: {}", toolId, e.getMessage(), e);
                resetModification("命令执行失败");
            }
        }
    }

    @Override
    public void resetModification(String reason) {
        LOGGER.debug("ModifyTool [{}] 重置修改状态: {}", toolId, reason);
        setModifyToolState(ToolState.IDLE);
        setPreviewEnabled(false);
        
        IModifyStrategy strategy = getStrategy();
        if (strategy != null) {
            strategy.reset();
        }
    }
    
    @Override
    public void cancel() {
        LOGGER.debug("ModifyTool [{}] cancel() 被调用，尝试通过策略的onKeyDown处理ESC键", toolId);
        
        // 尝试通过策略的onKeyDown方法处理ESC键，这样可以触发策略内的重置逻辑
        IModifyStrategy strategy = getStrategy();
        if (strategy != null) {
            // 传递ESC键码（GLFW格式：256），让策略处理
            try {
                IModifyStrategy.ModifyResult result = strategy.onKeyDown(256, this);
                if (result == IModifyStrategy.ModifyResult.CANCEL || result == IModifyStrategy.ModifyResult.COMPLETE) {
                    LOGGER.debug("ModifyTool [{}] 策略通过onKeyDown处理了ESC键", toolId);
                    return;
                }
            } catch (Exception e) {
                LOGGER.warn("ModifyTool [{}] 策略处理ESC键时出错: {}", toolId, e.getMessage());
            }
        }
        
        // 如果策略没有处理，使用默认的cancel逻辑
        LOGGER.debug("ModifyTool [{}] 使用默认cancel逻辑", toolId);
        resetModification("用户取消");
        super.cancel(); // 调用父类的cancel确保状态正确
    }

    @Override
    public void setPreviewEnabled(boolean enabled) {
        isPreviewEnabled.set(enabled);
        if (enabled) {
            setModifyToolState(ToolState.PREVIEWING);
        }
    }

    @Override
    public void setToolState(com.plot.api.tool.ToolState state) {
        // 转换为内部状态枚举
        if (Objects.requireNonNull(state) == com.plot.api.tool.ToolState.ACTIVE) {
            this.currentState = ToolState.MODIFYING;
        } else {
            this.currentState = ToolState.IDLE;
        }
        LOGGER.trace("ModifyTool [{}] 状态变更: {}", toolId, currentState.getDisplayName());
    }
    
    /**
     * 设置修改工具内部状态
     */
    public void setModifyToolState(ToolState state) {
        this.currentState = state;
        LOGGER.trace("ModifyTool [{}] 内部状态变更: {}", toolId, state.getDisplayName());
    }

    @Override
    public String getName() {
        return PlotI18n.toolLabel(toolId);
    }

    @Override
    public String getDescription() {
        return PlotI18n.toolDescription(toolId);
    }

    @Override
    public void setStatusMessage(String message) {
        updateStatusMessage(PlotI18n.localizeStatus(message));
    }

    @Override
    public List<Shape> getSelectedShapes() {
        if (selection != null) {
            return selection.getShapes();
        }
        return List.of();
    }

    @Override
    public void setSelectedShapes(List<Shape> shapes) {
        concreteAppState.setSelectedShapes(shapes);
        selection = concreteAppState.getSelection();
    }

    @Override
    public void addSelectedShape(Shape shape) {
        if (shape != null) {
            concreteAppState.addSelectedShape(shape);
            selection = concreteAppState.getSelection();
        }
    }

    @Override
    public void removeSelectedShape(Shape shape) {
        if (shape != null) {
            concreteAppState.removeSelectedShape(shape);
            selection = concreteAppState.getSelection();
        }
    }

    @Override
    public void clearSelection() {
        concreteAppState.clearSelection();
        selection = concreteAppState.getSelection();
    }

    @Override
    public Shape findShapeAt(Vec2d pos, double tolerance) {
        try {
            // 获取所有图层的图形
            List<Shape> allShapes = concreteAppState.getShapes();
            if (allShapes.isEmpty()) {
                LOGGER.debug("ModifyTool.findShapeAt: 没有图形可查找");
        return null;
            }
            
            LOGGER.debug("ModifyTool.findShapeAt: 在 {} 位置查找图形，容差 {}，总图形数 {}", 
                        pos, tolerance, allShapes.size());
            
            // 将像素容差转换为世界坐标容差（与几何距离一致）
            double worldTolerance = tolerance;
            try {
                if (camera != null) {
                    worldTolerance = camera.screenToWorldDistance(tolerance);
                } else if (getCanvas() != null && getCanvas().getScale() > 0) {
                    worldTolerance = tolerance / getCanvas().getScale();
                }
            } catch (Exception e) { ExceptionDebug.log("ModifyTool: convert selection tolerance to world", e); }
            
            // 从后往前遍历（后绘制的图形在上层，优先选择）
            for (int i = allShapes.size() - 1; i >= 0; i--) {
                Shape shape = allShapes.get(i);
                if (shape == null || shape.isDeleted() || !shape.isVisible()) {
                    continue;
                }
                
                try {
                    // 使用精确的几何点选择检测
                    double dynamicTolerancePixels = GeometricSelectionHelper.getDynamicTolerance(shape);
                    double dynamicWorldTolerance = dynamicTolerancePixels;
                    try {
                        if (camera != null) {
                            dynamicWorldTolerance = camera.screenToWorldDistance(dynamicTolerancePixels);
                        } else if (getCanvas() != null && getCanvas().getScale() > 0) {
                            dynamicWorldTolerance = dynamicTolerancePixels / getCanvas().getScale();
                        }
                    } catch (Exception e) { ExceptionDebug.log("ModifyTool: convert dynamic tolerance to world", e); }
                    double actualTolerance = Math.min(worldTolerance, dynamicWorldTolerance);
                    
                    if (GeometricSelectionHelper.isPointOnShape(shape, pos, actualTolerance)) {
                        LOGGER.debug("ModifyTool.findShapeAt: 通过精确几何检测找到图形 {} at {}", shape.getId(), pos);
                        return shape;
                    }
                } catch (Exception e) {
                    LOGGER.warn("ModifyTool.findShapeAt: 精确几何检测失败，回退到原始方法: {}", e.getMessage());
                    
                    // 回退到仅基于轮廓的距离检测（不允许内部填充命中）
                    try {
                        if (isPointNearShape(shape, pos, worldTolerance)) {
                            LOGGER.debug("ModifyTool.findShapeAt: 通过回退的轮廓距离检测找到图形 {} at {}", shape.getId(), pos);
                            return shape;
                        }
                    } catch (Exception ex) {
                        LOGGER.warn("ModifyTool.findShapeAt: 回退检测图形 {} 时出错: {}", shape.getId(), ex.getMessage());
                    }
                }
            }
            
            LOGGER.debug("ModifyTool.findShapeAt: 在 {} 位置未找到图形", pos);
            return null;
            
        } catch (Exception e) {
            LOGGER.error("ModifyTool.findShapeAt: 查找图形时出错", e);
            return null;
        }
    }

    @Override
    public List<Shape> findShapesInArea(Vec2d startPos, Vec2d endPos) {
        try {
            List<Shape> result = new ArrayList<>();
            List<Shape> allShapes = concreteAppState.getShapes();
            
            if (allShapes.isEmpty()) {
                LOGGER.debug("ModifyTool.findShapesInArea: 没有图形可查找");
                return result;
            }
            
            // 计算选择区域
            double minX = Math.min(startPos.x, endPos.x);
            double minY = Math.min(startPos.y, endPos.y);
            double maxX = Math.max(startPos.x, endPos.x);
            double maxY = Math.max(startPos.y, endPos.y);
            
            LOGGER.debug("ModifyTool.findShapesInArea: 在区域 ({},{}) - ({},{}) 查找图形，总图形数 {}", 
                        minX, minY, maxX, maxY, allShapes.size());
            
            for (Shape shape : allShapes) {
                if (shape == null || shape.isDeleted() || !shape.isVisible()) {
                    continue;
                }
                
                try {
                    BoundingBox shapeBounds = shape.getBoundingBox();
                    if (shapeBounds == null) {
                        continue;
                    }
                    
                    // 检查图形边界框是否与选择区域相交
                    BoundingBox selectionBounds = new BoundingBox(minX, minY, maxX, maxY);
                    if (shapeBounds.intersects(selectionBounds)) {
                        result.add(shape);
                        LOGGER.debug("ModifyTool.findShapesInArea: 找到图形 {} 在选择区域内", shape.getId());
                    }
                    
                } catch (Exception e) {
                    LOGGER.warn("ModifyTool.findShapesInArea: 检查图形 {} 时出错: {}", shape.getId(), e.getMessage());
                }
            }
            
            LOGGER.debug("ModifyTool.findShapesInArea: 在选择区域内找到 {} 个图形", result.size());
            return result;
            
        } catch (Exception e) {
            LOGGER.error("ModifyTool.findShapesInArea: 查找区域图形时出错", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 检查点是否接近图形边界（精确几何检测）
     */
    private boolean isPointNearShape(Shape shape, Vec2d point, double tolerance) {
        try {
            // 优先使用精确的几何距离检测
            try {
                double distance = Math.abs(shape.getSignedDistance(point));
                if (distance <= tolerance) {
                    LOGGER.debug("ModifyTool.isPointNearShape: 点到图形距离 {} <= 容差 {}", distance, tolerance);
                    return true;
                }
            } catch (Exception e) {
                LOGGER.debug("ModifyTool.isPointNearShape: 精确距离检测失败，回退到其他方法: {}", e.getMessage());
            }
            
            // 回退到最近点检测
            try {
                Vec2d closestPoint = shape.getClosestPoint(point);
                if (closestPoint != null) {
                    double distance = point.distance(closestPoint);
                    if (distance <= tolerance) {
                        LOGGER.debug("ModifyTool.isPointNearShape: 最近点距离 {} <= 容差 {}", distance, tolerance);
                        return true;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("ModifyTool.isPointNearShape: 最近点检测失败: {}", e.getMessage());
            }
            // 不再使用扩展包围框作为近似命中，避免误选内部
            return false;
                   
        } catch (Exception e) {
            LOGGER.warn("ModifyTool.isPointNearShape: 检查点接近度时出错", e);
            return false;
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
    public CanvasCamera getCamera() {
        return camera;
    }

    @Override
    public ICanvas getCanvas() {
        return canvas;
    }

    @Override
    public com.plot.api.state.IAppState getAppState() {
        return appState;
    }
    
    @Override
    public ControlPointEditTool getControlPointEditTool() {
        if (controlPointEditTool == null) {
            controlPointEditTool = new ControlPointEditTool(concreteAppState, snapManager, canvas);
        }
        return controlPointEditTool;
    }

    // 已在 ModifyToolContext 中定义 getSnapHandler(); 这里不重复声明
    
    @Override
    public List<Shape> getAllShapesInActiveLayer() {
        try {
            var activeLayer = concreteAppState.getActiveLayer();
            if (activeLayer != null) {
                return activeLayer.getShapes();
            }
            return concreteAppState.getShapes();
        } catch (Exception e) {
            LOGGER.warn("获取活动图层图形失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
    
    @Override
    public List<Shape> getAllShapesInLayer(String layerId) {
        try {
            if (concreteAppState.getLayerManager() != null) {
                var layer = concreteAppState.getLayerManager().getLayerById(layerId);
                if (layer != null) {
                    return layer.getShapes();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("获取指定图层图形失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    // ====== 工具生命周期 ======

    @Override
    public void onActivate() {
        super.onActivate();
        selection = concreteAppState.getSelection();
        
        // 检查策略的选择要求
        IModifyStrategy strategy = getStrategy();
        if (strategy != null && strategy.requiresSelection() && 
            (selection == null || selection.isEmpty())) {
            setStatusMessage("status.plot.common.select_modify_first");
        } else {
            setStatusMessage(getInitialStatusMessage());
        }
        
        setCursor(getDefaultCursor());
        LOGGER.debug("ModifyTool [{}] 已激活", toolId);
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        resetModification("工具停用");
        LOGGER.debug("ModifyTool [{}] 已停用", toolId);
    }

    @Override
    public void render(DrawContext context) {
        renderPreview(context);
        // 统一渲染捕捉指示器（DrawContext）
        try { snapEnhancer.renderSnapIndicator(context); } catch (Exception e) { ExceptionDebug.log("ModifyTool: render snap indicator", e); }
    }

    /**
     * ImGui 预览渲染入口（供子类按需调用）
     * 统一追加捕捉指示器的 ImGui 版本渲染
     */
    public void renderPreview(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        // 渲染控制点编辑工具的预览
        if (controlPointEditTool != null && controlPointEditTool.isActive()) {
            controlPointEditTool.renderPreview(drawList, camera);
        }
        
        // 在尾部统一渲染捕捉指示器
        try { snapEnhancer.renderSnapIndicator(drawList, camera); } catch (Exception e) { ExceptionDebug.log("ModifyTool: render snap indicator overlay", e); }
    }

    public void clearOptions() {
        options.clear();
    }

    // ====== 状态访问器 ======

    protected ToolState getCurrentState() {
        return currentState;
    }

    // ====== 设置方法 ======

    public void setCamera(CanvasCamera camera) {
        this.camera = camera;
    }

    public void setCanvas(ICanvas canvas) {
        this.canvas = canvas;
    }

    @Override
    public void reset() {
        super.reset();
        clearOptions();
        resetModification("工具重置");
    }

    @Override
    public String getDefaultCursor() {
        return "default";
    }

    // ====== 按键状态查询方法实现（解耦UI框架依赖） ======
    
    @Override
    public boolean isShiftKeyDown() {
        try {
            return imgui.ImGui.getIO().getKeyShift();
        } catch (Exception e) {
            LOGGER.debug("无法获取Shift键状态: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isAltKeyDown() {
        try {
            return imgui.ImGui.getIO().getKeyAlt() || imgui.ImGui.isKeyDown(ALT_KEY_CODE);
        } catch (Exception e) {
            LOGGER.debug("无法获取Alt键状态: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isCtrlKeyDown() {
        try {
            return imgui.ImGui.getIO().getKeyCtrl();
        } catch (Exception e) {
            LOGGER.debug("无法获取Ctrl键状态: {}", e.getMessage());
            return false;
        }
    }

    // ====== 抽象方法 ======

    /**
     * 创建修改策略（抽象方法）
     * 子类必须实现此方法来创建具体的策略实例
     */
    protected abstract IModifyStrategy createStrategy();
    
    /**
     * 获取修改策略（懒加载）
     * 确保策略在子类完全初始化后才创建
     */
    protected final IModifyStrategy getStrategy() {
        if (modifyStrategy == null) {
            modifyStrategy = createStrategy();
            LOGGER.debug("ModifyTool [{}] 懒加载创建策略: {}", toolId, 
                modifyStrategy != null ? modifyStrategy.getClass().getSimpleName() : "null");
        }
        return modifyStrategy;
    }

    /**
     * 获取初始状态消息
     */
    protected abstract String getInitialStatusMessage();

    /**
     * 渲染预览效果
     */
    protected abstract void renderPreview(DrawContext context);

    /**
     * 更新工具配置
     */
    public abstract void updateConfig(String key, String value);

    // ====== 便利方法 ======

    /**
     * 检查是否有选择的图形
     */
    protected boolean hasSelection() {
        return selection != null && !selection.isEmpty();
    }

    private static AppState requireConcreteAppState(IAppState appState) {
        if (appState instanceof AppState concreteAppState) {
            return concreteAppState;
        }
        throw new IllegalArgumentException(
                "ModifyTool requires AppState implementation, got: " + appState.getClass().getName());
    }
} 