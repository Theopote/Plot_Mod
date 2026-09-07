package com.plot.plugin.powerline.engineering;

/** 净空规则参数（规划单位 / blocks）。 */
public class ClearanceRules {
    private double minimumGroundClearance = 6.0;
    private double minimumRoadClearance = 8.0;
    private double minimumStructureClearance = 3.0;
    private double minimumConductorSeparation = 2.0;
    private double minimumGroundWireSeparation = 2.0;

    public double getMinimumGroundClearance() {
        return minimumGroundClearance;
    }

    public void setMinimumGroundClearance(double minimumGroundClearance) {
        this.minimumGroundClearance = Math.max(0.0, minimumGroundClearance);
    }

    public double getMinimumRoadClearance() {
        return minimumRoadClearance;
    }

    public void setMinimumRoadClearance(double minimumRoadClearance) {
        this.minimumRoadClearance = Math.max(0.0, minimumRoadClearance);
    }

    public double getMinimumStructureClearance() {
        return minimumStructureClearance;
    }

    public void setMinimumStructureClearance(double minimumStructureClearance) {
        this.minimumStructureClearance = Math.max(0.0, minimumStructureClearance);
    }

    public double getMinimumConductorSeparation() {
        return minimumConductorSeparation;
    }

    public void setMinimumConductorSeparation(double minimumConductorSeparation) {
        this.minimumConductorSeparation = Math.max(0.0, minimumConductorSeparation);
    }

    public double getMinimumGroundWireSeparation() {
        return minimumGroundWireSeparation;
    }

    public void setMinimumGroundWireSeparation(double minimumGroundWireSeparation) {
        this.minimumGroundWireSeparation = Math.max(0.0, minimumGroundWireSeparation);
    }

    public ClearanceRules copy() {
        ClearanceRules copy = new ClearanceRules();
        copy.minimumGroundClearance = minimumGroundClearance;
        copy.minimumRoadClearance = minimumRoadClearance;
        copy.minimumStructureClearance = minimumStructureClearance;
        copy.minimumConductorSeparation = minimumConductorSeparation;
        copy.minimumGroundWireSeparation = minimumGroundWireSeparation;
        return copy;
    }
}
