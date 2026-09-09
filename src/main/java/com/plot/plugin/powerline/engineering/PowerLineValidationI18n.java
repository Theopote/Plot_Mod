package com.plot.plugin.powerline.engineering;

import com.plot.plugin.powerline.design.structure.TowerValidationIssue;
import com.plot.plugin.powerline.engineering.optimization.OptimizationAction;
import com.plot.plugin.powerline.engineering.optimization.OptimizationActionType;
import com.plot.utils.PlotI18n;

/** 线路检查与优化建议的国际化文案。 */
public final class PowerLineValidationI18n {
    private PowerLineValidationI18n() {
    }

    public static String issueMessage(PowerLineIssue issue) {
        if (issue == null) {
            return "";
        }
        String detailA = issue instanceof SimplePowerLineIssue simple ? simple.detailA() : null;
        String detailB = issue instanceof SimplePowerLineIssue simple ? simple.detailB() : null;
        return switch (issue.ruleId()) {
            case EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM -> PlotI18n.tr(
                "plugin.powerline.engineering.issue.clearance_ground",
                issue.actual(),
                issue.required());
            case EngineeringRuleIds.SPAN_MAXIMUM -> PlotI18n.tr(
                "plugin.powerline.engineering.issue.span_max",
                issue.actual(),
                issue.required());
            case EngineeringRuleIds.SPAN_MINIMUM -> PlotI18n.tr(
                "plugin.powerline.engineering.issue.span_min",
                issue.actual(),
                issue.required());
            case EngineeringRuleIds.TOWER_ROLE_ANGLE -> PlotI18n.tr(
                "plugin.powerline.engineering.issue.tower_angle",
                issue.actual(),
                issue.required());
            case EngineeringRuleIds.CONDUCTOR_SEPARATION_PHASE -> PlotI18n.tr(
                "plugin.powerline.engineering.issue.conductor_sep_phase",
                issue.actual(),
                issue.required(),
                detailA != null ? detailA : "?",
                detailB != null ? detailB : "?");
            case EngineeringRuleIds.CONDUCTOR_SEPARATION_GROUND -> PlotI18n.tr(
                "plugin.powerline.engineering.issue.conductor_sep_ground",
                issue.actual(),
                issue.required(),
                detailA != null ? detailA : "?",
                detailB != null ? detailB : "?");
            case EngineeringRuleIds.SAG_MAXIMUM -> PlotI18n.tr(
                "plugin.powerline.engineering.issue.sag_max",
                issue.actual(),
                issue.required());
            case EngineeringRuleIds.TOWER_BASE_UNEVEN -> PlotI18n.tr(
                "plugin.powerline.engineering.issue.tower_base_uneven",
                issue.actual(),
                issue.required());
            default -> issue.message() != null ? issue.message() : issue.ruleId();
        };
    }

    public static String severityLabel(PowerLineIssueSeverity severity) {
        if (severity == null) {
            return "";
        }
        return switch (severity) {
            case ERROR -> PlotI18n.tr("plugin.powerline.engineering.severity.error");
            case WARNING -> PlotI18n.tr("plugin.powerline.engineering.severity.warning");
            case INFO -> PlotI18n.tr("plugin.powerline.engineering.severity.info");
        };
    }

    public static String optimizationReason(OptimizationAction action) {
        if (action == null) {
            return "";
        }
        if (action.getMessageKey() != null && !action.getMessageKey().isBlank()) {
            return PlotI18n.tr(action.getMessageKey(), action.getMessageArgs());
        }
        if (action.getType() == OptimizationActionType.MANUAL_REVIEW) {
            return PlotI18n.tr("plugin.powerline.engineering.reason.manual_review");
        }
        return action.getMessage() != null ? action.getMessage() : action.getType().name();
    }

    public static String towerValidationMessage(TowerValidationIssue issue) {
        if (issue == null) {
            return "";
        }
        return PlotI18n.tr(issue.messageKey(), issue.messageArgs());
    }

    public static String selectionReason(String reasonCode, Object... args) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "";
        }
        String key = "plugin.powerline.engineering.selection." + reasonCode;
        return PlotI18n.tr(key, args);
    }

    public static String selectionReasonToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String[] parts = token.split("\\|");
        String code = parts[0];
        if (parts.length == 1) {
            return selectionReason(code);
        }
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            args[i - 1] = parts[i];
        }
        return selectionReason(code, args);
    }
}
