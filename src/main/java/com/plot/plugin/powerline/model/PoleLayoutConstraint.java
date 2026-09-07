package com.plot.plugin.powerline.model;

import java.util.Objects;

/** 自动布局约束（与 PoleOverride 分离，按里程插入必需杆塔）。 */
public class PoleLayoutConstraint {
    private double requiredStationing;
    private String reason;

    public PoleLayoutConstraint() {
    }

    public PoleLayoutConstraint(double requiredStationing, String reason) {
        this.requiredStationing = Math.max(0.0, requiredStationing);
        this.reason = reason;
    }

    public double getRequiredStationing() {
        return requiredStationing;
    }

    public void setRequiredStationing(double requiredStationing) {
        this.requiredStationing = Math.max(0.0, requiredStationing);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public PoleLayoutConstraint copy() {
        return new PoleLayoutConstraint(requiredStationing, reason);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PoleLayoutConstraint other)) {
            return false;
        }
        return Double.compare(requiredStationing, other.requiredStationing) == 0
            && Objects.equals(reason, other.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredStationing, reason);
    }
}
