package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadParameterLimits;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 道路几何段（中心线 + 拓扑）。工程属性归属 {@link Road}。
 */
public class RoadEdge {
    private final String id;
    private String startNodeId;
    private String endNodeId;
    private List<Vec2d> centerlinePoints;
    private String roadId;
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
            this.maxSlope = RoadParameterLimits.clampGradePercent(maxSlope);
        }

        public SlopeOverride copy() {
            return new SlopeOverride(startDistance, endDistance, maxSlope);
        }
    }

    public RoadEdge(String startNodeId, String endNodeId, List<Vec2d> centerlinePoints) {
        this(UUID.randomUUID().toString(), startNodeId, endNodeId, centerlinePoints, null, null);
    }

    public RoadEdge(
            String id,
            String startNodeId,
            String endNodeId,
            List<Vec2d> centerlinePoints,
            String roadId,
            List<SlopeOverride> slopeOverrides) {
        this.id = id;
        this.startNodeId = startNodeId;
        this.endNodeId = endNodeId;
        this.centerlinePoints = copyPoints(centerlinePoints);
        this.roadId = roadId;
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

    public String getRoadId() {
        return roadId;
    }

    public void setRoadId(String roadId) {
        this.roadId = roadId;
    }

    /** @deprecated 使用 {@link #getRoadId()} */
    @Deprecated
    public String getSourceRoadId() {
        return roadId;
    }

    /** @deprecated 使用 {@link #setRoadId(String)} */
    @Deprecated
    public void setSourceRoadId(String sourceRoadId) {
        this.roadId = sourceRoadId;
    }

    public List<SlopeOverride> getSlopeOverrides() {
        return slopeOverrides.stream().map(SlopeOverride::copy).toList();
    }

    public void setSlopeOverrides(List<SlopeOverride> slopeOverrides) {
        if (slopeOverrides == null) {
            this.slopeOverrides = new ArrayList<>();
            return;
        }
        this.slopeOverrides = slopeOverrides.stream()
            .map(override -> {
                SlopeOverride copy = override.copy();
                copy.maxSlope = RoadParameterLimits.clampGradePercent(copy.maxSlope);
                return copy;
            })
            .toList();
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
        return new RoadEdge(id, startNodeId, endNodeId, centerlinePoints, roadId, slopeOverrides);
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
