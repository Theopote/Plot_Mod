package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;

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
    private EarthMaterialProperties materialOverride;
    private DesignSurface designSurface = new DesignSurface();
    private String buildingFootprintRef = "";

    public GradingZone(List<Vec2d> outerPoints) {
        this(new GradingRegion(outerPoints));
    }

    public GradingZone(String id, List<Vec2d> outerPoints) {
        this(new GradingRegion(id, outerPoints));
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

    public GradingZoneType getType() {
        return type != null ? type : GradingZoneType.FLAT;
    }

    public void setType(GradingZoneType type) {
        this.type = type != null ? type : GradingZoneType.FLAT;
        applyDefaultDesignSurfaceForType(this.type);
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

    public EarthMaterialProperties getMaterialOverride() {
        return materialOverride;
    }

    public void setMaterialOverride(EarthMaterialProperties materialOverride) {
        this.materialOverride = materialOverride;
    }

    public EarthMaterialProperties resolveMaterialModel(EarthMaterialProperties siteDefault) {
        if (materialOverride != null) {
            return materialOverride;
        }
        return siteDefault != null ? siteDefault : EarthMaterialProperties.DEFAULT;
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
        if (type == GradingZoneType.BUILDING_PAD || type == GradingZoneType.EXCAVATION_PIT) {
            return;
        }
        getDesignSurface().syncFrom(region);
        type = GradingZoneType.fromSurfaceMode(region.getSurfaceMode());
    }

    public double computeArea() {
        return region.computeArea();
    }

    public boolean isDelegatableToLegacyGenerator() {
        return enabled && getType().isSupportedInMvp();
    }

    public boolean isSupportedInComposer() {
        return enabled && getType().isSupportedInComposer();
    }
}
