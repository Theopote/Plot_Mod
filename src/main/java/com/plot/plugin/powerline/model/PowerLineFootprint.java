package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PowerLineSagUtils;
import com.plot.plugin.powerline.style.PowerLineStyleInstance;
import com.plot.plugin.powerline.style.StyleOverrides;

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
    /** 玩家可在 Route 高级区配置的最小间距下限（格）。 */
    public static final double MIN_CONFIGURABLE_SPACING = 5.0;
    /** 高级滑块绝对上限（格）。 */
    public static final double ABSOLUTE_MAX_POLE_SPACING = 300.0;

    private final String id;
    private String name;
    private List<Vec2d> pathPoints = new ArrayList<>();
    private String roadId;
    private double minPoleSpacing = 15.0;
    private double maxPoleSpacing = 30.0;
    private double cornerAngleThreshold = 5.0;
    private double poleHeight = 10.0;
    private double sagRatio = 0.15;
    /** 单跨最大下垂深度（格），{@code <= 0} 表示不限制。 */
    private double maxSagDepth = PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;
    private MaterialMix wireMaterial = MaterialMix.single(DEFAULT_WIRE_MATERIAL);
    private MaterialMix poleMaterial = MaterialMix.single(DEFAULT_POLE_MATERIAL);
    private String poleDesignId;
    private String towerFamilyId;
    /** 当前选中的风格预设 id。 */
    private String stylePresetId;
    /** 塔顶架空装饰线材质（视觉层次），非电气接地系统。 */
    private MaterialMix topWireMaterial = MaterialMix.single("minecraft:chain");
    private final List<PoleOverride> poleOverrides = new ArrayList<>();
    private final List<PoleLayoutConstraint> layoutConstraints = new ArrayList<>();
    private boolean lineChecksEnabled = true;
    private boolean terrainAvoidanceEnabled = true;
    private boolean automaticTowerSelectionEnabled;
    /** 玩家曾在 Route 高级区手工调整间距；切换风格时不自动覆盖。 */
    private boolean spacingCustomized;
    /** 相对 base {@link com.plot.plugin.powerline.style.PowerLineStyleDefinition} 的偏离项；见 {@link #styleInstance()}。 */
    private final StyleOverrides styleOverrides = new StyleOverrides();

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
        this.minPoleSpacing = Math.max(MIN_CONFIGURABLE_SPACING, minPoleSpacing);
        if (this.maxPoleSpacing < this.minPoleSpacing) {
            this.maxPoleSpacing = this.minPoleSpacing;
        }
    }

    public double getMaxPoleSpacing() {
        return maxPoleSpacing;
    }

    public void setMaxPoleSpacing(double maxPoleSpacing) {
        this.maxPoleSpacing = Math.min(
            ABSOLUTE_MAX_POLE_SPACING,
            Math.max(getMinPoleSpacing(), maxPoleSpacing));
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

    public double getMaxSagDepth() {
        return maxSagDepth;
    }

    /**
     * @param maxSagDepth 单跨最大下垂深度（格），{@code <= 0} 表示不单独限制（沿用工程 profile）
     */
    public void setMaxSagDepth(double maxSagDepth) {
        this.maxSagDepth = maxSagDepth <= 0.0 ? 0.0 : Math.max(1.0, Math.min(64.0, maxSagDepth));
    }

    public boolean isMaxSagDepthUnlimited() {
        return maxSagDepth <= 0.0;
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

    public String getStylePresetId() {
        return stylePresetId;
    }

    public void setStylePresetId(String stylePresetId) {
        this.stylePresetId = stylePresetId != null && stylePresetId.isBlank() ? null : stylePresetId;
    }

    /** @deprecated use {@link #getStylePresetId()} */
    @Deprecated
    public String getStylePackId() {
        return getStylePresetId();
    }

    /** @deprecated use {@link #setStylePresetId(String)} */
    @Deprecated
    public void setStylePackId(String stylePackId) {
        setStylePresetId(stylePackId);
    }

    public MaterialMix getTopWireMaterial() {
        return topWireMaterial;
    }

    public void setTopWireMaterial(MaterialMix topWireMaterial) {
        this.topWireMaterial = topWireMaterial != null
            ? topWireMaterial.copy()
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

    public void removeLayoutConstraint(int index) {
        if (index >= 0 && index < layoutConstraints.size()) {
            layoutConstraints.remove(index);
        }
    }

    public boolean isLineChecksEnabled() {
        return lineChecksEnabled;
    }

    public void setLineChecksEnabled(boolean lineChecksEnabled) {
        this.lineChecksEnabled = lineChecksEnabled;
    }

    /** 是否启用任意视觉/常识性检查（线路检查或地形净空）。 */
    public boolean isVisualChecksEnabled() {
        return lineChecksEnabled || terrainAvoidanceEnabled;
    }

    public void setVisualChecksEnabled(boolean enabled) {
        lineChecksEnabled = enabled;
        terrainAvoidanceEnabled = enabled;
    }

    /** @deprecated use {@link #isLineChecksEnabled()} */
    @Deprecated
    public boolean isEngineeringAnalysisEnabled() {
        return isLineChecksEnabled();
    }

    /** @deprecated use {@link #setLineChecksEnabled(boolean)} */
    @Deprecated
    public void setEngineeringAnalysisEnabled(boolean engineeringAnalysisEnabled) {
        setLineChecksEnabled(engineeringAnalysisEnabled);
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

    public boolean isSpacingCustomized() {
        return spacingCustomized;
    }

    public void setSpacingCustomized(boolean spacingCustomized) {
        this.spacingCustomized = spacingCustomized;
    }

    public StyleOverrides getStyleOverrides() {
        return styleOverrides;
    }

    public void clearStyleOverrides() {
        styleOverrides.clear();
    }

    /** Base preset + overrides 风格实例视图。 */
    public PowerLineStyleInstance styleInstance() {
        return PowerLineStyleInstance.of(this);
    }

    public double computePathLength() {
        double length = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            length += pathPoints.get(i - 1).distance(pathPoints.get(i));
        }
        return length;
    }

    public int estimatePoleCount() {
        return com.plot.plugin.powerline.PowerPoleLayoutUtils.computePoleSites(this).size();
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
        hash = 31 * hash + Double.hashCode(maxSagDepth);
        hash = 31 * hash + materialFingerprint(wireMaterial);
        hash = 31 * hash + materialFingerprint(poleMaterial);
        hash = 31 * hash + Objects.hashCode(poleDesignId);
        hash = 31 * hash + Objects.hashCode(towerFamilyId);
        hash = 31 * hash + Objects.hashCode(stylePresetId);
        hash = 31 * hash + materialFingerprint(topWireMaterial);
        hash = 31 * hash + poleOverrides.hashCode();
        hash = 31 * hash + layoutConstraints.hashCode();
        hash = 31 * hash + Boolean.hashCode(lineChecksEnabled);
        hash = 31 * hash + Boolean.hashCode(terrainAvoidanceEnabled);
        hash = 31 * hash + Boolean.hashCode(automaticTowerSelectionEnabled);
        hash = 31 * hash + Boolean.hashCode(spacingCustomized);
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
