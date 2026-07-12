package com.plot.plugin.road.model.section;

/**
 * 行车道组（横断面核心）。Phase B 以总宽度表示单车道组，后续可拆分为多条 {@code Lane}。
 */
public class LaneGroup {
    private Integer laneCount;
    private Integer width;
    private String material;

    public LaneGroup() {
    }

    public LaneGroup(Integer laneCount, Integer width, String material) {
        this.laneCount = laneCount;
        this.width = width;
        this.material = material;
    }

    public Integer getLaneCount() {
        return laneCount;
    }

    public void setLaneCount(Integer laneCount) {
        this.laneCount = laneCount;
    }

    public int getEffectiveLaneCount() {
        return laneCount != null && laneCount > 0 ? laneCount : 1;
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

    LaneGroup copy() {
        return new LaneGroup(laneCount, width, material);
    }
}
