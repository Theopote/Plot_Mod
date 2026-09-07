package com.plot.plugin.powerline.engineering.selection;

import com.plot.plugin.powerline.model.TowerRole;

/** 候选杆塔设计及其评分。 */
public class TowerCandidate {
    private String poleDesignId;
    private TowerRole role;
    private double supportedMaxAngle;
    private double supportedMaxSpan;
    private double nominalHeight;
    private double score;

    public String getPoleDesignId() {
        return poleDesignId;
    }

    public void setPoleDesignId(String poleDesignId) {
        this.poleDesignId = poleDesignId;
    }

    public TowerRole getRole() {
        return role;
    }

    public void setRole(TowerRole role) {
        this.role = role;
    }

    public double getSupportedMaxAngle() {
        return supportedMaxAngle;
    }

    public void setSupportedMaxAngle(double supportedMaxAngle) {
        this.supportedMaxAngle = supportedMaxAngle;
    }

    public double getSupportedMaxSpan() {
        return supportedMaxSpan;
    }

    public void setSupportedMaxSpan(double supportedMaxSpan) {
        this.supportedMaxSpan = supportedMaxSpan;
    }

    public double getNominalHeight() {
        return nominalHeight;
    }

    public void setNominalHeight(double nominalHeight) {
        this.nominalHeight = nominalHeight;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
