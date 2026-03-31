package com.plot.core.state;

import com.plot.api.geometry.Vec2d;
import com.plot.api.model.ILayer;
import com.plot.api.state.IAppState;
import com.plot.core.command.CommandHistory;
import com.plot.core.command.commands.DeleteShapesCommand;
import com.plot.core.tool.BaseTool;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Project;
import com.plot.core.model.Shape;
import com.plot.core.selection.Selection;
import com.plot.core.tool.ToolManager;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.selection.SelectionChangedEvent;
import com.plot.infrastructure.event.command.CommandExecutedEvent;
import com.plot.infrastructure.event.shapes.ShapesRemovedEvent;
import com.plot.api.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.ui.canvas.Canvas;
import com.plot.core.layer.LayerManager;
import com.plot.api.graphics.IShapeStyle;
import com.plot.core.layer.LayerEventSystem;
import com.plot.infrastructure.event.base.Event;
import com.plot.core.spatial.SpatialIndex;
import com.plot.core.spatial.QuadtreeSpatialIndex;
import com.plot.api.geometry.IBoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

/**
 * 应用程序状态管理类 (最终修复版 V2 - 优化版)
 * <p>
 * 主要优化：
 * 1. 移除冗余的shapes字段，所有图形通过图层管理
 * 2. 添加Shape到Layer的映射，优化查找性能
 * 3. 使用专门的DeleteShapesCommand类
 */
public class AppState implements IAppState {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/AppState");
    private static volatile AppState INSTANCE;
    private static final Object INSTANCE_LOCK = new Object();
    private static final float[] DEFAULT_VIEW_RANGES = {-100, 100, -100, 100};
    private static final float DEFAULT_ZOOM = 100.0f;
    private static final float DEFAULT_GRID_SIZE = 1.0f;

    // 事件相关常量
    private static final String EVENT_SELECTION = "selection";

    // 使用线程安全的集合
    private final List<Shape> selectedShapes = new CopyOnWriteArrayList<>();
    
    // 优化：Shape到Layer的映射，实现O(1)查找
    private final ConcurrentMap<Shape, ILayer> shapeToLayerMap = new ConcurrentHashMap<>();

    // 使用读写锁保护状态访问
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = stateLock.writeLock();

    // 使用原子引用保护可变对象
    private final AtomicReference<ILayer> activeLayer = new AtomicReference<>();
    private final AtomicReference<ShapeStyle> currentStyle = new AtomicReference<>(new ShapeStyle());

    // ====== 项目状态 ======
    private Project currentProject;         // 当前项目

    // ====== 视图状态 ======
    private float zoom;               // 缩放比例
    private volatile float opacity;
    private final Object opacityLock = new Object();

    // ====== 工具状态 ======
    private BaseTool currentTool;           // 当前工具
    private boolean isSnappingEnabled; // 是否启用捕捉
    private Vec2d mousePosition = new Vec2d(0, 0);  // 鼠标位置
    private Vec2d cursorPosition = new Vec2d(0, 0); // 光标位置

    // ====== 网格状态 ======
    private boolean isGridEnabled;   // 是否显示网格
    private float gridSize;         // 网格大小

    // ====== 命令历史 ======
    private final CommandHistory commandHistory;

    // ====== 核心组件引用 ======
    private Canvas canvas;
    private LayerManager layerManager;

    // ====== 插件系统 ======
    private IPluginLoader pluginLoader;
    private IPluginManager pluginManager;
    
    // ====== 空间索引 ======
    private SpatialIndex spatialIndex;
    private final Object spatialIndexLock = new Object();

    @Override
    public long getStateVersion() {
        return stateVersion;
    }

    /**
     * 事件合并发布器
     * 用于合并短时间内的重复事件，减少事件发布频率
     */
    private static class DebouncedEventPublisher {
        private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        private static final ConcurrentHashMap<String, ScheduledFuture<?>> pendingEvents = new ConcurrentHashMap<>();
        private static final int DEFAULT_DELAY_MS = 16;  // 约1帧的时间

        public static void publishDebounced(String eventKey, Runnable eventPublisher) {
            publishDebounced(eventKey, eventPublisher, DEFAULT_DELAY_MS);
        }

        public static void publishDebounced(String eventKey, Runnable eventPublisher, int delayMs) {
            // 取消之前pending的相同类型事件
            ScheduledFuture<?> previous = pendingEvents.get(eventKey);
            if (previous != null) {
                previous.cancel(false);
            }

            // 调度新的事件
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                try {
                    eventPublisher.run();
                } finally {
                    pendingEvents.remove(eventKey);
                }
            }, delayMs, TimeUnit.MILLISECONDS);

