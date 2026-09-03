package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.material.EarthMaterialClass;
import com.plot.core.material.MaterialConversionModel;

import java.util.List;

/**
 * 土方设计分区。Phase A 以 {@link GradingRegion} 为运行时参数载体，附加 Site 级元数据。
 */
public class GradingZone {
    public static final int DEFAULT_PRIORITY = 50;

    private final GradingRegion region;
    private GradingZoneType type = GradingZoneType.FLAT;
    private int priority = DEFAULT_PRIORITY;
    private boolean enabled = true;
    private MaterialConversionModel materialOverride;
    private EarthMaterialClass cutMaterialClass = EarthMaterialClass.UNKNOWN;
    private EarthMaterialClass fillMaterialClass = EarthMaterialClass.COMMON_FILL;
    private DesignSurface designSurface = new DesignSurface();
    private String buildingFootprintRef = "";
    private String roadEdgeRef = "";
    private ZoneEdgeSettings edgeSettings = new ZoneEdgeSettings();
    private VerticalAdjustmentPolicy verticalAdjustmentPolicy;
    private boolean verticalAdjustmentPolicyExplicit;

    public GradingZone(List<Vec2d> outerPoints) {
        this(new GradingRegion(outerPoints));
    }

    public GradingZone(String id, List<Vec2d> outerPoints) {
        this(new GradingRegion(id, outerPoints));
    }

    public GradingZone(String id, RegionGeometry geometry) {
        this(new GradingRegion(id, geometry));
    }

