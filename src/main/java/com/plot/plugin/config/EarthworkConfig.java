package com.plot.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.core.material.MaterialConversionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.model.GradingRegion;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * 土方插件<strong>全局偏好</strong>与<strong>认领默认值</strong>（{@code config/plugins/earthwork_balance.json}）。
 * <p>
 * 字段分类见 {@link EarthworkConfigInventory}。预览方量、解析标高、区域平衡状态属于
 * {@link com.plot.plugin.earthwork.model.EarthworkProject} / {@link com.plot.plugin.earthwork.model.GradingRegion}，
 * 不得写入本文件。
 */
public class EarthworkConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EarthworkConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String pluginId;

    // --- Plugin preferences (PLUGIN_PREFERENCE) ---
    private String workMode = EarthworkWorkMode.QUICK.name();
    private boolean showGrid = true;
    private boolean showEdgeTreatmentOverlay = true;

    // --- Adopt defaults (ADOPT_DEFAULT) — copied into GradingRegion on adopt only ---
    private int previewGridSize = GradingRegion.DEFAULT_PREVIEW_GRID_SIZE;
    private boolean autoBalance = true;
    private float reusableRatio;
    private float cutToCompactedFillRatio;

    public EarthworkConfig(String pluginId) {
        this.pluginId = pluginId;
    }

    private Path resolveConfigPath() {
        return getConfigDirectory().resolve(pluginId + ".json");
    }

    private static Path getConfigDirectory() {
        Path configDir = Paths.get("config", "plugins");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }
        return configDir;
    }

    public static <T extends EarthworkConfig> T load(Class<T> configClass, String pluginId) {
        Path configPath = getConfigDirectory().resolve(pluginId + ".json");
        if (Files.exists(configPath)) {
            try {
                String json = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
                return GSON.fromJson(json, configClass);
            } catch (IOException e) {
                LOGGER.error("Failed to load config: {}", configPath, e);
            }
        }
        return null;
    }

    public void save() {
        try {
            Path configPath = resolveConfigPath();
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(this);
            Files.write(configPath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("Failed to save config: {}", resolveConfigPath(), e);
        }
    }

    public int getPreviewGridSize() {
        if (previewGridSize > 0) {
            return previewGridSize;
        }
        return GradingRegion.DEFAULT_PREVIEW_GRID_SIZE;
    }

    public void setPreviewGridSize(int previewGridSize) {
        this.previewGridSize = Math.max(1, Math.min(20, previewGridSize));
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    public boolean isShowEdgeTreatmentOverlay() {
        return showEdgeTreatmentOverlay;
    }

    public void setShowEdgeTreatmentOverlay(boolean showEdgeTreatmentOverlay) {
        this.showEdgeTreatmentOverlay = showEdgeTreatmentOverlay;
    }

    public EarthworkWorkMode getWorkMode() {
        return EarthworkWorkMode.fromId(workMode);
    }

    public void setWorkMode(EarthworkWorkMode workMode) {
        this.workMode = (workMode != null ? workMode : EarthworkWorkMode.QUICK).name();
    }

    /** 认领新区块时是否默认开启自动平衡。 */
    public boolean isAdoptDefaultAutoBalance() {
        return autoBalance;
    }

    public void setAdoptDefaultAutoBalance(boolean autoBalance) {
        this.autoBalance = autoBalance;
    }

    public MaterialConversionModel getDefaultMaterialProperties() {
        if (reusableRatio > 0.0f && cutToCompactedFillRatio > 0.0f) {
            return new MaterialConversionModel(reusableRatio, cutToCompactedFillRatio);
        }
        return MaterialConversionModel.DEFAULT;
    }

    public void setDefaultMaterialProperties(MaterialConversionModel materialProperties) {
        if (materialProperties == null) {
            reusableRatio = 0.0f;
            cutToCompactedFillRatio = 0.0f;
            return;
        }
        reusableRatio = materialProperties.reusableRatio();
        cutToCompactedFillRatio = materialProperties.cutToCompactedFillRatio();
    }

    public float getReusableRatio() {
        return getDefaultMaterialProperties().reusableRatio();
    }

    public void setReusableRatio(float reusableRatio) {
        this.reusableRatio = reusableRatio;
    }

    public float getCutToCompactedFillRatio() {
        return getDefaultMaterialProperties().cutToCompactedFillRatio();
    }

    public void setCutToCompactedFillRatio(float cutToCompactedFillRatio) {
        this.cutToCompactedFillRatio = cutToCompactedFillRatio;
    }
}
