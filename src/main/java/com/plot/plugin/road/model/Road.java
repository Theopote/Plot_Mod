package com.plot.plugin.road.model;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.plugin.road.vertical.RoadVerticalIntentTransforms;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadsideEdgeMode;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.station.RoadDesignDirection;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.style.RoadStyleCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/**
 * 逻辑道路（工程对象）。一条认领的道路可对应多条几何 {@link RoadEdge} 段。
 * <p>
 * <strong>核心工程模型</strong>（逻辑视图，不一定全部持久化）：
 * <pre>
 * Road
 * ├ DesignDirection          → {@link #designDirection(RoadNetwork)}
 * ├ Oriented Segments        → {@link #orientedSegments(RoadNetwork)}（动态 derive）
 * │    edgeId, forward, startStation, length
 * ├ HorizontalAlignment      → {@link #getHorizontalAlignment()}
 * ├ VerticalAlignment        → {@link #getVerticalAlignment()}
 * ├ TypicalCrossSection      → {@link #getCrossSection()}
 * ├ VariableCrossSections    → {@link #getVariableCrossSections()}
 * └ StationFacilities        → {@link #getStationFacilities()}
 * </pre>
 * {@code segmentIds} 仅记录几何段归属与存储顺序；链方向与桩号坐标见 {@link RoadStationing}。
 */
public class Road {
    private final String id;
    private String name;
    private RoadCrossSection crossSection = new RoadCrossSection();
    private String styleId;
    private String themeId;
    private Float maxSlope;
    private RoadTopologyMode topologyMode = RoadTopologyMode.LINEAR;
    private RoadLoopSeam loopSeam;
    private RoadHorizontalAlignment horizontalAlignment;
    private RoadVerticalAlignment verticalAlignment;
    private RoadVerticalMode verticalMode;
    private ProfileSource profileSource;
    private int editedGeometrySignature;
    private RoadTerrainStyle terrainStyle;
    /**
     * 架桥净高阈值（格）；{@code null} 表示继承全局配置（再无则用地形风格表）。
     */
    private Integer bridgeThreshold;
    /** {@code null} 表示继承全局 {@link com.plot.plugin.config.RoadSystemConfig}。 */
    private TunnelStyle tunnelStyle;
    /** {@code null} 表示继承全局默认。 */
    private Boolean generateBridgePillars;
    /** {@code null} 表示继承全局默认。 */
    private Double bridgePierSpacingBlocks;
    /** {@code null} 表示继承全局默认。 */
    private String bridgePierMaterial;
    /** {@code null} 表示继承全局默认。 */
    private BridgePierStyle bridgePierStyle;
    /** {@code null} 表示继承全局默认；柱截面横向直径（格）。 */
    private Integer bridgePierDiameterBlocks;
    /** {@code null} 表示继承全局默认；双柱中心线到柱心偏移（格）。 */
    private Integer bridgePierOffsetBlocks;
    /** {@code null} 表示继承全局默认。 */
    private Boolean includeBridgeGuardrail;
    /** {@code null} 表示继承全局默认。 */
    private String bridgeGuardrailMaterial;
    /** {@code null} 表示继承全局默认。 */
    private Integer bridgeGuardrailHeightBlocks;
    private FlatVerticalIntent flatVerticalIntent;
    private RoadVariableCrossSections variableCrossSections;
    private RoadStationFacilities stationFacilities;
    private final List<String> segmentIds = new ArrayList<>();

    public Road() {
        this(UUID.randomUUID().toString());
    }

    public Road(String id) {
        this.id = id;
    }

    public Road(
            String id,
            String name,
            Integer width,
            String material,
            Boolean includeSidewalk,
            Integer sidewalkWidth,
            String sidewalkMaterial,
            Integer streetlightSpacing,
            Float maxSlope,
            Set<String> segmentIds) {
        this(id, name, RoadCrossSection.fromSimple(
            width, material, includeSidewalk, sidewalkWidth, sidewalkMaterial, streetlightSpacing
        ), maxSlope, segmentIds);
    }

    public Road(
            String id,
            String name,
            RoadCrossSection crossSection,
            Float maxSlope,
            Set<String> segmentIds) {
        this.id = id;
        this.name = name;
        if (crossSection != null) {
            this.crossSection = crossSection;
        }
        this.maxSlope = maxSlope;
        if (segmentIds != null) {
            for (String segmentId : segmentIds) {
                addSegment(segmentId);
            }
        }
    }

