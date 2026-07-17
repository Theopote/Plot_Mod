package com.plot.ui.canvas;

import com.plot.api.graphics.IShapeStyle;
import com.plot.api.graphics.ITextStyle;
import com.plot.core.layer.LayerManager;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.api.geometry.Vec2d;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.ui.component.UIComponent;
import com.plot.utils.PlotI18n;
import com.plot.api.model.ICanvas;
import com.plot.api.model.ILayer;

/**
 * 画布集成类 (最终修复版 V2 - 优化版)
 * <p>
 * 主要优化：
 * 1. 移除冗余的addShape方法，统一通过AppState管理图形
 * 2. 优化延迟初始化，将需要OpenGL上下文的组件初始化移到render()中
 * 3. 实现单向数据流：UI工具 -> AppState -> 事件 -> Canvas刷新
 */
public class Canvas implements ICanvas, UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(Canvas.class);

    private final AppState appState;
    private final CanvasCore core;
    private final CanvasRenderer renderer;
    private final CanvasInputHandler inputHandler;
    private final CanvasEventHandler eventHandler;
    private final CanvasGrid grid;

    // 延迟初始化标志
    private boolean rendererInitialized = false;
    private boolean gridInitialized = false;

    public Canvas(AppState appState) {
        if (appState == null || appState.getLayerManager() == null) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.canvas_prereq"));
        }
        LOGGER.debug("初始化 Canvas，使用 AppState 作为唯一数据源...");
        this.appState = appState;
        
        // 创建核心组件（不需要OpenGL上下文）
        this.core = new CanvasCore(appState);
        this.grid = new CanvasGrid(this.core);
        this.renderer = new CanvasRenderer(this.core, this.grid);
        this.inputHandler = new CanvasInputHandler(this.core);
        this.eventHandler = new CanvasEventHandler(this.core, this.grid);
    }

    @Override
    public void init() {
        try {
            LOGGER.debug("初始化Canvas组件（基础部分）...");
            
            // 只初始化不需要OpenGL上下文的组件
            core.init();
            
            // grid.init() 和 renderer.init() 延迟到首次渲染
            LOGGER.debug("Canvas基础初始化成功，OpenGL相关组件将延迟初始化");
        } catch (Exception e) {
            LOGGER.error("初始化Canvas失败", e);
            throw new RuntimeException(PlotI18n.error("error.plot.init.canvas_failed"), e);
        }
    }

    @Override
    public void close() throws Exception {
        LOGGER.debug("释放Canvas资源...");
        if (eventHandler != null) eventHandler.close();
        if (inputHandler != null) inputHandler.close();
        if (renderer != null) renderer.close();
        if (grid != null) grid.close();
        if (core != null) core.close();
    }
    
    @Override
    public void render() {
        try {
            // 延迟初始化需要OpenGL上下文的组件
            if (!rendererInitialized) {
                LOGGER.debug("延迟初始化renderer（需要OpenGL上下文）...");
                renderer.init();
                rendererInitialized = true;
            }
            
            if (!gridInitialized) {
                LOGGER.debug("延迟初始化grid（需要OpenGL上下文）...");
                grid.init();
                gridInitialized = true;
            }
            
            // 新版 BlockIconRenderer 为无状态实现，renderTick 不再需要
            
            renderer.render();
            inputHandler.handleInput();
        } catch (Exception e) {
            LOGGER.error("Canvas渲染时出错", e);
        }
    }

    /**
     * DockSpace 模式下，由外部布局系统托管窗口大小/位置。
     */
    public void setUseDockingLayout(boolean useDockingLayout) {
        try {
            renderer.setUseDockingLayout(useDockingLayout);
        } catch (Throwable ignored) {
        }
    }
    
    /**
     * 标记工具预览层需要重绘（控制点、选择框等 overlay）。
     */
    public void markToolPreviewDirty() {
        core.markDirty(CanvasCore.DirtyType.TOOL_PREVIEW);
    }

    @Override
    public void refresh() {
        LOGGER.debug("请求刷新画布...");
        core.markDirty(CanvasCore.DirtyType.CONTENT, CanvasCore.DirtyType.LAYOUT);
        renderer.requestRender();
        
        // 日志现在会100%准确
        LayerManager layerManager = this.appState.getLayerManager();
        if (layerManager != null) {
            int totalShapes = layerManager.getLayers().stream()
                                          .mapToInt(layer -> layer.getShapes().size())
                                          .sum();
            LOGGER.info("画布刷新状态: 图层数={}, 总图形数={}", layerManager.getLayerCount(), totalShapes);
        }
    }

    // 委托方法保持不变
    @Override public int getWidth() { return core.getWidth(); }
    @Override public int getHeight() { return core.getHeight(); }
    @Override public void setSize(int width, int height) { core.setSize(width, height); }
    @Override public List<ILayer> getLayers() { return core.getLayers(); }
    @Override public ILayer getCurrentLayer() { return core.getCurrentLayer(); }
    @Override public void setCurrentLayer(ILayer layer) { core.setCurrentLayer(layer); }
    @Override public void addLayer(ILayer layer) { core.addLayer(layer); }
    @Override public void removeLayer(ILayer layer) { core.removeLayer(layer); }
    @Override public Vec2d getOrigin() { return core.getOrigin(); }
    @Override public void setOrigin(Vec2d origin) { core.setOrigin(origin); }
    @Override public double getScale() { return core.getScale(); }
    @Override public void setScale(double scale) { core.setScale(scale); }
    @Override public double getRotation() { return core.getRotation(); }
    @Override public void setRotation(double rotation) { core.setRotation(rotation); }
    @Override public Vec2d worldToCanvas(Vec2d worldPoint) { return core.worldToCanvas(worldPoint); }
    @Override public Vec2d canvasToWorld(Vec2d canvasPoint) { return core.canvasToWorld(canvasPoint); }
    @Override public ITextStyle getCurrentTextStyle() { return core.getCurrentTextStyle(); }
    @Override public void setCurrentTextStyle(ITextStyle style) { core.setCurrentTextStyle(style); }
    @Override public IShapeStyle getCurrentShapeStyle() { return core.getCurrentShapeStyle(); }
    @Override public void setCurrentShapeStyle(IShapeStyle style) { core.setCurrentShapeStyle(style); }
    @Override public String getCursor() { return core.getCursor(); }
    @Override public void setCursor(String cursorType) { core.setCursor(cursorType); }
    
    /**
     * 添加图形到画布
     * <p>
     * 注意：为了保持接口兼容性，此方法仍然存在，但推荐直接使用 appState.addShape(shape)
     * 以确保单向数据流和更好的架构一致性。
     * <p>
     * 此方法现在委托给AppState，实现相同的功能但保持架构清晰。
     */
    @Override 
    public void addShape(Shape shape) {
        if (shape == null) {
            LOGGER.warn("尝试添加空图形");
            return;
        }
        
        LOGGER.debug("Canvas.addShape() 委托给 AppState.addShape()");
        // 委托给AppState，保持单向数据流
        appState.addShape(shape);
        
        // AppState会发布事件，CanvasEventHandler会监听并调用refresh()
        // 这里不需要手动调用refresh()
    }
    
    /**
     * 从画布移除图形
     * 修复：委托给AppState，保持API一致性和单向数据流
     */
    @Override 
    public void removeShape(Shape shape) {
        if (shape == null) {
            LOGGER.warn("尝试移除空图形");
            return;
        }
        
        LOGGER.debug("Canvas.removeShape() 委托给 AppState.removeShape()");
        // 委托给AppState，保持单向数据流和API一致性
        appState.removeShape(shape);
        
        // AppState会发布事件，CanvasEventHandler会监听并调用refresh()
        // 这里不需要手动调用refresh()
    }
    
    /**
     * 清空画布内容
     * 修复：委托给AppState，保持API一致性和单向数据流
     */
    @Override 
    public void clear() {
        LOGGER.debug("Canvas.clear() 委托给 AppState.clear()");
        // 委托给AppState，保持单向数据流和API一致性
        appState.clear();
        
        // AppState会发布事件，CanvasEventHandler会监听并调用refresh()
        // 这里不需要手动调用refresh()
    }
    @Override public List<Shape> getShapes() { return core.getShapes(); }
    @Override public Vec2d screenToWorld(Vec2d screenPos) { return core.screenToWorld(screenPos); }
    @Override public Vec2d worldToScreen(Vec2d worldPos) { return core.worldToScreen(worldPos); }

    public boolean isScreenPosInsideCanvas(Vec2d screenPos) {
        return core.isScreenPosInsideCanvas(screenPos);
    }

    // 其他方法保持不变
    public void undo() { com.plot.infrastructure.event.EventBus.getInstance().publish(new com.plot.infrastructure.event.command.UndoEvent("Canvas")); }
    public void redo() { com.plot.infrastructure.event.EventBus.getInstance().publish(new com.plot.infrastructure.event.command.RedoEvent("Canvas")); }
    public List<Shape> getSelectedShapes() { return inputHandler.getSelectedShapes(); }
    public void setOpacity(float opacity) { core.setOpacity(opacity); }
    public float getOpacity() { return core.getOpacity(); }
    public CanvasCamera getCamera() { return core.getCamera(); }

}