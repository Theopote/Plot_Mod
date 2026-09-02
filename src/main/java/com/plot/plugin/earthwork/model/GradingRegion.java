package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 已认领的整平区域及生成参数
 */
public class GradingRegion {
    public static final String DEFAULT_FILL_MATERIAL = "minecraft:dirt";
    public static final int DEFAULT_PREVIEW_GRID_SIZE = 5;
    /** @deprecated 请改用 {@link #DEFAULT_PREVIEW_GRID_SIZE} */
    @Deprecated
    public static final int DEFAULT_GRID_SIZE = DEFAULT_PREVIEW_GRID_SIZE;
    public static final int DEFAULT_SLOPE_PITCH_RATIO = 4;
    private static final int CONTROL_POINT_COUNT = 3;

    private final String id;
    private String name;
    private RegionGeometry geometry = RegionGeometry.empty();
    private GradingSurfaceMode surfaceMode = GradingSurfaceMode.LEVEL_PAD;
    private boolean autoBalance = true;
    private Integer manualTargetElevation;
    private MaterialConversionModel materialProperties = MaterialConversionModel.DEFAULT;
    private String cutExposeMaterial = "";
    private String fillMaterial = DEFAULT_FILL_MATERIAL;
    private int previewGridSize = DEFAULT_PREVIEW_GRID_SIZE;

    private double slopeDirectionDegrees = 0.0;
    private int slopePitchRatio = DEFAULT_SLOPE_PITCH_RATIO;
    private Double slopeAnchorCanvasX;
    private Double slopeAnchorCanvasY;
    private Integer slopeAnchorElevation;

    private final double[] threePointCanvasX = new double[CONTROL_POINT_COUNT];
    private final double[] threePointCanvasY = new double[CONTROL_POINT_COUNT];
    private final int[] threePointElevation = new int[] {64, 64, 64};

    private boolean fitSlopeBalanceCutFill = true;

    private transient EarthworkVolumeReport lastVolumeReport = EarthworkVolumeReport.empty();
    private transient int lastResolvedElevation;
    private transient int lastResolvedElevationMin;
    private transient int lastResolvedElevationMax;

    public GradingRegion(List<Vec2d> outerPoints) {
        this(UUID.randomUUID().toString(), outerPoints);
    }

    public GradingRegion(String id, List<Vec2d> outerPoints) {
        this.id = id;
        this.geometry = RegionGeometry.of(copyPoints(outerPoints));
        this.name = id.substring(0, Math.min(8, id.length()));
    }

    public GradingRegion(String id, RegionGeometry geometry) {
        this.id = id;
        this.geometry = geometry != null ? geometry : RegionGeometry.empty();
        this.name = id.substring(0, Math.min(8, id.length()));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null && !name.isBlank() ? name.trim() : this.name;
    }

    public List<Vec2d> getOuterPoints() {
        return geometry.outerRing();
    }

    public void setOuterPoints(List<Vec2d> outerPoints) {
        this.geometry = geometry.withOuterRing(copyPoints(outerPoints));
    }

    public RegionGeometry getGeometry() {
        return geometry;
    }

    public void setGeometry(RegionGeometry geometry) {
        this.geometry = geometry != null ? geometry : RegionGeometry.empty();
    }

    public List<List<Vec2d>> getHoles() {
        return geometry.holes();
    }

    public void setHoles(List<List<Vec2d>> holes) {
        this.geometry = geometry.withHoles(holes);
    }

    public boolean containsCanvasPoint(Vec2d canvasPoint) {
        return geometry.contains(canvasPoint);
    }

    public GradingSurfaceMode getSurfaceMode() {
        return surfaceMode != null ? surfaceMode : GradingSurfaceMode.LEVEL_PAD;
    }

    public void setSurfaceMode(GradingSurfaceMode surfaceMode) {
        this.surfaceMode = surfaceMode != null ? surfaceMode : GradingSurfaceMode.LEVEL_PAD;
    }

    public boolean isAutoBalance() {
        return autoBalance;
    }

    public void setAutoBalance(boolean autoBalance) {
        this.autoBalance = autoBalance;
    }

    public Integer getManualTargetElevation() {
        return manualTargetElevation;
    }

    public void setManualTargetElevation(Integer manualTargetElevation) {
        this.manualTargetElevation = manualTargetElevation;
    }

    public MaterialConversionModel getMaterialProperties() {
        return materialProperties != null ? materialProperties : MaterialConversionModel.DEFAULT;
    }

    public void setMaterialProperties(MaterialConversionModel materialProperties) {
        this.materialProperties = materialProperties != null
            ? materialProperties
            : MaterialConversionModel.DEFAULT;
    }

    /**
     * 解析用于平衡/方量换算的材料模型：区域未单独设置时继承场地默认。
     */
    public MaterialConversionModel resolveMaterialModel(MaterialConversionModel siteDefault) {
        MaterialConversionModel site = siteDefault != null ? siteDefault : MaterialConversionModel.DEFAULT;
        if (usesSiteMaterialDefault()) {
            return site;
        }
        return getMaterialProperties();
    }

    public boolean usesSiteMaterialDefault() {
        return materialProperties == null || materialProperties == MaterialConversionModel.DEFAULT;
    }

    public String getCutExposeMaterial() {
        return cutExposeMaterial != null ? cutExposeMaterial : "";
    }

    public void setCutExposeMaterial(String cutExposeMaterial) {
        this.cutExposeMaterial = cutExposeMaterial != null ? cutExposeMaterial.trim() : "";
    }

