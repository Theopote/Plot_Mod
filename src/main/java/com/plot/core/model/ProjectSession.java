package com.plot.core.model;

import com.plot.core.state.AppState;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 画布会话持久化：关闭 Plot / 退出游戏时自动保存，启动时自动加载。
 * <p>
 * 默认文件：{@code .minecraft/plot/projects/default.json}
 */
public final class ProjectSession {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ProjectSession");
    private static final String DEFAULT_PROJECT_FILE = "default.json";

    private static volatile boolean loaded = false;

    private ProjectSession() {
    }

    public static Path getDefaultProjectPath() {
        return FabricLoader.getInstance()
                .getGameDir()
                .resolve("plot")
                .resolve("projects")
                .resolve(DEFAULT_PROJECT_FILE);
    }

    /**
     * 启动时加载上次会话；若无存档则创建默认图层。
     */
    public static void loadOrInitialize(AppState appState) {
        if (appState == null) {
            return;
        }
        if (loaded) {
            return;
        }
        loaded = true;

        Path path = getDefaultProjectPath();
        if (Files.isRegularFile(path)) {
            try {
                Project.loadFromFile(appState, path);
                LOGGER.info("已加载画布会话: {}", path);
                return;
            } catch (Exception e) {
                LOGGER.error("加载画布会话失败，将使用默认图层: {}", e.getMessage(), e);
            }
        }

        appState.ensureDefaultLayer();
        if (appState.getCurrentProject() == null) {
            appState.setCurrentProject(Project.captureFromAppState(appState));
        }
    }

    /**
     * 将当前画布图层写入默认会话文件。
     */
    public static void save(AppState appState) {
        if (appState == null || appState.getLayerManager() == null) {
            return;
        }
        try {
            Path path = getDefaultProjectPath();
            Files.createDirectories(path.getParent());
            Project.saveToFile(appState, path);
            LOGGER.info("已保存画布会话: {}", path);
        } catch (Exception e) {
            LOGGER.error("保存画布会话失败: {}", e.getMessage(), e);
        }
    }
}
