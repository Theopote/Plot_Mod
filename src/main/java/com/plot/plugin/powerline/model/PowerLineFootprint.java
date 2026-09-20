package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.parametric.TowerGeneratorConfig;
import com.plot.plugin.powerline.path.PowerLineSourceDescriptor;
import com.plot.plugin.powerline.path.PowerLineSourcePath;
import com.plot.plugin.powerline.path.PolylineSourcePath;
import com.plot.core.model.Shape;
import com.plot.plugin.powerline.style.PowerLineStyleDefinition;
import com.plot.plugin.powerline.style.PowerLineStyleInstance;
import com.plot.plugin.powerline.style.StyleOverrides;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 已认领的电力线路走线及生成参数。
 */
public class PowerLineFootprint {
    public static final String DEFAULT_POLE_MATERIAL = "minecraft:oak_fence";
    public static final String DEFAULT_WIRE_MATERIAL = "minecraft:iron_bars";
    public static final double DEFAULT_SAG_RATIO = 0.15;
    /** 过近警告阈值与最大档距滑块的下限（格）。 */
    public static final double MIN_CONFIGURABLE_SPACING = 5.0;
    /** 高级滑块绝对上限（格）。 */
    public static final double ABSOLUTE_MAX_POLE_SPACING = 300.0;

    private final String id;
    private String name;
    private List<Vec2d> pathPoints = new ArrayList<>();
    private transient PathBounds cachedPathBounds;
    /** 认领时的参考路径快照；无则回退为 pathPoints 折线。 */
    private PowerLineSourceDescriptor sourceDescriptor;
    /** 快照路径是否为闭合环路（无 sourceDescriptor 时由认领写入）。 */
    private boolean closedPath;
    private String roadId;
    private double closeSpacingWarningThreshold = 15.0;
    private double maxPoleSpacing = 30.0;
    private double cornerAngleThreshold = 5.0;
    private double poleHeight = 10.0;
    private final List<PoleOverride> poleOverrides = new ArrayList<>();
    private final List<PoleLayoutConstraint> layoutConstraints = new ArrayList<>();
    /** 杆塔布置模式；默认按固定档距自动插杆。 */
    private PoleSpacingMode poleSpacingMode = PoleSpacingMode.AUTO_SPACING;
    /** {@link PoleSpacingMode#TOWER_COUNT} 时沿路径等距分布的杆塔数量。 */
    private int targetTowerCount = 2;
    /** Base preset + 运行时推导的 overrides；见 {@link #styleInstance()}。 */
    private final PowerLineStyleState styleState = new PowerLineStyleState();

    public PowerLineFootprint(List<Vec2d> pathPoints) {
        this.id = UUID.randomUUID().toString();
        this.roadId = UUID.randomUUID().toString();
        setPathPoints(pathPoints);
        this.name = "";
    }

    /** UI 预览等场景：用稳定 seed 绑定材质解析，无真实线路上下文。 */
    public static PowerLineFootprint forPreviewSeed(String materialSeedKey) {
        String seed = materialSeedKey != null && !materialSeedKey.isBlank()
            ? materialSeedKey
            : "powerline_preview";
        PowerLineFootprint footprint = new PowerLineFootprint(seed, seed);
        footprint.setPathPoints(List.of(new Vec2d(0, 0), new Vec2d(1, 0)));
        return footprint;
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
        cachedPathBounds = null;
    }

    public PowerLineSourceDescriptor getSourceDescriptor() {
        return sourceDescriptor;
    }

    public void setSourceDescriptor(PowerLineSourceDescriptor sourceDescriptor) {
        this.sourceDescriptor = sourceDescriptor;
    }

    /** 解除画布参考路径关联，保留当前已同步的杆塔折线作为独立线路。 */
    public void clearSourceDescriptor() {
        this.sourceDescriptor = null;
    }

    public void bindSource(Shape shape) {
        this.sourceDescriptor = PowerLineSourceDescriptor.capture(shape);
    }

    public PowerLineSourcePath resolveSourcePath() {
        return resolveSourcePath(null);
    }

    public PowerLineSourcePath resolveSourcePath(Shape liveShape) {
        PowerLineSourcePath resolved = PowerLineSourceDescriptor.resolve(liveShape, sourceDescriptor);
        if (resolved != null) {
            return resolved;
        }
        return PolylineSourcePath.of(pathPoints, closedPath);
    }

    public boolean hasSourcePath() {
        return sourceDescriptor != null;
    }

