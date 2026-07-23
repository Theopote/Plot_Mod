package com.plot.plugin.road.manager;

import com.plot.core.model.Project;
import com.plot.core.state.AppState;
import com.plot.plugin.common.ProjectPathHasher;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkHistory;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 道路网络持久化：按工程文件路径关联 networks/*.json。
 */
public final class RoadPersistenceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadPersistence");
    private static final String DEFAULT_NETWORK_FILE = "default.json";

    private final File dataFolder;
    private final RoadProjectStatus status;
    private String currentNetworkFile = DEFAULT_NETWORK_FILE;

    public RoadPersistenceManager(File dataFolder, RoadProjectStatus status) {
        this.dataFolder = dataFolder;
        this.status = status;
    }

    public String getCurrentNetworkFile() {
        return currentNetworkFile;
    }

    public Path getCurrentNetworkPath() {
        return getNetworksDir().resolve(currentNetworkFile);
    }

    public void loadForCurrentProject(
            Consumer<RoadNetwork> onLoaded,
            Runnable onSelectionReset) {
        Project project = AppState.getInstance().getCurrentProject();
        if (project != null && project.getFilePath() != null && !project.getFilePath().isBlank()) {
            onProjectLoaded(project.getFilePath(), onLoaded, onSelectionReset);
            return;
        }
        Path file = getNetworksDir().resolve(DEFAULT_NETWORK_FILE);
        if (loadNetworkFile(file, onLoaded, onSelectionReset)) {
            currentNetworkFile = DEFAULT_NETWORK_FILE;
            status.set(PlotI18n.tr("plugin.road.network.default_loaded"));
        }
    }

    public void onProjectLoaded(
            String filePath,
            Consumer<RoadNetwork> onLoaded,
            Runnable onSelectionReset) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String targetFile = ProjectPathHasher.projectFileName(filePath);
        Path file = getNetworksDir().resolve(targetFile);
        // 仅在加载成功后才绑定 currentNetworkFile，避免失败时把旧路网写进新工程文件
        if (loadNetworkFile(file, onLoaded, onSelectionReset)) {
            currentNetworkFile = targetFile;
            status.set(PlotI18n.tr("plugin.road.network.loaded", filePath));
        }
    }

    public void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentNetworkFile = ProjectPathHasher.projectFileName(filePath);
        status.set(PlotI18n.tr("plugin.road.network.saved", filePath));
    }

    public void saveOnDisable(RoadNetwork network) {
        saveNetworkFile(getCurrentNetworkPath(), network);
    }

    public RoadNetwork loadNetworkFile(
            Path file,
            RoadNetworkHistory history,
            Runnable onSelectionReset) {
        try {
            RoadNetwork loaded = RoadNetwork.loadFrom(file);
            // 只有加载成功后才清空历史，保证原子性
            history.clear();
            onSelectionReset.run();
            return loaded;
        } catch (IOException e) {
            LOGGER.error("加载道路网络失败: {}", e.getMessage(), e);
            status.set(PlotI18n.tr("plugin.road.network.load_failed", file.getFileName()));
            // 加载失败时不清空历史，保留当前状态
            return new RoadNetwork();
        }
    }

    /**
     * @return true 若加载成功（含文件不存在时返回空网络）
     */
    private boolean loadNetworkFile(
            Path file,
            Consumer<RoadNetwork> onLoaded,
            Runnable onSelectionReset) {
        try {
            RoadNetwork loaded = RoadNetwork.loadFrom(file);
            onLoaded.accept(loaded);
            onSelectionReset.run();
            return true;
        } catch (IOException e) {
            LOGGER.error("加载道路网络失败: {}", e.getMessage(), e);
            status.set(PlotI18n.tr("plugin.road.network.load_failed", file.getFileName()));
            return false;
        }
    }

    public boolean saveNetworkFile(Path file, RoadNetwork network) {
        if (network == null) {
            return false;
        }
        try {
            network.saveTo(file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存道路网络失败: {}", e.getMessage(), e);
            status.set(PlotI18n.tr(
                "plugin.road.network.save_failed",
                file != null ? file.getFileName() : "?"));
            return false;
        }
    }

    public Path getNetworksDir() {
        return dataFolder.toPath().resolve("networks");
    }
}
