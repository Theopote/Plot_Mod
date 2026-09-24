package com.plot.plugin;

import com.plot.api.geometry.Vec2d;
import com.plot.api.plugin.IPlugin;
import com.plot.core.plugin.PluginManager;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.earthwork.RoadEarthworkCorridorResolver;
import com.plot.plugin.road.earthwork.RoadEarthworkSurfaceSampler;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.manager.RoadJunctionPropertyProvider;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadPersistenceManager;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.plugin.road.manager.RoadUIManager;
import com.plot.plugin.road.overlay.RoadOverlayRenderer;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.canvas.CanvasOverlayRegistry;
import com.plot.ui.component.ExtensionPanelIcons;
import imgui.ImDrawList;
import java.util.List;
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

    private final CanvasOverlayRegistry.Overlay roadOverlay = this::renderRoadOverlay;

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
        persistenceManager = new RoadPersistenceManager(getDataFolder(), status, ctx());
        previewManager = new RoadPreviewManager(status, ctx());
        networkManager.setOnNetworkChanged(previewManager::invalidatePreview);
        toolManager = new RoadToolManager(status, ctx());
        uiManager = new RoadUIManager(
            networkManager, previewManager, persistenceManager, toolManager, status, ctx());
        toolManager.setPathsPickedHandler(paths -> {
            networkManager.adoptSelectedPaths(paths);
            uiManager.onPathsPicked();
        });

        try {
            RoadGenerator roadGenerator = new RoadGenerator(config, ctx().coordinates(), ctx().projection());
            previewManager.setNetworkGenerator(new RoadNetworkGenerator(roadGenerator));
        } catch (Exception e) {
            LOGGER.error("初始化道路生成器失败: {}", e.getMessage(), e);
            throw new RuntimeException("道路插件初始化失败", e);
        }

        try {
            ctx().events().subscribe(this, ProjectLoadedEvent.class, projectLoadedListener);
            ctx().events().subscribe(this, ProjectSavedEvent.class, projectSavedListener);
            CanvasOverlayRegistry.register(roadOverlay);
            persistenceManager.loadForCurrentProject(
                networkManager::setNetwork,
                () -> {
                    networkManager.getHistory().clear();
                    networkManager.resetSelection();
                });
        } catch (Exception e) {
            ctx().events().unsubscribeOwner(this);
            LOGGER.error("加载当前项目失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void onDeactivate() {
        if (toolManager != null) {
            toolManager.cancel();
        }
        if (uiManager != null) {
            uiManager.onDeactivate();
        }
        super.onDeactivate();
    }

    @Override
    public void onDisable() {
        if (persistenceManager != null && networkManager != null) {
            persistenceManager.saveOnDisable(networkManager.getNetwork());
        }
        if (previewManager != null) {
            previewManager.clearPreview();
        }
        if (toolManager != null) {
            toolManager.cancel();
        }

        ctx().events().unsubscribeOwner(this);
        CanvasOverlayRegistry.unregister(roadOverlay);

        if (config != null) {
            config.save();
        }
    }

    private void renderRoadOverlay(ImDrawList drawList, CanvasCamera camera) {
        if (!isEnabled() || uiManager == null) {
            return;
        }
        IPlugin active = PluginManager.getInstance().getActivePlugin();
        if (active != this) {
            return;
        }
        uiManager.refreshOverlayForCanvas();
        RoadOverlayRenderer.render(drawList, camera, uiManager.overlayEntries());
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

    public java.util.List<RoadEarthworkSurfaceSampler.EdgeRef> listEarthworkRoadEdges() {
        if (networkManager == null) {
            return java.util.List.of();
        }
        return RoadEarthworkSurfaceSampler.listEdgeRefs(networkManager.getNetwork());
    }

    public Integer sampleEarthworkDesignY(String edgeId, Vec2d planPoint) {
        if (networkManager == null) {
            return null;
        }
        return RoadEarthworkSurfaceSampler.sampleDesignSurfaceY(networkManager.getNetwork(), edgeId, planPoint);
    }

    public boolean hasEarthworkDesignElevation(String edgeId) {
        if (networkManager == null) {
            return false;
        }
        return RoadEarthworkSurfaceSampler.hasResolvableDesignElevation(
            networkManager.getNetwork(), edgeId);
    }

    public List<Vec2d> resolveEarthworkCorridorOutline(String edgeId, int extraMarginBlocks) {
        if (networkManager == null || config == null || edgeId == null || edgeId.isBlank()) {
            return List.of();
        }
        return RoadEarthworkCorridorResolver.resolveOutline(
            networkManager.getNetwork(),
            networkManager.getNetwork().getEdge(edgeId),
            config,
            extraMarginBlocks);
    }

    public List<Vec2d> resolveEarthworkRoadCenterline(String edgeId) {
        if (networkManager == null || edgeId == null || edgeId.isBlank()) {
            return List.of();
        }
        return RoadEarthworkCorridorResolver.resolveCenterline(
            networkManager.getNetwork(),
            networkManager.getNetwork().getEdge(edgeId));
    }
}
