package com.plot.plugin.road.model.section;

/**
 * 路面标线（车道分隔线、中央线等）。
 */
public class Markings {
    private Boolean laneDividers;
    private Boolean centerLine;
    private String material;

    public Markings() {
    }

    public Markings(Boolean laneDividers, Boolean centerLine, String material) {
        this.laneDividers = laneDividers;
        this.centerLine = centerLine;
        this.material = material;
    }

    public Boolean getLaneDividers() {
        return laneDividers;
    }

    public void setLaneDividers(Boolean laneDividers) {
        this.laneDividers = laneDividers;
    }

    public Boolean getCenterLine() {
        return centerLine;
    }

    public void setCenterLine(Boolean centerLine) {
        this.centerLine = centerLine;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    Markings copy() {
        return new Markings(laneDividers, centerLine, material);
    }
}
