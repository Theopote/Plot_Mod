package com.plot.plugin.road.model.section;

import com.plot.plugin.road.RoadParameterLimits;

/**
 * 中央隔离带：位于行车道路表之上，不占用行车道横向宽度。
 */
public class Median {
    private Boolean enabled;
    private Integer width;
    private Integer height;
    private String material;

    public Median() {
    }

    public Median(Boolean enabled, Integer width, String material) {
        this.enabled = enabled;
        this.width = width;
        this.material = material;
    }

    public Median(Boolean enabled, Integer width, Integer height, String material) {
        this.enabled = enabled;
        this.width = width;
        this.height = height;
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

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height != null
            ? RoadParameterLimits.clampMedianHeightBlocks(height)
            : null;
    }

    public int getEffectiveHeight() {
        return height != null
            ? RoadParameterLimits.clampMedianHeightBlocks(height)
            : RoadParameterLimits.DEFAULT_MEDIAN_HEIGHT_BLOCKS;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    Median copy() {
        return new Median(enabled, width, height, material);
    }
}
