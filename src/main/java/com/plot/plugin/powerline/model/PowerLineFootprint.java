package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 已认领的电力线路走线及生成参数。
 */
public class PowerLineFootprint {
    public static final String DEFAULT_POLE_MATERIAL = "minecraft:oak_fence";
    public static final String DEFAULT_WIRE_MATERIAL = "minecraft:iron_bars";

    private final String id;
    private String name;
    private List<Vec2d> pathPoints = new ArrayList<>();
    private String roadId;
    private double minPoleSpacing = 6.0;
    private double maxPoleSpacing = 20.0;
    private double cornerAngleThreshold = 5.0;
    private double poleHeight = 10.0;
    private double sagRatio = 0.15;
    private MaterialMix wireMaterial = MaterialMix.single(DEFAULT_WIRE_MATERIAL);
    private MaterialMix poleMaterial = MaterialMix.single(DEFAULT_POLE_MATERIAL);
    private String poleDesignId;

    public PowerLineFootprint(List<Vec2d> pathPoints) {
        this.id = UUID.randomUUID().toString();
        this.roadId = UUID.randomUUID().toString();
        setPathPoints(pathPoints);
        this.name = "";
    }

    PowerLineFootprint(String id, String roadId) {
        this.id = id;
        this.roadId = roadId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name != null && !name.isBlank() ? name : id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Vec2d> getPathPoints() {
        return List.copyOf(pathPoints);
    }

    public void setPathPoints(List<Vec2d> pathPoints) {
        if (pathPoints == null || pathPoints.size() < 2) {
            throw new IllegalArgumentException("Power line path requires at least 2 points");
        }
        this.pathPoints = new ArrayList<>(pathPoints.size());
        for (Vec2d point : pathPoints) {
            this.pathPoints.add(point.copy());
        }
    }

    public String getRoadId() {
        return roadId;
    }

    public void setRoadId(String roadId) {
        this.roadId = roadId;
    }

    public double getMinPoleSpacing() {
        return minPoleSpacing;
    }

    public void setMinPoleSpacing(double minPoleSpacing) {
        this.minPoleSpacing = Math.max(1.0, minPoleSpacing);
    }

    public double getMaxPoleSpacing() {
        return maxPoleSpacing;
    }

    public void setMaxPoleSpacing(double maxPoleSpacing) {
        this.maxPoleSpacing = Math.max(getMinPoleSpacing(), maxPoleSpacing);
    }

    public double getCornerAngleThreshold() {
        return cornerAngleThreshold;
    }

    public void setCornerAngleThreshold(double cornerAngleThreshold) {
        this.cornerAngleThreshold = Math.max(0.0, Math.min(180.0, cornerAngleThreshold));
    }

    public double getPoleHeight() {
        return poleHeight;
    }

    public void setPoleHeight(double poleHeight) {
        this.poleHeight = Math.max(1.0, Math.min(64.0, poleHeight));
    }

    public double getSagRatio() {
        return sagRatio;
    }

    public void setSagRatio(double sagRatio) {
        this.sagRatio = Math.max(0.0, Math.min(1.0, sagRatio));
    }

    public MaterialMix getWireMaterial() {
        return wireMaterial;
    }

    public void setWireMaterial(MaterialMix wireMaterial) {
        this.wireMaterial = wireMaterial != null
            ? wireMaterial.copy()
            : MaterialMix.single(DEFAULT_WIRE_MATERIAL);
    }

    public MaterialMix getPoleMaterial() {
        return poleMaterial;
    }

    public void setPoleMaterial(MaterialMix poleMaterial) {
        this.poleMaterial = poleMaterial != null
            ? poleMaterial.copy()
            : MaterialMix.single(DEFAULT_POLE_MATERIAL);
    }

    public String getPoleDesignId() {
        return poleDesignId;
    }

    public void setPoleDesignId(String poleDesignId) {
        this.poleDesignId = poleDesignId != null && poleDesignId.isBlank() ? null : poleDesignId;
    }

    public double computePathLength() {
        double length = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            length += pathPoints.get(i - 1).distance(pathPoints.get(i));
        }
        return length;
    }

    public int estimatePoleCount() {
        return com.plot.plugin.powerline.PowerPoleLayoutUtils.computePolePositions(
            pathPoints, cornerAngleThreshold, maxPoleSpacing).size();
    }
}