    /**
     * 用全局配置快照初始化道路横断面与最大坡度（显式写入，非继承态）。
     *
     * @see RoadParameterInheritance#snapshotGlobalDefaults(Road, RoadSystemConfig)
     */
    public void applyDefaults(RoadSystemConfig defaults) {
        if (defaults == null) {
            return;
        }
        crossSection.applyDefaults(defaults);
        maxSlope = defaults.getMaxSlope();
        bridgeThreshold = defaults.getBridgeThreshold();
        generateBridgePillars = defaults.isGenerateBridgePillars();
        bridgePierSpacingBlocks = defaults.getBridgePierSpacingBlocks();
        bridgePierMaterial = defaults.getBridgePierMaterial();
        bridgePierStyle = defaults.getBridgePierStyle();
        bridgePierDiameterBlocks = defaults.getBridgePierDiameterBlocks();
        bridgePierOffsetBlocks = defaults.getBridgePierOffsetBlocks();
        includeBridgeGuardrail = defaults.isIncludeBridgeGuardrail();
        bridgeGuardrailMaterial = defaults.getBridgeGuardrailMaterial();
        bridgeGuardrailHeightBlocks = defaults.getBridgeGuardrailHeightBlocks();
    }

    /** 清空道路级与横断面的全部显式覆盖，恢复继承全局配置。 */
    public void inheritAllDefaults() {
        crossSection.inheritAll();
        maxSlope = null;
        styleId = null;
        themeId = null;
        terrainStyle = null;
        bridgeThreshold = null;
        tunnelStyle = null;
        generateBridgePillars = null;
        bridgePierSpacingBlocks = null;
        bridgePierMaterial = null;
        bridgePierStyle = null;
        bridgePierDiameterBlocks = null;
        bridgePierOffsetBlocks = null;
        includeBridgeGuardrail = null;
        bridgeGuardrailMaterial = null;
        bridgeGuardrailHeightBlocks = null;
    }

    public void applyStyle(RoadStyle style) {
        applyStyle(style, null);
    }

    public void applyStyle(RoadStyle style, String themeIdOverride) {
        if (style == null) {
            return;
        }
        style.applyTo(this, themeIdOverride);
    }

