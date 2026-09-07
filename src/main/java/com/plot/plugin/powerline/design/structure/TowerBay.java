package com.plot.plugin.powerline.design.structure;

import java.util.Objects;

/** 相邻两个 station 之间的塔段。 */
public class TowerBay {
    private String lowerStationId;
    private String upperStationId;
    private BracingPattern frontBackBracing = BracingPattern.X;
    private BracingPattern sideBracing = BracingPattern.X;
    private boolean horizontalRing = true;

    public TowerBay() {
    }

    public TowerBay(String lowerStationId, String upperStationId) {
        this.lowerStationId = lowerStationId;
        this.upperStationId = upperStationId;
    }

    public String getLowerStationId() {
        return lowerStationId;
    }

    public void setLowerStationId(String lowerStationId) {
        this.lowerStationId = lowerStationId;
    }

    public String getUpperStationId() {
        return upperStationId;
    }

    public void setUpperStationId(String upperStationId) {
        this.upperStationId = upperStationId;
    }

    public BracingPattern getFrontBackBracing() {
        return frontBackBracing != null ? frontBackBracing : BracingPattern.NONE;
    }

    public void setFrontBackBracing(BracingPattern frontBackBracing) {
        this.frontBackBracing = frontBackBracing;
    }

    public BracingPattern getSideBracing() {
        return sideBracing != null ? sideBracing : BracingPattern.NONE;
    }

    public void setSideBracing(BracingPattern sideBracing) {
        this.sideBracing = sideBracing;
    }

    public boolean isHorizontalRing() {
        return horizontalRing;
    }

    public void setHorizontalRing(boolean horizontalRing) {
        this.horizontalRing = horizontalRing;
    }

    public TowerBay copy() {
        TowerBay copy = new TowerBay(lowerStationId, upperStationId);
        copy.frontBackBracing = frontBackBracing;
        copy.sideBracing = sideBracing;
        copy.horizontalRing = horizontalRing;
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TowerBay other)) {
            return false;
        }
        return Objects.equals(lowerStationId, other.lowerStationId)
            && Objects.equals(upperStationId, other.upperStationId)
            && getFrontBackBracing() == other.getFrontBackBracing()
            && getSideBracing() == other.getSideBracing()
            && horizontalRing == other.horizontalRing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerStationId, upperStationId, getFrontBackBracing(), getSideBracing(), horizontalRing);
    }
}
