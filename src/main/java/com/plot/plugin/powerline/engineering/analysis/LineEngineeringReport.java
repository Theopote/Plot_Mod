package com.plot.plugin.powerline.engineering.analysis;

import com.plot.plugin.powerline.engineering.EngineeringIssue;
import com.plot.plugin.powerline.engineering.EngineeringSeverity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 整条线路的工程分析报告。 */
public class LineEngineeringReport {
    private final List<EngineeringIssue> issues = new ArrayList<>();
    private final List<SpanAnalysis> spans = new ArrayList<>();
    private final List<PoleSiteAnalysis> poles = new ArrayList<>();
    private String profileId;
    private String profileName;

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public List<EngineeringIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public void addIssue(EngineeringIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }

    public List<SpanAnalysis> getSpans() {
        return Collections.unmodifiableList(spans);
    }

    public void addSpan(SpanAnalysis span) {
        if (span != null) {
            spans.add(span);
            issues.addAll(span.getIssues());
        }
    }

    public List<PoleSiteAnalysis> getPoles() {
        return Collections.unmodifiableList(poles);
    }

    public void addPole(PoleSiteAnalysis pole) {
        if (pole != null) {
            poles.add(pole);
            issues.addAll(pole.getIssues());
        }
    }

    public boolean passes() {
        return errorCount() == 0;
    }

    public int errorCount() {
        return countBySeverity(EngineeringSeverity.ERROR);
    }

    public int warningCount() {
        return countBySeverity(EngineeringSeverity.WARNING);
    }

    private int countBySeverity(EngineeringSeverity severity) {
        int count = 0;
        for (EngineeringIssue issue : issues) {
            if (issue.severity() == severity) {
                count++;
            }
        }
        return count;
    }
}
