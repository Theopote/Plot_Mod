package com.plot.plugin;

import com.plot.core.model.Project;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.building.BuildingGenerator;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.ui.BuildingPluginState;
import com.plot.plugin.building.ui.BuildingUiContext;
import com.plot.plugin.building.ui.BuildingUIManager;
import com.plot.ui.component.ExtensionPanelIcons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 建筑插件：负责生命周期与持久化编排，ImGui 界面由 {@link BuildingUIManager} 承担。
 */
public class BuildingPlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingPlugin");
    static final String DEFAULT_PROJECT_FILE = "default.json";

    private final Object projectLock = new Object();
    private final BuildingPluginState pluginState = new BuildingPluginState();

    private BuildingUiContext uiContext;
    private BuildingUIManager uiManager;

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

    public BuildingPlugin() {
        super(
            "building",
            "plugin.building.name",
            "plugin.building.desc",
            ExtensionPanelIcons.BUILDING
        );
    }

    @Override
    public void onEnable() {
        BuildingGenerator buildingGenerator;
        try {
            buildingGenerator = new BuildingGenerator(ctx().coordinates(), ctx().projection());
        } catch (Exception e) {
            LOGGER.error("初始化建筑生成器失败: {}", e.getMessage(), e);
            throw new RuntimeException("建筑插件初始化失败", e);
        }

        uiContext = new BuildingUiContext(ctx(), pluginState, projectLock);
        uiContext.setBuildingGenerator(buildingGenerator);
        uiManager = new BuildingUIManager(uiContext);

        ctx().events().subscribe(this, ProjectLoadedEvent.class, projectLoadedListener);
        ctx().events().subscribe(this, ProjectSavedEvent.class, projectSavedListener);
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
        persistProject();
        if (uiContext != null) {
            uiContext.pickSession().cancel();
        }

        try {
            ctx().events().unsubscribeOwner(this);
        } catch (Exception e) {
            LOGGER.error("取消事件订阅失败: {}", e.getMessage(), e);
        }
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

    private void onProjectLoaded(String filePath) {
        if (uiContext == null) {
            return;
        }
        uiContext.onProjectLoaded(filePath, getProjectsDir());
    }

    private void onProjectSaved(String filePath) {
        if (uiContext == null) {
            return;
        }
        uiContext.onProjectSaved(filePath, getProjectsDir());
    }

    private void persistProject() {
        if (uiContext != null) {
            uiContext.persistProject(getProjectsDir());
        }
    }

    private void loadProjectForCurrentProject() {
        if (uiContext == null) {
            return;
        }
        Project current = ctx().appState().getCurrentProject();
        if (current != null && current.getFilePath() != null && !current.getFilePath().isBlank()) {
            onProjectLoaded(current.getFilePath());
            return;
        }
        Path file = getProjectsDir().resolve(DEFAULT_PROJECT_FILE);
        if (uiContext.loadProjectFile(file)) {
            uiContext.setCurrentProjectFile(DEFAULT_PROJECT_FILE);
            uiContext.setProjectStatus(com.plot.utils.PlotI18n.tr("plugin.building.project.default_loaded"));
        }
    }

    private Path getProjectsDir() {
        return getDataFolder().toPath().resolve("projects");
    }

    public BuildingFootprint getBuildingFootprint(String id) {
        synchronized (projectLock) {
            if (uiContext == null) {
                return null;
            }
            return uiContext.project().getBuilding(id);
        }
    }

    public List<BuildingFootprint> listBuildingFootprints() {
        synchronized (projectLock) {
            if (uiContext == null) {
                return List.of();
            }
            return new ArrayList<>(uiContext.project().getBuildings().values());
        }
    }
}
