package com.plot.plugin.powerline.engineering.analysis;

import com.plot.plugin.powerline.engineering.EngineeringIssue;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 单座杆塔站点工程分析摘要。 */
public class PoleSiteAnalysis {
    private String poleSiteId;
    private TowerRole role;
    private double stationing;
    private double deflectionAngle;
    private String resolvedDesignId;
    private final List<EngineeringIssue> issues = new ArrayList<>();

    public String getPoleSiteId() {
        return poleSiteId;
    }

    public void setPoleSiteId(String poleSiteId) {
        this.poleSiteId = poleSiteId;
    }

    public TowerRole getRole() {
        return role;
    }

    public void setRole(TowerRole role) {
        this.role = role;
    }

    public double getStationing() {
        return stationing;
    }

    public void setStationing(double stationing) {
        this.stationing = stationing;
    }

    public double getDeflectionAngle() {
        return deflectionAngle;
    }

    public void setDeflectionAngle(double deflectionAngle) {
        this.deflectionAngle = deflectionAngle;
    }

    public String getResolvedDesignId() {
        return resolvedDesignId;
    }

    public void setResolvedDesignId(String resolvedDesignId) {
        this.resolvedDesignId = resolvedDesignId;
    }

    public List<EngineeringIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public void addIssue(EngineeringIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }
}
