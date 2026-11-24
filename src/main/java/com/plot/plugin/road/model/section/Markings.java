package com.plot.plugin.road.model.section;

/**
 * 路面标线（车道分隔线、中央线等）。
 * <p>
 * {@link #centerLineStyle} 决定中央标线占格（单/双/铁轨）；
 * {@link #centerLineSolid} 决定公路中央标线虚实（铁轨恒为实线）。
 */
public class Markings {
    private Boolean laneDividers;
    private Boolean centerLine;
    private CenterLineStyle centerLineStyle;
    /** {@code null} 表示未设置：解析时按样式默认（双黄→实，单黄→虚）。 */
    private Boolean centerLineSolid;
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

    public Boolean getCenterLineSolid() {
        return centerLineSolid;
    }

    public void setCenterLineSolid(Boolean centerLineSolid) {
        this.centerLineSolid = centerLineSolid;
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
        copy.centerLineSolid = centerLineSolid;
        return copy;
    }
}
