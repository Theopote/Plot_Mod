package com.plot.plugin.powerline.geometry;

/** 几何/净空检查问题记录。 */
public interface PowerLineIssue {
    String ruleId();

    PowerLineIssueSeverity severity();

    String message();

    PowerLineIssueLocation location();

    double actual();

    double required();
}
