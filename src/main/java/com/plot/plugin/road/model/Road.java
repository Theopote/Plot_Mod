package com.plot.plugin.road.model;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.CenterLineStyle;

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

    public void applyPreset(RoadSystemConfig.RoadPreset preset) {
        if (preset == null) {
            return;
        }
        crossSection = RoadCrossSection.fromPreset(preset);
        if (preset.maxSlope > 0f) {
            maxSlope = preset.maxSlope;
        }
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
        crossSection.getCarriageway().setWidth(width);
    }

    public String getMaterial() {
        return crossSection.getCarriageway().getMaterial();
    }

    public void setMaterial(String material) {
        crossSection.getCarriageway().setMaterial(material);
    }

    public Integer getLaneCount() {
        return crossSection.getCarriageway().getLaneCount();
    }

    public void setLaneCount(Integer laneCount) {
        if (laneCount == null || laneCount < 1) {
            return;
        }
        crossSection.getCarriageway().syncLaneCount(laneCount);
        if (crossSection.getMarkings().getLaneDividers() == null) {
            crossSection.getMarkings().setLaneDividers(laneCount > 1);
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
        crossSection.getMedian().setWidth(medianWidth);
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
        crossSection.getSidewalk().setWidth(sidewalkWidth);
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
        crossSection.getShoulder().setWidth(shoulderWidth);
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
        crossSection.getBikeLane().setWidth(bikeLaneWidth);
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
        crossSection.getStreetFurniture().setStreetlightSpacing(streetlightSpacing);
    }

    public Float getMaxSlope() {
        return maxSlope;
    }

    public void setMaxSlope(Float maxSlope) {
        this.maxSlope = maxSlope;
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

    public String getEffectiveMaterial(RoadSystemConfig defaults) {
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
        return new Road(id, name, crossSection.copy(), maxSlope, segmentIds);
    }
}
