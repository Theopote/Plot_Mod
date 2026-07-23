package com.plot.plugin;

import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.manager.RoadJunctionPropertyProvider;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadPersistenceManager;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.plugin.road.manager.RoadUIManager;
import com.plot.ui.component.ExtensionPanelIcons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 道路系统插件：负责生命周期编排，具体能力由各 Manager 承担。
 */
public class RoadSystemPlugin extends Plugin implements RoadJunctionPropertyProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadSystemPlugin");

    private RoadSystemConfig config;
    private final RoadProjectStatus status = new RoadProjectStatus();

    private RoadNetworkManager networkManager;
    private RoadPersistenceManager persistenceManager;
    private RoadPreviewManager previewManager;
    private RoadToolManager toolManager;
    private RoadUIManager uiManager;

    private final EventListener projectLoadedListener = event -> {
        if (event instanceof ProjectLoadedEvent loaded) {
            onProjectLoaded(loaded.getFilePath());
        }
    };
    private final EventListener projectSavedListener = event -> {
        if (event instanceof ProjectSavedEvent saved) {
            onProjectSaved(saved.getFilePath());
        }
    };

    public RoadSystemPlugin() {
        super(
            "road_system",
            "plugin.road_system.name",
            "plugin.road_system.desc",
            ExtensionPanelIcons.ROAD_SYSTEM
        );
    }

    @Override
    public void onEnable() {
        config = RoadSystemConfig.load(RoadSystemConfig.class, getId());
        if (config == null) {
            config = new RoadSystemConfig(getId());
        }

        networkManager = new RoadNetworkManager(config, status);
        persistenceManager = new RoadPersistenceManager(getDataFolder(), status);
        previewManager = new RoadPreviewManager(status);
        // 路网任何变更都使预览失效，避免按过期几何落地
        networkManager.setOnNetworkChanged(previewManager::invalidatePreview);
        toolManager = new RoadToolManager(status);
        toolManager.setPathsPickedHandler(networkManager::adoptSelectedPaths);
        uiManager = new RoadUIManager(
            networkManager, previewManager, persistenceManager, toolManager, status);

        try {
            CoordinateTransformer transformer = CoordinateTransformer.getInstance();
            RoadGenerator roadGenerator = new RoadGenerator(config, transformer);
            previewManager.setNetworkGenerator(new RoadNetworkGenerator(roadGenerator));
        } catch (Exception e) {
            LOGGER.error("初始化道路生成器失败: {}", e.getMessage(), e);
        }

        // 订阅事件并加载网络，如果失败则清理资源
        try {
            EventBus.getInstance().subscribe(ProjectLoadedEvent.class, projectLoadedListener);
            EventBus.getInstance().subscribe(ProjectSavedEvent.class, projectSavedListener);
            persistenceManager.loadForCurrentProject(
                networkManager::setNetwork,
                () -> {
                    networkManager.getHistory().clear();
                    networkManager.resetSelection();
                });
        } catch (Exception e) {
            // 清理已订阅的监听器，防止资源泄漏
            EventBus.getInstance().unsubscribe(ProjectLoadedEvent.class, projectLoadedListener);
            EventBus.getInstance().unsubscribe(ProjectSavedEvent.class, projectSavedListener);
            LOGGER.error("加载当前项目失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void onDeactivate() {
        if (!isEnabled() || persistenceManager == null || networkManager == null) {
            return;
        }
        persistenceManager.saveOnDisable(networkManager.getNetwork());
    }

    @Override
    public void onDisable() {
        if (persistenceManager != null && networkManager != null) {
            persistenceManager.saveOnDisable(networkManager.getNetwork());
        }
        if (toolManager != null) {
            toolManager.cancel();
        }

        EventBus.getInstance().unsubscribe(ProjectLoadedEvent.class, projectLoadedListener);
        EventBus.getInstance().unsubscribe(ProjectSavedEvent.class, projectSavedListener);

        if (config != null) {
            config.save();
        }
    }

    @Override
    public void render() {
        if (config == null) {
            return;
        }
        uiManager.render();
    }

    @Override
    public void renderDeferredModals() {
        if (config == null) {
            return;
        }
        uiManager.renderDeferredModals();
    }

    @Override
    public boolean hasJunctionPropertyContent() {
        return uiManager.hasJunctionPropertyContent();
    }

    @Override
    public void renderJunctionPropertySection() {
        uiManager.renderJunctionPropertySection();
    }

    @Override
    public String getPropertySectionTitleKey() {
        return uiManager.getPropertySectionTitleKey();
    }

    private void onProjectLoaded(String filePath) {
        persistenceManager.onProjectLoaded(
            filePath,
            networkManager::setNetwork,
            () -> {
                networkManager.getHistory().clear();
                networkManager.resetSelection();
            });
    }

    private void onProjectSaved(String filePath) {
        persistenceManager.onProjectSaved(filePath);
        persistenceManager.saveNetworkFile(
            persistenceManager.getCurrentNetworkPath(),
            networkManager.getNetwork());
    }
}
