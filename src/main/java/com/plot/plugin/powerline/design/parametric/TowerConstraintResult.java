package com.plot.plugin.powerline.design.parametric;

import java.util.List;

public record TowerConstraintResult(
        ResolvedTowerParameters resolved,
        List<ConstraintAdjustment> adjustments,
        List<ConstraintIssue> issues) {

    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == ConstraintSeverity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(issue -> issue.severity() == ConstraintSeverity.WARNING);
    }
}
