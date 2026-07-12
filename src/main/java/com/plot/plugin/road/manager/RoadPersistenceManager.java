package com.plot.plugin.road.manager;

import com.plot.core.model.Project;
import com.plot.core.state.AppState;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkHistory;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
        currentNetworkFile = DEFAULT_NETWORK_FILE;
        loadNetworkFile(getNetworksDir().resolve(currentNetworkFile), onLoaded, onSelectionReset);
        status.set(PlotI18n.tr("plugin.road.network.default_loaded"));
    }

    public void onProjectLoaded(
            String filePath,
            Consumer<RoadNetwork> onLoaded,
            Runnable onSelectionReset) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentNetworkFile = hashPath(filePath) + ".json";
        loadNetworkFile(getNetworksDir().resolve(currentNetworkFile), onLoaded, onSelectionReset);
        status.set(PlotI18n.tr("plugin.road.network.loaded", filePath));
    }

    public void onProjectSaved(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        currentNetworkFile = hashPath(filePath) + ".json";
        status.set(PlotI18n.tr("plugin.road.network.saved", filePath));
    }

    public void saveCurrentNetwork(RoadNetwork network) {
        saveNetworkFile(getCurrentNetworkPath(), network);
        status.set(PlotI18n.tr("plugin.road.network.manual_saved"));
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
            status.set(PlotI18n.tr("plugin.road.network.load_failed", file.getFileName()));
            return new RoadNetwork();
        }
    }

    private void loadNetworkFile(
            Path file,
            Consumer<RoadNetwork> onLoaded,
            Runnable onSelectionReset) {
        try {
            RoadNetwork loaded = RoadNetwork.loadFrom(file);
            onLoaded.accept(loaded);
            onSelectionReset.run();
        } catch (IOException e) {
            LOGGER.error("加载道路网络失败: {}", e.getMessage(), e);
            status.set(PlotI18n.tr("plugin.road.network.load_failed", file.getFileName()));
        }
    }

    public void saveNetworkFile(Path file, RoadNetwork network) {
        try {
            network.saveTo(file);
        } catch (IOException e) {
            LOGGER.error("保存道路网络失败: {}", e.getMessage(), e);
        }
    }

    public Path getNetworksDir() {
        return dataFolder.toPath().resolve("networks");
    }

    private static String hashPath(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(filePath.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return DEFAULT_NETWORK_FILE.replace(".json", "");
        }
    }
}
