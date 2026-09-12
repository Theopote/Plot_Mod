package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineValidationI18n;
import com.plot.plugin.powerline.engineering.optimization.AutoTowerOptimizationProposer;
import com.plot.plugin.powerline.engineering.optimization.OptimizationAction;
import com.plot.plugin.powerline.engineering.optimization.OptimizationActionType;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 线路视觉/常识检查（Build Tab：高级检查 / 智能修正）。 */
public final class PowerLineValidationPanel {
    private final PowerLineUiContext ctx;

    public PowerLineValidationPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void requestSmartFix(PowerLineFootprint line) {
        if (line == null || !line.isLineChecksEnabled() || !ctx.hasValidPreview(line)) {
            return;
        }
        if (line.isAutomaticTowerSelectionEnabled()) {
            ctx.state().getValidationState().setSmartFixStrategyPending(true);
            return;
        }
        beginClearanceFix(line);
    }

    public void renderSmartFixStrategyPopup() {
        if (PowerLineUiWidgets.beginDeferredPopupModal(
                "##powerline_smart_fix_strategy",
                ctx.state().getValidationState().isSmartFixStrategyPending(),
                () -> ctx.state().getValidationState().setSmartFixStrategyPending(false))) {
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.build.smart_fix_strategy_title"));
            PowerLineFootprint line = ctx.selection().primary(ctx.project());
            if (ImGui.button(PlotI18n.tr("plugin.powerline.engineering.fix_clearance"), 0, 0)) {
                beginClearanceFix(line);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.engineering.auto_select_towers"), 0, 0)) {
                beginAutoTowerSelection(line);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 0, 0)) {
                ctx.state().getValidationState().setSmartFixStrategyPending(false);
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    public void renderAdvancedChecksSection(
            PowerLineFootprint line,
            PowerLineBuildPanel.AdvancedReportFocus focus) {
        renderReanalyzeControl(line);
        renderFocusedReports(line, focus);
        renderRemainingReports(line, focus);
        var result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
        if (result != null) {
            ImGui.separator();
            PowerLineUiWidgets.renderTowerRoleStats(result);
        }
        PowerLineUiWidgets.renderLineCheckControls(ctx, line, true);
    }

    private void renderFocusedReports(PowerLineFootprint line, PowerLineBuildPanel.AdvancedReportFocus focus) {
        if (focus == PowerLineBuildPanel.AdvancedReportFocus.TERRAIN) {
            renderTerrainReportSection(line);
        } else if (focus == PowerLineBuildPanel.AdvancedReportFocus.ENGINEERING) {
            renderEngineeringReportSection(line);
        }
    }

    private void renderRemainingReports(PowerLineFootprint line, PowerLineBuildPanel.AdvancedReportFocus focus) {
        if (focus != PowerLineBuildPanel.AdvancedReportFocus.TERRAIN) {
            renderTerrainReportSection(line);
        }
        if (focus != PowerLineBuildPanel.AdvancedReportFocus.ENGINEERING) {
            renderEngineeringReportSection(line);
        }
    }

    private void renderTerrainReportSection(PowerLineFootprint line) {
        if (!line.isTerrainAvoidanceEnabled()) {
            return;
        }
        PowerLineValidationReport terrain = ctx.actions().cachedTerrainReport(line);
        if (terrain == null || !PowerLineFriendlyStatus.hasTerrainIssues(terrain)) {
            return;
        }
        renderTerrainReportSection(terrain);
    }

    private void renderEngineeringReportSection(PowerLineFootprint line) {
        PowerLineValidationReport report = ctx.actions().cachedEngineeringReport(line);
        if (report == null || PowerLineFriendlyStatus.lineCheckIssueCount(report) == 0) {
            return;
        }
        renderEngineeringReportSection(report);
    }

    private void renderReanalyzeControl(PowerLineFootprint line) {
        ImGui.separator();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.engineering.reanalyze"), 0, 0)) {
            ctx.actions().analyzeEngineering(line);
            if (line.isTerrainAvoidanceEnabled()) {
                ctx.actions().analyzeTerrainCollisions(line);
            }
        }
    }

    private void renderTerrainReportSection(PowerLineValidationReport report) {
        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.build.advanced.terrain_report"));
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.build.advanced.issue_count",
            PowerLineFriendlyStatus.terrainIssueCount(report)));
        renderIssueList(report, "powerline_validation_terrain_issues");
    }

    private void renderEngineeringReportSection(PowerLineValidationReport report) {
        ImGui.separator();
        renderReportSummary(report);
        renderIssueList(
            report.issuesExcluding(EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM),
            "powerline_validation_issues");
    }

    private void beginClearanceFix(PowerLineFootprint line) {
        ctx.state().getValidationState().setPendingEnableAutomaticTowers(false);
        ctx.actions().proposeClearanceFix(line);
        ctx.state().getValidationState().setSmartFixStrategyPending(false);
        ctx.state().getValidationState().setOptimizationConfirmPending(true);
    }

    private void beginAutoTowerSelection(PowerLineFootprint line) {
        ctx.actions().proposeAutoTowerSelection(line);
        ctx.state().getValidationState().setPendingEnableAutomaticTowers(true);
        ctx.state().getValidationState().setSmartFixStrategyPending(false);
        ctx.state().getValidationState().setOptimizationConfirmPending(true);
    }

    private void renderReportSummary(PowerLineValidationReport report) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.validation.summary"));
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.engineering.errors", report.errorCount()));
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.engineering.warnings", report.warningCount()));
        PowerLineUiWidgets.textColored(
            report.passes() ? PluginUiColors.HINT_GRAY : PluginUiColors.WARNING,
            PlotI18n.tr(
                report.passes()
                    ? "plugin.powerline.engineering.passes"
                    : "plugin.powerline.engineering.fails"));
    }

    private void renderIssueList(PowerLineValidationReport report, String childId) {
        renderIssueList(report.getIssues(), childId);
    }

    private void renderIssueList(
            java.util.List<com.plot.plugin.powerline.engineering.PowerLineIssue> issues,
            String childId) {
        ImGui.beginChild(childId, 0, 180, true);
        for (var issue : issues) {
            var color = issue.severity() == PowerLineIssueSeverity.ERROR
                ? PluginUiColors.ERROR_SOFT
                : issue.severity() == PowerLineIssueSeverity.WARNING
                    ? PluginUiColors.WARNING
                    : PluginUiColors.HINT_GRAY;
            PowerLineUiWidgets.textColored(color, "[%s] %s".formatted(
                PowerLineValidationI18n.severityLabel(issue.severity()),
                PowerLineValidationI18n.issueMessage(issue)));
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.powerline.engineering.issue_detail",
                    issue.actual(),
                    issue.required(),
                    issue.location().planPoint().x,
                    issue.location().planPoint().y));
        }
        ImGui.endChild();
    }

    public void renderOptimizationConfirmPopup() {
        if (PowerLineUiWidgets.beginDeferredPopupModal(
                "##powerline_validation_opt_confirm",
                ctx.state().getValidationState().isOptimizationConfirmPending(),
                () -> ctx.state().getValidationState().setOptimizationConfirmPending(false))) {
            var optimization = ctx.state().getValidationState().getPendingOptimization();
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.engineering.proposed_changes"));
            boolean hasChanges = optimization != null && !optimization.getActions().isEmpty();
            if (hasChanges) {
                PoleDesignResolver resolver = ctx.designResolver();
                for (OptimizationAction action : optimization.getActions()) {
                    renderProposedAction(action, resolver);
                }
            } else {
                PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.engineering.no_changes"));
            }
            if (hasChanges) {
                if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                    PowerLineFootprint line = ctx.selection().primary(ctx.project());
                    ctx.actions().applyPendingOptimization(line);
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                    ctx.state().getValidationState().clearOptimization();
                    ImGui.closeCurrentPopup();
                }
            } else if (ImGui.button(PlotI18n.tr("button.plot.close"), 120, 0)) {
                ctx.state().getValidationState().clearOptimization();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderProposedAction(OptimizationAction action, PoleDesignResolver resolver) {
        if (action.getType() == OptimizationActionType.INSERT_POLE) {
            PowerLineUiWidgets.text(PlotI18n.tr(
                "plugin.powerline.engineering.proposal_insert_pole",
                action.getStationing(),
                PowerLineValidationI18n.optimizationReason(action)));
            return;
        }
        if (action.getType() == OptimizationActionType.SELECT_TALLER_TOWER) {
            String from = AutoTowerOptimizationProposer.designLabel(resolver, action.getCurrentDesignId());
            String to = AutoTowerOptimizationProposer.designLabel(resolver, action.getProposedDesignId());
            PowerLineUiWidgets.text(PlotI18n.tr(
                "plugin.powerline.engineering.proposal_tower_change",
                action.getPoleIndex() > 0 ? action.getPoleIndex() : "?",
                from,
                to,
                PowerLineValidationI18n.optimizationReason(action)));
            return;
        }
        PowerLineUiWidgets.text(PowerLineValidationI18n.optimizationReason(action));
    }
}
