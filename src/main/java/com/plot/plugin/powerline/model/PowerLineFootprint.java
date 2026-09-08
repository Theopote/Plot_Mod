package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
    private String towerFamilyId;
    private String stylePackId;
    private MaterialMix groundWireMaterial = MaterialMix.single("minecraft:chain");
    private final List<PoleOverride> poleOverrides = new ArrayList<>();
    private final List<PoleLayoutConstraint> layoutConstraints = new ArrayList<>();
    private String engineeringProfileId;
    private boolean engineeringAnalysisEnabled = false;
    private boolean terrainAvoidanceEnabled = false;
    private boolean automaticTowerSelectionEnabled;

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
        if (this.maxPoleSpacing < this.minPoleSpacing) {
            this.maxPoleSpacing = this.minPoleSpacing;
        }
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

    public boolean hasPoleDesign() {
        return poleDesignId != null && !poleDesignId.isBlank();
    }

    public String getTowerFamilyId() {
        return towerFamilyId;
    }

    public void setTowerFamilyId(String towerFamilyId) {
        this.towerFamilyId = towerFamilyId != null && towerFamilyId.isBlank() ? null : towerFamilyId;
    }

    public boolean hasTowerFamily() {
        return towerFamilyId != null && !towerFamilyId.isBlank();
    }

    public String getStylePackId() {
        return stylePackId;
    }

    public void setStylePackId(String stylePackId) {
        this.stylePackId = stylePackId != null && stylePackId.isBlank() ? null : stylePackId;
    }

    public MaterialMix getGroundWireMaterial() {
        return groundWireMaterial;
    }

    public void setGroundWireMaterial(MaterialMix groundWireMaterial) {
        this.groundWireMaterial = groundWireMaterial != null
            ? groundWireMaterial.copy()
            : MaterialMix.single("minecraft:chain");
    }

    public List<PoleOverride> getPoleOverrides() {
        return Collections.unmodifiableList(new ArrayList<>(poleOverrides));
    }

    public void setPoleOverrides(List<PoleOverride> overrides) {
        poleOverrides.clear();
        if (overrides == null) {
            return;
        }
        for (PoleOverride override : overrides) {
            if (override != null) {
                poleOverrides.add(override.copy());
            }
        }
    }

    public void addPoleOverride(PoleOverride override) {
        if (override != null) {
            poleOverrides.add(override.copy());
        }
    }

    public void clearPoleOverrides() {
        poleOverrides.clear();
    }

    public List<PoleLayoutConstraint> getLayoutConstraints() {
        return Collections.unmodifiableList(new ArrayList<>(layoutConstraints));
    }

    public void setLayoutConstraints(List<PoleLayoutConstraint> constraints) {
        layoutConstraints.clear();
        if (constraints == null) {
            return;
        }
        for (PoleLayoutConstraint constraint : constraints) {
            if (constraint != null) {
                layoutConstraints.add(constraint.copy());
            }
        }
    }

    public void addLayoutConstraint(PoleLayoutConstraint constraint) {
        if (constraint != null) {
            layoutConstraints.add(constraint.copy());
        }
    }

    public void clearLayoutConstraints() {
        layoutConstraints.clear();
    }

    public String getEngineeringProfileId() {
        return engineeringProfileId;
    }

    public void setEngineeringProfileId(String engineeringProfileId) {
        this.engineeringProfileId = engineeringProfileId != null && engineeringProfileId.isBlank()
            ? null
            : engineeringProfileId;
    }

    public String effectiveEngineeringProfileId() {
        return engineeringProfileId != null && !engineeringProfileId.isBlank()
            ? engineeringProfileId
            : com.plot.plugin.powerline.engineering.EngineeringRuleProfile.GENERIC_PLANNING_ID;
    }

    public boolean isEngineeringAnalysisEnabled() {
        return engineeringAnalysisEnabled;
    }

    public void setEngineeringAnalysisEnabled(boolean engineeringAnalysisEnabled) {
        this.engineeringAnalysisEnabled = engineeringAnalysisEnabled;
    }

    public boolean isTerrainAvoidanceEnabled() {
        return terrainAvoidanceEnabled;
    }

    public void setTerrainAvoidanceEnabled(boolean terrainAvoidanceEnabled) {
        this.terrainAvoidanceEnabled = terrainAvoidanceEnabled;
    }

    public boolean isAutomaticTowerSelectionEnabled() {
        return automaticTowerSelectionEnabled;
    }

    public void setAutomaticTowerSelectionEnabled(boolean automaticTowerSelectionEnabled) {
        this.automaticTowerSelectionEnabled = automaticTowerSelectionEnabled;
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

    /**
     * 影响生成结果的参数指纹（不含名称等纯展示字段）。
     */
    public int generationFingerprint() {
        int hash = 1;
        for (Vec2d point : pathPoints) {
            hash = 31 * hash + Double.hashCode(point.x);
            hash = 31 * hash + Double.hashCode(point.y);
        }
        hash = 31 * hash + Double.hashCode(minPoleSpacing);
        hash = 31 * hash + Double.hashCode(maxPoleSpacing);
        hash = 31 * hash + Double.hashCode(cornerAngleThreshold);
        if (!hasPoleDesign()) {
            hash = 31 * hash + Double.hashCode(poleHeight);
        }
        hash = 31 * hash + Double.hashCode(sagRatio);
        hash = 31 * hash + materialFingerprint(wireMaterial);
        hash = 31 * hash + materialFingerprint(poleMaterial);
        hash = 31 * hash + Objects.hashCode(poleDesignId);
        hash = 31 * hash + Objects.hashCode(towerFamilyId);
        hash = 31 * hash + Objects.hashCode(stylePackId);
        hash = 31 * hash + materialFingerprint(groundWireMaterial);
        hash = 31 * hash + poleOverrides.hashCode();
        hash = 31 * hash + layoutConstraints.hashCode();
        hash = 31 * hash + Objects.hashCode(engineeringProfileId);
        hash = 31 * hash + Boolean.hashCode(engineeringAnalysisEnabled);
        hash = 31 * hash + Boolean.hashCode(terrainAvoidanceEnabled);
        hash = 31 * hash + Boolean.hashCode(automaticTowerSelectionEnabled);
        return hash;
    }

    private static int materialFingerprint(MaterialMix mix) {
        if (mix == null) {
            return 0;
        }
        int hash = Objects.hashCode(mix.getPrimaryMaterial());
        hash = 31 * hash + Objects.hashCode(mix.getAccentMaterial());
        hash = 31 * hash + Float.hashCode(mix.getAccentRatio());
        return hash;
    }
}
