package com.plot.plugin.earthwork.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 分区边界默认处理与逐边覆盖。
 */
public class ZoneEdgeSettings {
  public static final int DEFAULT_MAX_REACH_BLOCKS = 8;
  public static final int DEFAULT_CUT_SLOPE_PITCH = 1;
  public static final int DEFAULT_FILL_SLOPE_NUMERATOR = 3;
  public static final int DEFAULT_FILL_SLOPE_DENOMINATOR = 2;

  private EdgeTreatment defaultTreatment = EdgeTreatment.VERTICAL;
  private int cutSlopePitchRatio = DEFAULT_CUT_SLOPE_PITCH;
  private int fillSlopePitchNumerator = DEFAULT_FILL_SLOPE_NUMERATOR;
  private int fillSlopePitchDenominator = DEFAULT_FILL_SLOPE_DENOMINATOR;
  private int maximumReachBlocks = DEFAULT_MAX_REACH_BLOCKS;
  private int benchWidthBlocks;
  private String wallMaterial = MinecraftWallBlock.DEFAULT_BLOCK_ID;
  private boolean useLinkedZoneFillMaterial = true;
  private List<BoundaryEdgeOverride> edgeOverrides = new ArrayList<>();

  public EdgeTreatment getDefaultTreatment() {
    return defaultTreatment != null ? defaultTreatment : EdgeTreatment.VERTICAL;
  }

  public void setDefaultTreatment(EdgeTreatment defaultTreatment) {
    this.defaultTreatment = defaultTreatment != null ? defaultTreatment : EdgeTreatment.VERTICAL;
  }

  public int getCutSlopePitchRatio() {
    return Math.max(1, cutSlopePitchRatio);
  }

  public void setCutSlopePitchRatio(int cutSlopePitchRatio) {
    this.cutSlopePitchRatio = Math.max(1, Math.min(32, cutSlopePitchRatio));
  }

  public int getFillSlopePitchNumerator() {
    return Math.max(1, fillSlopePitchNumerator);
  }

  public void setFillSlopePitchNumerator(int fillSlopePitchNumerator) {
    this.fillSlopePitchNumerator = Math.max(1, fillSlopePitchNumerator);
  }

  public int getFillSlopePitchDenominator() {
    return Math.max(1, fillSlopePitchDenominator);
  }

  public void setFillSlopePitchDenominator(int fillSlopePitchDenominator) {
    this.fillSlopePitchDenominator = Math.max(1, fillSlopePitchDenominator);
  }

  /** 填方坡比水平:竖直，例如 3:2 → 1:1.5。 */
  public double getFillSlopePitchRatio() {
    return getFillSlopePitchNumerator() / (double) getFillSlopePitchDenominator();
  }

  public int getMaximumReachBlocks() {
    return Math.max(0, maximumReachBlocks);
  }

  public void setMaximumReachBlocks(int maximumReachBlocks) {
    this.maximumReachBlocks = Math.max(0, Math.min(64, maximumReachBlocks));
  }

  public int getBenchWidthBlocks() {
    return Math.max(0, benchWidthBlocks);
  }

  public void setBenchWidthBlocks(int benchWidthBlocks) {
    this.benchWidthBlocks = Math.max(0, Math.min(32, benchWidthBlocks));
  }

  public String getWallMaterial() {
    return wallMaterial != null && !wallMaterial.isBlank() ? wallMaterial : MinecraftWallBlock.DEFAULT_BLOCK_ID;
  }

  public void setWallMaterial(String wallMaterial) {
    this.wallMaterial = wallMaterial != null ? wallMaterial.trim() : "";
  }

  public boolean isUseLinkedZoneFillMaterial() {
    return useLinkedZoneFillMaterial;
  }

  public void setUseLinkedZoneFillMaterial(boolean useLinkedZoneFillMaterial) {
    this.useLinkedZoneFillMaterial = useLinkedZoneFillMaterial;
  }

  public List<BoundaryEdgeOverride> getEdgeOverrides() {
    return edgeOverrides != null ? edgeOverrides : List.of();
  }

  public void setEdgeOverrides(List<BoundaryEdgeOverride> edgeOverrides) {
    this.edgeOverrides = edgeOverrides != null ? new ArrayList<>(edgeOverrides) : new ArrayList<>();
  }

  public EdgeTreatment resolveTreatment(int edgeIndex) {
    if (edgeOverrides != null) {
      for (BoundaryEdgeOverride override : edgeOverrides) {
        if (override != null && override.getEdgeIndex() == edgeIndex) {
          return override.getTreatment();
        }
      }
    }
    return getDefaultTreatment();
  }

  public boolean hasActiveTreatment() {
    if (getDefaultTreatment() != EdgeTreatment.VERTICAL) {
      return true;
    }
    if (edgeOverrides == null || edgeOverrides.isEmpty()) {
      return false;
    }
    for (BoundaryEdgeOverride override : edgeOverrides) {
      if (override != null && override.getTreatment() != EdgeTreatment.VERTICAL) {
        return true;
      }
    }
    return false;
  }
}