            pendingEvents.put(eventKey, future);
        }
        
        /**
         * 调度延迟任务
         * @param task 要执行的任务
         * @param delayMs 延迟时间（毫秒）
         * @return ScheduledFuture 用于取消任务
         */
        public static ScheduledFuture<?> scheduleDelayed(Runnable task, int delayMs) {
            return scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        }

        public static void shutdown() {
            scheduler.shutdown();
        }
    }

    /**
     * 视图模式枚举
     */
    public enum ViewMode {
        TOP,    // 顶视图
        FRONT,  // 前视图
        RIGHT,  // 右视图
        BACK,   // 后视图
        LEFT,   // 左视图
        PAN,    // 平移模式
        NORMAL  // 普通模式
    }

    /**
     * 扩展类型枚举
     */
    public enum ExtensionType {
        NONE, ROAD_SYSTEM, IMAGE_TOOLS, EARTHWORK_BALANCE
    }

    /**
     * 构造函数
     * 初始化基础组件和默认状态
     */
    private AppState() {
        LOGGER.info("创建 AppState 单例实例...");
        this.commandHistory = CommandHistory.getInstance();
        // 初始化基础状态
        this.zoom = DEFAULT_ZOOM;
        this.opacity = 0.0f;
        this.gridSize = DEFAULT_GRID_SIZE;
        this.isGridEnabled = true;
        this.isSnappingEnabled = true;
        // 视图范围
        float[] viewRange = DEFAULT_VIEW_RANGES.clone();
        
        // 订阅图层相关事件，确保与LayerManager同步
        subscribeToLayerEvents();
    }

    public static AppState getInstance() {
        if (INSTANCE == null) {
            synchronized (INSTANCE_LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new AppState();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 订阅图层相关事件，确保AppState与LayerManager保持同步
     */
    private void subscribeToLayerEvents() {
        EventBus eventBus = EventBus.getInstance();
        
        // 订阅图层激活事件
        eventBus.subscribe(LayerEventSystem.LayerActivatedEvent.class, this::handleLayerActivated);
        
        // 订阅图层删除事件
        eventBus.subscribe(LayerEventSystem.LayerRemovedEvent.class, this::handleLayerRemoved);

        // 订阅“选择图层全部图元”事件
        eventBus.subscribe(LayerEventSystem.SelectAllElementsInLayerEvent.class, this::handleSelectAllElementsInLayer);
        
        LOGGER.debug("AppState 已订阅图层相关事件");
    }
    
    /**
     * 处理图层激活事件
     * 确保AppState的activeLayer与LayerManager保持同步
     */
    private void handleLayerActivated(Event event) {
        if (event instanceof LayerEventSystem.LayerActivatedEvent layerEvent) {
            ILayer activatedLayer = layerEvent.getLayer();
            LOGGER.debug("AppState 处理图层激活事件: {}", layerEvent.getDescription());
            
            // 更新AppState的活动图层引用
            this.activeLayer.set(activatedLayer);
            
            LOGGER.debug("AppState 活动图层已同步更新为: {}", 
                activatedLayer != null ? activatedLayer.getName() : "null");
        }
    }
    
    /**
     * 处理图层删除事件
     * 确保AppState状态的一致性
     */
    private void handleLayerRemoved(Event event) {
        if (event instanceof LayerEventSystem.LayerRemovedEvent layerEvent) {
            ILayer removedLayer = layerEvent.getLayer();
            ILayer currentActive = this.activeLayer.get();
            
            LOGGER.debug("AppState 处理图层删除事件: {}", layerEvent.getDescription());
            
            // 如果删除的图层是当前活动图层，清除引用
            // LayerManager 会自动设置新的活动图层并发送激活事件
            if (currentActive == removedLayer) {
                this.activeLayer.set(null);
                LOGGER.debug("AppState 已清除对已删除活动图层的引用");
            }
            
            // 清理图形映射中与该图层相关的条目
            shapeToLayerMap.entrySet().removeIf(entry -> entry.getValue() == removedLayer);
        }
    }

    /**
     * 处理“选择图层全部图元”事件
     */
    private void handleSelectAllElementsInLayer(Event event) {
        if (!(event instanceof LayerEventSystem.SelectAllElementsInLayerEvent selectEvent)) {
            return;
        }

        ILayer targetLayer = selectEvent.getLayer();
        if (targetLayer == null) {
            LOGGER.warn("收到选择图层全部图元事件，但目标图层为空");
            return;
        }

        try {
            List<Shape> shapesInLayer = targetLayer.getShapes().stream()
                .filter(shape -> shape != null && !shape.isDeleted())
                .collect(Collectors.toList());

            setSelectedShapes(shapesInLayer);
            LOGGER.debug("已选中图层 '{}' 的 {} 个图元", targetLayer.getName(), shapesInLayer.size());
        } catch (Exception e) {
            LOGGER.error("处理图层全部图元选择事件失败: {}", e.getMessage(), e);
        }
    }

    // --- 初始化流程 ---

    public void initializeLayerSystem() {
        if (this.layerManager == null) {
            LOGGER.info("初始化 Layer 系统...");
            this.layerManager = LayerManager.create();
            if (this.layerManager.getLayerCount() == 0) {
                this.layerManager.createLayer("默认图层");
            }
            this.activeLayer.set(this.layerManager.getActiveLayer());
        }
    }
    
    public void initializePluginSystem() {
        if (this.pluginLoader == null) {
            LOGGER.info("初始化插件系统...");
            
            // 创建插件加载器
            this.pluginLoader = new com.plot.core.plugin.EmptyPluginLoader();
            
            LOGGER.info("插件系统初始化完成");
        }
    }

    // --- 核心组件的 Setters 和 Getters ---

    public void setCanvas(Canvas canvas) {
        this.canvas = canvas;
    }
    
    @Override
    public Canvas getCanvas() {
        return this.canvas;
    }

    public LayerManager getLayerManager() {
        return this.layerManager;
    }

    public void setToolManager(ToolManager toolManager) {
    }

    // --- 简化和修正后的核心方法 ---

    public void addShape(Shape shape) {
        if (shape == null) return;
        
        ILayer layer = getActiveLayer();
        if (layer == null) {
            LOGGER.error("无法添加图形：没有活动的图层！");
            return;
        }
        if (layer.isLocked()) {
            LOGGER.warn("无法添加图形：活动图层 '{}' 已被锁定。", layer.getName());
            return;
        }

        // AppState只负责将数据添加到模型中
        layer.addShape(shape);
        
        // 优化：更新Shape到Layer的映射
        shapeToLayerMap.put(shape, layer);
        
        // 更新空间索引
        updateSpatialIndex(shape);
        
        LOGGER.debug("图形 {} 已添加到图层 '{}'", shape.getId(), layer.getName());

        // 发布事件通知UI更新
        EventBus.getInstance().publish(new LayerEventSystem.LayerContentChangedEvent(layer.getId(), layer, "shape_added", shape));
    }

    public void removeShape(Shape shape) {
        if (shape == null) return;

        // 修复：严格状态检查，不允许状态不一致
        // 直接从映射中获取图层，O(1)查找
        ILayer layer = shapeToLayerMap.get(shape);
        if (layer != null) {
            boolean removed = layer.removeShape(shape);
            if (removed) {
                shapeToLayerMap.remove(shape); // 清理映射
                removeFromSpatialIndex(shape); // 从空间索引中移除
                LOGGER.debug("图形 {} 已从图层 '{}' 移除", shape.getId(), layer.getName());
                EventBus.getInstance().publish(new LayerEventSystem.LayerContentChangedEvent(layer.getId(), layer, "shape_removed", shape));
            } else {
                // 映射存在但图层中没有该图形，这是严重的状态不一致
                LOGGER.error("严重状态不一致！shapeToLayerMap中记录图形 {} 在图层 '{}' 中，但图层中实际不存在该图形", 
                           shape.getId(), layer.getName());
                // 清理错误映射
                shapeToLayerMap.remove(shape);
            }
        } else {
            // 修复：不再回退，明确报告状态不一致
            // 这会强制开发者确保所有操作都正确维护shapeToLayerMap
            LOGGER.error("严重状态不一致！尝试移除一个不存在于 shapeToLayerMap 中的图形: {} (ID: {})", 
                       shape.getClass().getSimpleName(), shape.getId());
            
            // 在开发模式下提供额外的诊断信息
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("当前 shapeToLayerMap 包含 {} 个映射", shapeToLayerMap.size());
                LOGGER.debug("所有图层中的图形数量统计:");
                for (ILayer layerToCheck : layerManager.getLayers()) {
                    LOGGER.debug("  图层 '{}': {} 个图形", layerToCheck.getName(), layerToCheck.getShapes().size());
                }
            }
            
            // 注意：我们不再进行"修复"操作，让错误显露出来
            // 这样可以更快地发现和修复状态管理的bug
        }
    }

    public List<ILayer> getLayers() {
        // 直接从 LayerManager 获取，这是唯一的数据源
        if (this.layerManager != null) {
            return this.layerManager.getLayers();
        }
        return Collections.emptyList();
    }

    // --- 保持原有的其他方法 ---

    public void setCurrentTool(BaseTool tool) {
        if (this.currentTool != tool) {
            BaseTool previousTool = this.currentTool;
            
            if (previousTool != null) {
                try {
                    LOGGER.debug("停用工具: {}", previousTool.getName());
                    previousTool.deactivate();
                } catch (Exception e) {
                    LOGGER.error("停用工具时发生错误: {}", e.getMessage(), e);
                }
            }
            
            this.currentTool = tool;
            
            if (tool != null) {
                try {
                    LOGGER.debug("激活工具: {}", tool.getName());
                    tool.activate();
                } catch (Exception e) {
                    LOGGER.error("激活工具时发生错误: {}", e.getMessage(), e);
                }
            }
        }
    }

    public BaseTool getCurrentTool() {
        return currentTool;
    }

    public float getZoom() {
        return zoom;
    }

    public void setOpacity(float opacity) {
        synchronized (opacityLock) {
            this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        }
    }

    public float getOpacity() {
        synchronized (opacityLock) {
            return opacity;
        }
    }

    // --- 命令和历史相关 ---

    public CommandHistory getCommandHistory() {
        return commandHistory;
    }

    // --- 项目相关 ---

    public Project getCurrentProject() {
        return currentProject;
    }

    // --- 图层相关 ---

    public void setActiveLayer(ILayer layer) {
        if (layer != null && layerManager != null) {
            layerManager.setActiveLayer(layer);
            this.activeLayer.set(layer);
            LOGGER.debug("活动图层设置为: {}", layer.getName());
            
            // 同步通知其他组件
            syncActiveLayerFromManager(layer);
        }
    }

    /**
     * 显式同步指定的活动图层
     */
    public void syncActiveLayerFromManager(ILayer layer) {
        if (layer != null) {
            this.activeLayer.set(layer);
        }
    }

    @Override
    public ILayer getActiveLayer() {
        return activeLayer.get();
    }

    public String getActiveLayerName() {
        ILayer layer = getActiveLayer();
        return layer != null ? layer.getName() : "无图层";
    }

    public Selection getSelection() {
        return new Selection(selectedShapes);
    }

    // --- 选择相关 ---

    public List<Shape> getSelectedShapes() {
        return new ArrayList<>(selectedShapes);
    }

    public void setSelectedShapes(List<Shape> shapes) {
        // 记录旧选择用于差分更新选中标记
        List<Shape> oldSelection = new ArrayList<>(selectedShapes);
        List<Shape> newSelection = shapes != null ? new ArrayList<>(shapes) : new ArrayList<>();

        // 取消旧选择中但不在新选择中的图形的选中标记
        for (Shape old : oldSelection) {
            if (!newSelection.contains(old)) {
                try { old.setSelected(false); } catch (Exception ignored) {}
            }
        }

        // 设置新选择中的选中标记
        for (Shape cur : newSelection) {
            try { cur.setSelected(true); } catch (Exception ignored) {}
        }

        selectedShapes.clear();
        selectedShapes.addAll(newSelection);
        publishSelectionChangedEvent();
    }

    public void addSelectedShape(Shape shape) {
        if (shape != null && !selectedShapes.contains(shape)) {
            selectedShapes.add(shape);
            try { shape.setSelected(true); } catch (Exception ignored) {}
            publishSelectionChangedEvent();
        }
    }

    public void removeSelectedShape(Shape shape) {
        if (selectedShapes.remove(shape)) {
            try { shape.setSelected(false); } catch (Exception ignored) {}
            publishSelectionChangedEvent();
        }
    }

    private void publishSelectionChangedEvent() {
        DebouncedEventPublisher.publishDebounced(EVENT_SELECTION, () -> {
            SelectionChangedEvent event = new SelectionChangedEvent(new ArrayList<>(selectedShapes), this);
            EventBus.getInstance().publish(event);
        });
    }

    public void clearSelection() {
        if (!selectedShapes.isEmpty()) {
            // 清除视觉选中标记
            for (Shape s : new ArrayList<>(selectedShapes)) {
                try { s.setSelected(false); } catch (Exception ignored) {}
            }
            selectedShapes.clear();
            publishSelectionChangedEvent();
        }
    }

    /**
     * 优化：通过遍历所有图层来收集图形，移除冗余的shapes字段
     */
    public List<Shape> getShapes() {
        if (layerManager == null) {
            return Collections.emptyList();
        }
        
        stateLock.readLock().lock();
        try {
            return layerManager.getLayers().stream()
                             .flatMap(layer -> layer.getShapes().stream())
                             .collect(Collectors.toList());
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public void clear() {
        try {
            writeLock.lock();
            
            // 清空选择
            selectedShapes.clear();
            
            // 清空映射
            shapeToLayerMap.clear();
            
            LOGGER.info("应用状态已清空");
        } finally {
            writeLock.unlock();
        }
    }

    public void undo() {
        if (commandHistory != null && commandHistory.canUndo()) {
            commandHistory.undo();
        }
    }

    public void redo() {
        if (commandHistory != null && commandHistory.canRedo()) {
            commandHistory.redo();
        }
    }

    /**
     * 优化：使用专门的DeleteShapesCommand类
     */
    public void deleteSelectedShapes() {
        List<Shape> shapesToDelete = new ArrayList<>(selectedShapes);
        if (shapesToDelete.isEmpty()) {
            LOGGER.debug("没有选中的图形需要删除");
            return;
        }

        LOGGER.debug("准备删除 {} 个选中的图形", shapesToDelete.size());
        
        // 使用专门的DeleteShapesCommand类
        DeleteShapesCommand deleteCommand = new DeleteShapesCommand(shapesToDelete);
        commandHistory.execute(deleteCommand);
        
        // 清空选择
        clearSelection();
        
        // 发布删除事件
        EventBus.getInstance().publish(new ShapesRemovedEvent(shapesToDelete));
        EventBus.getInstance().publish(new CommandExecutedEvent("删除图形", CommandExecutedEvent.CommandType.EXECUTE));
    }

    public void dispose() {
        DebouncedEventPublisher.shutdown();
    }

    @Override
    public IShapeStyle getCurrentShapeStyle() {
        ShapeStyle style = currentStyle.get();
        if (style == null) {
            style = new ShapeStyle();
            // 设置默认样式
            style.setStrokeColor(java.awt.Color.BLACK);
            style.setStrokeWidth(2.0f);
            style.setFillColor(java.awt.Color.LIGHT_GRAY);
            currentStyle.set(style);
        }
        return style;
    }

    /**
     * 调度延迟任务
     */
    public ScheduledFuture<?> scheduleDelayedTask(Runnable task, int delayMs) {
        if (task == null || delayMs < 0) {
            throw new IllegalArgumentException("任务不能为null，延迟不能为负数");
        }
        return DebouncedEventPublisher.scheduleDelayed(task, delayMs);
    }

    @Override
    public ScheduledFuture<?> scheduleDelayedTask(Runnable task, long delayMs) {
        return scheduleDelayedTask(task, (int) delayMs);
    }

    @Override
    public boolean isValid() {
        try {
            // 检查关键组件是否正常
            boolean layerManagerValid = layerManager != null && layerManager.getLayerCount() >= 0;
            boolean commandHistoryValid = commandHistory != null;
            boolean basicStateValid = zoom > 0 && opacity >= 0 && opacity <= 1;
            
            return layerManagerValid && commandHistoryValid && basicStateValid;
        } catch (Exception e) {
            LOGGER.error("验证应用状态时出错", e);
            return false;
        }
    }

    // --- 版本控制 ---
    private volatile long stateVersion = 1;
    
    // ====== 空间索引方法 ======
    
    /**
     * 获取空间索引
     * 
     * @return 空间索引实例
     */
    public SpatialIndex getSpatialIndex() {
        synchronized (spatialIndexLock) {
            if (spatialIndex == null) {
                initializeSpatialIndex();
            }
            return spatialIndex;
        }
    }
    
    /**
     * 初始化空间索引
     */
    private void initializeSpatialIndex() {
        try {
            // 创建默认的边界框（可以根据需要调整）
            IBoundingBox defaultBounds = new IBoundingBox() {
                @Override
                public Vec2d getMin() { return new Vec2d(-10000, -10000); }
                @Override
                public Vec2d getMax() { return new Vec2d(10000, 10000); }
                @Override
                public double getWidth() { return 20000; }
                @Override
                public double getHeight() { return 20000; }
                @Override
                public Vec2d getCenter() { return new Vec2d(0, 0); }
                @Override
                public boolean contains(Vec2d point) {
                    return point.x >= -10000 && point.x <= 10000 &&
                           point.y >= -10000 && point.y <= 10000;
                }
                @Override
                public boolean intersects(IBoundingBox other) {
                    return !(10000 < other.getMin().x || -10000 > other.getMax().x ||
                            10000 < other.getMin().y || -10000 > other.getMax().y);
                }
                @Override
                public IBoundingBox expand(double margin) {
                    return new IBoundingBox() {
                        @Override
                        public Vec2d getMin() { return new Vec2d(-10000 - margin, -10000 - margin); }
                        @Override
                        public Vec2d getMax() { return new Vec2d(10000 + margin, 10000 + margin); }
                        @Override
                        public double getWidth() { return 20000 + 2 * margin; }
                        @Override
                        public double getHeight() { return 20000 + 2 * margin; }
                        @Override
                        public Vec2d getCenter() { return new Vec2d(0, 0); }
                        @Override
                        public boolean contains(Vec2d point) {
                            return point.x >= -10000 - margin && point.x <= 10000 + margin &&
                                   point.y >= -10000 - margin && point.y <= 10000 + margin;
                        }
                        @Override
                        public boolean intersects(IBoundingBox other) {
                            return !(10000 + margin < other.getMin().x || -10000 - margin > other.getMax().x ||
                                    10000 + margin < other.getMin().y || -10000 - margin > other.getMax().y);
                        }
                        @Override
                        public IBoundingBox expand(double newMargin) {
                            return expand(margin + newMargin);
                        }
                        @Override
                        public double distanceTo(Vec2d point) {
                            double dx = Math.max(0, Math.max(-10000 - margin - point.x, point.x - 10000 - margin));
                            double dy = Math.max(0, Math.max(-10000 - margin - point.y, point.y - 10000 - margin));
                            return Math.sqrt(dx * dx + dy * dy);
                        }
                    };
                }
                @Override
                public double distanceTo(Vec2d point) {
                    double dx = Math.max(0, Math.max(-10000 - point.x, point.x - 10000));
                    double dy = Math.max(0, Math.max(-10000 - point.y, point.y - 10000));
                    return Math.sqrt(dx * dx + dy * dy);
                }
            };
            
            spatialIndex = new QuadtreeSpatialIndex(defaultBounds);
            
            // 重新构建索引
            rebuildSpatialIndex();
            
            LOGGER.info("空间索引已初始化");
        } catch (Exception e) {
            LOGGER.error("初始化空间索引失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 重建空间索引
     */
    public void rebuildSpatialIndex() {
        synchronized (spatialIndexLock) {
            if (spatialIndex != null) {
                try {
                    spatialIndex.clear();
                    
                    // 添加所有图形到空间索引
                    List<Shape> allShapes = getShapes();
                    for (Shape shape : allShapes) {
                        spatialIndex.insert(shape);
                    }
                    
                    LOGGER.debug("空间索引已重建，包含 {} 个图形", allShapes.size());
                } catch (Exception e) {
                    LOGGER.error("重建空间索引失败: {}", e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * 更新空间索引中的图形
     * 
     * @param shape 要更新的图形
     */
    public void updateSpatialIndex(Shape shape) {
        synchronized (spatialIndexLock) {
            if (spatialIndex != null) {
                try {
                    spatialIndex.update(shape);
                    LOGGER.debug("空间索引已更新图形: {}", shape.getId());
                } catch (Exception e) {
                    LOGGER.error("更新空间索引失败: {}", e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * 从空间索引中移除图形
     * 
     * @param shape 要移除的图形
     */
    public void removeFromSpatialIndex(Shape shape) {
        synchronized (spatialIndexLock) {
            if (spatialIndex != null) {
                try {
                    spatialIndex.remove(shape);
                    LOGGER.debug("图形已从空间索引移除: {}", shape.getId());
                } catch (Exception e) {
                    LOGGER.error("从空间索引移除图形失败: {}", e.getMessage(), e);
                }
            }
        }
    }
}