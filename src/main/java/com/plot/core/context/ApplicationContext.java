package com.plot.core.context;

import com.plot.core.command.CommandService;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.layer.LayerService;
import com.plot.core.model.Project;
import com.plot.core.model.ProjectSession;
import com.plot.core.snap.SnapManager;
import com.plot.core.snap.SnapService;
import com.plot.core.spatial.SpatialIndexService;
import com.plot.core.state.ActiveToolState;
import com.plot.core.state.AppState;
import com.plot.core.state.DebouncedTasks;
import com.plot.core.state.SelectionState;
import com.plot.core.state.ViewportState;
import com.plot.api.graphics.IShapeStyle;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.infrastructure.event.block.GhostBlockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 应用组合根：持有聚焦状态与服务，替代原先的 AppState God Object。
 * <p>
 * {@link AppState} 仍作为兼容门面，委托到本上下文。
 */
public final class ApplicationContext {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ApplicationContext");
    private static volatile ApplicationContext INSTANCE;
    private static final Object LOCK = new Object();

    private final SelectionState selectionState;
    private final ViewportState viewportState;
    private final ActiveToolState activeToolState;
    private final SpatialIndexService spatialIndexService;
    private final LayerService layerService;
    private final CommandService commandService;
    private final ProjectSession projectSession;
    private final AppState appState;
    private final EventBus eventBus;
    private final SnapManager snapManager;
    private final AtomicReference<ShapeStyle> currentStyle = new AtomicReference<>(new ShapeStyle());
    private final AtomicLong stateVersion = new AtomicLong(1);

    @SuppressWarnings("deprecation")
    private ApplicationContext() {
        LOGGER.info("创建 ApplicationContext...");
        // 事件总线先解析：后续服务一律注入，不再各自 EventBus.getInstance()
        this.eventBus = EventBus.getInstance();
        this.commandService = CommandService.initialize(eventBus);
        this.projectSession = new ProjectSession();
        this.viewportState = new ViewportState();
        this.activeToolState = new ActiveToolState();

        // AppState facade first so SelectionState can publish events with it
        this.appState = new AppState(this);

        this.selectionState = new SelectionState(() -> appState);

        // SpatialIndex 延迟通过 LayerService 取图元；先建 LayerService 再绑 supplier
        java.util.concurrent.atomic.AtomicReference<LayerService> layerRef =
            new java.util.concurrent.atomic.AtomicReference<>();
        this.spatialIndexService = new SpatialIndexService(() -> {
            LayerService layers = layerRef.get();
            return layers != null ? layers.getShapes() : java.util.Collections.emptyList();
        });

        // 先挂 INSTANCE：子组件若回查 getInstance() 不会再递归创建
        INSTANCE = this;

        this.layerService = new LayerService(spatialIndexService, selectionState, eventBus);
        layerRef.set(this.layerService);

        this.snapManager = SnapManager.initialize(eventBus, appState);
    }

    public static ApplicationContext getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    new ApplicationContext();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 通用逻辑侧初始化：图层系统等。
     */
    public void initialize() {
        layerService.initialize();
    }

    public AppState getAppState() {
        return appState;
    }

    public SelectionState getSelectionState() {
        return selectionState;
    }

    public ViewportState getViewportState() {
        return viewportState;
    }

    public ActiveToolState getActiveToolState() {
        return activeToolState;
    }

    public LayerService getLayerService() {
        return layerService;
    }

    public SpatialIndexService getSpatialIndexService() {
        return spatialIndexService;
    }

    public CommandService getCommandService() {
        return commandService;
    }

    /**
     * 为插件安装路径创建宿主服务句柄（见 {@link PluginContext}）。
     */
    public PluginContext createPluginContext() {
        return PluginContext.from(this);
    }

    public SnapService getSnapService() {
        return getSnapManager();
    }

    public SnapManager getSnapManager() {
        return snapManager;
    }

    public com.plot.core.tool.ToolManager getToolManager() {
        return com.plot.core.tool.ToolManager.getInstance();
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public CoordinateTransformer getCoordinateTransformer() {
        return CoordinateTransformer.getInstance();
    }

    public GhostBlockManager getGhostBlockManager() {
        return GhostBlockManager.getInstance();
    }

    public BlockPlacementScheduler getBlockPlacementScheduler() {
        return BlockPlacementScheduler.getInstance();
    }

    public BlockProjectionHandler getBlockProjectionHandler() {
        return BlockProjectionHandler.getInstance();
    }

    public ProjectSession getProjectSession() {
        return projectSession;
    }

    public Project getCurrentProject() {
        return projectSession.getCurrentProject();
    }

    public void setCurrentProject(Project project) {
        projectSession.setCurrentProject(project);
        bumpStateVersion();
    }

    public IShapeStyle getCurrentShapeStyle() {
        ShapeStyle style = currentStyle.get();
        if (style == null) {
            style = new ShapeStyle();
            style.setStrokeColor(java.awt.Color.BLACK);
            style.setStrokeWidth(2.0f);
            style.setFillColor(java.awt.Color.LIGHT_GRAY);
            currentStyle.set(style);
        }
        return style;
    }

    public long getStateVersion() {
        return stateVersion.get();
    }

    public void bumpStateVersion() {
        stateVersion.incrementAndGet();
    }

    public void clear() {
        selectionState.clear();
        layerService.clearMappings();
        bumpStateVersion();
        LOGGER.info("应用状态已清空");
    }

    public void dispose() {
        // 仅取消防抖 pending；线程池由 PlotRuntime.shutdown() 统一关闭
        DebouncedTasks.shutdown();
    }

    public boolean isValid() {
        try {
            boolean layerManagerValid = layerService.getLayerManager() != null
                && layerService.getLayerManager().getLayerCount() >= 0;
            boolean commandServiceValid = commandService != null;
            float opacity = viewportState.getOpacity();
            boolean basicStateValid = opacity >= 0 && opacity <= 1;
            return layerManagerValid && commandServiceValid && basicStateValid;
        } catch (Exception e) {
            LOGGER.error("验证应用上下文时出错", e);
            return false;
        }
    }
}
