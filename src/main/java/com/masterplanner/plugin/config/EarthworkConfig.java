package com.masterplanner.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.masterplanner.core.log.LogManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * 土方平衡插件配置
 */
public class EarthworkConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String pluginId;
    private final Path configPath;

    // 网格设置
    private int gridSize = 5;
    private boolean showGrid = true;

    // 计算设置
    private boolean autoBalance = true;
    private float targetElevation = 0.0f;
    private float fillFactor = 1.1f;

    // 统计数据
    private float cutVolume = 0.0f;
    private float fillVolume = 0.0f;

    public EarthworkConfig(String pluginId) {
        this.pluginId = pluginId;
        this.configPath = getConfigDirectory().resolve(pluginId + ".json");
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
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(this);
            Files.write(configPath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to save config: " + configPath, e);
        }
    }

    // Getters and setters
    public int getGridSize() {
        return gridSize;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
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

    public float getFillFactor() {
        return fillFactor;
    }

    public void setFillFactor(float fillFactor) {
        this.fillFactor = fillFactor;
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