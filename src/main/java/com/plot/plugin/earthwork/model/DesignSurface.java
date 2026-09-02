package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;

/**
 * 分区设计面定义（JSON v2 显式结构）。
 */
public class DesignSurface {
    private DesignSurfaceKind kind = DesignSurfaceKind.FLAT;
    private boolean autoBalance = true;
    private Integer manualTargetElevation;
    private boolean fitSlopeBalanceCutFill = true;

    private double slopeDirectionDegrees;
    private int slopePitchRatio = GradingRegion.DEFAULT_SLOPE_PITCH_RATIO;
    private Double anchorCanvasX;
    private Double anchorCanvasY;
    private Integer anchorElevation;

    private final double[] threePointCanvasX = new double[3];
    private final double[] threePointCanvasY = new double[3];
    private final int[] threePointElevation = new int[] {64, 64, 64};

    private Integer elevation;
    private String buildingFootprintRef = "";
    private String elevationSource = DesignSurfaceElevationSource.MANUAL.name();
    private Integer bottomElevation;
    private int workingMarginBlocks = 1;

    public DesignSurfaceKind getKind() {
        return kind != null ? kind : DesignSurfaceKind.FLAT;
    }

    public void setKind(DesignSurfaceKind kind) {
        this.kind = kind != null ? kind : DesignSurfaceKind.FLAT;
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

    public boolean isFitSlopeBalanceCutFill() {
        return fitSlopeBalanceCutFill;
    }

    public void setFitSlopeBalanceCutFill(boolean fitSlopeBalanceCutFill) {
        this.fitSlopeBalanceCutFill = fitSlopeBalanceCutFill;
    }

    public double getSlopeDirectionDegrees() {
        return slopeDirectionDegrees;
    }

    public void setSlopeDirectionDegrees(double slopeDirectionDegrees) {
        this.slopeDirectionDegrees = slopeDirectionDegrees;
    }

    public int getSlopePitchRatio() {
        return slopePitchRatio;
    }

    public void setSlopePitchRatio(int slopePitchRatio) {
        this.slopePitchRatio = slopePitchRatio;
    }

    public Vec2d getAnchorCanvas() {
        if (anchorCanvasX == null || anchorCanvasY == null) {
            return null;
        }
        return new Vec2d(anchorCanvasX, anchorCanvasY);
    }

    public void setAnchorCanvas(Vec2d anchor) {
        if (anchor == null) {
            anchorCanvasX = null;
            anchorCanvasY = null;
            return;
        }
        anchorCanvasX = anchor.x;
        anchorCanvasY = anchor.y;
    }

    public Integer getAnchorElevation() {
        return anchorElevation;
    }

    public void setAnchorElevation(Integer anchorElevation) {
        this.anchorElevation = anchorElevation;
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

    public Integer getElevation() {
        return elevation;
    }

    public void setElevation(Integer elevation) {
        this.elevation = elevation;
    }

    public String getBuildingFootprintRef() {
        return buildingFootprintRef != null ? buildingFootprintRef : "";
    }

    public void setBuildingFootprintRef(String buildingFootprintRef) {
        this.buildingFootprintRef = buildingFootprintRef != null ? buildingFootprintRef.trim() : "";
    }

    public DesignSurfaceElevationSource getElevationSource() {
        return DesignSurfaceElevationSource.fromId(elevationSource);
    }

    public void setElevationSource(DesignSurfaceElevationSource elevationSource) {
        this.elevationSource = elevationSource != null ? elevationSource.name() : DesignSurfaceElevationSource.MANUAL.name();
    }

    public Integer getBottomElevation() {
        return bottomElevation;
    }

    public void setBottomElevation(Integer bottomElevation) {
        this.bottomElevation = bottomElevation;
    }

    public int getWorkingMarginBlocks() {
        return workingMarginBlocks;
    }

    public void setWorkingMarginBlocks(int workingMarginBlocks) {
        this.workingMarginBlocks = Math.max(0, workingMarginBlocks);
    }

    public static DesignSurface fromGradingRegion(GradingRegion region) {
        DesignSurface surface = new DesignSurface();
        if (region == null) {
            return surface;
        }
        surface.kind = DesignSurfaceKind.fromSurfaceMode(region.getSurfaceMode());
        surface.autoBalance = region.isAutoBalance();
        surface.manualTargetElevation = region.getManualTargetElevation();
        surface.fitSlopeBalanceCutFill = region.isFitSlopeBalanceCutFill();
        surface.slopeDirectionDegrees = region.getSlopeDirectionDegrees();
        surface.slopePitchRatio = region.getSlopePitchRatio();
        surface.setAnchorCanvas(region.getSlopeAnchorCanvas());
        surface.anchorElevation = region.getSlopeAnchorElevation();
        for (int i = 0; i < 3; i++) {
            surface.setThreePointControl(
                i,
                new Vec2d(region.getThreePointCanvasX(i), region.getThreePointCanvasY(i)),
                region.getThreePointElevation(i));
        }
        return surface;
    }

    public void applyTo(GradingRegion region) {
        if (region == null) {
            return;
        }
        region.setSurfaceMode(getKind().toSurfaceMode());
        region.setAutoBalance(autoBalance);
        region.setManualTargetElevation(manualTargetElevation);
        region.setFitSlopeBalanceCutFill(fitSlopeBalanceCutFill);
        region.setSlopeDirectionDegrees(slopeDirectionDegrees);
        region.setSlopePitchRatio(slopePitchRatio);
        region.setSlopeAnchorCanvas(getAnchorCanvas());
        region.setSlopeAnchorElevation(anchorElevation);
        for (int i = 0; i < 3; i++) {
            region.setThreePointControl(
                i,
                new Vec2d(threePointCanvasX[i], threePointCanvasY[i]),
                threePointElevation[i]);
        }
    }

    public void syncFrom(GradingRegion region) {
        if (region == null) {
            return;
        }
        kind = DesignSurfaceKind.fromSurfaceMode(region.getSurfaceMode());
        autoBalance = region.isAutoBalance();
        manualTargetElevation = region.getManualTargetElevation();
        fitSlopeBalanceCutFill = region.isFitSlopeBalanceCutFill();
        slopeDirectionDegrees = region.getSlopeDirectionDegrees();
        slopePitchRatio = region.getSlopePitchRatio();
        setAnchorCanvas(region.getSlopeAnchorCanvas());
        anchorElevation = region.getSlopeAnchorElevation();
        for (int i = 0; i < 3; i++) {
            setThreePointControl(
                i,
                new Vec2d(region.getThreePointCanvasX(i), region.getThreePointCanvasY(i)),
                region.getThreePointElevation(i));
        }
    }

    private static int clampControlIndex(int index) {
        if (index <= 0) {
            return 0;
        }
        return Math.min(index, 2);
    }
}
