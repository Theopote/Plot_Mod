package com.plot.plugin.road.model;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.RoadCrossSection;
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
    private RoadHorizontalAlignment horizontalAlignment;
    private RoadVerticalAlignment verticalAlignment;
    private RoadVerticalMode verticalMode;
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
        this(id, name, RoadCrossSection.fromLegacy(
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
    }

    /** 清空道路级与横断面的全部显式覆盖，恢复继承全局配置。 */
    public void inheritAllDefaults() {
        crossSection.inheritAll();
        maxSlope = null;
        styleId = null;
        themeId = null;
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

    /** @deprecated 使用 {@link #applyStyle(RoadStyle)} */
    @Deprecated
    public void applyPreset(RoadStyle preset) {
        applyStyle(preset);
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
        this.name = trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
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
        crossSection.getCarriageway().setWidth(clamped);
        crossSection.getCarriageway().reclampLaneWidths(clamped);
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
        Integer width = crossSection.getCarriageway().getWidth();
        if (width != null) {
            crossSection.getCarriageway().reclampLaneWidths(width);
        }
        if (crossSection.getMarkings().getLaneDividers() == null) {
            crossSection.getMarkings().setLaneDividers(clamped > 1);
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
        crossSection.getMedian().setWidth(RoadParameterLimits.clampStripWidth(medianWidth));
    }

    public Boolean getLaneDividers() {
        return crossSection.getMarkings().getLaneDividers();
    }

    public void setLaneDividers(Boolean laneDividers) {
        crossSection.getMarkings().setLaneDividers(laneDividers);
    }

    public CenterLineStyle getCenterLineStyle() {
        return crossSection.getMarkings().getCenterLineStyle();
    }

    public void setCenterLineStyle(CenterLineStyle centerLineStyle) {
        crossSection.getMarkings().setCenterLineStyle(centerLineStyle);
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
        return crossSection.getSlopeBatter().getEnabled();
    }

    public void setIncludeSlopeBatter(Boolean includeSlopeBatter) {
        crossSection.getSlopeBatter().setEnabled(includeSlopeBatter);
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

    public Boolean getIncludeDrainage() {
        return crossSection.getDrain().getEnabled();
    }

    public void setIncludeDrainage(Boolean includeDrainage) {
        crossSection.getDrain().setEnabled(includeDrainage);
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
        return verticalAlignment != null && !verticalAlignment.isEmpty()
            ? RoadVerticalMode.MANUAL_PROFILE
            : RoadVerticalMode.AUTO_SMOOTH;
    }

    public void setVerticalMode(RoadVerticalMode verticalMode) {
        this.verticalMode = verticalMode;
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

    public boolean getEffectiveIncludeDrainage(RoadSystemConfig defaults) {
        return crossSection.resolve(defaults).includeDrain;
    }

    public float getEffectiveMaxSlope(RoadSystemConfig defaults) {
        return maxSlope != null ? maxSlope : defaults.getMaxSlope();
    }

    Road copy() {
        Road copy = new Road(id, name, crossSection.copy(), maxSlope, new LinkedHashSet<>(segmentIds));
        copy.styleId = styleId;
        copy.themeId = themeId;
        copy.topologyMode = getTopologyMode();
        copy.horizontalAlignment = horizontalAlignment != null ? horizontalAlignment.copy() : null;
        copy.verticalAlignment = verticalAlignment != null ? verticalAlignment.copy() : null;
        copy.verticalMode = verticalMode;
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
    }
}
