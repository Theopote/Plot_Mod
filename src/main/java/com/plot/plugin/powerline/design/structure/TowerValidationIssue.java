package com.plot.plugin.powerline.design.structure;

/** 塔体校验问题。 */
public record TowerValidationIssue(
        TowerValidationSeverity severity,
        String message) {
}