    public String getFillMaterial() {
        return fillMaterial != null && !fillMaterial.isBlank()
            ? fillMaterial
            : DEFAULT_FILL_MATERIAL;
    }

    public void setFillMaterial(String fillMaterial) {
        this.fillMaterial = fillMaterial != null && !fillMaterial.isBlank()
            ? fillMaterial.trim()
            : DEFAULT_FILL_MATERIAL;
    }

    public int getPreviewGridSize() {
        return previewGridSize;
    }

    public void setPreviewGridSize(int previewGridSize) {
        this.previewGridSize = Math.max(1, Math.min(20, previewGridSize));
    }

    /** @deprecated 请改用 {@link #getPreviewGridSize()} */
    @Deprecated
    public int getGridSize() {
        return getPreviewGridSize();
    }

    /** @deprecated 请改用 {@link #setPreviewGridSize(int)} */
    @Deprecated
    public void setGridSize(int gridSize) {
        setPreviewGridSize(gridSize);
    }

    public double getSlopeDirectionDegrees() {
        return slopeDirectionDegrees;
    }

    public void setSlopeDirectionDegrees(double slopeDirectionDegrees) {
        this.slopeDirectionDegrees = ((slopeDirectionDegrees % 360.0) + 360.0) % 360.0;
    }

    public int getSlopePitchRatio() {
        return slopePitchRatio;
    }

    public void setSlopePitchRatio(int slopePitchRatio) {
        this.slopePitchRatio = Math.max(1, Math.min(32, slopePitchRatio));
    }

    public Vec2d getSlopeAnchorCanvas() {
        if (slopeAnchorCanvasX == null || slopeAnchorCanvasY == null) {
            return null;
        }
        return new Vec2d(slopeAnchorCanvasX, slopeAnchorCanvasY);
    }

    public void setSlopeAnchorCanvas(Vec2d anchor) {
        if (anchor == null) {
            slopeAnchorCanvasX = null;
            slopeAnchorCanvasY = null;
            return;
        }
        slopeAnchorCanvasX = anchor.x;
        slopeAnchorCanvasY = anchor.y;
    }

    public Integer getSlopeAnchorElevation() {
        return slopeAnchorElevation;
    }

    public void setSlopeAnchorElevation(Integer slopeAnchorElevation) {
        this.slopeAnchorElevation = slopeAnchorElevation;
    }

    public double getThreePointCanvasX(int index) {
        return threePointCanvasX[clampControlIndex(index)];
    }

    public double getThreePointCanvasY(int index) {
        return threePointCanvasY[clampControlIndex(index)];
    }

    public int getThreePointElevation(int index) {
        return threePointElevation[clampControlIndex(index)];
    }

    public void setThreePointControl(int index, Vec2d canvasPoint, int elevation) {
        int safeIndex = clampControlIndex(index);
        if (canvasPoint != null) {
            threePointCanvasX[safeIndex] = canvasPoint.x;
            threePointCanvasY[safeIndex] = canvasPoint.y;
        }
        threePointElevation[safeIndex] = elevation;
    }

    public void setThreePointCanvasX(int index, double canvasX) {
        threePointCanvasX[clampControlIndex(index)] = canvasX;
    }

    public void setThreePointCanvasY(int index, double canvasY) {
        threePointCanvasY[clampControlIndex(index)] = canvasY;
    }

    public void setThreePointElevation(int index, int elevation) {
        threePointElevation[clampControlIndex(index)] = elevation;
    }

    public boolean isFitSlopeBalanceCutFill() {
        return fitSlopeBalanceCutFill;
    }

    public void setFitSlopeBalanceCutFill(boolean fitSlopeBalanceCutFill) {
        this.fitSlopeBalanceCutFill = fitSlopeBalanceCutFill;
    }

    public EarthworkVolumeReport getLastVolumeReport() {
        return lastVolumeReport != null ? lastVolumeReport : EarthworkVolumeReport.empty();
    }

    public void setLastVolumeReport(EarthworkVolumeReport lastVolumeReport) {
        this.lastVolumeReport = lastVolumeReport != null ? lastVolumeReport : EarthworkVolumeReport.empty();
    }

    public int getLastResolvedElevation() {
        return lastResolvedElevation;
    }

    public void setLastResolvedElevation(int lastResolvedElevation) {
        this.lastResolvedElevation = lastResolvedElevation;
    }

    public int getLastResolvedElevationMin() {
        return lastResolvedElevationMin;
    }

    public void setLastResolvedElevationMin(int lastResolvedElevationMin) {
        this.lastResolvedElevationMin = lastResolvedElevationMin;
    }

    public int getLastResolvedElevationMax() {
        return lastResolvedElevationMax;
    }

    public void setLastResolvedElevationMax(int lastResolvedElevationMax) {
        this.lastResolvedElevationMax = lastResolvedElevationMax;
    }

    public double computeArea() {
        return geometry.area();
    }

    public static double signedArea(List<Vec2d> points) {
        return PolygonRegionUtils.signedAreaOfRing(points);
    }

    private static List<Vec2d> copyPoints(List<Vec2d> points) {
        List<Vec2d> copy = new ArrayList<>();
        if (points != null) {
            for (Vec2d point : points) {
                if (point != null) {
                    copy.add(new Vec2d(point.x, point.y));
                }
            }
        }
        return copy;
    }

    private static int clampControlIndex(int index) {
        if (index <= 0) {
            return 0;
        }
        return Math.min(index, CONTROL_POINT_COUNT - 1);
    }
}
