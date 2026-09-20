package com.plot.plugin;

import com.plot.core.model.Project;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.project.ProjectLoadedEvent;
import com.plot.infrastructure.event.project.ProjectSavedEvent;
import com.plot.plugin.powerline.PowerLineGenerator;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.ui.PowerLinePluginState;
import com.plot.plugin.powerline.ui.PowerLineUiContext;
import com.plot.plugin.powerline.ui.PowerLineUIManager;
import com.plot.ui.component.ExtensionPanelIcons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 电力线路插件：认领走线 → 布置电线杆 → 生成下垂导线。
 */
public class PowerLinePlugin extends Plugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PowerLinePlugin");
    static final String DEFAULT_PROJECT_FILE = "default.json";

    private final Object projectLock = new Object();
    private final PowerLinePluginState pluginState = new PowerLinePluginState();

    private PowerLineUiContext uiContext;
    private PowerLineUIManager uiManager;

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

    public PowerLinePlugin() {
        super(
            "power_line",
            "plugin.powerline.name",
            "plugin.powerline.desc",
            ExtensionPanelIcons.POWER_LINE
        );
    }

    @Override
    public void onEnable() {
        PowerLineGenerator generator;
        try {
            generator = new PowerLineGenerator(ctx().coordinates(), ctx().projection());
        } catch (Exception e) {
            LOGGER.error("初始化电力线路生成器失败: {}", e.getMessage(), e);
            throw new RuntimeException("电力线路插件初始化失败", e);
        }

        uiContext = new PowerLineUiContext(ctx(), pluginState, projectLock);
        uiContext.setGenerator(generator);
        uiManager = new PowerLineUIManager(uiContext);

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
        uiContext.onProjectLoaded(filePath, getProjectsDir(), getDesignProjectsDir());
    }

    private void onProjectSaved(String filePath) {
        if (uiContext == null) {
            return;
        }
        uiContext.onProjectSaved(filePath, getProjectsDir(), getDesignProjectsDir());
    }

    private void persistProject() {
        if (uiContext != null) {
            uiContext.persistProject(getProjectsDir(), getDesignProjectsDir());
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
        uiContext.loadProjectForCurrentProject(
            getProjectsDir(),
            getDesignProjectsDir(),
            DEFAULT_PROJECT_FILE);
    }

    private Path getProjectsDir() {
        return getDataFolder().toPath().resolve("projects");
    }

    private Path getDesignProjectsDir() {
        return getDataFolder().toPath().resolve("pole-designs");
    }

    public List<PowerLineFootprint> listLines() {
        synchronized (projectLock) {
            if (uiContext == null) {
                return List.of();
            }
            return new ArrayList<>(uiContext.project().getLines().values());
        }
    }
}