    public GradingZone(GradingRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("Grading region cannot be null");
        }
        this.region = region;
        this.type = GradingZoneType.fromSurfaceMode(region.getSurfaceMode());
        this.designSurface = DesignSurface.fromGradingRegion(region);
    }

    public static GradingZone fromGradingRegion(GradingRegion region) {
        return new GradingZone(region);
    }

    public String getId() {
        return region.getId();
    }

    public String getName() {
        return region.getName();
    }

    public void setName(String name) {
        region.setName(name);
    }

    public List<Vec2d> getOuterPoints() {
        return region.getOuterPoints();
    }

    public void setOuterPoints(List<Vec2d> outerPoints) {
        region.setOuterPoints(outerPoints);
    }

    public RegionGeometry getGeometry() {
        return region.getGeometry();
    }

    public void setGeometry(RegionGeometry geometry) {
        region.setGeometry(geometry);
    }

    public List<List<Vec2d>> getHoles() {
        return region.getHoles();
    }

    public void setHoles(List<List<Vec2d>> holes) {
        region.setHoles(holes);
    }

    public boolean containsCanvasPoint(Vec2d canvasPoint) {
        return region.containsCanvasPoint(canvasPoint);
    }

    public GradingZoneType getType() {
        return type != null ? type : GradingZoneType.FLAT;
    }

    public void setType(GradingZoneType type) {
        this.type = type != null ? type : GradingZoneType.FLAT;
        applyDefaultDesignSurfaceForType(this.type);
        applyDefaultMaterialClassesForType(this.type);
        clearVerticalAdjustmentPolicyOverride();
    }

    public String getBuildingFootprintRef() {
        return buildingFootprintRef != null ? buildingFootprintRef : "";
    }

    public void setBuildingFootprintRef(String buildingFootprintRef) {
        this.buildingFootprintRef = buildingFootprintRef != null ? buildingFootprintRef.trim() : "";
        if (!this.buildingFootprintRef.isBlank()) {
            getDesignSurface().setBuildingFootprintRef(this.buildingFootprintRef);
        }
    }

    public String getRoadEdgeRef() {
        return roadEdgeRef != null ? roadEdgeRef : "";
    }

    public void setRoadEdgeRef(String roadEdgeRef) {
        this.roadEdgeRef = roadEdgeRef != null ? roadEdgeRef.trim() : "";
        if (!this.roadEdgeRef.isBlank()) {
            getDesignSurface().setRoadEdgeRef(this.roadEdgeRef);
        }
    }

    public ZoneEdgeSettings getEdgeSettings() {
        if (edgeSettings == null) {
            edgeSettings = new ZoneEdgeSettings();
        }
        return edgeSettings;
    }

    public void setEdgeSettings(ZoneEdgeSettings edgeSettings) {
        this.edgeSettings = edgeSettings != null ? edgeSettings : new ZoneEdgeSettings();
    }

    private void applyDefaultDesignSurfaceForType(GradingZoneType zoneType) {
        DesignSurface surface = getDesignSurface();
        if (zoneType == GradingZoneType.BUILDING_PAD) {
            surface.setKind(DesignSurfaceKind.CONSTANT_ELEVATION);
            if (surface.getElevationSource() == DesignSurfaceElevationSource.MANUAL
                && surface.getElevation() == null
                && !getBuildingFootprintRef().isBlank()) {
                surface.setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
            }
        } else if (zoneType == GradingZoneType.EXCAVATION_PIT) {
            surface.setKind(DesignSurfaceKind.EXCAVATION_PIT);
            if (!getBuildingFootprintRef().isBlank()
                && surface.getElevationSource() == DesignSurfaceElevationSource.MANUAL
                && surface.getBottomElevation() == null) {
                surface.setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
            }
        } else if (zoneType == GradingZoneType.TERRAIN_FIT || zoneType == GradingZoneType.LANDSCAPE) {
            surface.setKind(DesignSurfaceKind.BEST_FIT_PLANE);
            region.setSurfaceMode(GradingSurfaceMode.BEST_FIT_PLANE);
        } else if (zoneType == GradingZoneType.ROAD_CORRIDOR) {
            surface.setKind(DesignSurfaceKind.ROAD_CORRIDOR);
        }
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public MaterialConversionModel getMaterialOverride() {
        return materialOverride;
    }

    public void setMaterialOverride(MaterialConversionModel materialOverride) {
        this.materialOverride = materialOverride;
    }

    public MaterialConversionModel resolveMaterialModel(MaterialConversionModel siteDefault) {
        if (materialOverride != null) {
            return materialOverride;
        }
        return siteDefault != null ? siteDefault : MaterialConversionModel.DEFAULT;
    }

    public EarthMaterialClass getCutMaterialClass() {
        return cutMaterialClass != null ? cutMaterialClass : EarthMaterialClass.UNKNOWN;
    }

    public void setCutMaterialClass(EarthMaterialClass cutMaterialClass) {
        this.cutMaterialClass = cutMaterialClass != null ? cutMaterialClass : EarthMaterialClass.UNKNOWN;
    }

    public EarthMaterialClass getFillMaterialClass() {
        return fillMaterialClass != null ? fillMaterialClass : EarthMaterialClass.COMMON_FILL;
    }

    public void setFillMaterialClass(EarthMaterialClass fillMaterialClass) {
        this.fillMaterialClass = fillMaterialClass != null ? fillMaterialClass : EarthMaterialClass.COMMON_FILL;
    }

    private void applyDefaultMaterialClassesForType(GradingZoneType zoneType) {
        if (zoneType == null) {
            return;
        }
        switch (zoneType) {
            case BUILDING_PAD, ROAD_CORRIDOR -> {
                fillMaterialClass = EarthMaterialClass.STRUCTURAL_FILL;
            }
            case EXCAVATION_PIT -> {
                fillMaterialClass = EarthMaterialClass.STRUCTURAL_FILL;
            }
            case LANDSCAPE -> {
                fillMaterialClass = EarthMaterialClass.TOPSOIL;
                if (cutMaterialClass == null || cutMaterialClass == EarthMaterialClass.UNKNOWN) {
                    cutMaterialClass = EarthMaterialClass.TOPSOIL;
                }
            }
            case TERRAIN_FIT -> {
                fillMaterialClass = EarthMaterialClass.COMMON_FILL;
            }
            case FLAT, SLOPED -> {
                if (fillMaterialClass == null) {
                    fillMaterialClass = EarthMaterialClass.COMMON_FILL;
                }
            }
        }
    }

    public String getCutExposeMaterial() {
        return region.getCutExposeMaterial();
    }

    public void setCutExposeMaterial(String cutExposeMaterial) {
        region.setCutExposeMaterial(cutExposeMaterial);
    }

    public String getFillMaterial() {
        return region.getFillMaterial();
    }

    public void setFillMaterial(String fillMaterial) {
        region.setFillMaterial(fillMaterial);
    }

    public int getPreviewGridSize() {
        return region.getPreviewGridSize();
    }

    public void setPreviewGridSize(int previewGridSize) {
        region.setPreviewGridSize(previewGridSize);
    }

    public DesignSurface getDesignSurface() {
        if (designSurface == null) {
            designSurface = DesignSurface.fromGradingRegion(region);
        }
        return designSurface;
    }

    public void setDesignSurface(DesignSurface designSurface) {
        this.designSurface = designSurface != null ? designSurface : new DesignSurface();
        this.designSurface.applyTo(region);
        if (type == GradingZoneType.BUILDING_PAD
            || type == GradingZoneType.EXCAVATION_PIT
            || type == GradingZoneType.TERRAIN_FIT
            || type == GradingZoneType.LANDSCAPE
            || type == GradingZoneType.ROAD_CORRIDOR) {
            return;
        }
        this.type = GradingZoneType.fromSurfaceMode(region.getSurfaceMode());
    }

    /**
     * 返回分区参数载体（与 UI / Generator 共享同一实例）。
     */
    public GradingRegion getRegion() {
        syncDesignSurfaceFromRegion();
        return region;
    }

    /**
     * 将 {@link DesignSurface} 写回 {@link GradingRegion}（保存 / 生成前调用）。
     */
    public void syncDesignSurfaceToRegion() {
        getDesignSurface().applyTo(region);
        type = GradingZoneType.fromSurfaceMode(region.getSurfaceMode());
    }

    /**
     * 从 {@link GradingRegion} 同步设计面（UI 编辑后调用）。
     */
    public void syncDesignSurfaceFromRegion() {
        if (type == GradingZoneType.BUILDING_PAD
            || type == GradingZoneType.EXCAVATION_PIT
            || type == GradingZoneType.TERRAIN_FIT
            || type == GradingZoneType.LANDSCAPE
            || type == GradingZoneType.ROAD_CORRIDOR) {
            return;
        }
        getDesignSurface().syncFrom(region);
        type = GradingZoneType.fromSurfaceMode(region.getSurfaceMode());
    }

    public double computeArea() {
        return region.computeArea();
    }

    public boolean isDelegatableToLegacyGenerator() {
        if (!enabled || !getType().isSupportedInMvp()) {
            return false;
        }
        DesignSurfaceKind kind = getDesignSurface().getKind();
        if (kind == DesignSurfaceKind.MATCH_EXISTING
            || kind == DesignSurfaceKind.MULTI_PLANE
            || kind == DesignSurfaceKind.DRAINAGE_SURFACE) {
            return false;
        }
        GradingSurfaceMode mode = region.getSurfaceMode();
        return mode != GradingSurfaceMode.MATCH_EXISTING
            && mode != GradingSurfaceMode.MULTI_PLANE
            && mode != GradingSurfaceMode.DRAINAGE_SURFACE;
    }

    public boolean isSupportedInComposer() {
        return enabled && getType().isSupportedInComposer();
    }

    public VerticalAdjustmentPolicy getVerticalAdjustmentPolicy() {
        if (verticalAdjustmentPolicyExplicit && verticalAdjustmentPolicy != null) {
            return verticalAdjustmentPolicy;
        }
        return VerticalAdjustmentPolicy.defaultFor(
            getType(),
            region.isAutoBalance(),
            getDesignSurface().getKind());
    }

    public void setVerticalAdjustmentPolicy(VerticalAdjustmentPolicy verticalAdjustmentPolicy) {
        if (verticalAdjustmentPolicy == null) {
            clearVerticalAdjustmentPolicyOverride();
            return;
        }
        this.verticalAdjustmentPolicy = verticalAdjustmentPolicy.copy();
        this.verticalAdjustmentPolicyExplicit = true;
    }

    public boolean hasExplicitVerticalAdjustmentPolicy() {
        return verticalAdjustmentPolicyExplicit && verticalAdjustmentPolicy != null;
    }

    public void clearVerticalAdjustmentPolicyOverride() {
        verticalAdjustmentPolicy = null;
        verticalAdjustmentPolicyExplicit = false;
    }

    /**
     * 场地平整 / 坡向分区随自动平衡开关切换锁定或可调；类型默认（建筑、基坑、道路、景观）不变。
     */
    public void syncVerticalPolicyWithAutoBalance() {
        GradingZoneType zoneType = getType();
        if (zoneType == GradingZoneType.FLAT || zoneType == GradingZoneType.SLOPED) {
            clearVerticalAdjustmentPolicyOverride();
        }
    }

    /**
     * Minecraft 简化开关：允许自动平衡调整本区标高。关闭即锁定。
     */
    public boolean isAutoAdjustElevation() {
        return allowsVerticalAdjustment();
    }

    public void setAutoAdjustElevation(boolean enabled) {
        setAutoAdjustElevation(enabled, VerticalAdjustmentPolicy.LANDSCAPE_RANGE);
    }

    public void setAutoAdjustElevation(boolean enabled, int maxAutoAdjustment) {
        if (!enabled) {
            setVerticalAdjustmentPolicy(VerticalAdjustmentPolicy.locked());
            return;
        }
        int range = Math.max(0, maxAutoAdjustment);
        setVerticalAdjustmentPolicy(
            VerticalAdjustmentPolicy.adjustable(range, VerticalAdjustmentPolicy.DEFAULT_WEIGHT));
    }

    public int getMaxAutoAdjustment() {
        VerticalAdjustmentPolicy policy = getVerticalAdjustmentPolicy();
        return Math.max(Math.abs(policy.getMinOffset()), Math.abs(policy.getMaxOffset()));
    }

    public boolean allowsVerticalAdjustment() {
        if (getDesignSurface().getKind() == DesignSurfaceKind.MATCH_EXISTING) {
            return false;
        }
        return getVerticalAdjustmentPolicy().allowsVerticalAdjustment();
    }

    public boolean isBalanceEligible() {
        return allowsVerticalAdjustment();
    }

    /**
     * 全场平衡不得改动的分区：{@link VerticalAdjustmentPolicy.Mode#LOCKED} /
     * {@link VerticalAdjustmentPolicy.Mode#DERIVED}、贴合现状。未覆盖策略时，
     * 建筑地坪 / 道路走廊 / 基坑锁定，关闭自动平衡的平整分区锁定。
     */
    public boolean isElevationLocked() {
        return !allowsVerticalAdjustment();
    }

    public int applyProposedVerticalOffset(int zoneAllocationOffset, int uniformOffset) {
        if (!allowsVerticalAdjustment()) {
            return 0;
        }
        return getVerticalAdjustmentPolicy().applyProposedOffset(zoneAllocationOffset, uniformOffset);
    }
}
