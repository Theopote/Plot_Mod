package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.graphics.DrawContext;

import com.plot.core.model.Shape;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.drawing.helper.*;
import com.plot.core.state.AppState;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import imgui.ImColor;

import java.awt.Color;
import java.util.Map;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * 多段线工具 - 完整的策略模式实现
 * 
 * 支持三种绘制模式：
 * 1. 折线模式：绘制连续的直线段，多点点击，双击结束
 * 2. 钢笔模式：绘制贝塞尔曲线，支持控制点拖动
 * 3. 编辑模式：编辑已存在的线条，支持节点和控制点调整
 */
public class PolylineTool extends DrawingTool implements PolylineDrawingSession.GeometrySink {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolylineTool.class);

    // ====== 常量定义 ======
    public static final class Constants {
        
        public static final class Modes {
            public static final String POLYLINE = "polyline";  // 折线模式
            public static final String PEN = "pen";           // 钢笔模式
            public static final String EDIT = "edit";         // 编辑模式
        }
        
        public static final class NodeTypes {
            public static final String AUTO = "auto";         // 自动
        }
        
        public static final class ConfigKeys {
            public static final String MODE = "mode";          // 绘制模式
            public static final String MULTIPLE = "multiple";  // 连续绘制模式
            public static final String NODE_TYPE = "nodeType"; // 节点类型
            public static final String DOUBLE_CLICK_TIME = "doubleClickTime"; // 双击时间
        }
        
        public static final class Timing {
            public static long DOUBLE_CLICK_TIME = 500; // 双击时间阈值（毫秒）
        }
        
        public static final class Rendering {
            public static final float PREVIEW_LINE_THICKNESS = 2.5f;
            public static final float CONTROL_LINE_THICKNESS = 1.5f;
            public static final float POINT_SIZE = 4.0f;
            public static final float CONTROL_POINT_SIZE = 3.0f;
            public static final double SELECTION_TOLERANCE = 8.0;
        }
        
        public static final class Colors {
            public static final Color LINE = PolylineTool.toColor(ThemeManager.getInstance().getCurrentTheme().accent);
        }

        public static final class Keys {
            public static final int ESC = 27;    // ESC键
        }
    }

    // ====== 工具状态 ======
    private String drawMode = Constants.Modes.POLYLINE;           // 当前绘制模式
    private boolean multipleMode = false;              // 连续绘制模式
    private String nodeType = Constants.NodeTypes.AUTO;          // 当前节点类型
    
    // ====== 适配器实例 ======
    private final ReusableDrawContextAdapter drawContextAdapter = new ReusableDrawContextAdapter();
    private final ReusableImGuiAdapter imGuiAdapter = new ReusableImGuiAdapter();
    
    // ====== 模式处理器 ======
    private IModeHandler currentModeHandler;
    private final Map<String, IModeHandler> modeHandlers;

    // ====== 状态已移至各自的模式处理器 ======
    // 所有绘制状态现在由对应的 IModeHandler 实现管理

    // ====== 交互结果枚举 ======
    // 使用IInteractionStrategy中定义的InteractionResult枚举

    // ====== 构造函数 ======

    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public PolylineTool(IAppState appState, ISnapManager snapManager) {
        super("polyline", Icons.POLYLINE_IDENTIFIER, 
              appState, snapManager, InteractionType.CLICK_AND_CLICK);
        
        // 初始化模式处理器（在super()调用之后，确保基类已初始化）
        StyleHandlerAdapter styleHandlerAdapter = new StyleHandlerAdapter();
        this.modeHandlers = Map.of(
            Constants.Modes.POLYLINE, new PolylineDrawModeHandler(styleHandlerAdapter),
            Constants.Modes.PEN, new PenDrawModeHandler(styleHandlerAdapter),
            Constants.Modes.EDIT, new EditModeHandler(styleHandlerAdapter)
        );
        
        // 设置默认模式
        this.currentModeHandler = modeHandlers.get(Constants.Modes.POLYLINE);
        
        // 注册配置事件监听
        eventBus.subscribe(ToolConfigEvent.class, this::onToolConfigEvent);
        
        // 修复：在模式处理器初始化后重新创建策略
        this.interactionStrategy = new PolylineInteractionStrategy();
        
        LOGGER.debug("PolylineTool 初始化完成，当前模式: {}", drawMode);
    }

    @Deprecated
    public PolylineTool() {
        this(com.plot.core.state.AppState.getInstance(), 
             com.plot.core.snap.SnapManager.getInstance());
    }

    @Override
    public void setCamera(CanvasCamera camera) {
        super.setCamera(camera);
        LOGGER.debug("PolylineTool.setCamera: 相机已设置，当前模式处理器: {}", 
            currentModeHandler != null ? currentModeHandler.getClass().getSimpleName() : "null");
        
        // 验证相机设置
        if (camera != null) {
            LOGGER.debug("PolylineTool.setCamera: 相机验证成功，相机类型: {}", camera.getClass().getSimpleName());
        } else {
            LOGGER.warn("PolylineTool.setCamera: 相机为null，这可能导致坐标转换失败");
        }
    }

    @Override
    public void setCanvas(Canvas canvas) {
        super.setCanvas(canvas);
        LOGGER.debug("PolylineTool.setCanvas: 画布已设置");
        
        // 验证画布设置
        if (canvas != null) {
            CanvasCamera canvasCamera = canvas.getCamera();
            LOGGER.debug("PolylineTool.setCanvas: 画布验证成功，画布相机: {}", 
                canvasCamera != null ? canvasCamera.getClass().getSimpleName() : "null");
        } else {
            LOGGER.warn("PolylineTool.setCanvas: 画布为null");
        }
    }

    // ====== 策略模式集成 ======

    @Override
    protected IInteractionStrategy createStrategy(InteractionType type) {
        // 多段线工具使用自定义策略，支持多种绘制模式
        // 修复：总是返回PolylineInteractionStrategy，因为模式处理器在构造函数中已经初始化
        LOGGER.debug("PolylineTool.createStrategy: 创建PolylineInteractionStrategy");
        return new PolylineInteractionStrategy();
    }

    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 对于多段线工具，实际的图形创建现在完全委托给当前的模式处理器
        // 这个方法保留仅用于向后兼容
        if (currentModeHandler != null) {
            return currentModeHandler.getFinalShape();
        }
        return null;
    }

    @Override
    protected void renderPreview(DrawContext context) {
        if (currentModeHandler != null) {
            drawContextAdapter.setContext(context);
            currentModeHandler.renderPreview(drawContextAdapter);
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentModeHandler != null) {
            imGuiAdapter.setContext(drawList, camera);
            currentModeHandler.renderPreview(imGuiAdapter);
        }
    }

    @Override
    public String getDefaultCursor() {
        return super.getDefaultCursor();
    }

    @Override
    public void updateConfig(String key, String value) {
        LOGGER.debug("PolylineTool 配置更新: {}={}", key, value);
        
        switch (key) {
            case Constants.ConfigKeys.MODE -> updateDrawMode(value);
            case Constants.ConfigKeys.MULTIPLE -> updateMultipleMode(value);
            case Constants.ConfigKeys.NODE_TYPE -> updateNodeType(value);
            case Constants.ConfigKeys.DOUBLE_CLICK_TIME -> {
                try {
                    setDoubleClickTime(Long.parseLong(value));
                    LOGGER.debug("双击时间阈值已更新为: {} ms", Constants.Timing.DOUBLE_CLICK_TIME);
                } catch (Exception e) {
                    LOGGER.warn("无效的双击时间阈值: {}", value);
                }
            }
            default -> LOGGER.debug("PolylineTool: 未知配置键: {}", key);
        }
    }

    // ====== 配置更新方法 ======

    private void updateDrawMode(String value) {
        if (drawMode.equals(value)) return;

        // 如果之前的处理器正在绘制，先重置它
        if (currentModeHandler != null && currentModeHandler.isDrawing()) {
            currentModeHandler.reset();
        }
        
        String previousMode = drawMode;
        switch (value) {
            case Constants.Modes.POLYLINE, Constants.Modes.PEN, Constants.Modes.EDIT -> drawMode = value;
            default -> {
                LOGGER.warn("无效的绘制模式: {}, 使用默认折线模式", value);
                drawMode = Constants.Modes.POLYLINE;
            }
        }
        
        // 切换到新的模式处理器
        currentModeHandler = modeHandlers.getOrDefault(drawMode, modeHandlers.get(Constants.Modes.POLYLINE));
        
        LOGGER.debug("多段线工具模式从 {} 更改为 {}", previousMode, drawMode);
        updateStatusMessageForCurrentMode();
        activateEditModeIfNeeded();
    }

    @Override
    protected void onActivate() {
        super.onActivate();
        PolylineDrawingSession.register(this);
        activateEditModeIfNeeded();
    }

    @Override
    protected void onDeactivate() {
        PolylineDrawingSession.unregister(this);
        super.onDeactivate();
    }

    @Override
    public boolean applySnapshot(DrawingGeometrySnapshot snapshot) {
        if (snapshot == null || currentModeHandler == null) {
            return false;
        }
        boolean applied = currentModeHandler.applyGeometrySnapshot(snapshot);
        if (applied) {
            setToolState(ToolState.DRAWING);
            updatePreview();
            updateStatusMessageForCurrentMode();
        }
        return applied;
    }

    private void activateEditModeIfNeeded() {
        if (!Constants.Modes.EDIT.equals(drawMode)) {
            return;
        }
        IModeHandler editHandler = modeHandlers.get(Constants.Modes.EDIT);
        if (!(editHandler instanceof EditModeHandler handler) || handler.isDrawing()) {
            return;
        }
        if (!(appState instanceof AppState concreteAppState)) {
            return;
        }
        if (concreteAppState.getSelection() == null || concreteAppState.getSelection().isEmpty()) {
            return;
        }
        List<Shape> selected = concreteAppState.getSelection().getShapes();
        if (selected.size() != 1) {
            return;
        }
        Shape shape = selected.get(0);
        if (!(shape instanceof PolylineShape || shape instanceof BezierCurveShape)) {
            return;
        }
        List<Vec2d> controlPoints = shape.getControlPoints();
        if (controlPoints == null || controlPoints.isEmpty()) {
            return;
        }
        handler.beginEditing(shape);
        setToolState(ToolState.DRAWING);
        updateStatusMessageForCurrentMode();
    }

    private void updateMultipleMode(String value) {
        try {
            multipleMode = Boolean.parseBoolean(value);
            LOGGER.debug("连续绘制模式更新为: {}", multipleMode);
        } catch (Exception e) {
            LOGGER.warn("无效的连续绘制模式值: {}", value);
        }
    }

    private void updateNodeType(String value) {
        nodeType = value;
        LOGGER.debug("更新节点类型: {}", nodeType);
        
        // 节点类型的应用现在由 EditModeHandler 处理
        if (currentModeHandler instanceof EditModeHandler) {
            // 可以在这里通知编辑模式处理器节点类型发生了变化
            LOGGER.debug("节点类型更新已通知编辑模式处理器");
        }
    }

    // ====== 配置事件处理 ======

    private void onToolConfigEvent(Object eventObj) {
        if (!(eventObj instanceof ToolConfigEvent event)) {
            LOGGER.warn("无效的事件对象: {}", eventObj);
            return;
        }
        if (event.getToolId() == null) {
            LOGGER.warn("ToolConfigEvent 为空或 toolId 为空: {}", event);
            return;
        }
        if ("polyline".equals(event.getToolId())) {
            LOGGER.debug("接收到多段线工具配置事件: {}={}", event.getOptionName(), event.getValue());
                            updateConfig(event.getOptionName(), String.valueOf(event.getValue()));
        }
    }

    // ====== 状态管理方法 ======

    private void resetDrawingState() {
        // 重置所有模式处理器的状态
        modeHandlers.values().forEach(IModeHandler::reset);
        
        // 重置工具状态
        setToolState(ToolState.IDLE);
        
        updateStatusMessageForCurrentMode();
        LOGGER.debug("多段线工具状态已重置");
    }

    /**
     * 更新预览图形
     * 根据当前模式处理器的状态创建预览图形
     */
    public void updatePreview() {
        if (currentModeHandler == null) {
            this.previewShape = null;
            markDirty();
            return;
        }

        try {
            // 修复：在绘制过程中，我们不设置previewShape
            // 而是依赖renderPreview方法进行实时渲染
            // 只有在绘制完成时才设置previewShape
            if (currentModeHandler.isDrawing()) {
                // 绘制过程中，不设置previewShape，依赖实时渲染
                LOGGER.debug("多段线工具：绘制过程中，使用实时渲染");
            } else {
                // 绘制完成时，获取最终图形
                Shape preview = currentModeHandler.getFinalShape();
                if (preview != null) {
                    this.previewShape = preview;
                    LOGGER.debug("多段线工具预览已更新，图形类型: {}", preview.getClass().getSimpleName());
                } else {
                    this.previewShape = null;
                    LOGGER.debug("多段线工具预览已更新，无预览图形");
                }
            }
            
        } catch (Exception e) {
            LOGGER.warn("多段线工具无法更新预览: {}", e.getMessage());
            this.previewShape = null;
        }
        
        markDirty();
    }

    private void updateStatusMessageForCurrentMode() {
        String message = currentModeHandler != null ? 
            currentModeHandler.getStatusMessage() : 
            PlotI18n.status("status.plot.draw.polyline.select_mode");
        updateStatusMessage(message);
    }

    // ====== 自定义交互策略 ======

    private class PolylineInteractionStrategy implements com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy {
        
        @Override
        public IInteractionStrategy.InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
            LOGGER.debug("PolylineInteractionStrategy.onMouseDown: 位置={}, 按钮={}, 当前模式={}", pos, button, drawMode);
            
            // 直接将调用委托给当前激活的模式处理器
            if (currentModeHandler != null) {
                LOGGER.debug("委托给模式处理器: {}", currentModeHandler.getClass().getSimpleName());
                IInteractionStrategy.InteractionResult result = currentModeHandler.onMouseDown(pos, button, new DrawingToolContextAdapter());
                
                LOGGER.debug("模式处理器返回结果: {}", result);
                
                // 关键修复：如果模式处理器返回CONTINUE，说明开始绘制，设置绘制状态
                if (result == IInteractionStrategy.InteractionResult.CONTINUE) {
                    // 设置绘制状态，这样shouldShowPreview会返回true
                    setToolState(ToolState.DRAWING);
                    LOGGER.debug("PolylineInteractionStrategy: 设置绘制状态");
                }
                
                // 关键修复：如果模式处理器返回COMPLETE，我们需要正确传递这个状态
                if (result == IInteractionStrategy.InteractionResult.COMPLETE) {
                    LOGGER.debug("PolylineInteractionStrategy: 模式处理器完成绘制，准备提交图形");
                }
                
                return result;
            }
            LOGGER.warn("PolylineInteractionStrategy: 没有当前模式处理器");
            return IInteractionStrategy.InteractionResult.IGNORED;
        }
        
        @Override
        public IInteractionStrategy.InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            // 直接委托给当前模式处理器
            if (currentModeHandler != null) {
                return currentModeHandler.onMouseMove(pos, new DrawingToolContextAdapter());
            }
            return IInteractionStrategy.InteractionResult.IGNORED;
        }

        @Override
        public IInteractionStrategy.InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            // 直接委托给当前模式处理器
            if (currentModeHandler != null) {

                // 传递模式处理器的结果
                return currentModeHandler.onMouseUp(pos, button, new DrawingToolContextAdapter());
            }
            return IInteractionStrategy.InteractionResult.CONTINUE;
        }

        @Override
        public Shape getFinalShape() {
            // 直接从当前处理器获取最终图形
            if (currentModeHandler != null) {
                return currentModeHandler.getFinalShape();
            }
            return null;
        }
    
        @Override
        public void reset() {
            // 重置所有处理器和工具状态
            resetDrawingState();
        }
        
        @Override
        public String getStrategyName() {
            return "PolylineInteractionStrategy";
        }
        
        @Override
        public String getStrategyDescription() {
            return PlotI18n.modeLabel("strategy.plot.draw.polyline");
        }
        
        @Override
        public List<Vec2d> getControlPoints() {
            // 委托给当前模式处理器获取控制点
            if (currentModeHandler != null) {
                return currentModeHandler.getControlPoints();
            }
            return new ArrayList<>();
        }
        
        @Override
        public Vec2d getCurrentMousePoint() {
            // 委托给当前模式处理器获取当前鼠标位置
            if (currentModeHandler != null) {
                return currentModeHandler.getCurrentMousePoint();
            }
            return null;
        }
    }

    // ====== 旧的交互处理方法已移至各自的模式处理器 ======
    // 这些功能现在由 PolylineDrawModeHandler、PenDrawModeHandler 和 EditModeHandler 处理

    // ====== 图形创建方法已移至各自的模式处理器 ======
    // 图形创建现在由对应的 IModeHandler 实现处理

    // commitShape 方法使用基类的实现，无需重写

    // ====== 预览渲染方法已移至各自的模式处理器 ======
    // 渲染逻辑现在由对应的 IModeHandler 实现处理

    // ====== 编辑模式方法已移至 EditModeHandler ======
    // 所有编辑相关功能现在由 EditModeHandler 处理

    // ====== 所有编辑模式处理方法已移至 EditModeHandler ======
    // 包括：handleEditModeMouseDown, handleEditModeMouseMove, findNearestPoint,
    // updateSymmetricControlPoint, toggleSegmentType, calculateSmartControlPoints,
    // applyNodeType, applySmoothControlPoints, applyAutoNodeType, renderEditModePreview
    
    // ====== 适配器类实现 ======
    
    /**
     * 绘制工具上下文适配器
     */
    private class DrawingToolContextAdapter implements IModeHandler.DrawingToolContext {
        @Override
        public IModeHandler.StyleHandler getStyleHandler() {
            return new StyleHandlerAdapter();
        }
        
        @Override
        public IModeHandler.SnapHandler getSnapHandler() {
            return new SnapHandlerAdapter();
        }
        
        @Override
        public Object getCamera() {
            CanvasCamera camera = PolylineTool.this.camera;
            if (camera != null) {
                LOGGER.trace("PolylineTool.getCamera: 返回相机实例");
                return camera;
            } else {
                LOGGER.warn("PolylineTool.getCamera: 相机为null，这可能导致坐标转换失败");
                return null;
            }
        }
        
        @Override
        public void updateStatusMessage(String message) {
            PolylineTool.this.updateStatusMessage(message);
            // 修复：确保在状态消息更新后也更新预览
            PolylineTool.this.updatePreview();
        }
        
        @Override
        public void resetDrawing(String reason) {
            LOGGER.debug("PolylineTool.resetDrawing: {}", reason);
            PolylineTool.this.resetDrawingState();
        }
        
        @Override
        public void commitShape(Shape shape) {
            // 不需要在这里处理图形提交，因为DrawingTool的策略模式会处理
            // 这个方法保留是为了接口兼容性，但实际提交由DrawingTool.commitShape()处理
            LOGGER.debug("PolylineTool.DrawingToolContextAdapter.commitShape() 被调用，但提交将由DrawingTool处理");
        }
        
        @Override
        public void setToolState(Object state) {
            // 可以根据需要实现状态设置
        }
        
        @Override
        public boolean isMultipleMode() {
            return multipleMode;
        }
    }

    /**
     * 样式处理器适配器
     */
    private class StyleHandlerAdapter implements IModeHandler.StyleHandler {
        @Override
        public Object getFinalStyle() {
            return getStyleHandler().getFinalStyle();
        }
    }
    
    /**
     * 吸附处理器适配器
     */
    private class SnapHandlerAdapter implements IModeHandler.SnapHandler {
        @Override
        public Vec2d getSnappedWorldPoint(Vec2d screenPos, Object camera) {
            // 使用工具的吸附管理器进行吸附计算
            if (snapManager != null && camera instanceof CanvasCamera) {
                try {
                    // 先转换为世界坐标
                    Vec2d worldPoint = ((CanvasCamera) camera).screenToWorld(screenPos);
                    
                    // 获取所有图形作为吸附目标
                    List<Shape> snapTargets = new ArrayList<>();
                    if (appState != null) {
                        try {
                            // 使用类型转换访问AppState的getShapes方法
                            if (appState instanceof com.plot.core.state.AppState) {
                                snapTargets = ((com.plot.core.state.AppState) appState).getShapes();
                            } else {
                                // 兼容性处理：尝试通过反射获取
                                java.lang.reflect.Method getShapesMethod = appState.getClass().getMethod("getShapes");
                                Object result = getShapesMethod.invoke(appState);
                                if (result instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<Shape> shapes = (List<Shape>) result;
                                    snapTargets.addAll(shapes);
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.debug("获取吸附目标失败: {}", e.getMessage());
                        }
                    }
                    
                    // 进行吸附处理
                    Vec2d snappedPoint = snapManager.snapPoint(worldPoint, null, snapTargets);
                    
                    LOGGER.trace("PolylineTool 吸附处理: ({}, {}) -> ({}, {})", 
                                worldPoint.x, worldPoint.y, snappedPoint.x, snappedPoint.y);
                    
                    return snappedPoint;
                    
                } catch (Exception e) {
                    LOGGER.debug("吸附处理失败，使用原始位置: {}", e.getMessage());
                    return ((CanvasCamera) camera).screenToWorld(screenPos);
                }
            }
            
            // 如果无法进行吸附，至少进行坐标转换
            if (camera instanceof CanvasCamera) {
                return ((CanvasCamera) camera).screenToWorld(screenPos);
            }
            
            return screenPos; // 最后的回退
        }
    }

    /**
     * 绘制适配器接口
     */
    private interface DrawingAdapter {
        void drawLine(Vec2d p1, Vec2d p2, Color color, float thickness);
        void drawCircle(Vec2d center, double radius, Color color, boolean filled);
        void drawBezierCurve(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3, Color color, float thickness);
    }

    /**
     * DrawContext 适配器，适配到 IModeHandler.DrawingAdapter
     */
    private class ReusableDrawContextAdapter implements DrawingAdapter, IModeHandler.DrawingAdapter {
        private DrawContext context;
        
        public void setContext(DrawContext context) {
            this.context = context;
        }
        
        @Override
        public void drawLine(Vec2d p1, Vec2d p2, Color color, float thickness) {
            if (context != null) {
            context.drawLine(p1, p2, color);
            }
        }
        
        @Override
        public void drawCircle(Vec2d center, double radius, Color color, boolean filled) {
            if (context != null) {
            context.drawCircle(center, radius, color);
            }
        }
        
        @Override
        public void drawBezierCurve(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3, Color color, float thickness) {
            if (context != null) {
            drawBezierCurveImpl(p0, p1, p2, p3, color);
            }
        }
        
        private void drawBezierCurveImpl(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3, Color color) {
            // 使用 BezierUtils 计算最优步数
            int steps = BezierUtils.calculateOptimalSteps(p0, p1, p2, p3, 12, 128);
            Vec2d prev = p0;
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                Vec2d current = BezierUtils.evaluateCubicBezier(p0, p1, p2, p3, t);
                context.drawLine(prev, current, color);
                prev = current;
            }
        }
    }

    private static Color toColor(int color) {
        return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255);
    }
    
    /**
     * ImGui 适配器，适配到 IModeHandler.DrawingAdapter
     */
    private class ReusableImGuiAdapter implements DrawingAdapter, IModeHandler.DrawingAdapter {
        private ImDrawList drawList;
        private CanvasCamera camera;
        private final com.plot.ui.tools.snap.SnapEnhancer snapEnhancer =
                new com.plot.ui.tools.snap.SnapEnhancer("PolylineTool");
        
        public void setContext(ImDrawList drawList, CanvasCamera camera) {
            this.drawList = drawList;
            this.camera = camera;
        }
        
        @Override
        public void drawLine(Vec2d p1, Vec2d p2, Color color, float thickness) {
            if (drawList != null && camera != null) {
            Vec2d screenP1 = camera.worldToScreen(p1);
            Vec2d screenP2 = camera.worldToScreen(p2);
            
            int imColor = ImColor.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            
            drawList.addLine(
                    (float)screenP1.x, (float)screenP1.y,
                    (float)screenP2.x, (float)screenP2.y,
                    imColor, thickness
            );
                snapEnhancer.renderSnapIndicator(drawList, camera);
            }
        }
        
        @Override
        public void drawCircle(Vec2d center, double radius, Color color, boolean filled) {
            if (drawList != null && camera != null) {
            Vec2d screenCenter = camera.worldToScreen(center);
            
            int imColor = ImColor.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            
            if (filled) {
                drawList.addCircleFilled(
                        (float)screenCenter.x, (float)screenCenter.y,
                            (float)radius, imColor
                );
            } else {
                drawList.addCircle(
                        (float)screenCenter.x, (float)screenCenter.y,
                            (float)radius, imColor
                );
                }
                snapEnhancer.renderSnapIndicator(drawList, camera);
            }
        }
        
        @Override
        public void drawBezierCurve(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3, Color color, float thickness) {
            if (drawList != null && camera != null) {
            Vec2d screenP0 = camera.worldToScreen(p0);
            Vec2d screenP1 = camera.worldToScreen(p1);
            Vec2d screenP2 = camera.worldToScreen(p2);
            Vec2d screenP3 = camera.worldToScreen(p3);
            
            int imColor = ImColor.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            
            drawList.addBezierCubic(
                    (float)screenP0.x, (float)screenP0.y,
                    (float)screenP1.x, (float)screenP1.y,
                    (float)screenP2.x, (float)screenP2.y,
                    (float)screenP3.x, (float)screenP3.y,
                    imColor, thickness, 20
            );
            }
        }
    }
    
    // ====== 实用方法 ======



    /**
     * 设置双击时间阈值（毫秒）
     */
    public static void setDoubleClickTime(long ms) {
        Constants.Timing.DOUBLE_CLICK_TIME = ms > 0 ? ms : 500;
    }

    /**
     * 设置状态消息（兼容方法）
     */
    // 移除递归调用的方法

    @Override
    public boolean onKeyDown(int key) {
        // 委托给当前模式处理器
        if (currentModeHandler != null) {
            IInteractionStrategy.InteractionResult result = 
                currentModeHandler.onKeyDown(key, new DrawingToolContextAdapter());
            
            // 处理模式处理器的返回结果
            switch (result) {
                case COMPLETE:
                    // 完成绘制：获取最终图形并提交
                    try {
                        Shape finalShape = currentModeHandler.getFinalShape();
                        if (finalShape != null) {
                            commitShape(finalShape);
                            LOGGER.debug("多段线工具：键盘事件完成绘制，图形已提交");
                        } else {
                            LOGGER.warn("多段线工具：键盘事件完成绘制，但无法获取最终图形");
                            resetDrawingState();
                        }
                    } catch (Exception e) {
                        LOGGER.error("多段线工具：键盘事件处理最终图形失败: {}", e.getMessage(), e);
                        resetDrawingState();
                    }
                    return true;
                    
                case CANCEL:
                    // 取消绘制：重置状态
                    resetDrawingState();
                    LOGGER.debug("多段线工具：键盘事件取消绘制");
                    return true;
                    
                case CONTINUE:
                    // 继续绘制：事件已处理但绘制继续
                    LOGGER.debug("多段线工具：键盘事件已处理，绘制继续");
                    return true;
                    
                case IGNORED:
                    // 事件被忽略：继续处理其他按键
                    break;
            }
        }
        
        // 如果模式处理器没有处理，执行默认行为
        if (key == Constants.Keys.ESC) {  // ESC键
            resetDrawingState();
            return true;
        }
        return false;
    }
}