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
    private final AtomicReference<ShapeStyle> currentStyle = new AtomicReference<>(new ShapeStyle());
    private final AtomicLong stateVersion = new AtomicLong(1);

    private ApplicationContext() {
        LOGGER.info("创建 ApplicationContext...");
        this.commandService = CommandService.getInstance();
        // SnapManager 构造会回调 AppState.getInstance()，须等本 Context 挂到 INSTANCE 后再取
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
        this.layerService = new LayerService(spatialIndexService, selectionState);
        layerRef.set(this.layerService);
    }

    public static ApplicationContext getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new ApplicationContext();
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

    public SnapService getSnapService() {
        return SnapManager.getInstance();
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
