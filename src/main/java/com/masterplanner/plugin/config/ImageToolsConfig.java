package com.masterplanner.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.masterplanner.core.log.LogManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 图片工具插件配置
 */
public class ImageToolsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String pluginId;
    private final Path configPath;

    // 图片设置
    private float brightness = 100.0f;
    private float contrast = 100.0f;
    private float scale = 100.0f;
    private float rotation = 0.0f;
    private boolean keepAspectRatio = true;

    // 预设和历史记录
    private List<ImagePreset> presets;
    private List<RecentImage> recentImages;

    public ImageToolsConfig(String pluginId) {
        this.pluginId = pluginId;
        this.configPath = getConfigDirectory().resolve(pluginId + ".json");
        initDefaultPresets();
    }

    private void initDefaultPresets() {
        presets = new ArrayList<>();
        presets.add(new ImagePreset("normal", "标准", new ImageEffect(100, 100)));
        presets.add(new ImagePreset("bright", "明亮", new ImageEffect(120, 110)));
        presets.add(new ImagePreset("dark", "暗调", new ImageEffect(80, 90)));
        presets.add(new ImagePreset("high_contrast", "高对比度", new ImageEffect(100, 150)));
        presets.add(new ImagePreset("low_contrast", "低对比度", new ImageEffect(100, 50)));

        recentImages = new ArrayList<>();
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
    public static <T extends ImageToolsConfig> T load(Class<T> configClass, String pluginId) {
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
    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public float getContrast() {
        return contrast;
    }

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public boolean isKeepAspectRatio() {
        return keepAspectRatio;
    }

    public void setKeepAspectRatio(boolean keepAspectRatio) {
        this.keepAspectRatio = keepAspectRatio;
    }

    public List<ImagePreset> getPresets() {
        return presets;
    }

    public List<RecentImage> getRecentImages() {
        return recentImages;
    }

    /**
     * 图片预设
     */
    public static class ImagePreset {
        public final String id;
        public final String name;
        public final ImageEffect effect;

        public ImagePreset(String id, String name, ImageEffect effect) {
            this.id = id;
            this.name = name;
            this.effect = effect;
        }
    }

    /**
     * 图片效果
     */
    public static class ImageEffect {
        public final float brightness;
        public final float contrast;

        public ImageEffect(float brightness, float contrast) {
            this.brightness = brightness;
            this.contrast = contrast;
        }
    }

    /**
     * 最近使用的图片
     */
    public static class RecentImage {
        public final String path;
        public final long lastUsed;

        public RecentImage(String path) {
            this.path = path;
            this.lastUsed = System.currentTimeMillis();
        }
    }
}
