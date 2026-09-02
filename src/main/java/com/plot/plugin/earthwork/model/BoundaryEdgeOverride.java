package com.plot.plugin.earthwork.model;

/**
 * 单条边界边的处理覆盖（{@code outerPoints[i] → outerPoints[i+1]}）。
 */
public class BoundaryEdgeOverride {
  private int edgeIndex;
  private EdgeTreatment treatment;

  public BoundaryEdgeOverride() {
  }

  public BoundaryEdgeOverride(int edgeIndex, EdgeTreatment treatment) {
    this.edgeIndex = edgeIndex;
    this.treatment = treatment;
  }

  public int getEdgeIndex() {
    return edgeIndex;
  }

  public void setEdgeIndex(int edgeIndex) {
    this.edgeIndex = Math.max(0, edgeIndex);
  }

  public EdgeTreatment getTreatment() {
    return treatment != null ? treatment : EdgeTreatment.VERTICAL;
  }

  public void setTreatment(EdgeTreatment treatment) {
    this.treatment = treatment;
  }
}
