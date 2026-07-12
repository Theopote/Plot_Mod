package com.plot.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.core.log.LogManager;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.style.RoadStyleCatalog;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 道路系统插件配置
 */
public class RoadSystemConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private String pluginId;

    private int roadWidth = 5;
    private boolean includeSidewalk = true;
    private int sidewalkWidth = 1;
    private String selectedMaterial = RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
    private String selectedSidewalkMaterial = RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
    private String selectedPreset = "";
    private List<RoadStyle> presets;
    
    // 新增参数
    private float maxSlope = 10.0f; // 最大坡度（百分比）
    private int bridgeThreshold = 5; // 桥阈值（方块高度差）
    private int tunnelThreshold = 8; // 隧道阈值（方块高度差）
    private boolean includeShoulder = false; // 是否包含路肩
    private int shoulderWidth = 1; // 路肩宽度
    private boolean includeDrainage = false; // 是否包含排水沟
    private double pathLength = 0.0; // 当前路径长度（米）

    private float fillSlopeRatio = 1.5f;
    private float cutSlopeRatio = 1.0f;
    private String fillSlopeMaterial = "material.plot.gravel";
    private String cutSlopeMaterial = "";
    private double maxContinuousSlopeLength = 30.0;
    private double relaxedSlopeLength = 5.0;
    private float relaxedSlopePercent = 1.0f;
    private float defaultCornerRadius = (float) RoadNode.DEFAULT_CORNER_RADIUS;

    public RoadSystemConfig(String pluginId) {
        this.pluginId = pluginId;
        initDefaultPresets();
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
    public static <T extends RoadSystemConfig> T load(Class<T> configClass, String pluginId) {
        Path configPath = getConfigDirectory().resolve(pluginId + ".json");
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                T config = GSON.fromJson(json, configClass);
                if (config != null) {
                    applyLoadedState(config, pluginId);
                }
                return config;
            } catch (IOException e) {
                LogManager.getInstance().error("Failed to load config: " + configPath, e);
            }
        }
        return null;
    }

    private static void applyLoadedState(RoadSystemConfig config, String pluginId) {
        if (config.pluginId == null) {
            config.pluginId = pluginId;
        }
        if (config.presets == null) {
            config.initDefaultPresets();
        } else {
            mergeMissingBuiltinStyles(config);
        }
        String normalizedMaterial = RoadMaterialUtils.normalizeStoredMaterial(config.selectedMaterial);
        config.selectedMaterial = normalizedMaterial != null
            ? normalizedMaterial
            : RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
        String normalizedSidewalk = RoadMaterialUtils.normalizeStoredMaterial(config.selectedSidewalkMaterial);
        config.selectedSidewalkMaterial = normalizedSidewalk != null
            ? normalizedSidewalk
            : config.selectedMaterial;
    }

    private static void mergeMissingBuiltinStyles(RoadSystemConfig config) {
        Map<String, RoadStyle> existing = RoadStyleCatalog.indexById(config.presets);
        for (RoadStyle builtin : RoadStyleCatalog.defaultStyles()) {
            if (!existing.containsKey(builtin.id)) {
                config.presets.add(builtin);
            }
        }
    }

    /**
     * 保存配置
     */
    public void save() {
        try {
            Path configPath = resolveConfigPath();
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(this);
            Files.writeString(configPath, json);
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to save config: " + resolveConfigPath(), e);
        }
    }

    private void initDefaultPresets() {
        presets = new ArrayList<>(RoadStyleCatalog.defaultStyles());
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

    public String getSelectedSidewalkMaterial() {
        return selectedSidewalkMaterial != null ? selectedSidewalkMaterial : selectedMaterial;
    }

    public void setSelectedSidewalkMaterial(String selectedSidewalkMaterial) {
        this.selectedSidewalkMaterial = selectedSidewalkMaterial;
    }

    public String getSelectedPreset() {
        return selectedPreset;
    }

    public void setSelectedPreset(String selectedPreset) {
        this.selectedPreset = selectedPreset != null ? selectedPreset : "";
    }

    public void markCustom() {
        this.selectedPreset = "";
    }

    public List<RoadStyle> getStyles() {
        return presets;
    }

    public RoadStyle findStyle(String styleId) {
        return RoadStyleCatalog.findById(this, styleId);
    }

    /** @deprecated 使用 {@link #getStyles()} */
    @Deprecated
    public List<RoadStyle> getPresets() {
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

    public float getFillSlopeRatio() {
        return fillSlopeRatio;
    }

    public void setFillSlopeRatio(float fillSlopeRatio) {
        this.fillSlopeRatio = Math.max(0.5f, Math.min(5.0f, fillSlopeRatio));
    }

    public float getCutSlopeRatio() {
        return cutSlopeRatio;
    }

    public void setCutSlopeRatio(float cutSlopeRatio) {
        this.cutSlopeRatio = Math.max(0.5f, Math.min(5.0f, cutSlopeRatio));
    }

    public String getFillSlopeMaterial() {
        return fillSlopeMaterial;
    }

    public void setFillSlopeMaterial(String fillSlopeMaterial) {
        this.fillSlopeMaterial = fillSlopeMaterial != null ? fillSlopeMaterial : "";
    }

    public String getCutSlopeMaterial() {
        return cutSlopeMaterial;
    }

    public void setCutSlopeMaterial(String cutSlopeMaterial) {
        this.cutSlopeMaterial = cutSlopeMaterial != null ? cutSlopeMaterial : "";
    }

    public double getMaxContinuousSlopeLength() {
        return maxContinuousSlopeLength;
    }

    public void setMaxContinuousSlopeLength(double maxContinuousSlopeLength) {
        this.maxContinuousSlopeLength = Math.max(0.0, maxContinuousSlopeLength);
    }

    public double getRelaxedSlopeLength() {
        return relaxedSlopeLength;
    }

    public void setRelaxedSlopeLength(double relaxedSlopeLength) {
        this.relaxedSlopeLength = Math.max(0.0, relaxedSlopeLength);
    }

    public float getRelaxedSlopePercent() {
        return relaxedSlopePercent;
    }

    public void setRelaxedSlopePercent(float relaxedSlopePercent) {
        float upperBound = maxSlope > 0 ? maxSlope : 45.0f;
        if (relaxedSlopePercent >= upperBound) {
            relaxedSlopePercent = upperBound / 2.0f;
        }
        this.relaxedSlopePercent = Math.max(0.0f, relaxedSlopePercent);
    }

    public float getDefaultCornerRadius() {
        return defaultCornerRadius;
    }

    public void setDefaultCornerRadius(float defaultCornerRadius) {
        this.defaultCornerRadius = (float) Math.max(
            RoadNode.MIN_CORNER_RADIUS,
            Math.min(RoadNode.MAX_CORNER_RADIUS, defaultCornerRadius)
        );
    }
    
    /**
     * 应用道路风格到全局默认参数。
     */
    public void applyStyle(RoadStyle style) {
        if (style == null) {
            return;
        }
        String resolvedRoadMaterial = style.roadMaterial != null && !style.roadMaterial.isBlank()
            ? style.roadMaterial
            : RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
        String resolvedSidewalkMaterial = style.sidewalkMaterial != null && !style.sidewalkMaterial.isBlank()
            ? style.sidewalkMaterial
            : resolvedRoadMaterial;
        this.roadWidth = style.width;
        this.includeSidewalk = style.hasSidewalk;
        this.sidewalkWidth = style.hasSidewalk ? Math.max(1, style.sidewalkWidth) : sidewalkWidth;
        this.includeShoulder = style.includeShoulder;
        this.shoulderWidth = style.shoulderWidth;
        this.includeDrainage = style.includeDrainage;
        if (style.maxSlope > 0f) {
            this.maxSlope = style.maxSlope;
        }
        if (style.fillSlopeRatio > 0f) {
            this.fillSlopeRatio = style.fillSlopeRatio;
        }
        if (style.cutSlopeRatio > 0f) {
            this.cutSlopeRatio = style.cutSlopeRatio;
        }
        if (style.fillSlopeMaterial != null && !style.fillSlopeMaterial.isBlank()) {
            this.fillSlopeMaterial = style.fillSlopeMaterial;
        }
        this.selectedMaterial = resolvedRoadMaterial;
        this.selectedSidewalkMaterial = resolvedSidewalkMaterial;
        this.selectedPreset = style.id;
    }

    /** @deprecated 使用 {@link #applyStyle(RoadStyle)} */
    @Deprecated
    public void applyPreset(RoadStyle style) {
        applyStyle(style);
    }

    /**
     * @deprecated Gson 兼容；新代码请使用 {@link RoadStyle}。
     */
    @Deprecated
    public static class RoadPreset extends RoadStyle {
        public RoadPreset(
                String id,
                int width,
                boolean hasSidewalk,
                int sidewalkWidth,
                boolean includeShoulder,
                int shoulderWidth,
                boolean includeDrainage,
                float maxSlope,
                String roadMaterial,
                String sidewalkMaterial) {
            this.id = id;
            this.name = id;
            this.width = width;
            this.hasSidewalk = hasSidewalk;
            this.sidewalkWidth = sidewalkWidth;
            this.includeShoulder = includeShoulder;
            this.shoulderWidth = shoulderWidth;
            this.includeDrainage = includeDrainage;
            this.maxSlope = maxSlope;
            this.roadMaterial = roadMaterial;
            this.sidewalkMaterial = sidewalkMaterial;
        }

        /** @deprecated Gson 反序列化兼容 */
        @Deprecated
        public RoadPreset(String id, String name, int width, boolean hasSidewalk, int sidewalkWidth) {
            this(id, width, hasSidewalk, sidewalkWidth, false, 0, false, 10.0f, null, null);
            this.name = name;
        }
    }
}
