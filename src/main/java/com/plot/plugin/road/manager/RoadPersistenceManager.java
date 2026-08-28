package com.plot.plugin.road.manager;

import com.plot.core.context.PluginContext;
import com.plot.core.model.Project;
import com.plot.core.persistence.ContentFingerprint;
import com.plot.core.persistence.ProjectPathResolver;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkHistory;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 道路网络持久化：按工程文件路径关联 networks/*.json。
 *
 * <p>保存时在 client 线程读取 live {@link RoadNetwork} 并 {@link RoadNetwork#toJson()}；
 * 若将来改为异步落盘，应先 {@link RoadNetwork#snapshot()} 再写文件，避免与 UI 编辑交错。
 */
public final class RoadPersistenceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadPersistence");
    private static final String DEFAULT_NETWORK_FILE = "default.json";

    private final File dataFolder;
    private final RoadProjectStatus status;
    private final PluginContext host;
    private String currentNetworkFile = DEFAULT_NETWORK_FILE;
    private final ContentFingerprint.Tracker contentFingerprint = new ContentFingerprint.Tracker();

    public RoadPersistenceManager(File dataFolder, RoadProjectStatus status, PluginContext host) {
        this.dataFolder = dataFolder;
        this.status = status;
        this.host = Objects.requireNonNull(host, "host");
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
        Project project = host.appState().getCurrentProject();
        if (project != null && project.getFilePath() != null && !project.getFilePath().isBlank()) {
            onProjectLoaded(project.getFilePath(), onLoaded, onSelectionReset);
            return;
        }
        Path file = getNetworksDir().resolve(DEFAULT_NETWORK_FILE);
        if (loadNetworkFile(file, onLoaded, onSelectionReset)) {
            currentNetworkFile = DEFAULT_NETWORK_FILE;
            status.info(PlotI18n.tr("plugin.road.network.default_loaded"));
        }
    }

    public void onProjectLoaded(
            String filePath,
            Consumer<RoadNetwork> onLoaded,
            Runnable onSelectionReset) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String targetFile = ProjectPathResolver.sidecarFileName(filePath);
        Path file = getNetworksDir().resolve(targetFile);
        // 仅在加载成功后才绑定 currentNetworkFile，避免失败时把旧路网写进新工程文件
        if (loadNetworkFile(file, onLoaded, onSelectionReset)) {
            currentNetworkFile = targetFile;
            status.success(PlotI18n.tr("plugin.road.network.loaded", filePath));
        }
    }

    public void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentNetworkFile = ProjectPathResolver.sidecarFileName(filePath);
        status.success(PlotI18n.tr("plugin.road.network.saved", filePath));
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
            history.clear();
            onSelectionReset.run();
            return loaded;
        } catch (IOException e) {
            LOGGER.error("加载道路网络失败: {}", e.getMessage(), e);
            status.error(PlotI18n.tr("plugin.road.network.load_failed", file.getFileName()));
            return new RoadNetwork();
        }
    }

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
            status.error(PlotI18n.tr("plugin.road.network.load_failed", file.getFileName()));
            return false;
        }
    }

    public boolean saveNetworkFile(Path file, RoadNetwork network) {
        if (network == null || file == null) {
            return false;
        }
        try {
            String json = network.toJson();
            if (contentFingerprint.isUnchanged(json, file)) {
                LOGGER.debug("路网内容未变，跳过重复保存: {}", file.getFileName());
                return true;
            }
            network.saveTo(file);
            contentFingerprint.markSaved(json, file);
            return true;
        } catch (IOException e) {
            LOGGER.error("保存道路网络失败: {}", e.getMessage(), e);
            status.error(PlotI18n.tr(
                "plugin.road.network.save_failed",
                file.getFileName()));
            return false;
        }
    }

    public Path getNetworksDir() {
        return dataFolder.toPath().resolve("networks");
    }
}
