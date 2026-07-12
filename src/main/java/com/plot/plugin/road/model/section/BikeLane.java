package com.plot.plugin.road.model.section;

/**
 * 自行车道横断面组件（对称布置于路肩与人行道之间）。
 */
public class BikeLane {
    private Boolean enabled;
    private Integer width;
    private String material;

    public BikeLane() {
    }

    public BikeLane(Boolean enabled, Integer width, String material) {
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

    BikeLane copy() {
        return new BikeLane(enabled, width, material);
    }
}
