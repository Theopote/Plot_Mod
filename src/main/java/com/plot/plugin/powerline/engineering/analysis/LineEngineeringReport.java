package com.plot.plugin.powerline.engineering.analysis;

import com.plot.plugin.powerline.engineering.EngineeringIssue;
import com.plot.plugin.powerline.engineering.EngineeringSeverity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** 整条线路的检查报告（聚合 span / pole 条目中的问题）。 */
public class LineEngineeringReport {
    private final List<EngineeringIssue> directIssues = new ArrayList<>();
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
        List<EngineeringIssue> aggregated = new ArrayList<>(directIssues);
        for (SpanAnalysis span : spans) {
            aggregated.addAll(span.getIssues());
        }
        for (PoleSiteAnalysis pole : poles) {
            aggregated.addAll(pole.getIssues());
        }
        return Collections.unmodifiableList(aggregated);
    }

    public void addIssue(EngineeringIssue issue) {
        if (issue != null) {
            directIssues.add(issue);
        }
    }

    public List<SpanAnalysis> getSpans() {
        return Collections.unmodifiableList(spans);
    }

    public void addSpan(SpanAnalysis span) {
        if (span != null) {
            spans.add(span);
        }
    }

    public List<PoleSiteAnalysis> getPoles() {
        return Collections.unmodifiableList(poles);
    }

    public void addPole(PoleSiteAnalysis pole) {
        if (pole != null) {
            poles.add(pole);
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
        for (EngineeringIssue issue : getIssues()) {
            if (issue.severity() == severity) {
                count++;
            }
        }
        return count;
    }

    /** 过滤指定规则（用于避免地形区与线路检查重复展示同一净空问题）。 */
    public List<EngineeringIssue> issuesExcluding(String... excludedRuleIds) {
        if (excludedRuleIds == null || excludedRuleIds.length == 0) {
            return getIssues();
        }
        Set<String> excluded = Set.of(excludedRuleIds);
        List<EngineeringIssue> filtered = new ArrayList<>();
        for (EngineeringIssue issue : getIssues()) {
            if (!excluded.contains(issue.ruleId())) {
                filtered.add(issue);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public boolean hasIssuesExcluding(String... excludedRuleIds) {
        return !issuesExcluding(excludedRuleIds).isEmpty();
    }
}
