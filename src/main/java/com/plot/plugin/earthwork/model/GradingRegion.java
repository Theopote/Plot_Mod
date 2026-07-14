package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 已认领的整平区域及生成参数
 */
public class GradingRegion {
    public static final String DEFAULT_FILL_MATERIAL = "minecraft:dirt";
    public static final float DEFAULT_FILL_FACTOR = 1.1f;
    public static final int DEFAULT_GRID_SIZE = 5;
    public static final int DEFAULT_SLOPE_PITCH_RATIO = 4;
    private static final int CONTROL_POINT_COUNT = 3;

    private final String id;
    private String name;
    private List<Vec2d> outerPoints;
    private GradingSurfaceMode surfaceMode = GradingSurfaceMode.FLAT;
    private boolean autoBalance = true;
    private Integer manualTargetElevation;
    private float fillFactor = DEFAULT_FILL_FACTOR;
    private String cutExposeMaterial = "";
    private String fillMaterial = DEFAULT_FILL_MATERIAL;
    private int gridSize = DEFAULT_GRID_SIZE;

    private double slopeDirectionDegrees = 0.0;
    private int slopePitchRatio = DEFAULT_SLOPE_PITCH_RATIO;
    private Double slopeAnchorCanvasX;
    private Double slopeAnchorCanvasY;
    private Integer slopeAnchorElevation;

    private final double[] threePointCanvasX = new double[CONTROL_POINT_COUNT];
    private final double[] threePointCanvasY = new double[CONTROL_POINT_COUNT];
    private final int[] threePointElevation = new int[] {64, 64, 64};

    private boolean fitSlopeBalanceCutFill = true;

    private transient long lastCutVolume;
    private transient long lastFillVolume;
    private transient int lastResolvedElevation;
    private transient int lastResolvedElevationMin;
    private transient int lastResolvedElevationMax;

    public GradingRegion(List<Vec2d> outerPoints) {
        this(UUID.randomUUID().toString(), outerPoints);
    }

    public GradingRegion(String id, List<Vec2d> outerPoints) {
        this.id = id;
        this.outerPoints = copyPoints(outerPoints);
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
        return copyPoints(outerPoints);
    }

    public void setOuterPoints(List<Vec2d> outerPoints) {
        this.outerPoints = copyPoints(outerPoints);
    }

    public GradingSurfaceMode getSurfaceMode() {
        return surfaceMode != null ? surfaceMode : GradingSurfaceMode.FLAT;
    }

    public void setSurfaceMode(GradingSurfaceMode surfaceMode) {
        this.surfaceMode = surfaceMode != null ? surfaceMode : GradingSurfaceMode.FLAT;
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

    public float getFillFactor() {
        return fillFactor;
    }

    public void setFillFactor(float fillFactor) {
        this.fillFactor = Math.max(1.0f, Math.min(2.0f, fillFactor));
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

    public int getGridSize() {
        return gridSize;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = Math.max(1, Math.min(20, gridSize));
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

    public long getLastCutVolume() {
        return lastCutVolume;
    }

    public void setLastCutVolume(long lastCutVolume) {
        this.lastCutVolume = Math.max(0L, lastCutVolume);
    }

    public long getLastFillVolume() {
        return lastFillVolume;
    }

    public void setLastFillVolume(long lastFillVolume) {
        this.lastFillVolume = Math.max(0L, lastFillVolume);
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
        return Math.abs(signedArea(outerPoints));
    }

    public static double signedArea(List<Vec2d> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        double area = 0.0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get((i + 1) % n);
            area += a.x * b.y - b.x * a.y;
        }
        return area / 2.0;
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
