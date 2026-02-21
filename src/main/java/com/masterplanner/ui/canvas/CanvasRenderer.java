package com.masterplanner.ui.canvas;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.tool.BaseTool;
import com.masterplanner.infrastructure.event.EventListener;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.ui.tools.impl.modify.SelectionTool;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiWindowFlags;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.masterplanner.core.geometry.BoundingBox;

import java.util.List;

/**
 * 画布渲染类
 * <p>
 * 负责画布的渲染逻辑，包括背景、网格、图层、工具预览和选择的绘制。
 * 已优化渲染性能，添加视锥体裁剪和智能缓存机制。
 * 【优化】增强鼠标事件处理、ImGui窗口标志、缓存机制和性能监控
 */
public class CanvasRenderer implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CanvasRenderer.class);

    private final CanvasCore core;
    private final CanvasGrid grid;
    private final RenderingOptimizer optimizer;
    
    // 布局缓存（线程安全）
    private volatile LayoutCache layoutCache;
    private final Object layoutCacheLock = new Object();
    
    // 【新增】画布实际渲染区域记录
    private volatile float canvasX, canvasY, canvasWidth, canvasHeight;
    private final Object canvasBoundsLock = new Object();

    /**
     * DockSpace 模式下：Canvas 窗口将被 dock 到 central node，大小/位置由 docking 系统管理。
     * 关闭时：沿用旧逻辑（全屏固定窗口）。
     */
    private volatile boolean useDockingLayout = false;
    
    // Canvas 窗口基础 flags
    private static final int CANVAS_WINDOW_FLAGS_BASE = ImGuiWindowFlags.NoMove |
            ImGuiWindowFlags.NoResize |
            ImGuiWindowFlags.NoCollapse |
            ImGuiWindowFlags.NoBringToFrontOnFocus |
            ImGuiWindowFlags.NoTitleBar;

    /**
     * 布局缓存类
     * 【优化】缓存视口边界以减少计算开销
     */
    private static class LayoutCache {
        final float toolPanelWidth;
        final float rightPanelWidth;
        final float controlPanelHeight;
        final float statusBarHeight;
        final float canvasWidth;
        final float canvasHeight;
        final BoundingBox viewportBounds; // 【新增】缓存视口边界
        final long timestamp;
        
        LayoutCache(float toolPanelWidth, float rightPanelWidth, float controlPanelHeight, 
                   float statusBarHeight, float canvasWidth, float canvasHeight, BoundingBox viewportBounds) {
            this.toolPanelWidth = toolPanelWidth;
            this.rightPanelWidth = rightPanelWidth;
            this.controlPanelHeight = controlPanelHeight;
            this.statusBarHeight = statusBarHeight;
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
            this.viewportBounds = viewportBounds; // 【新增】缓存视口边界
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isValid() {
            return System.currentTimeMillis() - timestamp < 100; // 100ms缓存有效期
        }
    }

    /**
     * 渲染优化器
     * 【优化】增强性能监控和批量渲染支持
     */
    private static class RenderingOptimizer {
        private BoundingBox viewportBounds;
        private int frameCount = 0;
        private long lastFrameTime = 0;
        private long lastPerformanceCheck = 0;
        private static final long PERFORMANCE_CHECK_INTERVAL = 5000; // 5秒检查一次性能
        private static final int MIN_FPS_THRESHOLD = 30; // 最低帧率阈值
        
        /**
         * 更新视口边界
         */
        void updateViewport(float x, float y, float width, float height) {
            viewportBounds = new BoundingBox(x, y, x + width, y + height);
        }

        
        /**
         * 检查形状是否在视口内（视锥体裁剪）
         */
        boolean isShapeVisible(Shape shape, CanvasCamera camera) {
            if (viewportBounds == null || shape == null) {
                return true; // 保险起见，默认可见
            }
            
            try {
                BoundingBox shapeBounds = shape.getBoundingBox();
                if (shapeBounds == null) {
                    return true; // 无边界信息，假设可见
                }
                
                // 简单的边界框相交测试
                return viewportBounds.intersects(shapeBounds);
            } catch (Exception e) {
                // 出错时保险起见返回可见
                return true;
            }
        }

        
        /**
         * 更新帧计数和性能统计
         * 【优化】增强性能监控
         */
        void updateFrameStats() {
            frameCount++;
            long currentTime = System.currentTimeMillis();
            
            // 每秒更新一次帧率统计
            if (currentTime - lastFrameTime > 1000) {
                int fps = frameCount;
                frameCount = 0;
                lastFrameTime = currentTime;
                
                // 【新增】性能监控：检查帧率是否低于阈值
                if (fps < MIN_FPS_THRESHOLD) {
                    LOGGER.warn("Canvas渲染帧率过低: {} FPS (阈值: {})", fps, MIN_FPS_THRESHOLD);
                } else {
                    LOGGER.debug("Canvas渲染帧率: {} FPS", fps);
                }
            }
            
            // 【新增】定期性能检查
            if (currentTime - lastPerformanceCheck > PERFORMANCE_CHECK_INTERVAL) {
                lastPerformanceCheck = currentTime;
                performPerformanceCheck();
            }
        }
        
        /**
         * 【新增】执行性能检查
         */
        private void performPerformanceCheck() {
            try {
                // 检查内存使用情况
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                long maxMemory = runtime.maxMemory();
                double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
                
                if (memoryUsagePercent > 80) {
                    LOGGER.warn("Canvas渲染器内存使用率过高: {:.1f}%", memoryUsagePercent);
                } else {
                    LOGGER.debug("Canvas渲染器内存使用率: {:.1f}%", memoryUsagePercent);
                }
            } catch (Exception e) {
                LOGGER.debug("性能检查失败", e);
            }
        }
        
        /**
         * 检查是否应该跳过当前帧渲染（帧率限制）
         */
        boolean shouldSkipFrame() {
            return false; // 暂时不实现帧率限制
        }
    }

    /**
     * 构造函数
     * @param core 画布核心对象
     * @param grid 画布网格对象
     */
    public CanvasRenderer(CanvasCore core, CanvasGrid grid) {
        this.core = core;
        this.grid = grid; // 使用外部传入的grid实例，确保共享同一个网格对象
        this.optimizer = new RenderingOptimizer();
        
        // 不要在构造函数中调用ImGui相关方法，等待首次渲染时再初始化布局缓存
        // updateLayoutCache(); // 移除这行，避免在ImGui上下文初始化之前调用
        
        // 标记布局为脏，确保首次渲染时会更新布局
        core.markDirty(CanvasCore.DirtyType.LAYOUT);
        
        LOGGER.debug("CanvasRenderer构造完成，等待ImGui上下文初始化后再更新布局");
    }

    /**
     * 更新布局缓存（线程安全）
     * 【优化】缓存视口边界以减少计算开销
     */
    private void updateLayoutCache() {
        try {
            // 确保只在ImGui准备好后更新布局
            if (!isImGuiReady()) {
                LOGGER.debug("ImGui未准备好，跳过布局更新");
                return;
            }
            
            // 【核心修改】画布现在占据整个窗口，布局缓存反映全屏模式
            float displayWidth = ImGui.getIO().getDisplaySizeX();
            float displayHeight = ImGui.getIO().getDisplaySizeY();
            
            if (displayWidth <= 0 || displayHeight <= 0) {
                LOGGER.warn("显示尺寸无效，使用默认值");
                displayWidth = 1200.0f;
                displayHeight = 800.0f;
            }
            
            // 全屏画布布局：画布占据整个窗口
            float toolPanelWidth = 0.0f;  // 不再需要偏移
            float rightPanelWidth = 0.0f;   // 不再需要偏移
            float controlPanelHeight = 0.0f;  // 不再需要偏移
            float statusBarHeight = 0.0f;   // 不再需要偏移
            float canvasWidth = displayWidth;
            float canvasHeight = displayHeight;
            
            // 【新增】创建视口边界缓存
            BoundingBox viewportBounds = new BoundingBox(0.0f, 0.0f, canvasWidth, canvasHeight);
            
            synchronized (layoutCacheLock) {
                layoutCache = new LayoutCache(toolPanelWidth, rightPanelWidth, 
                                            controlPanelHeight, statusBarHeight, 
                                            canvasWidth, canvasHeight, viewportBounds);
            }
            
            // 【新增】更新画布实际渲染区域记录
            synchronized (canvasBoundsLock) {
                this.canvasX = 0.0f;
                this.canvasY = 0.0f;
                this.canvasWidth = canvasWidth;
                this.canvasHeight = canvasHeight;
            }
            
            LOGGER.debug("全屏画布布局已更新，宽度={}，高度={}", canvasWidth, canvasHeight);
        } catch (Exception e) {
            LOGGER.error("更新布局时出错", e);
            
            // 使用安全的默认值
            BoundingBox defaultViewport = new BoundingBox(0.0f, 0.0f, 1200.0f, 800.0f);
            synchronized (layoutCacheLock) {
                layoutCache = new LayoutCache(0.0f, 0.0f, 0.0f, 0.0f, 1200.0f, 800.0f, defaultViewport);
            }
            
            synchronized (canvasBoundsLock) {
                this.canvasX = 0.0f;
                this.canvasY = 0.0f;
                this.canvasWidth = 1200.0f;
                this.canvasHeight = 800.0f;
            }
        }
    }

    /**
     * 获取当前布局缓存（线程安全）
     */
    private LayoutCache getLayoutCache() {
        synchronized (layoutCacheLock) {
            if (layoutCache == null || !layoutCache.isValid()) {
                updateLayoutCache();
            }
            
            // 如果仍然为null（ImGui未准备好），返回默认缓存
            if (layoutCache == null) {
                LOGGER.debug("ImGui未准备好，使用默认全屏布局缓存");
                BoundingBox defaultViewport = new BoundingBox(0.0f, 0.0f, 1200.0f, 800.0f);
                return new LayoutCache(
                    0.0f, 0.0f, 0.0f, 0.0f, 1200.0f, 800.0f, defaultViewport
                );
            }
            
            return layoutCache;
        }
    }

    /**
     * 主要渲染方法
     * 【优化】记录画布实际渲染区域，增强性能监控
     */
    public void render() {
        // 性能优化：检查是否应该跳过当前帧
        if (optimizer.shouldSkipFrame()) {
            return;
        }
        
        try {
            // 检查ImGui是否已初始化并可用
            if (!isImGuiReady()) {
                LOGGER.warn("ImGui未准备好，跳过Canvas渲染");
                return;
            }
            
            // 更新帧统计
            optimizer.updateFrameStats();
            
            // 智能缓存检查：只在必要时更新布局
            if (core.isDirty(CanvasCore.DirtyType.LAYOUT) || isWindowResized()) {
                updateLayoutCache();
                core.clearDirty(CanvasCore.DirtyType.LAYOUT);
            }

            float displayWidth = ImGui.getIO().getDisplaySizeX();
            float displayHeight = ImGui.getIO().getDisplaySizeY();

            if (displayWidth <= 0 || displayHeight <= 0) {
                LOGGER.warn("显示尺寸无效: {}x{}", displayWidth, displayHeight);
                return;
            }

            // ====== 非 DockSpace：直接画到 BackgroundDrawList，确保永远在最底层 ======
            if (!useDockingLayout) {
                synchronized (canvasBoundsLock) {
                    canvasX = 0.0f;
                    canvasY = 0.0f;
                    canvasWidth = displayWidth;
                    canvasHeight = displayHeight;
                }
                // 同步画布区域给输入系统
                core.setCanvasScreenBounds(0.0f, 0.0f, displayWidth, displayHeight);
                core.setSize(Math.max(1, Math.round(displayWidth)), Math.max(1, Math.round(displayHeight)));
                optimizer.updateViewport(0.0f, 0.0f, displayWidth, displayHeight);
                // 相机偏移为全屏原点
                try { core.getCamera().setOffset(new com.masterplanner.api.geometry.Vec2d(0, 0)); } catch (Exception ignored) {}

                ImDrawList drawList = ImGui.getBackgroundDrawList();
                if (drawList != null) {
                    renderBackground(drawList);
                    renderGrid(drawList);
                    renderLayersBatch(drawList);

                    if (core.isDirty(CanvasCore.DirtyType.CONTENT)) {
                        core.clearDirty(CanvasCore.DirtyType.CONTENT);
                    }
                    if (core.isDirty(CanvasCore.DirtyType.TOOL_PREVIEW)) {
                        renderToolPreview(drawList);
                        core.clearDirty(CanvasCore.DirtyType.TOOL_PREVIEW);
                    }
                } else {
                    LOGGER.warn("BackgroundDrawList 为 null，无法渲染 Canvas");
                }
                return;
            }

            String windowName = "Canvas##Canvas";
            
            // 使用try-catch包裹ImGui操作，防止崩溃
            try {
                // 重要：无论 begin() 返回 true/false，都必须 end()，否则会触发 ImGui 的窗口栈断言
                // 画布作为全屏背景窗口：不参与 DockSpace，但要能在空白区域接收输入用于绘制。
                int canvasWindowFlags = useDockingLayout
                    ? (CANVAS_WINDOW_FLAGS_BASE | ImGuiWindowFlags.NoDocking)
                    : (CANVAS_WINDOW_FLAGS_BASE |
                       ImGuiWindowFlags.NoDocking |
                       ImGuiWindowFlags.NoSavedSettings |
                       ImGuiWindowFlags.NoScrollbar |
                       ImGuiWindowFlags.NoScrollWithMouse);

                boolean windowVisible = ImGui.begin(windowName, canvasWindowFlags);
                try {
                    if (windowVisible) {
                        if (useDockingLayout) {
                            // DockSpace：用 ImGui 分配到 Canvas 窗口的真实区域作为渲染边界
                            float wx = ImGui.getWindowPosX();
                            float wy = ImGui.getWindowPosY();
                            float ww = ImGui.getWindowWidth();
                            float wh = ImGui.getWindowHeight();
                            synchronized (canvasBoundsLock) {
                                canvasX = wx;
                                canvasY = wy;
                                canvasWidth = ww;
                                canvasHeight = wh;
                            }
                            optimizer.updateViewport(wx, wy, ww, wh);
                            // 同步画布屏幕区域到 core，供输入系统使用
                            core.setCanvasScreenBounds(wx, wy, ww, wh);
                            // 同步 core 尺寸（用于潜在的布局/网格逻辑）
                            core.setSize(Math.max(1, Math.round(ww)), Math.max(1, Math.round(wh)));
                        } else {
                            // 非 DockSpace：Canvas 全屏窗口，同样同步边界到 core
                            try {
                                float wx = ImGui.getWindowPosX();
                                float wy = ImGui.getWindowPosY();
                                float ww = ImGui.getWindowWidth();
                                float wh = ImGui.getWindowHeight();
                                core.setCanvasScreenBounds(wx, wy, ww, wh);
                                core.setSize(Math.max(1, Math.round(ww)), Math.max(1, Math.round(wh)));
                            } catch (Exception ignored) {}
                        }
                        // 无论是否 docking，都同步相机 offset 到当前窗口内容区域
                        try {
                            float windowX = ImGui.getWindowPosX();
                            float windowY = ImGui.getWindowPosY();
                            float contentRegionX = ImGui.getWindowContentRegionMinX();
                            float contentRegionY = ImGui.getWindowContentRegionMinY();
                            core.getCamera().setOffset(new com.masterplanner.api.geometry.Vec2d(
                                windowX + contentRegionX,
                                windowY + contentRegionY
                            ));
                        } catch (Exception ignored) {}

                        // 获取draw list前检查窗口状态
                        if (!ImGui.isWindowCollapsed()) {
                            ImDrawList drawList = ImGui.getWindowDrawList();
                            if (drawList != null) {
                                // 按顺序渲染各个组件
                                renderBackground(drawList);
                                renderGrid(drawList);

                                // 【优化】批量渲染图层内容，提升效率
                                renderLayersBatch(drawList);

                                // 清除内容脏标记（如果有的话）
                                if (core.isDirty(CanvasCore.DirtyType.CONTENT)) {
                                    LOGGER.debug("清除内容脏标记");
                                    core.clearDirty(CanvasCore.DirtyType.CONTENT);
                                }

                                // 工具预览更新
                                if (core.isDirty(CanvasCore.DirtyType.TOOL_PREVIEW)) {
                                    renderToolPreview(drawList);
                                    core.clearDirty(CanvasCore.DirtyType.TOOL_PREVIEW);
                                }

                                // 注释掉选择框渲染，改为使用图形本身的高亮
                                // renderSelection(drawList);
                            } else {
                                LOGGER.warn("ImGui drawList为null，无法渲染Canvas内容");
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("渲染Canvas内容时出错", e);
                } finally {
                    ImGui.end();
                }
            } catch (Exception e) {
                LOGGER.error("ImGui窗口创建失败", e);
                // 不需要在这里调用ImGui.end()，因为begin()失败时不应调用end()
            }
            
            // 设置默认光标
            try {
                ImGui.setMouseCursor(ImGuiMouseCursor.Arrow);
            } catch (Exception e) {
                LOGGER.error("设置默认光标时出错", e);
            }

        } catch (Exception e) {
            LOGGER.error("渲染画布时发生错误", e);
        }
    }

    /**
     * 渲染背景（优化版本）
     */
    private void renderBackground(ImDrawList drawList) {
        // 统一使用画布屏幕区域（避免依赖 ImGui 当前窗口）
        float windowX;
        float windowY;
        float windowWidth;
        float windowHeight;
        synchronized (canvasBoundsLock) {
            windowX = this.canvasX;
            windowY = this.canvasY;
            windowWidth = this.canvasWidth;
            windowHeight = this.canvasHeight;
        }
        
        int backgroundColor = UITheme.Canvas.getBackgroundColor(core.getOpacity());
        drawList.addRectFilled(windowX, windowY, 
                              windowX + windowWidth, 
                              windowY + windowHeight, 
                              backgroundColor);
    }

    /**
     * 渲染网格（优化版本）
     */
    private void renderGrid(ImDrawList drawList) {
        if (grid != null && grid.isVisible()) {
            grid.render(drawList, core.getCamera());
        }
    }

    /**
     * 渲染工具预览（优化版本）
     */
    private void renderToolPreview(ImDrawList drawList) {
        BaseTool currentTool = AppState.getInstance().getCurrentTool();
        if (currentTool != null) {
            LOGGER.debug("CanvasRenderer.renderToolPreview: 当前工具: {}", currentTool.getClass().getSimpleName());
            try {
                // 优先使用ImDrawList渲染方法（如果工具支持）
                if (currentTool instanceof SelectionTool selectionTool) {
                    LOGGER.debug("CanvasRenderer.renderToolPreview: 使用SelectionTool的ImDrawList渲染");
                    selectionTool.renderPreview(drawList, core.getCamera());
                } else {
                    LOGGER.debug("CanvasRenderer.renderToolPreview: 使用DrawContext渲染");
                    // 回退到DrawContext渲染
                    DrawContext context = getDrawContext(drawList);

                    // 调用工具的render方法
            currentTool.render(context);
                }
            } catch (Exception e) {
                LOGGER.error("渲染工具预览时出错", e);
            }
        } else {
            LOGGER.debug("CanvasRenderer.renderToolPreview: 当前工具为null");
        }
    }

    private @NotNull DrawContext getDrawContext(ImDrawList drawList) {
        DrawContext context = new DrawContext();
        context.setDrawList(drawList);
        context.setCamera(core.getCamera());
        context.setRenderer(this);

        // 设置绘制上下文偏移：使用相机 offset（由渲染器同步）
        Vec2d off = core.getCamera() != null ? core.getCamera().getOffset() : new Vec2d(0, 0);
        context.setOffset(off);
        return context;
    }

    /**
     * 【优化】批量渲染图层（提升效率）
     * 考虑批量处理（如合并draw call）以提升效率
     */
    private void renderLayersBatch(ImDrawList drawList) {
        List<ILayer> layers = core.getLayers();
        if (layers.isEmpty()) {
            LOGGER.debug("没有图层需要渲染");
            return;
        }
        
        int visibleLayerCount = 0;
        int totalShapeCount = 0;
        int culledShapeCount = 0;
        int batchCount = 0;
        
        // 【优化】批量渲染：按图层分组，减少状态切换
        for (ILayer layer : layers) {
            if (layer.isVisible()) {
                visibleLayerCount++;
                List<Shape> shapes = layer.getShapes();
                int layerShapeCount = 0;
                int layerCulledCount = 0;
                
                LOGGER.debug("渲染图层[{}]: 图形数={}", layer.getName(), shapes.size());
                
                // 【优化】批量处理同一图层的图形
                List<Shape> visibleShapes = new java.util.ArrayList<>();
                for (Shape shape : shapes) {
                    // 视锥体裁剪优化
                    if (optimizer.isShapeVisible(shape, core.getCamera())) {
                        visibleShapes.add(shape);
                        layerShapeCount++;
                        totalShapeCount++;
                    } else {
                        layerCulledCount++;
                        culledShapeCount++;
                    }
                }
                
                // 【优化】批量渲染可见图形
                if (!visibleShapes.isEmpty()) {
                    renderShapesBatch(drawList, visibleShapes);
                    batchCount++;
                }
                
                LOGGER.debug("图层[{}]渲染完成: 渲染={}，裁剪={}", 
                           layer.getName(), layerShapeCount, layerCulledCount);
            } else {
                LOGGER.debug("图层[{}]不可见，跳过渲染", layer.getName());
            }
        }
        
        LOGGER.debug("所有图层渲染完成: 可见图层={}，总渲染图形={}，裁剪图形={}，批次数={}", 
                   visibleLayerCount, totalShapeCount, culledShapeCount, batchCount);
    }
    
    /**
     * 【新增】批量渲染图形
     * 减少状态切换，提升渲染效率
     */
    private void renderShapesBatch(ImDrawList drawList, List<Shape> shapes) {
        if (shapes.isEmpty()) {
            return;
        }
        
        // 获取相机以便坐标转换
        CanvasCamera camera = core.getCamera();
        
        // 创建绘制上下文（复用以减少对象创建）
        DrawContext context = new DrawContext();
        context.setRenderer(this);
        context.setCamera(camera);
        context.setOpacity(1.0f);
        context.setDrawList(drawList);
        
        // 设置绘制上下文偏移：使用相机 offset（由渲染器同步）
        Vec2d off = camera != null ? camera.getOffset() : new Vec2d(0, 0);
        context.setOffset(off);
        
        // 【优化】批量渲染：减少状态切换
        for (Shape shape : shapes) {
            if (shape == null || !shape.isVisible() || shape.isDeleted()) {
                continue;
            }
            
            try {
                // 根据选中/高亮状态应用统一样式（选中：黄色加粗；高亮：橙色）
                com.masterplanner.core.graphics.style.ShapeStyle previousStyle = context.getCurrentStyle();
                ShapeStyle styleToApply = getShapeStyle(shape);

                context.setStyle(styleToApply);
                shape.render(context);
                // 恢复之前样式，避免影响后续图形
                if (previousStyle != null) {
                    context.setStyle(previousStyle);
                }
            } catch (Exception e) {
                LOGGER.error("批量渲染图形时出错: {}", e.getMessage());
                LOGGER.debug("图形渲染异常详情", e);
            }
        }
    }

    private static ShapeStyle getShapeStyle(Shape shape) {
        ShapeStyle styleToApply;

        if (shape.isSelected()) {
            styleToApply = ShapeStyle.SELECTED;
        } else if (shape.isHighlighted()) {
            styleToApply = ShapeStyle.HIGHLIGHTED;
        } else {
            // 使用图形自身样式
            styleToApply = (ShapeStyle) shape.getStyle();
        }
        return styleToApply;
    }

    /**
     * 检查窗口是否调整了大小
     */
    private boolean isWindowResized() {
        try {
            LayoutCache cache = getLayoutCache();
            if (cache == null) return true;
            
            // 【核心修改】全屏画布布局：直接比较显示尺寸
            float currentDisplayWidth = ImGui.getIO().getDisplaySizeX();
            float currentDisplayHeight = ImGui.getIO().getDisplaySizeY();
            
            if (currentDisplayWidth <= 0 || currentDisplayHeight <= 0) {
                LOGGER.warn("显示尺寸无效，假设需要更新");
                return true;
            }
            
            // 全屏画布：画布尺寸应该等于显示尺寸
            return Math.abs(currentDisplayWidth - cache.canvasWidth) > 1.0f ||
                   Math.abs(currentDisplayHeight - cache.canvasHeight) > 1.0f;
        } catch (Exception e) {
            LOGGER.error("检查窗口大小时出错", e);
            return true; // 出错时假设需要更新
        }
    }

    /**
     * 初始化渲染器
     */
    public void init() {
        try {
            LOGGER.debug("初始化CanvasRenderer...");
            
            // 避免在init阶段调用任何ImGui方法，推迟到首次渲染
            // 不要在此处更新布局，等待首次渲染时再进行
            
            // 仍然可以安全地初始化网格，因为它不应该依赖于ImGui
            if (grid != null) {
                grid.init();
            }
            
            // 标记布局为脏，确保首次渲染时会更新布局
            core.markDirty(CanvasCore.DirtyType.LAYOUT);
            
            LOGGER.debug("CanvasRenderer初始化成功");
        } catch (Exception e) {
            LOGGER.error("初始化CanvasRenderer失败", e);
            throw new RuntimeException("初始化CanvasRenderer失败", e);
        }
    }

    public void setUseDockingLayout(boolean useDockingLayout) {
        this.useDockingLayout = useDockingLayout;
    }

    /**
     * 关闭资源
     */
    public void close() throws Exception {
        LOGGER.debug("释放CanvasRenderer资源...");
        
        // 清理缓存
        synchronized (layoutCacheLock) {
            layoutCache = null;
        }
        
        if (grid != null) {
            grid.close();
        }
        
        LOGGER.debug("CanvasRenderer资源释放完成");
    }

    /**
     * 检查ImGui是否已初始化并可用
     */
    private boolean isImGuiReady() {
        try {
            // 尝试获取ImGui IO对象和帧计数
            return ImGui.getIO() != null && ImGui.getFrameCount() >= 0;
        } catch (Exception e) {
            LOGGER.trace("ImGui状态检查失败", e);
            return false;
        }
    }

    /**
     * 请求渲染
     * 标记画布需要在下一帧重新渲染
     */
    public void requestRender() {
        LOGGER.debug("请求渲染画布");
        core.markDirty(CanvasCore.DirtyType.CONTENT);
    }

    /**
     * 处理事件
     */
    @Override
    public void onEvent(Event event) {
        // 保留空的事件处理方法以符合EventListener接口
    }
}