package com.plot.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.plot.core.log.LogManager;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;
import com.plot.core.persistence.AtomicFileWriter;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.style.RoadStyleCatalog;
import com.plot.plugin.road.style.RoadThemeCatalog;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 道路系统插件配置
 */
public class RoadSystemConfig {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();
    private static final String DEFAULT_FILL_SLOPE_MATERIAL = "material.plot.gravel";
    private String pluginId;

    private int roadWidth = 5;
    private boolean includeSidewalk = true;
    private int sidewalkWidth = 1;
    private MaterialMix selectedMaterial = MaterialMix.single(RoadMaterialUtils.DEFAULT_ROAD_BLOCK);
    private String selectedSidewalkMaterial = RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
    private String selectedPreset = "";
    private String roadThemeId = RoadThemeCatalog.MODERN_ID;
    private List<RoadStyle> presets;
    
    // 新增参数
    private float maxSlope = 10.0f; // 最大坡度（百分比）
    private RoadTerrainStyle terrainStyle = RoadTerrainStyle.BALANCED;
    private boolean generateBridgePillars = true;
    private boolean includeBridgeGuardrail = false;
    private String bridgeGuardrailMaterial = "minecraft:oak_fence";
    private TunnelStyle tunnelStyle = new TunnelStyle();
    /** Legacy JSON fields; migrated into {@link #tunnelStyle} on load. */
    private String tunnelLiningMaterial;
    private String tunnelAccentMaterial;
    private int tunnelAccentSpacing;
    private boolean includeShoulder = true; // 是否包含路肩 - 从false改为true，默认启用路肩和边坡填充
    private int shoulderWidth = 1; // 路肩宽度
    private int laneCount = 1;
    private List<Integer> laneWidths = new ArrayList<>();
    private boolean includeBikeLane = false;
    private int bikeLaneWidth = 1;
    private boolean includeMedian = false;
    private int medianWidth = 1;
    private boolean laneDividers = false;
    private String centerLineStyle = CenterLineStyle.NONE.name();
    private String markingMaterial = "material.plot.white_concrete";
    private int streetlightSpacing = 0;
    private boolean includeSlopeBatter = true;
    private double pathLength = 0.0; // 当前路径长度（米）
    private double pathSampleDistance = 1.0; // 路径采样密度（米/点），用于控制路径细分精度

