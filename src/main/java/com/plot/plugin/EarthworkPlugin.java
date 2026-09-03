package com.plot.plugin;

import com.plot.core.model.Project;
import com.plot.core.persistence.ContentFingerprint;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.config.EarthworkConfig;
import com.plot.plugin.earthwork.EarthworkCutFillHeatmapRenderer;
import com.plot.plugin.earthwork.EarthworkEdgeTreatmentCanvasRenderer;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.EarthworkRegionGeometryCanvasRenderer;
import com.plot.plugin.earthwork.EarthworkRegionPickSession;
import com.plot.plugin.earthwork.EarthworkThreePointPickSession;
import com.plot.plugin.earthwork.terrain.TerrainSnapshotCache;
import com.plot.plugin.earthwork.manager.EarthworkBuildManager;
import com.plot.plugin.earthwork.manager.EarthworkPreviewManager;
import com.plot.plugin.earthwork.manager.EarthworkUIManager;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkProjectHistory;
import com.plot.plugin.earthwork.ui.EarthworkUiContext;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.canvas.CanvasOverlayRegistry;
import com.plot.ui.component.ExtensionPanelIcons;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 土方平衡插件：负责生命周期与持久化编排，ImGui 界面由 {@link EarthworkUIManager} 承担。
 */
public class EarthworkPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EarthworkPlugin");
    private static final String DEFAULT_PROJECT_FILE = "default.json";

    private EarthworkConfig config;
    private final EarthworkProjectHistory projectHistory = new EarthworkProjectHistory();
    private final EarthworkRegionPickSession pickSession = new EarthworkRegionPickSession();
    private final EarthworkThreePointPickSession threePointPickSession = new EarthworkThreePointPickSession();
    private final TerrainSnapshotCache terrainSnapshotCache = new TerrainSnapshotCache();
    private final Object projectLock = new Object();
    private final ContentFingerprint.Tracker contentFingerprint = new ContentFingerprint.Tracker();

    private EarthworkUiContext uiContext;
    private EarthworkUIManager uiManager;
    private String currentProjectFile = DEFAULT_PROJECT_FILE;

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

    private final CanvasOverlayRegistry.Overlay edgeTreatmentOverlay = this::renderEdgeTreatmentOverlay;

    public EarthworkPlugin() {
        super(
            "earthwork_balance",
            "plugin.earthwork_balance.name",
            "plugin.earthwork_balance.desc",
            ExtensionPanelIcons.EARTHWORK
        );
    }

    @Override
    public void onEnable() {
        config = EarthworkConfig.load(EarthworkConfig.class, getId());
        if (config == null) {
            config = new EarthworkConfig(getId());
        }

        EarthworkPipelines.Bundle pipelines;
        try {
            pipelines = EarthworkPipelines.create(ctx().coordinates());
        } catch (Exception e) {
            LOGGER.error("初始化土方管线失败: {}", e.getMessage(), e);
            throw new RuntimeException("土方插件初始化失败", e);
        }

        EarthworkProject project = new EarthworkProject();
        final EarthworkUiContext[] contextHolder = new EarthworkUiContext[1];
        EarthworkPreviewManager previewManager = new EarthworkPreviewManager(
            ctx(), pipelines, terrainSnapshotCache,
            msg -> {
                if (contextHolder[0] != null) {
                    contextHolder[0].setProjectStatus(msg);
                }
            });
        EarthworkBuildManager buildManager = new EarthworkBuildManager(
            ctx(), terrainSnapshotCache, previewManager,
            msg -> {
                if (contextHolder[0] != null) {
                    contextHolder[0].setProjectStatus(msg);
                }
            });

        uiContext = new EarthworkUiContext(
            ctx(),
            config,
            project,
            projectHistory,
            pickSession,
            threePointPickSession,
            terrainSnapshotCache,
            previewManager,
            buildManager);
        contextHolder[0] = uiContext;
        uiContext.autoBalanceRef().set(config.isAutoBalance());
        uiContext.showGridRef().set(config.isShowGrid());
        uiContext.showEdgeTreatmentOverlayRef().set(config.isShowEdgeTreatmentOverlay());

        uiManager = new EarthworkUIManager(uiContext);

        ctx().events().subscribe(this, ProjectLoadedEvent.class, projectLoadedListener);
        ctx().events().subscribe(this, ProjectSavedEvent.class, projectSavedListener);
        CanvasOverlayRegistry.register(edgeTreatmentOverlay);
        loadProjectForCurrentProject();
    }

    @Override
    public void onDeactivate() {
        if (isEnabled()) {
            persistProject();
        }
        super.onDeactivate();
    }

    @Override
    public void onDisable() {
        pickSession.cancel();
        threePointPickSession.cancel();
        persistProject();
        if (config != null) {
            config.save();
        }

        try {
            ctx().events().unsubscribeOwner(this);
        } catch (Exception e) {
            LOGGER.error("取消事件订阅失败: {}", e.getMessage(), e);
        }
        CanvasOverlayRegistry.unregister(edgeTreatmentOverlay);
    }

    @Override
    public void render() {
        if (uiManager != null) {
            uiManager.render();
        }
    }

    @Override
    public void renderDeferredModals() {
        if (uiManager != null) {
            uiManager.renderDeferredModals();
        }
    }

    private void renderEdgeTreatmentOverlay(imgui.ImDrawList drawList, CanvasCamera camera) {
        if (!isEnabled() || config == null || uiContext == null) {
            return;
        }
        synchronized (projectLock) {
            if (uiContext.project().getRegionCount() <= 0) {
                return;
            }
            EarthworkGenerationResult preview = uiContext.previewManager().getLastGenerationResult();
            if (preview != null && preview.designTerrainGrid != null) {
                EarthworkCutFillHeatmapRenderer.render(drawList, camera, preview.designTerrainGrid);
            }
            if (config.isShowEdgeTreatmentOverlay()) {
                EarthworkEdgeTreatmentCanvasRenderer.render(
                    drawList, camera, uiContext.project(), uiContext.selectedRegionId());
            }
            EarthworkRegionGeometryCanvasRenderer.render(
                drawList, camera, uiContext.project(), uiContext.selectedRegionId());
        }
    }

    private void onProjectLoaded(String filePath) {
        if (filePath == null || filePath.isBlank() || uiContext == null) {
            return;
        }
        String targetFile = ProjectPathResolver.sidecarFileName(filePath);
        Path file = getProjectsDir().resolve(targetFile);
        if (loadProjectFile(file)) {
            currentProjectFile = targetFile;
            uiContext.setProjectStatus(PlotI18n.tr("plugin.earthwork.project.loaded", filePath));
        }
    }

    private void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank() || uiContext == null) {
            return;
        }
        currentProjectFile = ProjectPathResolver.sidecarFileName(filePath);
        if (saveProjectFile(getProjectsDir().resolve(currentProjectFile))) {
            uiContext.setProjectStatus(PlotI18n.tr("plugin.earthwork.project.saved", filePath));
        }
    }

    private void persistProject() {
        saveProjectFile(getProjectsDir().resolve(currentProjectFile));
    }

    private boolean loadProjectFile(Path file) {
        if (uiContext == null) {
            return false;
        }
        try {
            EarthworkProject loaded = EarthworkProject.loadFrom(file);
            uiContext.setProject(loaded);
            projectHistory.clear();
            String firstRegionId = loaded.getRegions().isEmpty()
                ? ""
                : loaded.getRegions().keySet().iterator().next();
            uiContext.resetAfterProjectLoad(firstRegionId);
            return true;
        } catch (IOException e) {
            LOGGER.error("加载土方项目失败: {}", e.getMessage(), e);
            uiContext.setProjectStatus(PlotI18n.tr("plugin.earthwork.project.load_failed", file.getFileName()));
            return false;
        }
    }

    private void loadProjectForCurrentProject() {
        Project current = ctx().appState().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath());
            return;
        }
        Path file = getProjectsDir().resolve(DEFAULT_PROJECT_FILE);
        if (loadProjectFile(file)) {
            currentProjectFile = DEFAULT_PROJECT_FILE;
            uiContext.setProjectStatus(PlotI18n.tr("plugin.earthwork.project.default_loaded"));
        }
    }

    private boolean saveProjectFile(Path file) {
        if (file == null || uiContext == null) {
            return false;
        }
        EarthworkProject project = uiContext.project();
        try {
            String json = project.toJson();
            if (contentFingerprint.isUnchanged(json, file)) {
                LOGGER.debug("土方项目内容未变，跳过重复保存: {}", file.getFileName());
                return true;
            }
            project.saveTo(file);
            contentFingerprint.markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存土方项目失败: {}", e.getMessage(), e);
            uiContext.setProjectStatus(PlotI18n.tr("plugin.earthwork.project.save_failed", file.getFileName()));
            return false;
        }
    }

    private Path getProjectsDir() {
        return getDataFolder().toPath().resolve("projects");
    }
}
