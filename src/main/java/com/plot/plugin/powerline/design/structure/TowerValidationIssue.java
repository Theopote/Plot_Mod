package com.plot.plugin.powerline.design.structure;

/** 塔体校验问题。 */
public record TowerValidationIssue(
        TowerValidationSeverity severity,
        String messageKey,
        Object[] messageArgs) {

    public static TowerValidationIssue of(TowerValidationSeverity severity, String messageKey, Object... args) {
        return new TowerValidationIssue(severity, messageKey, args != null ? args : new Object[0]);
    }

    public String localizedMessage() {
        return com.plot.plugin.powerline.engineering.EngineeringI18n.towerValidationMessage(this);
    }
}
