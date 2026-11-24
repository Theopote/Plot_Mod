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
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.model.section.CarriagewayWidthRules;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.CrossSectionGeometryRules;
import com.plot.plugin.road.model.section.MarkingRules;
import com.plot.plugin.road.model.section.RoadsideEdgeMode;
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
    private static final String DEFAULT_FILL_SLOPE_MATERIAL = RoadMaterialUtils.DEFAULT_SLOPE_FILL_BLOCK;
    private static final String DEFAULT_RETAINING_WALL_MATERIAL = "minecraft:stone_bricks";
    private String pluginId;

    /** 派生写入以保持序列化兼容；权威输入为车道数 / 单车道宽 / 标线。 */
    private int roadWidth = RoadParameterLimits.DEFAULT_LANE_WIDTH_BLOCKS;
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
    /**
     * 架桥净高阈值（格）；{@code null} 表示跟随当前地形风格表。
     * 路面相对地面高差超过该值时，施工分类更倾向桥梁而非开敞填方。
     */
    private Integer bridgeThreshold;
    private boolean generateBridgePillars = true;
    private double bridgePierSpacingBlocks = RoadParameterLimits.DEFAULT_BRIDGE_PIER_SPACING_BLOCKS;
    private String bridgePierMaterial = "minecraft:stone_bricks";
    /** {@link BridgePierStyle} name; blank → SINGLE. */
    private String bridgePierStyle = BridgePierStyle.SINGLE.name();
    private int bridgePierDiameterBlocks = RoadParameterLimits.DEFAULT_BRIDGE_PIER_DIAMETER_BLOCKS;
    private int bridgePierOffsetBlocks = RoadParameterLimits.DEFAULT_BRIDGE_PIER_OFFSET_BLOCKS;
    private boolean includeBridgeGuardrail = false;
    private String bridgeGuardrailMaterial = "minecraft:oak_fence";
    private int bridgeGuardrailHeightBlocks = RoadParameterLimits.DEFAULT_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS;
    private TunnelStyle tunnelStyle = new TunnelStyle();
    private boolean includeShoulder = true; // 是否包含路肩 - 从false改为true，默认启用路肩和边坡填充
    private int shoulderWidth = 1; // 路肩宽度
    private int laneCount = 1;
    private int defaultLaneWidth = RoadParameterLimits.DEFAULT_LANE_WIDTH_BLOCKS;
    private List<Integer> laneWidths = new ArrayList<>();
    private boolean includeBikeLane = false;
    private int bikeLaneWidth = 1;
    private boolean includeMedian = false;
    private int medianWidth = 1;
    private int medianHeight = RoadParameterLimits.DEFAULT_MEDIAN_HEIGHT_BLOCKS;
    private boolean laneDividers = false;
    private String centerLineStyle = CenterLineStyle.NONE.name();
    /** 中央公路标线是否实线；与 {@link #centerLineStyle} 占格解耦。 */
    private boolean centerLineSolid = false;
    private String markingMaterial = "minecraft:white_concrete";
    private int streetlightSpacing = 0;
    private boolean includeSlopeBatter = true;
    /** 全局路边边缘模式；缺省时由 {@link #includeSlopeBatter} 迁移为边坡/无。 */
    private String roadsideEdgeMode = RoadsideEdgeMode.SLOPE_BATTER.name();
    /** 边坡水平最大跑距（格）：范围外不开挖/填坡。 */
    private int slopeBatterMaxRunBlocks = RoadParameterLimits.DEFAULT_SLOPE_BATTER_RUN_BLOCKS;
    private double pathSampleDistance = 1.0; // 路径采样密度（米/点），用于控制路径细分精度

    private float fillSlopeRatio = 1.0f;
    private float cutSlopeRatio = 1.0f;
    private String fillSlopeMaterial = DEFAULT_FILL_SLOPE_MATERIAL;
    private String cutSlopeMaterial = "";
    private String retainingWallMaterial = DEFAULT_RETAINING_WALL_MATERIAL;
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
        config.setRetainingWallMaterial(config.retainingWallMaterial);
        if (config.fillSlopeMaterial.isBlank()) {
            config.fillSlopeMaterial = DEFAULT_FILL_SLOPE_MATERIAL;
        }
        if (config.retainingWallMaterial == null || config.retainingWallMaterial.isBlank()) {
            config.retainingWallMaterial = DEFAULT_RETAINING_WALL_MATERIAL;
        }
        if (RoadsideEdgeMode.fromId(config.roadsideEdgeMode) == null) {
            config.roadsideEdgeMode = config.includeSlopeBatter
                ? RoadsideEdgeMode.SLOPE_BATTER.name()
                : RoadsideEdgeMode.NONE.name();
        }
        if (config.roadThemeId == null || config.roadThemeId.isBlank()) {
            config.roadThemeId = RoadThemeCatalog.MODERN_ID;
        }
        if (config.tunnelStyle == null) {
            config.tunnelStyle = new TunnelStyle();
        }
        config.tunnelStyle.ensureConfigDefaults();
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
        int target = RoadParameterLimits.clampCarriagewayWidth(roadWidth);
        // 旧调用方仍按总宽写入：打包为车道行驶宽，保证派生总宽尽量等于目标。
        List<Integer> packed = CarriagewayWidthRules.packTravelWidthsForTarget(
            target,
            getLaneCount(),
            getCenterLineStyle(),
            laneDividers,
            0);
        this.laneCount = packed.size();
        int first = packed.getFirst();
        boolean uniform = true;
        for (int width : packed) {
            if (width != first) {
                uniform = false;
                break;
            }
        }
        this.defaultLaneWidth = first;
        if (uniform) {
            this.laneWidths = new ArrayList<>();
        } else {
            this.laneWidths = new ArrayList<>(packed);
        }
        this.roadWidth = CarriagewayWidthRules.derivedCarriagewayWidth(
            effectiveLaneTravelWidths(),
            getCenterLineStyle(),
            laneDividers);
    }

    public int getDefaultLaneWidth() {
        return RoadParameterLimits.clampLaneTravelWidth(
            defaultLaneWidth > 0 ? defaultLaneWidth : RoadParameterLimits.DEFAULT_LANE_WIDTH_BLOCKS);
    }

    public void setDefaultLaneWidth(int defaultLaneWidth) {
        this.defaultLaneWidth = RoadParameterLimits.clampLaneTravelWidth(defaultLaneWidth);
        recomputeDerivedRoadWidth();
    }

    /** 按当前车道/标线/分隔带重写 {@link #roadWidth}（序列化兼容）。 */
    public void recomputeDerivedRoadWidth() {
        List<Integer> widths = effectiveLaneTravelWidths();
        this.roadWidth = CarriagewayWidthRules.derivedCarriagewayWidth(
            widths,
            getCenterLineStyle(),
            laneDividers);
        syncMedianWidthFromMarkings();
    }

    private void syncMedianWidthFromMarkings() {
        int derived = CrossSectionGeometryRules.derivedMedianWidth(
            getCenterLineStyle(), laneDividers, getLaneCount());
        if (derived <= 0) {
            includeMedian = false;
            medianWidth = 0;
            return;
        }
        if (includeMedian) {
            medianWidth = derived;
        }
    }

    private List<Integer> effectiveLaneTravelWidths() {
        int lanes = getLaneCount();
        List<Integer> configured = getLaneWidths();
        boolean anyExplicit = false;
        List<Integer> widths = new ArrayList<>(lanes);
        for (int i = 0; i < lanes; i++) {
            if (i < configured.size() && configured.get(i) != null && configured.get(i) > 0) {
                widths.add(RoadParameterLimits.clampLaneTravelWidth(configured.get(i)));
                anyExplicit = true;
            } else {
                widths.add(getDefaultLaneWidth());
            }
        }
        if (!anyExplicit) {
            return CarriagewayWidthRules.uniformLaneWidths(lanes, getDefaultLaneWidth());
        }
        return widths;
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

    /**
     * 持久化字段；{@code null} 表示使用地形风格默认架桥净高阈值。
     */
    public Integer getBridgeThreshold() {
        return RoadParameterLimits.clampBridgeThresholdNullable(bridgeThreshold);
    }

    public void setBridgeThreshold(Integer bridgeThreshold) {
        this.bridgeThreshold = RoadParameterLimits.clampBridgeThresholdNullable(bridgeThreshold);
    }

    public boolean isGenerateBridgePillars() {
        return generateBridgePillars;
    }

    public void setGenerateBridgePillars(boolean generateBridgePillars) {
        this.generateBridgePillars = generateBridgePillars;
    }

    public double getBridgePierSpacingBlocks() {
        return RoadParameterLimits.clampBridgePierSpacingBlocks(bridgePierSpacingBlocks);
    }

    public void setBridgePierSpacingBlocks(double bridgePierSpacingBlocks) {
        this.bridgePierSpacingBlocks =
            RoadParameterLimits.clampBridgePierSpacingBlocks(bridgePierSpacingBlocks);
    }

    public String getBridgePierMaterial() {
        return bridgePierMaterial == null || bridgePierMaterial.isBlank()
            ? "minecraft:stone_bricks"
            : bridgePierMaterial;
    }

    public void setBridgePierMaterial(String bridgePierMaterial) {
        this.bridgePierMaterial = bridgePierMaterial;
    }

    public BridgePierStyle getBridgePierStyle() {
        return BridgePierStyle.fromStoredOrDefault(bridgePierStyle);
    }

    public void setBridgePierStyle(BridgePierStyle bridgePierStyle) {
        this.bridgePierStyle = bridgePierStyle != null
            ? bridgePierStyle.name()
            : BridgePierStyle.SINGLE.name();
    }

    public int getBridgePierDiameterBlocks() {
        return RoadParameterLimits.clampBridgePierDiameterStored(bridgePierDiameterBlocks);
    }

    public void setBridgePierDiameterBlocks(int bridgePierDiameterBlocks) {
        this.bridgePierDiameterBlocks =
            RoadParameterLimits.clampBridgePierDiameterStored(bridgePierDiameterBlocks);
    }

    public int getBridgePierOffsetBlocks() {
        return RoadParameterLimits.clampBridgePierOffsetStored(bridgePierOffsetBlocks);
    }

    public void setBridgePierOffsetBlocks(int bridgePierOffsetBlocks) {
        this.bridgePierOffsetBlocks =
            RoadParameterLimits.clampBridgePierOffsetStored(bridgePierOffsetBlocks);
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

    public int getBridgeGuardrailHeightBlocks() {
        return RoadParameterLimits.clampBridgeGuardrailHeightBlocks(bridgeGuardrailHeightBlocks);
    }

    public void setBridgeGuardrailHeightBlocks(int bridgeGuardrailHeightBlocks) {
        this.bridgeGuardrailHeightBlocks =
            RoadParameterLimits.clampBridgeGuardrailHeightBlocks(bridgeGuardrailHeightBlocks);
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
        recomputeDerivedRoadWidth();
    }

    public List<Integer> getLaneWidths() {
        return laneWidths != null ? List.copyOf(laneWidths) : List.of();
    }

    public void setLaneWidths(List<Integer> laneWidths) {
        this.laneWidths = laneWidths != null ? new ArrayList<>(laneWidths) : new ArrayList<>();
        recomputeDerivedRoadWidth();
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
        // 不在此处因「标线尚未写好」而关掉；标线变更与 recompute 会再 sync。
        if (includeMedian) {
            int derived = CrossSectionGeometryRules.derivedMedianWidth(
                getCenterLineStyle(), laneDividers, getLaneCount());
            if (derived > 0) {
                medianWidth = derived;
            }
        }
    }

    public int getMedianWidth() {
        return medianWidth;
    }

    public void setMedianWidth(int medianWidth) {
        this.medianWidth = RoadParameterLimits.clampMedianWidthBlocks(0, medianWidth);
        if (includeMedian) {
            int derived = CrossSectionGeometryRules.derivedMedianWidth(
                getCenterLineStyle(), laneDividers, getLaneCount());
            if (derived > 0) {
                this.medianWidth = derived;
            }
        }
    }

    public int getMedianHeight() {
        return RoadParameterLimits.clampMedianHeightBlocks(
            medianHeight > 0 ? medianHeight : RoadParameterLimits.DEFAULT_MEDIAN_HEIGHT_BLOCKS);
    }

    public void setMedianHeight(int medianHeight) {
        this.medianHeight = RoadParameterLimits.clampMedianHeightBlocks(medianHeight);
    }

    public boolean isLaneDividers() {
        return laneDividers;
    }

    public void setLaneDividers(boolean laneDividers) {
        this.laneDividers = laneDividers;
        recomputeDerivedRoadWidth();
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
        if (style == CenterLineStyle.DOUBLE_SOLID) {
            this.centerLineSolid = true;
        } else if (style == CenterLineStyle.SINGLE_DASHED) {
            this.centerLineSolid = false;
        }
        recomputeDerivedRoadWidth();
    }

    public boolean isCenterLineSolid() {
        CenterLineStyle style = getCenterLineStyle();
        if (style == CenterLineStyle.RAIL) {
            return true;
        }
        if (style == CenterLineStyle.NONE) {
            return false;
        }
        return centerLineSolid;
    }

    public void setCenterLineSolid(boolean centerLineSolid) {
        this.centerLineSolid = centerLineSolid;
    }

    public String getMarkingMaterial() {
        return markingMaterial != null && !markingMaterial.isBlank()
            ? markingMaterial
            : "minecraft:white_concrete";
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
        return getRoadsideEdgeMode().isSlopeBatter();
    }

    public void setIncludeSlopeBatter(boolean includeSlopeBatter) {
        this.includeSlopeBatter = includeSlopeBatter;
        setRoadsideEdgeMode(includeSlopeBatter ? RoadsideEdgeMode.SLOPE_BATTER : RoadsideEdgeMode.NONE);
    }

    public RoadsideEdgeMode getRoadsideEdgeMode() {
        RoadsideEdgeMode mode = RoadsideEdgeMode.fromId(roadsideEdgeMode);
        if (mode != null) {
            return mode;
        }
        return includeSlopeBatter ? RoadsideEdgeMode.SLOPE_BATTER : RoadsideEdgeMode.NONE;
    }

    public void setRoadsideEdgeMode(RoadsideEdgeMode mode) {
        RoadsideEdgeMode resolved = mode != null ? mode : RoadsideEdgeMode.NONE;
        this.roadsideEdgeMode = resolved.name();
        this.includeSlopeBatter = resolved.isSlopeBatter();
    }

    public boolean isIncludeRetainingWall() {
        return getRoadsideEdgeMode().isRetainingWall();
    }

    public int getSlopeBatterMaxRunBlocks() {
        return RoadParameterLimits.clampSlopeBatterRunBlocks(slopeBatterMaxRunBlocks);
    }

    public void setSlopeBatterMaxRunBlocks(int slopeBatterMaxRunBlocks) {
        this.slopeBatterMaxRunBlocks = RoadParameterLimits.clampSlopeBatterRunBlocks(slopeBatterMaxRunBlocks);
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

    public String getRetainingWallMaterial() {
        return retainingWallMaterial != null && !retainingWallMaterial.isBlank()
            ? retainingWallMaterial
            : DEFAULT_RETAINING_WALL_MATERIAL;
    }

    public void setRetainingWallMaterial(String retainingWallMaterial) {
        this.retainingWallMaterial = retainingWallMaterial != null && !retainingWallMaterial.isBlank()
            ? retainingWallMaterial
            : DEFAULT_RETAINING_WALL_MATERIAL;
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
            bridgeThreshold,
            pathSampleDistance,
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
        // 先写入车道数与标线，再按总宽打包，避免 setRoadWidth 用到过期的车道/分隔上下文。
        this.laneCount = RoadParameterLimits.clampLaneCount(effective.resolveLaneCount());
        this.laneDividers = effective.resolveLaneDividers();
        CenterLineStyle appliedCenter = effective.resolveCenterLineStyle();
        this.centerLineStyle = appliedCenter.name();
        this.centerLineSolid = MarkingRules.defaultSolidForStyle(appliedCenter);
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
        if (effective.markingMaterial != null && !effective.markingMaterial.isBlank()) {
            setMarkingMaterial(effective.markingMaterial);
        }
        if (effective.streetlightSpacing != null) {
            setStreetlightSpacing(effective.streetlightSpacing);
        }
        RoadsideEdgeMode edgeMode = effective.resolveRoadsideEdgeMode();
        if (edgeMode != null) {
            setRoadsideEdgeMode(edgeMode);
        }
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
        if (effective.wallMaterial != null && !effective.wallMaterial.isBlank()) {
            this.retainingWallMaterial = effective.wallMaterial;
        }
        this.selectedMaterial = MaterialMix.single(resolvedRoadMaterial);
        this.selectedSidewalkMaterial = resolvedSidewalkMaterial;
        this.selectedPreset = style.id;
    }
}
