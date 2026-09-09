package com.plot.plugin.powerline.engineering.analysis;

import com.plot.plugin.powerline.engineering.PowerLineIssue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 单跨工程分析摘要。 */
public class SpanAnalysis {
    private String id;
    private String startPoleSiteId;
    private String endPoleSiteId;
    private double horizontalLength;
    private double elevationDifference;
    private double minimumGroundClearance = Double.MAX_VALUE;
    private final List<PowerLineIssue> issues = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStartPoleSiteId() {
        return startPoleSiteId;
    }

    public void setStartPoleSiteId(String startPoleSiteId) {
        this.startPoleSiteId = startPoleSiteId;
    }

    public String getEndPoleSiteId() {
        return endPoleSiteId;
    }

    public void setEndPoleSiteId(String endPoleSiteId) {
        this.endPoleSiteId = endPoleSiteId;
    }

    public double getHorizontalLength() {
        return horizontalLength;
    }

    public void setHorizontalLength(double horizontalLength) {
        this.horizontalLength = horizontalLength;
    }

    public double getElevationDifference() {
        return elevationDifference;
    }

    public void setElevationDifference(double elevationDifference) {
        this.elevationDifference = elevationDifference;
    }

    public double getMinimumGroundClearance() {
        return minimumGroundClearance;
    }

    public void setMinimumGroundClearance(double minimumGroundClearance) {
        this.minimumGroundClearance = minimumGroundClearance;
    }

    public List<PowerLineIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public void addIssue(PowerLineIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }
}
