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
 * 土方插件<strong>全局偏好</strong>与<strong>认领默认值</strong>（{@code config/plugins/earthwork_balance.json}）。
 * <p>
 * 字段分类见 {@link EarthworkConfigInventory}。预览方量、解析标高、区域平衡状态属于
 * {@link com.plot.plugin.earthwork.model.EarthworkProject} / {@link com.plot.plugin.earthwork.model.GradingRegion}，
 * 不得写入本文件。
 */
public class EarthworkConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String pluginId;

    // --- Plugin preferences (PLUGIN_PREFERENCE) ---
    private String workMode = EarthworkWorkMode.QUICK.name();
    private boolean showGrid = true;
    private boolean showEdgeTreatmentOverlay = true;

    // --- Adopt defaults (ADOPT_DEFAULT) — copied into GradingRegion on adopt only ---
    private int previewGridSize = GradingRegion.DEFAULT_PREVIEW_GRID_SIZE;
    /** @deprecated 旧 JSON 字段 */
    @Deprecated
    private int gridSize;
    private boolean autoBalance = true;
    private float reusableRatio;
    private float cutToCompactedFillRatio;
    /** @deprecated 旧版填方松散系数，仅用于读取 legacy 配置。 */
    @Deprecated
    private float fillFactor;

    // --- Runtime legacy (RUNTIME_LEGACY) — loaded for migration only, never saved ---
    /** @deprecated 误放的全局手动标高；认领已改用地形采样，见 {@link EarthworkConfigInventory#TARGET_ELEVATION}。 */
    @Deprecated
    private transient float targetElevation;
    /** @deprecated 误放的预览挖方缓存。 */
    @Deprecated
    private transient float cutVolume;
    /** @deprecated 误放的预览填方缓存。 */
    @Deprecated
    private transient float fillVolume;

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
     * 加载配置并在内存中剥离误放的运行时字段。
     */
    public static <T extends EarthworkConfig> T load(Class<T> configClass, String pluginId) {
        Path configPath = getConfigDirectory().resolve(pluginId + ".json");
        if (Files.exists(configPath)) {
            try {
                String json = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
                T loaded = GSON.fromJson(json, configClass);
                if (loaded != null) {
                    loaded.normalizeAfterLoad();
                }
                return loaded;
            } catch (IOException e) {
                LogManager.getInstance().error("Failed to load config: " + configPath, e);
            }
        }
        return null;
    }

    /**
     * 保存配置（不含 RUNTIME_LEGACY 字段）。
     */
    public void save() {
        stripRuntimeLegacyFields();
        try {
            Path configPath = resolveConfigPath();
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(this);
            Files.write(configPath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to save config: " + resolveConfigPath(), e);
        }
    }

    /**
     * 丢弃误放在 config 中的预览/标高缓存，避免下次 save 写回磁盘。
     */
    public void normalizeAfterLoad() {
        stripRuntimeLegacyFields();
    }

    private void stripRuntimeLegacyFields() {
        targetElevation = 0.0f;
        cutVolume = 0.0f;
        fillVolume = 0.0f;
    }

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

    /** 认领新区块时是否默认开启自动平衡。 */
    public boolean isAdoptDefaultAutoBalance() {
        return autoBalance;
    }

    /** @deprecated 请改用 {@link #isAdoptDefaultAutoBalance()} */
    @Deprecated
    public boolean isAutoBalance() {
        return isAdoptDefaultAutoBalance();
    }

    public void setAdoptDefaultAutoBalance(boolean autoBalance) {
        this.autoBalance = autoBalance;
    }

    /** @deprecated 请改用 {@link #setAdoptDefaultAutoBalance(boolean)} */
    @Deprecated
    public void setAutoBalance(boolean autoBalance) {
        setAdoptDefaultAutoBalance(autoBalance);
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

    /**
     * @deprecated 运行时状态，已移出 config；认领改用地形采样。
     */
    @Deprecated
    public float getTargetElevation() {
        return targetElevation;
    }

    /**
     * @deprecated 运行时状态，已移出 config。
     */
    @Deprecated
    public void setTargetElevation(float targetElevation) {
        this.targetElevation = targetElevation;
    }

    /**
     * @deprecated 预览运行时挖方，已移出 config。
     */
    @Deprecated
    public float getCutVolume() {
        return cutVolume;
    }

    /**
     * @deprecated 预览运行时挖方，已移出 config。
     */
    @Deprecated
    public void setCutVolume(float cutVolume) {
        this.cutVolume = cutVolume;
    }

    /**
     * @deprecated 预览运行时填方，已移出 config。
     */
    @Deprecated
    public float getFillVolume() {
        return fillVolume;
    }

    /**
     * @deprecated 预览运行时填方，已移出 config。
     */
    @Deprecated
    public void setFillVolume(float fillVolume) {
        this.fillVolume = fillVolume;
    }
}
