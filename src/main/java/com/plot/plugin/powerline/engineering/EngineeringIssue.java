package com.plot.plugin.powerline.engineering;

/** 统一工程问题接口。 */
public interface EngineeringIssue {
    String ruleId();

    EngineeringSeverity severity();

    String message();

    EngineeringIssueLocation location();

    double actual();

    double required();
}
