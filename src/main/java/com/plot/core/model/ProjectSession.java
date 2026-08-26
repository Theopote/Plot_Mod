package com.plot.core.model;

import com.plot.core.context.ApplicationContext;
import com.plot.core.layer.LayerService;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 画布会话：持有当前 {@link Project}，并负责默认会话文件的加载/保存。
 * <p>
 * 默认文件：{@code .minecraft/plot/projects/default.json}
 */
public final class ProjectSession {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ProjectSession");
    private static final String DEFAULT_PROJECT_FILE = "default.json";

    private Project currentProject;
    private boolean loaded;

    public Path getDefaultProjectPath() {
        return FabricLoader.getInstance()
            .getGameDir()
            .resolve("plot")
            .resolve("projects")
            .resolve(DEFAULT_PROJECT_FILE);
    }

    public Project getCurrentProject() {
        return currentProject;
    }

    public void setCurrentProject(Project project) {
        this.currentProject = project;
    }

    /**
     * 启动时加载上次会话；若无存档则创建默认图层。
     */
    public void loadOrInitialize(ApplicationContext context) {
        if (context == null || loaded) {
            return;
        }
        loaded = true;

        LayerService layers = context.getLayerService();
        Path path = getDefaultProjectPath();
        if (Files.isRegularFile(path)) {
            try {
                Project.loadFromFile(context.getAppState(), path);
                LOGGER.info("已加载画布会话: {}", path);
                return;
            } catch (Exception e) {
                LOGGER.error("加载画布会话失败，将使用默认图层: {}", e.getMessage(), e);
            }
        }

        layers.ensureDefaultLayer();
        if (currentProject == null) {
            setCurrentProject(Project.captureFromAppState(context.getAppState()));
        }
    }

    /**
     * 将当前画布图层写入默认会话文件。
     */
    public void save(ApplicationContext context) {
        if (context == null || context.getLayerService().getLayerManager() == null) {
            return;
        }
        try {
            Path path = getDefaultProjectPath();
            Files.createDirectories(path.getParent());
            Project.saveToFile(context.getAppState(), path);
            LOGGER.info("已保存画布会话: {}", path);
        } catch (Exception e) {
            LOGGER.error("保存画布会话失败: {}", e.getMessage(), e);
        }
    }

    /**
     * @deprecated 使用 {@link #loadOrInitialize(ApplicationContext)}
     */
    @Deprecated
    public static void loadOrInitialize(com.plot.core.state.AppState appState) {
        if (appState == null) {
            return;
        }
        ApplicationContext.getInstance().getProjectSession().loadOrInitialize(ApplicationContext.getInstance());
    }

    /**
     * @deprecated 使用 {@link #save(ApplicationContext)}
     */
    @Deprecated
    public static void save(com.plot.core.state.AppState appState) {
        ApplicationContext.getInstance().getProjectSession().save(ApplicationContext.getInstance());
    }
}
