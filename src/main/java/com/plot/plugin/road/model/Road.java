package com.plot.plugin.road.model;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.style.RoadStyleCatalog;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 逻辑道路（工程对象）。一条认领的道路可对应多条几何 {@link RoadEdge} 段。
 */
public class Road {
    private final String id;
    private String name;
    private RoadCrossSection crossSection = new RoadCrossSection();
    private String styleId;
    private Float maxSlope;
    private final Set<String> segmentIds = new LinkedHashSet<>();

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
            this.segmentIds.addAll(segmentIds);
        }
    }

    public void applyDefaults(RoadSystemConfig defaults) {
        if (defaults == null) {
            return;
        }
        crossSection.applyDefaults(defaults);
        maxSlope = defaults.getMaxSlope();
    }

    public void applyStyle(RoadStyle style) {
        if (style == null) {
            return;
        }
        style.applyTo(this);
    }

    public void applyStyle(String styleId, RoadSystemConfig defaults) {
        RoadStyle style = RoadStyleCatalog.findById(defaults, styleId);
        if (style != null) {
            applyStyle(style);
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

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Set<String> getSegmentIds() {
        return Set.copyOf(segmentIds);
    }

    public void addSegment(String edgeId) {
        if (edgeId != null && !edgeId.isBlank()) {
            segmentIds.add(edgeId);
        }
    }

    public void removeSegment(String edgeId) {
        segmentIds.remove(edgeId);
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
        Road copy = new Road(id, name, crossSection.copy(), maxSlope, segmentIds);
        copy.styleId = styleId;
        return copy;
    }
}