    public void applyStyle(String styleId, RoadSystemConfig defaults) {
        RoadStyle style = RoadStyleCatalog.findById(defaults, styleId);
        if (style != null) {
            String themeId = defaults != null ? defaults.getRoadThemeId() : null;
            applyStyle(style, themeId);
        }
    }

    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        this.styleId = styleId != null && !styleId.isBlank() ? styleId : null;
    }

    public void clearStyleId() {
        this.styleId = null;
    }

    public String getThemeId() {
        return themeId;
    }

    public void setThemeId(String themeId) {
        this.themeId = themeId != null && !themeId.isBlank() ? themeId : null;
    }

    public void clearThemeId() {
        this.themeId = null;
    }

    public String getEffectiveThemeId(RoadSystemConfig defaults) {
        if (themeId != null && !themeId.isBlank()) {
            return themeId;
        }
        return defaults != null ? defaults.getRoadThemeId() : com.plot.plugin.road.style.RoadThemeCatalog.MODERN_ID;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            this.name = null;
            return;
        }
        String trimmed = name.trim();
        this.name = trimmed;
    }

    public RoadCrossSection getCrossSection() {
        return crossSection;
    }

    public void setCrossSection(RoadCrossSection crossSection) {
        this.crossSection = crossSection != null ? crossSection : new RoadCrossSection();
    }

    public Integer getWidth() {
        return crossSection.getCarriageway().getWidth();
    }

    public void setWidth(Integer width) {
        if (width == null) {
            crossSection.getCarriageway().setWidth(null);
            return;
        }
        int clamped = RoadParameterLimits.clampCarriagewayWidth(width);
        CenterLineStyle style = getCenterLineStyle() != null ? getCenterLineStyle() : CenterLineStyle.NONE;
        boolean dividers = getLaneDividers() != null && getLaneDividers();
        // 隔离带在路表之上，不参与总宽打包。
        crossSection.getCarriageway().applyPackedTravelWidths(clamped, style, dividers);
    }

    public MaterialMix getMaterial() {
        return crossSection.getCarriageway().getMaterial();
    }

    public void setMaterial(MaterialMix material) {
        crossSection.getCarriageway().setMaterial(material);
    }

    public void setMaterial(String material) {
        crossSection.getCarriageway().setMaterial(material);
    }

    public Integer getLaneCount() {
        return crossSection.getCarriageway().getLaneCount();
    }

    public void setLaneCount(Integer laneCount) {
        if (laneCount == null || laneCount < RoadParameterLimits.MIN_LANE_COUNT) {
            return;
        }
        int clamped = RoadParameterLimits.clampLaneCount(laneCount);
        crossSection.getCarriageway().syncLaneCount(clamped);
        crossSection.getCarriageway().reclampLaneWidths(0);
        if (crossSection.getMarkings().getLaneDividers() == null) {
            crossSection.getMarkings().setLaneDividers(clamped > 1);
        }
        // 仅在已有显式总宽时重算；width==null 表示继承全局，避免物化。
        if (crossSection.getCarriageway().getWidth() != null) {
            CenterLineStyle style = getCenterLineStyle() != null ? getCenterLineStyle() : CenterLineStyle.NONE;
            boolean dividers = getLaneDividers() != null ? getLaneDividers() : clamped > 1;
            crossSection.getCarriageway().syncDerivedWidth(style, dividers, 0);
        }
    }

    public Boolean getIncludeMedian() {
        return crossSection.getMedian().getEnabled();
    }

    public void setIncludeMedian(Boolean includeMedian) {
        crossSection.getMedian().setEnabled(includeMedian);
    }

    public Integer getMedianWidth() {
        return crossSection.getMedian().getWidth();
    }

    public void setMedianWidth(Integer medianWidth) {
        if (medianWidth == null) {
            crossSection.getMedian().setWidth(null);
            return;
        }
        crossSection.getMedian().setWidth(RoadParameterLimits.clampMedianWidthBlocks(0, medianWidth));
    }

    public Integer getMedianHeight() {
        return crossSection.getMedian().getHeight();
    }

    public void setMedianHeight(Integer medianHeight) {
        crossSection.getMedian().setHeight(medianHeight);
    }

    public Boolean getLaneDividers() {
        return crossSection.getMarkings().getLaneDividers();
    }

    public void setLaneDividers(Boolean laneDividers) {
        crossSection.getMarkings().setLaneDividers(laneDividers);
        syncDerivedWidthAfterMarkingChange(laneDividers);
    }

    public CenterLineStyle getCenterLineStyle() {
        return crossSection.getMarkings().getCenterLineStyle();
    }

    public void setCenterLineStyle(CenterLineStyle centerLineStyle) {
        crossSection.getMarkings().setCenterLineStyle(centerLineStyle);
        Boolean dividers = getLaneDividers();
        syncDerivedWidthAfterMarkingChange(dividers);
    }

    /**
     * 标线/分隔开关变化后，按车道行驶宽重推显式总宽，避免旧总宽仍含分隔占格，
     * 解析时被 pack 进车道行驶宽（预览上像残留一格分割线）。
     */
    private void syncDerivedWidthAfterMarkingChange(Boolean laneDividers) {
        if (laneDividers == null || crossSection.getCarriageway().getWidth() == null) {
            return;
        }
        CenterLineStyle style = getCenterLineStyle() != null ? getCenterLineStyle() : CenterLineStyle.NONE;
        crossSection.getCarriageway().syncDerivedWidth(style, laneDividers, 0);
    }

    public String getMarkingMaterial() {
        return crossSection.getMarkings().getMaterial();
    }

    public void setMarkingMaterial(String markingMaterial) {
        crossSection.getMarkings().setMaterial(markingMaterial);
    }

    public Boolean getIncludeSidewalk() {
        return crossSection.getSidewalk().getEnabled();
    }

    public void setIncludeSidewalk(Boolean includeSidewalk) {
        crossSection.getSidewalk().setEnabled(includeSidewalk);
    }

    public Integer getSidewalkWidth() {
        return crossSection.getSidewalk().getWidth();
    }

    public void setSidewalkWidth(Integer sidewalkWidth) {
        if (sidewalkWidth == null) {
            crossSection.getSidewalk().setWidth(null);
            return;
        }
        crossSection.getSidewalk().setWidth(RoadParameterLimits.clampStripWidth(sidewalkWidth));
    }

    public String getSidewalkMaterial() {
        return crossSection.getSidewalk().getMaterial();
    }

    public void setSidewalkMaterial(String sidewalkMaterial) {
        crossSection.getSidewalk().setMaterial(sidewalkMaterial);
    }

    public Boolean getIncludeShoulder() {
        return crossSection.getShoulder().getEnabled();
    }

    public void setIncludeShoulder(Boolean includeShoulder) {
        crossSection.getShoulder().setEnabled(includeShoulder);
    }

    public Integer getShoulderWidth() {
        return crossSection.getShoulder().getWidth();
    }

    public void setShoulderWidth(Integer shoulderWidth) {
        if (shoulderWidth == null) {
            crossSection.getShoulder().setWidth(null);
            return;
        }
        crossSection.getShoulder().setWidth(RoadParameterLimits.clampShoulderWidth(shoulderWidth));
    }

    public String getShoulderMaterial() {
        return crossSection.getShoulder().getMaterial();
    }

    public void setShoulderMaterial(String shoulderMaterial) {
        crossSection.getShoulder().setMaterial(shoulderMaterial);
    }

    public Boolean getIncludeSlopeBatter() {
        RoadsideEdgeMode mode = crossSection.getSlopeBatter().getMode();
        if (mode == null) {
            return null;
        }
        return mode.isSlopeBatter();
    }

    public void setIncludeSlopeBatter(Boolean includeSlopeBatter) {
        crossSection.getSlopeBatter().setEnabled(includeSlopeBatter);
    }

    public RoadsideEdgeMode getRoadsideEdgeMode() {
        return crossSection.getSlopeBatter().getMode();
    }

    public void setRoadsideEdgeMode(RoadsideEdgeMode mode) {
        crossSection.getSlopeBatter().setMode(mode);
    }

    public String getRetainingWallMaterial() {
        return crossSection.getSlopeBatter().getWallMaterial();
    }

    public void setRetainingWallMaterial(String wallMaterial) {
        crossSection.getSlopeBatter().setWallMaterial(wallMaterial);
    }

    public Float getFillSlopeRatio() {
        return crossSection.getSlopeBatter().getFillRatio();
    }

    public void setFillSlopeRatio(Float fillSlopeRatio) {
        crossSection.getSlopeBatter().setFillRatio(fillSlopeRatio);
    }

    public Float getCutSlopeRatio() {
        return crossSection.getSlopeBatter().getCutRatio();
    }

    public void setCutSlopeRatio(Float cutSlopeRatio) {
        crossSection.getSlopeBatter().setCutRatio(cutSlopeRatio);
    }

    public String getFillSlopeMaterial() {
        return crossSection.getSlopeBatter().getFillMaterial();
    }

    public void setFillSlopeMaterial(String fillSlopeMaterial) {
        crossSection.getSlopeBatter().setFillMaterial(fillSlopeMaterial);
    }

    public String getCutSlopeMaterial() {
        return crossSection.getSlopeBatter().getCutMaterial();
    }

    public void setCutSlopeMaterial(String cutSlopeMaterial) {
        crossSection.getSlopeBatter().setCutMaterial(cutSlopeMaterial);
    }

    public Boolean getIncludeBikeLane() {
        return crossSection.getBikeLane().getEnabled();
    }

    public void setIncludeBikeLane(Boolean includeBikeLane) {
        crossSection.getBikeLane().setEnabled(includeBikeLane);
    }

    public Integer getBikeLaneWidth() {
        return crossSection.getBikeLane().getWidth();
    }

    public void setBikeLaneWidth(Integer bikeLaneWidth) {
        if (bikeLaneWidth == null) {
            crossSection.getBikeLane().setWidth(null);
            return;
        }
        crossSection.getBikeLane().setWidth(RoadParameterLimits.clampStripWidth(bikeLaneWidth));
    }

    public String getBikeLaneMaterial() {
        return crossSection.getBikeLane().getMaterial();
    }

    public void setBikeLaneMaterial(String bikeLaneMaterial) {
        crossSection.getBikeLane().setMaterial(bikeLaneMaterial);
    }

    public String getMedianMaterial() {
        return crossSection.getMedian().getMaterial();
    }

    public void setMedianMaterial(String medianMaterial) {
        crossSection.getMedian().setMaterial(medianMaterial);
    }

    public Integer getStreetlightSpacing() {
        return crossSection.getStreetFurniture().getStreetlightSpacing();
    }

    public void setStreetlightSpacing(Integer streetlightSpacing) {
        crossSection.getStreetFurniture().setStreetlightSpacing(
            RoadParameterLimits.normalizeStreetlightSpacing(streetlightSpacing));
    }

    public Float getMaxSlope() {
        return maxSlope;
    }

    public void setMaxSlope(Float maxSlope) {
        this.maxSlope = maxSlope != null ? RoadParameterLimits.clampGradePercent(maxSlope) : null;
    }

    public RoadTopologyMode getTopologyMode() {
        return topologyMode != null ? topologyMode : RoadTopologyMode.LINEAR;
    }

    public void setTopologyMode(RoadTopologyMode topologyMode) {
        this.topologyMode = topologyMode != null ? topologyMode : RoadTopologyMode.LINEAR;
    }

    public RoadLoopSeam getLoopSeam() {
        return loopSeam;
    }

    public void setLoopSeam(RoadLoopSeam loopSeam) {
        this.loopSeam = loopSeam;
    }

    public RoadHorizontalAlignment getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public void setHorizontalAlignment(RoadHorizontalAlignment horizontalAlignment) {
        this.horizontalAlignment = horizontalAlignment != null ? horizontalAlignment.copy() : null;
    }

    public RoadVerticalAlignment getVerticalAlignment() {
        return verticalAlignment;
    }

    public void setVerticalAlignment(RoadVerticalAlignment verticalAlignment) {
        this.verticalAlignment = verticalAlignment != null ? verticalAlignment.copy() : null;
    }

    public RoadVerticalMode getVerticalMode() {
        if (verticalMode != null) {
            return verticalMode;
        }
        return RoadVerticalMode.FIT_TERRAIN;
    }

    /** 持久化字段；{@code null} 表示未显式设置（加载时填默认）。 */
    public RoadVerticalMode getStoredVerticalMode() {
        return verticalMode;
    }

    public void setVerticalMode(RoadVerticalMode verticalMode) {
        this.verticalMode = verticalMode;
    }

    /** 持久化字段；{@code null} 表示未显式设置（加载时填默认）。 */
    public ProfileSource getStoredProfileSource() {
        return profileSource;
    }

    public void setProfileSource(ProfileSource profileSource) {
        this.profileSource = profileSource;
    }

    /** 有效纵断面来源；适应地形下默认自动设计。 */
    public ProfileSource getEffectiveProfileSource() {
        if (profileSource != null) {
            return profileSource;
        }
        if (verticalMode == RoadVerticalMode.MANUAL_PROFILE) {
            return ProfileSource.EDITED;
        }
        return ProfileSource.AUTO;
    }

    public int getEditedGeometrySignature() {
        return editedGeometrySignature;
    }

    public void setEditedGeometrySignature(int editedGeometrySignature) {
        this.editedGeometrySignature = editedGeometrySignature;
    }

    public RoadTerrainStyle getEffectiveTerrainStyle(RoadSystemConfig config) {
        return RoadTerrainStyle.effective(this, config);
    }

    public TerrainFollowPreset getEffectiveTerrainFollowPreset(RoadSystemConfig config) {
        return getEffectiveTerrainStyle(config).followPreset();
    }

    /** 持久化字段；{@code null} 表示继承全局默认地形风格。 */
    public RoadTerrainStyle getStoredTerrainStyle() {
        return terrainStyle;
    }

    public void setTerrainStyle(RoadTerrainStyle terrainStyle) {
        this.terrainStyle = terrainStyle;
    }

    /** 持久化字段；{@code null} 表示继承全局架桥净高阈值 / 地形风格表。 */
    public Integer getBridgeThreshold() {
        return bridgeThreshold;
    }

    public void setBridgeThreshold(Integer bridgeThreshold) {
        this.bridgeThreshold = RoadParameterLimits.clampBridgeThresholdNullable(bridgeThreshold);
    }

    public int getEffectiveBridgeThreshold(RoadSystemConfig defaults) {
        return RoadConstructionHeuristics.bridgeThreshold(this, defaults);
    }

    public ResolvedTunnelStyle getResolvedTunnelStyle(RoadSystemConfig config) {
        return TunnelStyle.resolve(this, config);
    }

    /** 持久化字段；{@code null} 表示继承全局默认隧道样式。 */
    public TunnelStyle getStoredTunnelStyle() {
        return tunnelStyle;
    }

    public void setTunnelStyle(TunnelStyle tunnelStyle) {
        this.tunnelStyle = tunnelStyle != null ? tunnelStyle.copy() : null;
    }

    /** 持久化字段；{@code null} 表示继承全局是否生成桥墩。 */
    public Boolean getGenerateBridgePillars() {
        return generateBridgePillars;
    }

    public void setGenerateBridgePillars(Boolean generateBridgePillars) {
        this.generateBridgePillars = generateBridgePillars;
    }

    public boolean getEffectiveGenerateBridgePillars(RoadSystemConfig defaults) {
        return generateBridgePillars != null
            ? generateBridgePillars
            : defaults != null && defaults.isGenerateBridgePillars();
    }

    /** 持久化字段；{@code null} 表示继承全局桥墩间距。 */
    public Double getBridgePierSpacingBlocks() {
        return bridgePierSpacingBlocks;
    }

    public void setBridgePierSpacingBlocks(Double bridgePierSpacingBlocks) {
        this.bridgePierSpacingBlocks =
            RoadParameterLimits.clampBridgePierSpacingBlocksNullable(bridgePierSpacingBlocks);
    }

    public double getEffectiveBridgePierSpacingBlocks(RoadSystemConfig defaults) {
        if (bridgePierSpacingBlocks != null) {
            return bridgePierSpacingBlocks;
        }
        return defaults != null
            ? defaults.getBridgePierSpacingBlocks()
            : RoadParameterLimits.DEFAULT_BRIDGE_PIER_SPACING_BLOCKS;
    }

    /** 持久化字段；{@code null} 表示继承全局桥墩材质。 */
    public String getBridgePierMaterial() {
        return bridgePierMaterial;
    }

    public void setBridgePierMaterial(String bridgePierMaterial) {
        this.bridgePierMaterial = bridgePierMaterial != null && !bridgePierMaterial.isBlank()
            ? bridgePierMaterial
            : null;
    }

    public String getEffectiveBridgePierMaterial(RoadSystemConfig defaults) {
        if (bridgePierMaterial != null && !bridgePierMaterial.isBlank()) {
            return bridgePierMaterial;
        }
        return defaults != null
            ? defaults.getBridgePierMaterial()
            : "minecraft:stone_bricks";
    }

    /** 持久化字段；{@code null} 表示继承全局桥墩样式。 */
    public BridgePierStyle getBridgePierStyle() {
        return bridgePierStyle;
    }

    public void setBridgePierStyle(BridgePierStyle bridgePierStyle) {
        this.bridgePierStyle = bridgePierStyle;
    }

    public BridgePierStyle getEffectiveBridgePierStyle(RoadSystemConfig defaults) {
        if (bridgePierStyle != null) {
            return bridgePierStyle;
        }
        return defaults != null ? defaults.getBridgePierStyle() : BridgePierStyle.SINGLE;
    }

    /** 持久化字段；{@code null} 表示继承全局桥墩直径。 */
    public Integer getBridgePierDiameterBlocks() {
        return bridgePierDiameterBlocks;
    }

    public void setBridgePierDiameterBlocks(Integer bridgePierDiameterBlocks) {
        this.bridgePierDiameterBlocks =
            RoadParameterLimits.clampBridgePierDiameterStoredNullable(bridgePierDiameterBlocks);
    }

    public int getEffectiveBridgePierDiameterBlocks(RoadSystemConfig defaults) {
        if (bridgePierDiameterBlocks != null) {
            return bridgePierDiameterBlocks;
        }
        return defaults != null
            ? defaults.getBridgePierDiameterBlocks()
            : RoadParameterLimits.DEFAULT_BRIDGE_PIER_DIAMETER_BLOCKS;
    }

    /** 持久化字段；{@code null} 表示继承全局双柱偏移。 */
    public Integer getBridgePierOffsetBlocks() {
        return bridgePierOffsetBlocks;
    }

    public void setBridgePierOffsetBlocks(Integer bridgePierOffsetBlocks) {
        this.bridgePierOffsetBlocks =
            RoadParameterLimits.clampBridgePierOffsetStoredNullable(bridgePierOffsetBlocks);
    }

    public int getEffectiveBridgePierOffsetBlocks(RoadSystemConfig defaults) {
        if (bridgePierOffsetBlocks != null) {
            return bridgePierOffsetBlocks;
        }
        return defaults != null
            ? defaults.getBridgePierOffsetBlocks()
            : RoadParameterLimits.DEFAULT_BRIDGE_PIER_OFFSET_BLOCKS;
    }

    /** 持久化字段；{@code null} 表示继承全局是否生成桥梁护栏。 */
    public Boolean getIncludeBridgeGuardrail() {
        return includeBridgeGuardrail;
    }

    public void setIncludeBridgeGuardrail(Boolean includeBridgeGuardrail) {
        this.includeBridgeGuardrail = includeBridgeGuardrail;
    }

    public boolean getEffectiveIncludeBridgeGuardrail(RoadSystemConfig defaults) {
        return includeBridgeGuardrail != null
            ? includeBridgeGuardrail
            : defaults != null && defaults.isIncludeBridgeGuardrail();
    }

    /** 持久化字段；{@code null} 表示继承全局桥梁护栏材质。 */
    public String getBridgeGuardrailMaterial() {
        return bridgeGuardrailMaterial;
    }

    public void setBridgeGuardrailMaterial(String bridgeGuardrailMaterial) {
        this.bridgeGuardrailMaterial = bridgeGuardrailMaterial != null && !bridgeGuardrailMaterial.isBlank()
            ? bridgeGuardrailMaterial
            : null;
    }

    public String getEffectiveBridgeGuardrailMaterial(RoadSystemConfig defaults) {
        if (bridgeGuardrailMaterial != null && !bridgeGuardrailMaterial.isBlank()) {
            return bridgeGuardrailMaterial;
        }
        return defaults != null
            ? defaults.getBridgeGuardrailMaterial()
            : "minecraft:oak_fence";
    }

    /** 持久化字段；{@code null} 表示继承全局桥梁护栏高度。 */
    public Integer getBridgeGuardrailHeightBlocks() {
        return bridgeGuardrailHeightBlocks;
    }

    public void setBridgeGuardrailHeightBlocks(Integer bridgeGuardrailHeightBlocks) {
        this.bridgeGuardrailHeightBlocks =
            RoadParameterLimits.clampBridgeGuardrailHeightBlocksNullable(bridgeGuardrailHeightBlocks);
    }

    public int getEffectiveBridgeGuardrailHeightBlocks(RoadSystemConfig defaults) {
        if (bridgeGuardrailHeightBlocks != null) {
            return bridgeGuardrailHeightBlocks;
        }
        return defaults != null
            ? defaults.getBridgeGuardrailHeightBlocks()
            : RoadParameterLimits.DEFAULT_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS;
    }

    public FlatVerticalIntent getFlatVerticalIntent() {
        return flatVerticalIntent;
    }

    public void setFlatVerticalIntent(FlatVerticalIntent flatVerticalIntent) {
        this.flatVerticalIntent = flatVerticalIntent != null ? flatVerticalIntent.copy() : null;
    }

    public RoadVariableCrossSections getVariableCrossSections() {
        return variableCrossSections;
    }

    public void setVariableCrossSections(RoadVariableCrossSections variableCrossSections) {
        this.variableCrossSections = variableCrossSections != null ? variableCrossSections.copy() : null;
    }

    public RoadStationFacilities getStationFacilities() {
        return stationFacilities;
    }

    public void setStationFacilities(RoadStationFacilities stationFacilities) {
        this.stationFacilities = stationFacilities != null ? stationFacilities.copy() : null;
    }

    /**
     * 设计链方向（入口 → 出口节点）；不可桩号化时返回 empty。
     */
    public Optional<RoadDesignDirection> designDirection(RoadNetwork network) {
        Optional<String> entry = RoadStationing.chainEntryNodeId(network, this);
        Optional<String> exit = RoadStationing.chainExitNodeId(network, this);
        if (entry.isEmpty() || exit.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RoadDesignDirection(entry.get(), exit.get()));
    }

    /**
     * 沿设计链的定向分段（edgeId、forward、startStation、length）；动态 derive，不持久化。
     */
    public List<OrientedRoadSegment> orientedSegments(RoadNetwork network) {
        return RoadStationing.orientedSegments(network, this);
    }

    /**
     * 分段 ID 集合，仅用于成员判定（{@code contains} / {@code size}）。
     * <p>
     * 需要沿道路链的顺序时，请使用 {@link #getOrderedSegmentIds()} 或
     * {@link RoadSegmentOrdering#orderedSegmentIds(RoadNetwork, Road)}。
     */
    public Set<String> getSegmentIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(segmentIds));
    }

    /**
     * 按道路内部存储顺序返回分段 ID。
     * <p>
     * 打断、拆分、重分配后若需与几何拓扑一致，应调用
     * {@link RoadSegmentOrdering#orderedSegmentIds(RoadNetwork, Road)} 或
     * {@link RoadSegmentOrdering#applyTopologicalOrder(RoadNetwork, Road)}。
     */
    public List<String> getOrderedSegmentIds() {
        return List.copyOf(segmentIds);
    }

    public void addSegment(String edgeId) {
        if (edgeId != null && !edgeId.isBlank() && !segmentIds.contains(edgeId)) {
            segmentIds.add(edgeId);
        }
    }

    public void removeSegment(String edgeId) {
        segmentIds.remove(edgeId);
    }

    /**
     * 按给定顺序重写 segmentIds，未出现在列表中的既有分段追加在末尾。
     */
    public void reorderSegments(List<String> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }
        List<String> remaining = new ArrayList<>(segmentIds);
        segmentIds.clear();
        for (String edgeId : orderedIds) {
            if (edgeId != null && !edgeId.isBlank() && remaining.remove(edgeId)) {
                segmentIds.add(edgeId);
            }
        }
        segmentIds.addAll(remaining);
    }

    public int getEffectiveWidth(RoadSystemConfig defaults) {
        // 未覆盖总宽时直接跟随全局配置，避免 resolve 写回 defaultLaneWidth 后挡住后续 setRoadWidth。
        if (getWidth() == null && defaults != null) {
            return defaults.getRoadWidth();
        }
        return crossSection.resolve(defaults).carriagewayWidth;
    }

    public MaterialMix getEffectiveMaterial(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).carriagewayMaterial;
    }

    public boolean getEffectiveIncludeSidewalk(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).includeSidewalk;
    }

    public int getEffectiveSidewalkWidth(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).sidewalkWidth;
    }

    public String getEffectiveSidewalkMaterial(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).sidewalkMaterial;
    }

    public boolean getEffectiveIncludeShoulder(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).includeShoulder;
    }

    public boolean getEffectiveIncludeSlopeBatter(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).includeSlopeBatter;
    }

    public boolean getEffectiveIncludeRetainingWall(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).includeRetainingWall;
    }

    public RoadsideEdgeMode getEffectiveRoadsideEdgeMode(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).roadsideEdgeMode();
    }

    public String getEffectiveRetainingWallMaterial(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).retainingWallMaterial;
    }

    public float getEffectiveFillSlopeRatio(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).fillSlopeRatio;
    }

    public float getEffectiveCutSlopeRatio(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).cutSlopeRatio;
    }

    public String getEffectiveFillSlopeMaterial(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).fillSlopeMaterial;
    }

    public String getEffectiveCutSlopeMaterial(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).cutSlopeMaterial;
    }

    public boolean getEffectiveIncludeBikeLane(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).includeBikeLane;
    }

    public int getEffectiveBikeLaneWidth(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).bikeLaneWidth;
    }

    public int getEffectiveShoulderWidth(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).shoulderWidth;
    }

    public String getEffectiveShoulderMaterial(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).shoulderMaterial;
    }

    public float getEffectiveMaxSlope(RoadSystemConfig defaults) {
        return maxSlope != null ? maxSlope : defaults.getMaxSlope();
    }

    Road copy() {
        Road copy = new Road(id, name, crossSection.copy(), maxSlope, new LinkedHashSet<>(segmentIds));
        copy.styleId = styleId;
        copy.themeId = themeId;
        copy.topologyMode = getTopologyMode();
        copy.loopSeam = loopSeam;
        copy.horizontalAlignment = horizontalAlignment != null ? horizontalAlignment.copy() : null;
        copy.verticalAlignment = verticalAlignment != null ? verticalAlignment.copy() : null;
        copy.verticalMode = verticalMode;
        copy.profileSource = profileSource;
        copy.editedGeometrySignature = editedGeometrySignature;
        copy.terrainStyle = terrainStyle;
        copy.bridgeThreshold = bridgeThreshold;
        copy.tunnelStyle = tunnelStyle != null ? tunnelStyle.copy() : null;
        copy.generateBridgePillars = generateBridgePillars;
        copy.bridgePierSpacingBlocks = bridgePierSpacingBlocks;
        copy.bridgePierMaterial = bridgePierMaterial;
        copy.bridgePierStyle = bridgePierStyle;
        copy.bridgePierDiameterBlocks = bridgePierDiameterBlocks;
        copy.bridgePierOffsetBlocks = bridgePierOffsetBlocks;
        copy.includeBridgeGuardrail = includeBridgeGuardrail;
        copy.bridgeGuardrailMaterial = bridgeGuardrailMaterial;
        copy.bridgeGuardrailHeightBlocks = bridgeGuardrailHeightBlocks;
        copy.flatVerticalIntent = flatVerticalIntent != null ? flatVerticalIntent.copy() : null;
        copy.variableCrossSections = variableCrossSections != null ? variableCrossSections.copy() : null;
        copy.stationFacilities = stationFacilities != null ? stationFacilities.copy() : null;
        return copy;
    }

    /** 复制工程属性到另一条逻辑道路（不含 segment 归属）。 */
    public void copyEngineeringFrom(Road source) {
        if (source == null) {
            return;
        }
        crossSection = source.crossSection.copy();
        maxSlope = source.maxSlope;
        styleId = source.styleId;
        themeId = source.themeId;
        terrainStyle = source.terrainStyle;
        bridgeThreshold = source.bridgeThreshold;
        tunnelStyle = source.tunnelStyle != null ? source.tunnelStyle.copy() : null;
        generateBridgePillars = source.generateBridgePillars;
        bridgePierSpacingBlocks = source.bridgePierSpacingBlocks;
        bridgePierMaterial = source.bridgePierMaterial;
        bridgePierStyle = source.bridgePierStyle;
        bridgePierDiameterBlocks = source.bridgePierDiameterBlocks;
        bridgePierOffsetBlocks = source.bridgePierOffsetBlocks;
        includeBridgeGuardrail = source.includeBridgeGuardrail;
        bridgeGuardrailMaterial = source.bridgeGuardrailMaterial;
        bridgeGuardrailHeightBlocks = source.bridgeGuardrailHeightBlocks;
        RoadVerticalIntentTransforms.copyIntentFrom(this, source);
    }
}