    private float fillSlopeRatio = 1.5f;
    private float cutSlopeRatio = 1.0f;
    private String fillSlopeMaterial = DEFAULT_FILL_SLOPE_MATERIAL;
    private String cutSlopeMaterial = "";
    /** AUTO_SMOOTH v1 preferred continuous grade run; 300 m remains a manual-design warning. */
    private double maxContinuousSlopeLength = 180.0;
    private double relaxedSlopeLength = 5.0;
    private float relaxedSlopePercent = 1.0f;
    private float defaultCornerRadius = (float) RoadNode.DEFAULT_CORNER_RADIUS;
    private double defaultCrossingClearance = 3.0;

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
        return loadFrom(getConfigDirectory().resolve(pluginId + ".json"), configClass, pluginId);
    }

    static <T extends RoadSystemConfig> T loadFrom(Path configPath, Class<T> configClass, String pluginId) {
        if (!Files.exists(configPath)) {
            return null;
        }
        try {
            String json = Files.readString(configPath);
            T config = GSON.fromJson(json, configClass);
            if (config != null) {
                applyLoadedState(config, pluginId);
            }
            return config;
        } catch (IOException | JsonParseException e) {
            LogManager.getInstance().error("Failed to load config: " + configPath, e);
        }
        return null;
    }

    private static void applyLoadedState(RoadSystemConfig config, String pluginId) {
        // 文件名决定配置归属；不要信任 JSON 中可能过期或被手工改坏的 pluginId。
        config.pluginId = pluginId;
        if (config.presets == null) {
            config.initDefaultPresets();
        } else {
            mergeMissingBuiltinStyles(config);
        }
        config.selectedMaterial = MaterialMix.orDefault(
            config.selectedMaterial,
            MaterialMix.single(RoadMaterialUtils.DEFAULT_ROAD_BLOCK));
        String normalizedSidewalk = RoadMaterialUtils.normalizeStoredMaterial(config.selectedSidewalkMaterial);
        config.selectedSidewalkMaterial = normalizedSidewalk != null
            ? normalizedSidewalk
            : config.selectedMaterial.getPrimaryMaterial();
        config.setFillSlopeMaterial(config.fillSlopeMaterial);
        config.setCutSlopeMaterial(config.cutSlopeMaterial);
        if (config.fillSlopeMaterial.isBlank()) {
            config.fillSlopeMaterial = DEFAULT_FILL_SLOPE_MATERIAL;
        }
        if (config.roadThemeId == null || config.roadThemeId.isBlank()) {
            config.roadThemeId = RoadThemeCatalog.MODERN_ID;
        }
        config.migrateLegacyTunnelFields();
    }

    private void migrateLegacyTunnelFields() {
        if (tunnelStyle == null) {
            tunnelStyle = new TunnelStyle();
        }
        if (tunnelLiningMaterial != null && !tunnelLiningMaterial.isBlank()) {
            tunnelStyle.setLiningMaterial(tunnelLiningMaterial);
        }
        if (tunnelAccentMaterial != null && !tunnelAccentMaterial.isBlank()) {
            tunnelStyle.setAccentMaterial(tunnelAccentMaterial);
        }
        if (tunnelAccentSpacing > 0) {
            tunnelStyle.setAccentSpacing(tunnelAccentSpacing);
            tunnelStyle.setAccentRings(true);
        }
        tunnelStyle.ensureConfigDefaults();
        tunnelLiningMaterial = null;
        tunnelAccentMaterial = null;
        tunnelAccentSpacing = 0;
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
            saveTo(resolveConfigPath());
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to save config: " + resolveConfigPath(), e);
        }
    }

    void saveTo(Path configPath) throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String json = GSON.toJson(this);
        AtomicFileWriter.write(configPath, json, AtomicFileWriter.Options.simple());
    }

    private void initDefaultPresets() {
        presets = new ArrayList<>(RoadStyleCatalog.defaultStyles());
    }

    public int getRoadWidth() {
        return roadWidth;
    }

    public void setRoadWidth(int roadWidth) {
        this.roadWidth = RoadParameterLimits.clampCarriagewayWidth(roadWidth);
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
        this.sidewalkWidth = RoadParameterLimits.clampStripWidth(sidewalkWidth);
    }

    public MaterialMix getSelectedMaterial() {
        return selectedMaterial;
    }

    public void setSelectedMaterial(MaterialMix selectedMaterial) {
        this.selectedMaterial = selectedMaterial != null
            ? selectedMaterial
            : MaterialMix.single(RoadMaterialUtils.DEFAULT_ROAD_BLOCK);
    }

    public void setSelectedMaterial(String selectedMaterial) {
        String normalized = RoadMaterialUtils.normalizeStoredMaterial(selectedMaterial);
        this.selectedMaterial = MaterialMix.single(
            normalized != null ? normalized : RoadMaterialUtils.DEFAULT_ROAD_BLOCK);
    }

    public String getSelectedSidewalkMaterial() {
        return selectedSidewalkMaterial != null
            ? selectedSidewalkMaterial
            : selectedMaterial.getPrimaryMaterial();
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

    public String getRoadThemeId() {
        return roadThemeId != null && !roadThemeId.isBlank() ? roadThemeId : RoadThemeCatalog.MODERN_ID;
    }

    public void setRoadThemeId(String roadThemeId) {
        this.roadThemeId = roadThemeId != null && !roadThemeId.isBlank()
            ? roadThemeId
            : RoadThemeCatalog.MODERN_ID;
    }

    public List<RoadStyle> getStyles() {
        return presets;
    }

    public RoadStyle findStyle(String styleId) {
        return RoadStyleCatalog.findById(this, styleId);
    }
    
    public float getMaxSlope() {
        return maxSlope;
    }
    
    public void setMaxSlope(float maxSlope) {
        this.maxSlope = RoadParameterLimits.clampGradePercent(maxSlope);
    }

    public RoadTerrainStyle getTerrainStyle() {
        return terrainStyle != null ? terrainStyle : RoadTerrainStyle.BALANCED;
    }

    public void setTerrainStyle(RoadTerrainStyle terrainStyle) {
        this.terrainStyle = terrainStyle != null ? terrainStyle : RoadTerrainStyle.BALANCED;
    }

    public boolean isGenerateBridgePillars() {
        return generateBridgePillars;
    }

    public void setGenerateBridgePillars(boolean generateBridgePillars) {
        this.generateBridgePillars = generateBridgePillars;
    }

    public boolean isIncludeBridgeGuardrail() {
        return includeBridgeGuardrail;
    }

    public void setIncludeBridgeGuardrail(boolean includeBridgeGuardrail) {
        this.includeBridgeGuardrail = includeBridgeGuardrail;
    }

    public String getBridgeGuardrailMaterial() {
        return bridgeGuardrailMaterial == null || bridgeGuardrailMaterial.isBlank()
            ? "minecraft:oak_fence"
            : bridgeGuardrailMaterial;
    }

    public void setBridgeGuardrailMaterial(String bridgeGuardrailMaterial) {
        this.bridgeGuardrailMaterial = bridgeGuardrailMaterial;
    }

    public TunnelStyle getTunnelStyle() {
        if (tunnelStyle == null) {
            tunnelStyle = new TunnelStyle();
        }
        tunnelStyle.ensureConfigDefaults();
        return tunnelStyle;
    }

    public void setTunnelStyle(TunnelStyle tunnelStyle) {
        this.tunnelStyle = tunnelStyle != null ? tunnelStyle : new TunnelStyle();
        this.tunnelStyle.ensureConfigDefaults();
    }

    public String getTunnelLiningMaterial() {
        return getTunnelStyle().getLiningMaterial();
    }

    public void setTunnelLiningMaterial(String tunnelLiningMaterial) {
        getTunnelStyle().setLiningMaterial(tunnelLiningMaterial);
    }

    public String getTunnelAccentMaterial() {
        return getTunnelStyle().getAccentMaterial();
    }

    public void setTunnelAccentMaterial(String tunnelAccentMaterial) {
        getTunnelStyle().setAccentMaterial(tunnelAccentMaterial);
    }

    public int getTunnelAccentSpacing() {
        return getTunnelStyle().isAccentRings() ? getTunnelStyle().getAccentSpacing() : 0;
    }

    public void setTunnelAccentSpacing(int tunnelAccentSpacing) {
        TunnelStyle style = getTunnelStyle();
        style.setAccentSpacing(Math.clamp(tunnelAccentSpacing, 0, TunnelStyle.MAX_ACCENT_SPACING));
        style.setAccentRings(tunnelAccentSpacing > 0);
    }

    public double getPathSampleDistance() {
        return pathSampleDistance;
    }

    public void setPathSampleDistance(double pathSampleDistance) {
        this.pathSampleDistance = Math.clamp(pathSampleDistance,
                RoadParameterLimits.MIN_PATH_SAMPLE_DISTANCE, RoadParameterLimits.MAX_PATH_SAMPLE_DISTANCE);
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
        this.shoulderWidth = RoadParameterLimits.clampShoulderWidth(shoulderWidth);
    }

    public int getLaneCount() {
        return laneCount > 0 ? laneCount : 1;
    }

    public void setLaneCount(int laneCount) {
        this.laneCount = RoadParameterLimits.clampLaneCount(laneCount);
    }

    public List<Integer> getLaneWidths() {
        return laneWidths != null ? List.copyOf(laneWidths) : List.of();
    }

    public void setLaneWidths(List<Integer> laneWidths) {
        this.laneWidths = laneWidths != null ? new ArrayList<>(laneWidths) : new ArrayList<>();
    }

    public boolean isIncludeBikeLane() {
        return includeBikeLane;
    }

    public void setIncludeBikeLane(boolean includeBikeLane) {
        this.includeBikeLane = includeBikeLane;
    }

    public int getBikeLaneWidth() {
        return bikeLaneWidth;
    }

    public void setBikeLaneWidth(int bikeLaneWidth) {
        this.bikeLaneWidth = RoadParameterLimits.clampStripWidth(bikeLaneWidth);
    }

    public boolean isIncludeMedian() {
        return includeMedian;
    }

    public void setIncludeMedian(boolean includeMedian) {
        this.includeMedian = includeMedian;
    }

    public int getMedianWidth() {
        return medianWidth;
    }

    public void setMedianWidth(int medianWidth) {
        this.medianWidth = RoadParameterLimits.clampStripWidth(medianWidth);
    }

    public boolean isLaneDividers() {
        return laneDividers;
    }

    public void setLaneDividers(boolean laneDividers) {
        this.laneDividers = laneDividers;
    }

    public CenterLineStyle getCenterLineStyle() {
        if (centerLineStyle == null || centerLineStyle.isBlank()) {
            return CenterLineStyle.NONE;
        }
        try {
            return CenterLineStyle.valueOf(centerLineStyle);
        } catch (IllegalArgumentException ignored) {
            return CenterLineStyle.NONE;
        }
    }

    public void setCenterLineStyle(CenterLineStyle style) {
        this.centerLineStyle = style != null ? style.name() : CenterLineStyle.NONE.name();
    }

    public String getMarkingMaterial() {
        return markingMaterial != null && !markingMaterial.isBlank()
            ? markingMaterial
            : "material.plot.white_concrete";
    }

    public void setMarkingMaterial(String markingMaterial) {
        this.markingMaterial = RoadMaterialUtils.normalizeStoredMaterial(markingMaterial);
    }

    public int getStreetlightSpacing() {
        return streetlightSpacing;
    }

    public void setStreetlightSpacing(int streetlightSpacing) {
        Integer normalized = RoadParameterLimits.normalizeStreetlightSpacing(streetlightSpacing);
        this.streetlightSpacing = normalized != null ? normalized : 0;
    }

    public boolean isIncludeSlopeBatter() {
        return includeSlopeBatter;
    }

    public void setIncludeSlopeBatter(boolean includeSlopeBatter) {
        this.includeSlopeBatter = includeSlopeBatter;
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
        this.fillSlopeRatio = Math.clamp(fillSlopeRatio, 0.5f, 5.0f);
    }

    public float getCutSlopeRatio() {
        return cutSlopeRatio;
    }

    public void setCutSlopeRatio(float cutSlopeRatio) {
        this.cutSlopeRatio = Math.clamp(cutSlopeRatio, 0.5f, 5.0f);
    }

    public String getFillSlopeMaterial() {
        return fillSlopeMaterial != null && !fillSlopeMaterial.isBlank()
            ? fillSlopeMaterial
            : DEFAULT_FILL_SLOPE_MATERIAL;
    }

    public void setFillSlopeMaterial(String fillSlopeMaterial) {
        this.fillSlopeMaterial = fillSlopeMaterial != null ? fillSlopeMaterial : "";
    }

    public String getCutSlopeMaterial() {
        if (cutSlopeMaterial != null && !cutSlopeMaterial.isBlank()) {
            return cutSlopeMaterial;
        }
        return getFillSlopeMaterial();
    }

    public void setCutSlopeMaterial(String cutSlopeMaterial) {
        this.cutSlopeMaterial = cutSlopeMaterial != null ? cutSlopeMaterial : "";
    }

    public double getMaxContinuousSlopeLength() {
        return maxContinuousSlopeLength;
    }

    public void setMaxContinuousSlopeLength(double maxContinuousSlopeLength) {
        this.maxContinuousSlopeLength = RoadParameterLimits.clampMaxContinuousSlopeLength(maxContinuousSlopeLength);
        this.relaxedSlopeLength = RoadParameterLimits.clampRelaxedSlopeLength(
            this.relaxedSlopeLength,
            this.maxContinuousSlopeLength);
    }

    public double getRelaxedSlopeLength() {
        return relaxedSlopeLength;
    }

    public void setRelaxedSlopeLength(double relaxedSlopeLength) {
        this.relaxedSlopeLength = RoadParameterLimits.clampRelaxedSlopeLength(
            relaxedSlopeLength,
            this.maxContinuousSlopeLength);
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
        this.defaultCornerRadius = (float) Math.clamp(defaultCornerRadius,
                RoadNode.MIN_CORNER_RADIUS, RoadNode.MAX_CORNER_RADIUS);
    }

    public double getDefaultCrossingClearance() {
        return defaultCrossingClearance;
    }

    public void setDefaultCrossingClearance(double defaultCrossingClearance) {
        this.defaultCrossingClearance = RoadParameterLimits.clampCrossingClearance(defaultCrossingClearance);
    }

    /**
     * 影响纵断面/自然高度推算的全局参数指纹，供 UI 缓存失效。
     */
    public long generationInputsFingerprint() {
        return java.util.Objects.hash(
            maxSlope,
            terrainStyle,
            pathSampleDistance,
            maxContinuousSlopeLength,
            relaxedSlopeLength,
            relaxedSlopePercent,
            getTunnelStyle());
    }
    
    /**
     * 应用道路风格到全局默认参数。
     */
    public void applyStyle(RoadStyle style) {
        if (style == null) {
            return;
        }
        RoadStyle effective = RoadThemeCatalog.applyTheme(getRoadThemeId(), style);
        String resolvedRoadMaterial = effective.roadMaterial != null && !effective.roadMaterial.isBlank()
            ? effective.roadMaterial
            : RoadMaterialUtils.DEFAULT_ROAD_BLOCK;
        String resolvedSidewalkMaterial = effective.sidewalkMaterial != null && !effective.sidewalkMaterial.isBlank()
            ? effective.sidewalkMaterial
            : resolvedRoadMaterial;
        setRoadWidth(effective.width);
        this.includeSidewalk = effective.hasSidewalk;
        if (effective.hasSidewalk) {
            setSidewalkWidth(effective.sidewalkWidth);
        }
        this.includeShoulder = effective.includeShoulder;
        setShoulderWidth(effective.shoulderWidth);
        this.includeBikeLane = effective.includeBikeLane;
        if (effective.includeBikeLane) {
            setBikeLaneWidth(effective.bikeLaneWidth);
        }
        this.includeMedian = effective.includeMedian;
        if (effective.includeMedian) {
            setMedianWidth(effective.medianWidth);
        }
        setLaneCount(effective.resolveLaneCount());
        this.laneDividers = effective.resolveLaneDividers();
        setCenterLineStyle(effective.resolveCenterLineStyle());
        if (effective.markingMaterial != null && !effective.markingMaterial.isBlank()) {
            setMarkingMaterial(effective.markingMaterial);
        }
        if (effective.streetlightSpacing != null) {
            setStreetlightSpacing(effective.streetlightSpacing);
        }
        this.includeSlopeBatter = effective.resolveIncludeSlopeBatter();
        if (effective.fillSlopeRatio > 0f) {
            this.fillSlopeRatio = effective.fillSlopeRatio;
        }
        if (effective.cutSlopeRatio > 0f) {
            this.cutSlopeRatio = effective.cutSlopeRatio;
        }
        if (effective.fillSlopeMaterial != null && !effective.fillSlopeMaterial.isBlank()) {
            this.fillSlopeMaterial = effective.fillSlopeMaterial;
        }
        if (effective.cutSlopeMaterial != null && !effective.cutSlopeMaterial.isBlank()) {
            this.cutSlopeMaterial = effective.cutSlopeMaterial;
        }
        this.selectedMaterial = MaterialMix.single(resolvedRoadMaterial);
        this.selectedSidewalkMaterial = resolvedSidewalkMaterial;
        this.selectedPreset = style.id;
    }
}
