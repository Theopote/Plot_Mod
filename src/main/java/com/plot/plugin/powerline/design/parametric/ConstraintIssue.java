package com.plot.plugin.powerline.design.parametric;

public record ConstraintIssue(
        ConstraintSeverity severity,
        String code,
        String message) {
}
