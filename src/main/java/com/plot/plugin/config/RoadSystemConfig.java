package com.plot.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.core.log.LogManager;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 道路系统插件配置
 */
public class RoadSystemConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;

    private int roadWidth = 5;
    private boolean includeSidewalk = true;
    private int sidewalkWidth = 1;
    private String selectedMaterial = "混凝土";
    private String selectedPreset = "";
    private List<RoadPreset> presets;
    
    // 新增参数
    private float maxSlope = 10.0f; // 最大坡度（百分比）
    private int bridgeThreshold = 5; // 桥阈值（方块高度差）
    private int tunnelThreshold = 8; // 隧道阈值（方块高度差）
    private boolean includeShoulder = false; // 是否包含路肩
    private int shoulderWidth = 1; // 路肩宽度
    private boolean includeDrainage = false; // 是否包含排水沟
    private double pathLength = 0.0; // 当前路径长度（米）

    public RoadSystemConfig(String pluginId) {
        this.configPath = getConfigDirectory().resolve(pluginId + ".json");
        initDefaultPresets();
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
    public static <T extends RoadSystemConfig> T load(Class<T> configClass, String pluginId) {
        Path configPath = getConfigDirectory().resolve(pluginId + ".json");
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
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
            Files.writeString(configPath, json);
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to save config: " + configPath, e);
        }
    }

    private void initDefaultPresets() {
        presets = new ArrayList<>();
        presets.add(new RoadPreset("city_main", "城市主干道", 7, true, 2));
        presets.add(new RoadPreset("city_secondary", "城市次干道", 5, true, 1));
        presets.add(new RoadPreset("country_road", "乡村公路", 3, false, 0));
        presets.add(new RoadPreset("highway", "高速公路", 9, false, 0));
    }

    public int getRoadWidth() {
        return roadWidth;
    }

    public void setRoadWidth(int roadWidth) {
        this.roadWidth = roadWidth;
    }

    public boolean isIncludeSidewalk() {
        return includeSidewalk;
    }

    public void setIncludeSidewalk(boolean includeSidewalk) {
        this.includeSidewalk = includeSidewalk;
    }

    public int getSidewalkWidth() {
        return sidewalkWidth;
    }

    public void setSidewalkWidth(int sidewalkWidth) {
        this.sidewalkWidth = sidewalkWidth;
    }

    public String getSelectedMaterial() {
        return selectedMaterial;
    }

    public void setSelectedMaterial(String selectedMaterial) {
        this.selectedMaterial = selectedMaterial;
    }

    public String getSelectedPreset() {
        return selectedPreset;
    }

    public void setSelectedPreset(String selectedPreset) {
        this.selectedPreset = selectedPreset;
    }

    public List<RoadPreset> getPresets() {
        return presets;
    }
    
    public float getMaxSlope() {
        return maxSlope;
    }
    
    public void setMaxSlope(float maxSlope) {
        this.maxSlope = Math.max(0.0f, Math.min(45.0f, maxSlope));
    }
    
    public int getBridgeThreshold() {
        return bridgeThreshold;
    }
    
    public void setBridgeThreshold(int bridgeThreshold) {
        this.bridgeThreshold = Math.max(1, Math.min(20, bridgeThreshold));
    }
    
    public int getTunnelThreshold() {
        return tunnelThreshold;
    }
    
    public void setTunnelThreshold(int tunnelThreshold) {
        this.tunnelThreshold = Math.max(1, Math.min(30, tunnelThreshold));
    }
    
    public boolean isIncludeShoulder() {
        return includeShoulder;
    }
    
    public void setIncludeShoulder(boolean includeShoulder) {
        this.includeShoulder = includeShoulder;
    }
    
    public int getShoulderWidth() {
        return shoulderWidth;
    }
    
    public void setShoulderWidth(int shoulderWidth) {
        this.shoulderWidth = Math.max(0, Math.min(3, shoulderWidth));
    }
    
    public boolean isIncludeDrainage() {
        return includeDrainage;
    }
    
    public void setIncludeDrainage(boolean includeDrainage) {
        this.includeDrainage = includeDrainage;
    }
    
    public double getPathLength() {
        return pathLength;
    }
    
    public void setPathLength(double pathLength) {
        this.pathLength = Math.max(0.0, pathLength);
    }
    
    /**
     * 应用预设配置
     */
    public void applyPreset(RoadPreset preset) {
        if (preset == null) return;
        this.roadWidth = preset.width;
        this.includeSidewalk = preset.hasSidewalk;
        this.sidewalkWidth = preset.sidewalkWidth;
        this.selectedPreset = preset.id;
    }

    /**
     * 道路预设
     */
    public static class RoadPreset {
        public final String id;
        public final String name;
        public final int width;
        public final boolean hasSidewalk;
        public final int sidewalkWidth;

        public RoadPreset(String id, String name, int width, boolean hasSidewalk, int sidewalkWidth) {
            this.id = id;
            this.name = name;
            this.width = width;
            this.hasSidewalk = hasSidewalk;
            this.sidewalkWidth = sidewalkWidth;
        }
    }
}
