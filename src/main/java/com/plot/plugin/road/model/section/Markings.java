package com.plot.plugin.road.model.section;

/**
 * 路面标线（车道分隔线、中央线等）。
 */
public class Markings {
    private Boolean laneDividers;
    private Boolean centerLine;
    private CenterLineStyle centerLineStyle;
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
        if (centerLine != null) {
            centerLineStyle = centerLine ? CenterLineStyle.SINGLE_DASHED : CenterLineStyle.NONE;
        }
    }

    public CenterLineStyle getCenterLineStyle() {
        return centerLineStyle;
    }

    public void setCenterLineStyle(CenterLineStyle centerLineStyle) {
        this.centerLineStyle = centerLineStyle;
        if (centerLineStyle == CenterLineStyle.NONE) {
            centerLine = false;
        } else if (centerLineStyle != null) {
            centerLine = true;
        }
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    Markings copy() {
        Markings copy = new Markings(laneDividers, centerLine, material);
        copy.centerLineStyle = centerLineStyle;
        return copy;
    }
}
