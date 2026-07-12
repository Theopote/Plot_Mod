package com.plot.plugin.road.model.section;

/**
 * 路肩横断面组件。
 */
public class Shoulder {
    private Boolean enabled;
    private Integer width;
    private String material;

    public Shoulder() {
    }

    public Shoulder(Boolean enabled, Integer width, String material) {
        this.enabled = enabled;
        this.width = width;
        this.material = material;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    Shoulder copy() {
        return new Shoulder(enabled, width, material);
    }
}
