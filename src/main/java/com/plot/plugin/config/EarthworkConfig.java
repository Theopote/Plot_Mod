package com.plot.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.core.log.LogManager;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.model.EarthworkWorkMode;
import com.plot.plugin.earthwork.model.GradingRegion;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * 土方平衡插件配置
 */
public class EarthworkConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String pluginId;

    // 网格设置
    private int previewGridSize = GradingRegion.DEFAULT_PREVIEW_GRID_SIZE;
    /** @deprecated 旧 JSON 字段 */
    @Deprecated
    private int gridSize;
    private boolean showGrid = true;
    private boolean showEdgeTreatmentOverlay = true;

    /** Quick / Builder / Learn。缺省 Quick。 */
    private String workMode = EarthworkWorkMode.QUICK.name();

    // 计算设置
    private boolean autoBalance = true;
    private float targetElevation = 0.0f;
    /** 新格式材料参数；为 0 时表示 JSON 未写入，回退 legacy {@link #fillFactor} 或默认值。 */
    private float reusableRatio;
    private float cutToCompactedFillRatio;
    /** @deprecated 旧版填方松散系数，仅用于读取 legacy 配置。 */
    @Deprecated
    private float fillFactor;

    // 统计数据
    private float cutVolume = 0.0f;
    private float fillVolume = 0.0f;

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
            LogManager.getInstance().error("Failed to create config directory", e);
        }
        return configDir;
    }

    /**
     * 加载配置
     */
    public static <T extends EarthworkConfig> T load(Class<T> configClass, String pluginId) {
        Path configPath = getConfigDirectory().resolve(pluginId + ".json");
        if (Files.exists(configPath)) {
            try {
                String json = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
                return GSON.fromJson(json, configClass);
            } catch (IOException e) {
                LogManager.getInstance().error("Failed to load config: " + configPath, e);
            }
        }
        return null;
    }

    /**
     * 保存配置
     */
    public void save() {
        try {
            Path configPath = resolveConfigPath();
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(this);
            Files.write(configPath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to save config: " + resolveConfigPath(), e);
        }
    }

    // Getters and setters
    public int getPreviewGridSize() {
        if (previewGridSize > 0) {
            return previewGridSize;
        }
        if (gridSize > 0) {
            return gridSize;
        }
        return GradingRegion.DEFAULT_PREVIEW_GRID_SIZE;
    }

    public void setPreviewGridSize(int previewGridSize) {
        this.previewGridSize = Math.max(1, Math.min(20, previewGridSize));
        this.gridSize = 0;
    }

    /** @deprecated 请改用 {@link #getPreviewGridSize()} */
    @Deprecated
    public int getGridSize() {
        return getPreviewGridSize();
    }

    /** @deprecated 请改用 {@link #setPreviewGridSize(int)} */
    @Deprecated
    public void setGridSize(int gridSize) {
        setPreviewGridSize(gridSize);
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

    public boolean isAutoBalance() {
        return autoBalance;
    }

    public void setAutoBalance(boolean autoBalance) {
        this.autoBalance = autoBalance;
    }

    public float getTargetElevation() {
        return targetElevation;
    }

    public void setTargetElevation(float targetElevation) {
        this.targetElevation = targetElevation;
    }

    public MaterialConversionModel getDefaultMaterialProperties() {
        if (reusableRatio > 0.0f && cutToCompactedFillRatio > 0.0f) {
            return new MaterialConversionModel(reusableRatio, cutToCompactedFillRatio);
        }
        if (fillFactor >= 1.0f) {
            return MaterialConversionModel.fromLegacyFillFactor(fillFactor);
        }
        return MaterialConversionModel.DEFAULT;
    }

    public void setDefaultMaterialProperties(MaterialConversionModel materialProperties) {
        if (materialProperties == null) {
            reusableRatio = 0.0f;
            cutToCompactedFillRatio = 0.0f;
            fillFactor = 0.0f;
            return;
        }
        reusableRatio = materialProperties.reusableRatio();
        cutToCompactedFillRatio = materialProperties.cutToCompactedFillRatio();
        fillFactor = 0.0f;
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

    /** @deprecated 请改用 {@link #getDefaultMaterialProperties()}。 */
    @Deprecated
    public float getFillFactor() {
        MaterialConversionModel properties = getDefaultMaterialProperties();
        return 1.0f / Math.max(0.01f, properties.cutToCompactedFillRatio());
    }

    /** @deprecated 请改用 {@link #setDefaultMaterialProperties(MaterialConversionModel)}。 */
    @Deprecated
    public void setFillFactor(float fillFactor) {
        MaterialConversionModel migrated = MaterialConversionModel.fromLegacyFillFactor(fillFactor);
        setDefaultMaterialProperties(migrated);
    }

    public float getCutVolume() {
        return cutVolume;
    }

    public void setCutVolume(float cutVolume) {
        this.cutVolume = cutVolume;
    }

    public float getFillVolume() {
        return fillVolume;
    }

    public void setFillVolume(float fillVolume) {
        this.fillVolume = fillVolume;
    }
} 