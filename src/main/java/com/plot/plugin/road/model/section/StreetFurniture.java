package com.plot.plugin.road.model.section;

/**
 * 道路附属设施（路灯等），挂在横断面下便于与路缘偏移对齐。
 */
public class StreetFurniture {
    private Integer streetlightSpacing;
    private String streetlightBlock;

    public StreetFurniture() {
    }

    public StreetFurniture(Integer streetlightSpacing) {
        this.streetlightSpacing = streetlightSpacing;
    }

    public Integer getStreetlightSpacing() {
        return streetlightSpacing;
    }

    public void setStreetlightSpacing(Integer streetlightSpacing) {
        this.streetlightSpacing = streetlightSpacing;
    }

    public String getStreetlightBlock() {
        return streetlightBlock;
    }

    public void setStreetlightBlock(String streetlightBlock) {
        this.streetlightBlock = streetlightBlock != null && !streetlightBlock.isBlank()
            ? streetlightBlock
            : null;
    }

    StreetFurniture copy() {
        StreetFurniture copy = new StreetFurniture(streetlightSpacing);
        copy.streetlightBlock = streetlightBlock;
        return copy;
    }
}
