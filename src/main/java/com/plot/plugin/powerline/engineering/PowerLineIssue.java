package com.plot.plugin.powerline.engineering;

/** 线路检查问题（净空、跨距、转角等）。 */
public interface PowerLineIssue {
    String ruleId();

    PowerLineIssueSeverity severity();

    String message();

    PowerLineIssueLocation location();

    double actual();

    double required();
}
