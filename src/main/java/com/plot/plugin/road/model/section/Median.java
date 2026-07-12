package com.plot.plugin.road.model.section;

/**
 * 中央分隔带横断面组件。
 */
public class Median {
    private Boolean enabled;
    private Integer width;
    private String material;

    public Median() {
    }

    public Median(Boolean enabled, Integer width, String material) {
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

    Median copy() {
        return new Median(enabled, width, material);
    }
}
