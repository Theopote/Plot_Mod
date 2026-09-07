package com.plot.plugin.powerline.engineering;

/** 通用工程问题实现。 */
public final class SimpleEngineeringIssue implements EngineeringIssue {
    private final String ruleId;
    private final EngineeringSeverity severity;
    private final String message;
    private final EngineeringIssueLocation location;
    private final double actual;
    private final double required;

    public SimpleEngineeringIssue(
            String ruleId,
            EngineeringSeverity severity,
            String message,
            EngineeringIssueLocation location,
            double actual,
            double required) {
        this.ruleId = ruleId;
        this.severity = severity != null ? severity : EngineeringSeverity.WARNING;
        this.message = message != null ? message : ruleId;
        this.location = location != null ? location : EngineeringIssueLocation.at(new com.plot.api.geometry.Vec2d(0, 0));
        this.actual = actual;
        this.required = required;
    }

    @Override
    public String ruleId() {
        return ruleId;
    }

    @Override
    public EngineeringSeverity severity() {
        return severity;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public EngineeringIssueLocation location() {
        return location;
    }

    @Override
    public double actual() {
        return actual;
    }

    @Override
    public double required() {
        return required;
    }
}
