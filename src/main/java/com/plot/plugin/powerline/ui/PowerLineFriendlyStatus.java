package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.engineering.EngineeringIssue;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.utils.PlotI18n;

/** 将工程分析结果翻译为玩家友好的提示文案。 */
public final class PowerLineFriendlyStatus {
    private PowerLineFriendlyStatus() {
    }

    public static String friendlyIssue(EngineeringIssue issue) {
        if (issue == null) {
            return "";
        }
        return switch (issue.ruleId()) {
            case EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM -> PlotI18n.tr(
                "plugin.powerline.friendly.terrain_close");
            case EngineeringRuleIds.SPAN_MAXIMUM -> PlotI18n.tr(
                "plugin.powerline.friendly.span_too_long");
            case EngineeringRuleIds.SPAN_MINIMUM -> PlotI18n.tr(
                "plugin.powerline.friendly.span_too_short");
            case EngineeringRuleIds.TOWER_ROLE_ANGLE -> PlotI18n.tr(
                "plugin.powerline.friendly.corner_tower");
            case EngineeringRuleIds.CONDUCTOR_SEPARATION_PHASE,
                 EngineeringRuleIds.CONDUCTOR_SEPARATION_GROUND -> PlotI18n.tr(
                "plugin.powerline.friendly.wires_too_close");
            case EngineeringRuleIds.TOWER_BASE_UNEVEN -> PlotI18n.tr(
                "plugin.powerline.friendly.uneven_ground");
            default -> PlotI18n.tr("plugin.powerline.friendly.generic_issue");
        };
    }

    public static String friendlySuggestion(EngineeringIssue issue) {
        if (issue == null) {
            return "";
        }
        return switch (issue.ruleId()) {
            case EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM -> PlotI18n.tr(
                "plugin.powerline.friendly.suggest_terrain");
            case EngineeringRuleIds.SPAN_MAXIMUM -> PlotI18n.tr(
                "plugin.powerline.friendly.suggest_span");
            case EngineeringRuleIds.SPAN_MINIMUM -> PlotI18n.tr(
                "plugin.powerline.friendly.suggest_dense");
            default -> PlotI18n.tr("plugin.powerline.friendly.suggest_review");
        };
    }

    public static boolean hasTerrainIssues(LineEngineeringReport report) {
        if (report == null) {
            return false;
        }
        return report.getIssues().stream()
            .anyMatch(i -> EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM.equals(i.ruleId()));
    }

    public static int friendlyIssueCount(LineEngineeringReport report) {
        return report != null ? report.getIssues().size() : 0;
    }

    public static boolean spacingLooksGood(PowerLineFootprint line) {
        return line != null && line.getMaxPoleSpacing() >= line.getMinPoleSpacing();
    }
}
