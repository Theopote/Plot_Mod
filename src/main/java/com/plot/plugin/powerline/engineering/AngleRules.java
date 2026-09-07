package com.plot.plugin.powerline.engineering;

/** 转角与角色适配规则。 */
public class AngleRules {
    private double suspensionMaxAngle = 5.0;
    private double angleTowerMaxAngle = 60.0;
    private double deadEndRecommendedAngle = 30.0;

    public double getSuspensionMaxAngle() {
        return suspensionMaxAngle;
    }

    public void setSuspensionMaxAngle(double suspensionMaxAngle) {
        this.suspensionMaxAngle = Math.max(0.0, Math.min(180.0, suspensionMaxAngle));
    }

    public double getAngleTowerMaxAngle() {
        return angleTowerMaxAngle;
    }

    public void setAngleTowerMaxAngle(double angleTowerMaxAngle) {
        this.angleTowerMaxAngle = Math.max(0.0, Math.min(180.0, angleTowerMaxAngle));
    }

    public double getDeadEndRecommendedAngle() {
        return deadEndRecommendedAngle;
    }

    public void setDeadEndRecommendedAngle(double deadEndRecommendedAngle) {
        this.deadEndRecommendedAngle = Math.max(0.0, Math.min(180.0, deadEndRecommendedAngle));
    }

    public AngleRules copy() {
        AngleRules copy = new AngleRules();
        copy.suspensionMaxAngle = suspensionMaxAngle;
        copy.angleTowerMaxAngle = angleTowerMaxAngle;
        copy.deadEndRecommendedAngle = deadEndRecommendedAngle;
        return copy;
    }
}