    public String getSourceShapeId() {
        return sourceDescriptor != null ? sourceDescriptor.shapeId() : null;
    }

    public void setClosedPath(boolean closedPath) {
        this.closedPath = closedPath;
    }

    public boolean isClosedLoop() {
        if (sourceDescriptor != null) {
            return resolveSourcePath().isClosed();
        }
        return closedPath;
    }

    /** 路径在平面坐标下的轴对齐包围盒（随路径修改失效重算）。 */
    public PathBounds pathBounds() {
        if (cachedPathBounds == null) {
            cachedPathBounds = computePathBounds();
        }
        return cachedPathBounds;
    }

    public record PathBounds(double minX, double minZ, double maxX, double maxZ) {
    }

    private PathBounds computePathBounds() {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec2d point : pathPoints) {
            minX = Math.min(minX, point.x);
            minZ = Math.min(minZ, point.y);
            maxX = Math.max(maxX, point.x);
            maxZ = Math.max(maxZ, point.y);
        }
        if (pathPoints.isEmpty()) {
            return new PathBounds(0.0, 0.0, 0.0, 0.0);
        }
        return new PathBounds(minX, minZ, maxX, maxZ);
    }

    public String getRoadId() {
        return roadId;
    }

    public void setRoadId(String roadId) {
        this.roadId = roadId;
    }

    public double getCloseSpacingWarningThreshold() {
        return closeSpacingWarningThreshold;
    }

    public void setCloseSpacingWarningThreshold(double closeSpacingWarningThreshold) {
        this.closeSpacingWarningThreshold = Math.max(
            MIN_CONFIGURABLE_SPACING,
            closeSpacingWarningThreshold);
    }

    public double getMaxPoleSpacing() {
        return maxPoleSpacing;
    }

    public void setMaxPoleSpacing(double maxPoleSpacing) {
        this.maxPoleSpacing = Math.min(
            ABSOLUTE_MAX_POLE_SPACING,
            Math.max(MIN_CONFIGURABLE_SPACING, maxPoleSpacing));
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
        return styleState.resolveSagRatio(styleDefinition());
    }

    public void setSagRatio(double sagRatio) {
        styleState.setSagRatio(sagRatio, styleDefinition());
    }

    public double getMaxSagDepth() {
        return styleState.resolveMaxSagDepth(styleDefinition());
    }

    /**
     * @param maxSagDepth 单跨最大下垂深度（格），{@code <= 0} 表示不单独限制（沿用工程 profile）
     */
    public void setMaxSagDepth(double maxSagDepth) {
        styleState.setMaxSagDepth(maxSagDepth, styleDefinition());
    }

    public boolean isMaxSagDepthUnlimited() {
        return styleState.isMaxSagDepthUnlimited(styleDefinition());
    }

    public MaterialMix getWireMaterial() {
        return styleState.resolveWireMaterial(styleDefinition());
    }

    public void setWireMaterial(MaterialMix wireMaterial) {
        styleState.setWireMaterial(wireMaterial, styleDefinition());
    }

    public MaterialMix getPoleMaterial() {
        return styleState.resolvePoleMaterial(styleDefinition());
    }

    public void setPoleMaterial(MaterialMix poleMaterial) {
        styleState.setPoleMaterial(poleMaterial, styleDefinition());
    }

    public String getPoleDesignId() {
        return styleState.resolvePoleDesignId(styleDefinition());
    }

    public void setPoleDesignId(String poleDesignId) {
        styleState.setPoleDesignId(poleDesignId, styleDefinition());
    }

    public boolean hasPoleDesign() {
        String poleDesignId = getPoleDesignId();
        return poleDesignId != null && !poleDesignId.isBlank();
    }

    public String getTowerFamilyId() {
        return styleState.resolveTowerFamilyId(styleDefinition());
    }

    public void setTowerFamilyId(String towerFamilyId) {
        styleState.setTowerFamilyId(towerFamilyId, styleDefinition());
    }

    public boolean hasTowerFamily() {
        String towerFamilyId = getTowerFamilyId();
        return towerFamilyId != null && !towerFamilyId.isBlank();
    }

    public String getStylePresetId() {
        return styleState.presetId();
    }

    public void setStylePresetId(String stylePresetId) {
        styleState.setPresetId(stylePresetId);
    }

    public PowerLineStyleState styleState() {
        return styleState;
    }

    public TowerGeneratorConfig getParametricTowerConfig() {
        return styleState.parametricConfig();
    }

    public void setParametricTowerConfig(TowerGeneratorConfig parametricTowerConfig) {
        styleState.setParametricConfig(parametricTowerConfig);
    }

    public boolean hasParametricTowerConfig() {
        return styleState.hasParametricConfig();
    }

    public MaterialMix getTopWireMaterial() {
        return styleState.resolveTopWireMaterial(styleDefinition());
    }

    public void setTopWireMaterial(MaterialMix topWireMaterial) {
        styleState.setTopWireMaterial(topWireMaterial, styleDefinition());
    }

    public List<PoleOverride> getPoleOverrides() {
        return List.copyOf(poleOverrides);
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
        return List.copyOf(layoutConstraints);
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

    public PoleSpacingMode getPoleSpacingMode() {
        return poleSpacingMode != null ? poleSpacingMode : PoleSpacingMode.AUTO_SPACING;
    }

    public void setPoleSpacingMode(PoleSpacingMode poleSpacingMode) {
        this.poleSpacingMode = poleSpacingMode != null
            ? poleSpacingMode
            : PoleSpacingMode.AUTO_SPACING;
    }

    public int getTargetTowerCount() {
        return Math.max(2, targetTowerCount);
    }

    public void setTargetTowerCount(int targetTowerCount) {
        this.targetTowerCount = Math.max(2, targetTowerCount);
    }

    public StyleOverrides getStyleOverrides() {
        return styleState.overrides();
    }

    public void clearStyleOverrides() {
        styleState.clearOverrides();
    }

    /** Base preset + overrides 风格实例视图。 */
    public PowerLineStyleInstance styleInstance() {
        return PowerLineStyleInstance.of(this);
    }

    /** 画布折线长度（canvas units），不含 Minecraft 投影。 */
    public double computePathLength() {
        double length = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            length += pathPoints.get(i - 1).distance(pathPoints.get(i));
        }
        return length;
    }

    /** 投影到 Minecraft XZ 后的路径总长度（blocks）。 */
    public double computeWorldPathLength(com.plot.api.world.ICoordinateService coordinates) {
        return java.util.Objects.requireNonNull(coordinates, "coordinates")
            .pathWorldLength(pathPoints);
    }

    public int estimatePoleCount(com.plot.api.world.ICoordinateService coordinates) {
        return com.plot.plugin.powerline.PowerPoleLayoutUtils.computePoleSites(this, coordinates).size();
    }

    /** 影响杆塔布局的指纹（路径、档距、布置模式等）。 */
    public int layoutFingerprint() {
        int hash = 1;
        if (sourceDescriptor != null) {
            hash = 31 * hash + sourceDescriptor.fingerprint();
        } else {
            for (Vec2d point : pathPoints) {
                hash = 31 * hash + Double.hashCode(point.x);
                hash = 31 * hash + Double.hashCode(point.y);
            }
        }
        hash = 31 * hash + Double.hashCode(closeSpacingWarningThreshold);
        hash = 31 * hash + Double.hashCode(maxPoleSpacing);
        hash = 31 * hash + Double.hashCode(cornerAngleThreshold);
        hash = 31 * hash + poleOverrides.hashCode();
        hash = 31 * hash + layoutConstraints.hashCode();
        hash = 31 * hash + Objects.hashCode(poleSpacingMode);
        hash = 31 * hash + targetTowerCount;
        return hash;
    }

    /** 影响视觉/导线/塔型的风格指纹（生效值 + 风格状态）。 */
    public int styleFingerprint() {
        int hash = 1;
        if (!hasPoleDesign()) {
            hash = 31 * hash + Double.hashCode(poleHeight);
        }
        hash = 31 * hash + Double.hashCode(getSagRatio());
        hash = 31 * hash + Double.hashCode(getMaxSagDepth());
        hash = 31 * hash + materialFingerprint(getWireMaterial());
        hash = 31 * hash + materialFingerprint(getPoleMaterial());
        hash = 31 * hash + Objects.hashCode(getPoleDesignId());
        hash = 31 * hash + Objects.hashCode(getTowerFamilyId());
        hash = 31 * hash + materialFingerprint(getTopWireMaterial());
        hash = 31 * hash + styleState.generationFingerprint();
        return hash;
    }

    private PowerLineStyleDefinition styleDefinition() {
        return styleState.resolveDefinition();
    }

    /** 影响塔/线几何的指纹（预览缓存用，不含纯分析开关）。 */
    public int geometryFingerprint() {
        return 31 * layoutFingerprint() + styleFingerprint();
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
