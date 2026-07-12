package com.plot.plugin.road.model.section;

/**
 * 道路附属设施（路灯等），挂在横断面下便于与路缘偏移对齐。
 */
public class StreetFurniture {
    private Integer streetlightSpacing;

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

    StreetFurniture copy() {
        return new StreetFurniture(streetlightSpacing);
    }
}
