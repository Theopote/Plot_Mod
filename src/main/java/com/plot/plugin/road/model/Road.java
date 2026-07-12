package com.plot.plugin.road.model;

import com.plot.plugin.config.RoadSystemConfig;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 逻辑道路（工程对象）。一条认领的道路可对应多条几何 {@link RoadEdge} 段。
 */
public class Road {
    private final String id;
    private String name;
    private Integer width;
    private String material;
    private Boolean includeSidewalk;
    private Integer sidewalkWidth;
    private String sidewalkMaterial;
    private Integer streetlightSpacing;
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
        this.id = id;
        this.name = name;
        this.width = width;
        this.material = material;
        this.includeSidewalk = includeSidewalk;
        this.sidewalkWidth = sidewalkWidth;
        this.sidewalkMaterial = sidewalkMaterial;
        this.streetlightSpacing = streetlightSpacing;
        this.maxSlope = maxSlope;
        if (segmentIds != null) {
            this.segmentIds.addAll(segmentIds);
        }
    }

    public void applyDefaults(RoadSystemConfig defaults) {
        if (defaults == null) {
            return;
        }
        width = defaults.getRoadWidth();
        material = defaults.getSelectedMaterial();
        includeSidewalk = defaults.isIncludeSidewalk();
        sidewalkWidth = defaults.getSidewalkWidth();
        sidewalkMaterial = defaults.getSelectedSidewalkMaterial();
        maxSlope = defaults.getMaxSlope();
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

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public Boolean getIncludeSidewalk() {
        return includeSidewalk;
    }

    public void setIncludeSidewalk(Boolean includeSidewalk) {
        this.includeSidewalk = includeSidewalk;
    }

    public Integer getSidewalkWidth() {
        return sidewalkWidth;
    }

    public void setSidewalkWidth(Integer sidewalkWidth) {
        this.sidewalkWidth = sidewalkWidth;
    }

    public String getSidewalkMaterial() {
        return sidewalkMaterial;
    }

    public void setSidewalkMaterial(String sidewalkMaterial) {
        this.sidewalkMaterial = sidewalkMaterial;
    }

    public Integer getStreetlightSpacing() {
        return streetlightSpacing;
    }

    public void setStreetlightSpacing(Integer streetlightSpacing) {
        this.streetlightSpacing = streetlightSpacing;
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
        return width != null ? width : defaults.getRoadWidth();
    }

    public String getEffectiveMaterial(RoadSystemConfig defaults) {
        return material != null ? material : defaults.getSelectedMaterial();
    }

    public boolean getEffectiveIncludeSidewalk(RoadSystemConfig defaults) {
        return includeSidewalk != null ? includeSidewalk : defaults.isIncludeSidewalk();
    }

    public int getEffectiveSidewalkWidth(RoadSystemConfig defaults) {
        return sidewalkWidth != null ? sidewalkWidth : defaults.getSidewalkWidth();
    }

    public String getEffectiveSidewalkMaterial(RoadSystemConfig defaults) {
        if (sidewalkMaterial != null) {
            return sidewalkMaterial;
        }
        return defaults.getSelectedSidewalkMaterial();
    }

    public float getEffectiveMaxSlope(RoadSystemConfig defaults) {
        return maxSlope != null ? maxSlope : defaults.getMaxSlope();
    }

    Road copy() {
        return new Road(
            id, name, width, material, includeSidewalk, sidewalkWidth,
            sidewalkMaterial, streetlightSpacing, maxSlope, segmentIds);
    }
}
