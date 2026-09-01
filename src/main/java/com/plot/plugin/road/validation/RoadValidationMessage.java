package com.plot.plugin.road.validation;

import com.plot.plugin.road.RoadNetworkValidationReport;

/**
 * 面向用户的产品层校验消息：人话标题 + 可选说明 + 建议操作。
 * <p>
 * 底层仍保留 {@link com.plot.plugin.road.model.RoadTopologyViolationKind} 等工程码；
 * 此类仅负责 UI 展示，不替代校验器。
 */
public record RoadValidationMessage(
        RoadNetworkValidationReport.Level severity,
        String titleKey,
        String detailKey,
        Object[] args,
        RoadValidationAction action) {

    public RoadValidationMessage {
        args = args != null ? args : new Object[0];
    }

    public static RoadValidationMessage of(
            RoadNetworkValidationReport.Level severity,
            String issueId,
            Object... args) {
        return new RoadValidationMessage(
            severity,
            "plugin.road.issue." + issueId + ".title",
            "plugin.road.issue." + issueId + ".detail",
            args,
            null);
    }

    public static RoadValidationMessage of(
            RoadNetworkValidationReport.Level severity,
            String issueId,
            RoadValidationAction action,
            Object... args) {
        return new RoadValidationMessage(
            severity,
            "plugin.road.issue." + issueId + ".title",
            "plugin.road.issue." + issueId + ".detail",
            args,
            action);
    }

    public boolean hasDetail() {
        return detailKey != null && !detailKey.isBlank();
    }

    public boolean hasAction() {
        return action != null;
    }

    public String actionKey() {
        return action != null ? "plugin.road.issue.action." + action.name().toLowerCase() : null;
    }

    /** 从 titleKey 解析 issue id，例如 {@code road_disconnected}。 */
    public String issueId() {
        String prefix = "plugin.road.issue.";
        String suffix = ".title";
        if (titleKey != null && titleKey.startsWith(prefix) && titleKey.endsWith(suffix)) {
            return titleKey.substring(prefix.length(), titleKey.length() - suffix.length());
        }
        return null;
    }
}
