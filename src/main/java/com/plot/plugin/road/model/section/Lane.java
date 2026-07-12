package com.plot.plugin.road.model.section;

/**
 * 单条车道（横断面组件）。宽度为空时由 {@link LaneGroup} 按车道数均分。
 */
public class Lane {
    private Integer width;
    private String material;

    public Lane() {
    }

    public Lane(Integer width, String material) {
        this.width = width;
        this.material = material;
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

    Lane copy() {
        return new Lane(width, material);
    }
}
