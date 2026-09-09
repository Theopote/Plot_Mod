package com.plot.plugin.powerline.engineering;

/** 通用线路检查问题实现。 */
public final class SimplePowerLineIssue implements PowerLineIssue {
    private final String ruleId;
    private final PowerLineIssueSeverity severity;
    private final String message;
    private final PowerLineIssueLocation location;
    private final double actual;
    private final double required;
    private final String detailA;
    private final String detailB;

    public SimplePowerLineIssue(
            String ruleId,
            PowerLineIssueSeverity severity,
            String message,
            PowerLineIssueLocation location,
            double actual,
            double required) {
        this(ruleId, severity, message, location, actual, required, null, null);
    }

    public SimplePowerLineIssue(
            String ruleId,
            PowerLineIssueSeverity severity,
            String message,
            PowerLineIssueLocation location,
            double actual,
            double required,
            String detailA,
            String detailB) {
        this.ruleId = ruleId;
        this.severity = severity != null ? severity : PowerLineIssueSeverity.WARNING;
        this.message = message != null ? message : ruleId;
        this.location = location != null ? location : PowerLineIssueLocation.at(new com.plot.api.geometry.Vec2d(0, 0));
        this.actual = actual;
        this.required = required;
        this.detailA = detailA;
        this.detailB = detailB;
    }

    @Override
    public String ruleId() {
        return ruleId;
    }

    @Override
    public PowerLineIssueSeverity severity() {
        return severity;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public PowerLineIssueLocation location() {
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

    public String detailA() {
        return detailA;
    }

    public String detailB() {
        return detailB;
    }
}
