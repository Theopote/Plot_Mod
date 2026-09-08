package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.engineering.EngineeringIssue;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 将工程分析结果翻译为玩家友好的提示文案。 */
public final class PowerLineFriendlyStatus {
    private static final double SPACING_TOLERANCE = 0.15;

    private PowerLineFriendlyStatus() {
    }

    public enum SpacingKind {
        INVALID_SETTINGS,
        SETTINGS_ONLY,
        OK,
        TOO_CLOSE,
        TOO_FAR
    }

    public record SpacingEvaluation(
            SpacingKind kind,
            double worstSpan,
            double limit) {
        public static SpacingEvaluation of(SpacingKind kind) {
            return new SpacingEvaluation(kind, 0.0, 0.0);
        }
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

    public static boolean spacingSettingsValid(PowerLineFootprint line) {
        return line != null && line.getMaxPoleSpacing() >= line.getMinPoleSpacing();
    }

    /** @deprecated 使用 {@link #evaluateSpacing(PowerLineFootprint)} */
    @Deprecated
    public static boolean spacingLooksGood(PowerLineFootprint line) {
        return evaluateSpacing(line).kind() == SpacingKind.OK
            || evaluateSpacing(line).kind() == SpacingKind.SETTINGS_ONLY;
    }

    public static SpacingEvaluation evaluateSpacing(PowerLineFootprint line) {
        if (!spacingSettingsValid(line)) {
            return SpacingEvaluation.of(SpacingKind.INVALID_SETTINGS);
        }

        List<PowerPoleSite> sites = new ArrayList<>(PowerPoleLayoutUtils.computePoleSites(line));
        if (sites.size() < 2) {
            return SpacingEvaluation.of(SpacingKind.SETTINGS_ONLY);
        }

        sites.sort(Comparator.comparingDouble(PowerPoleSite::getStationing));
        double minAllowed = line.getMinPoleSpacing();
        double maxAllowed = line.getMaxPoleSpacing();

        double worstShort = Double.MAX_VALUE;
        double worstLong = 0.0;
        boolean tooClose = false;
        boolean tooFar = false;

        for (int i = 0; i < sites.size() - 1; i++) {
            double span = sites.get(i).getPlanPosition().distance(sites.get(i + 1).getPlanPosition());
            if (span < minAllowed - SPACING_TOLERANCE) {
                tooClose = true;
                worstShort = Math.min(worstShort, span);
            }
            if (span > maxAllowed + SPACING_TOLERANCE) {
                tooFar = true;
                worstLong = Math.max(worstLong, span);
            }
        }

        if (tooClose) {
            return new SpacingEvaluation(SpacingKind.TOO_CLOSE, worstShort, minAllowed);
        }
        if (tooFar) {
            return new SpacingEvaluation(SpacingKind.TOO_FAR, worstLong, maxAllowed);
        }
        return SpacingEvaluation.of(SpacingKind.OK);
    }
}
