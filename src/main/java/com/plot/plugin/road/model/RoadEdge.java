package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 道路边（中心线及属性）
 */
public class RoadEdge {
    private final String id;
    private String startNodeId;
    private String endNodeId;
    private List<Vec2d> centerlinePoints;

    private Integer width;
    private String material;
    private Boolean includeSidewalk;
    private Integer sidewalkWidth;
    private String sidewalkMaterial;
    private Integer streetlightSpacing;
    private Float maxSlope;
    private List<SlopeOverride> slopeOverrides;

    public static class SlopeOverride {
        public double startDistance;
        public double endDistance;
        public float maxSlope;

        public SlopeOverride() {
        }

        public SlopeOverride(double startDistance, double endDistance, float maxSlope) {
            this.startDistance = startDistance;
            this.endDistance = endDistance;
            this.maxSlope = maxSlope;
        }

        public SlopeOverride copy() {
            return new SlopeOverride(startDistance, endDistance, maxSlope);
        }
    }

    public RoadEdge(String startNodeId, String endNodeId, List<Vec2d> centerlinePoints) {
        this(UUID.randomUUID().toString(), startNodeId, endNodeId, centerlinePoints,
            null, null, null, null, null, null, null, null);
    }

    public RoadEdge(String id, String startNodeId, String endNodeId, List<Vec2d> centerlinePoints,
                    Integer width, String material, Boolean includeSidewalk, Integer sidewalkWidth,
                    String sidewalkMaterial, Integer streetlightSpacing, Float maxSlope,
                    List<SlopeOverride> slopeOverrides) {
        this.id = id;
        this.startNodeId = startNodeId;
        this.endNodeId = endNodeId;
        this.centerlinePoints = copyPoints(centerlinePoints);
        this.width = width;
        this.material = material;
        this.includeSidewalk = includeSidewalk;
        this.sidewalkWidth = sidewalkWidth;
        this.sidewalkMaterial = sidewalkMaterial;
        this.streetlightSpacing = streetlightSpacing;
        this.maxSlope = maxSlope;
        this.slopeOverrides = slopeOverrides != null
            ? slopeOverrides.stream().map(SlopeOverride::copy).toList()
            : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public void setStartNodeId(String startNodeId) {
        this.startNodeId = startNodeId;
    }

    public String getEndNodeId() {
        return endNodeId;
    }

    public void setEndNodeId(String endNodeId) {
        this.endNodeId = endNodeId;
    }

    public List<Vec2d> getCenterlinePoints() {
        return copyPoints(centerlinePoints);
    }

    public void setCenterlinePoints(List<Vec2d> centerlinePoints) {
        this.centerlinePoints = copyPoints(centerlinePoints);
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

    public List<SlopeOverride> getSlopeOverrides() {
        return slopeOverrides.stream().map(SlopeOverride::copy).toList();
    }

    public void setSlopeOverrides(List<SlopeOverride> slopeOverrides) {
        this.slopeOverrides = slopeOverrides != null
            ? slopeOverrides.stream().map(SlopeOverride::copy).toList()
            : new ArrayList<>();
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

    public float getEffectiveMaxSlope(double distanceAlongEdge, RoadSystemConfig defaults) {
        if (slopeOverrides != null) {
            for (SlopeOverride override : slopeOverrides) {
                if (distanceAlongEdge >= override.startDistance && distanceAlongEdge <= override.endDistance) {
                    return override.maxSlope;
                }
            }
        }
        return getEffectiveMaxSlope(defaults);
    }

    public double getLength() {
        if (centerlinePoints == null || centerlinePoints.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < centerlinePoints.size() - 1; i++) {
            total += centerlinePoints.get(i).distance(centerlinePoints.get(i + 1));
        }
        return total;
    }

    RoadEdge copy() {
        return new RoadEdge(id, startNodeId, endNodeId, centerlinePoints,
            width, material, includeSidewalk, sidewalkWidth, sidewalkMaterial,
            streetlightSpacing, maxSlope, slopeOverrides);
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>();
        if (points != null) {
            for (Vec2d point : points) {
                copy.add(point.copy());
            }
        }
        return copy;
    }
}
