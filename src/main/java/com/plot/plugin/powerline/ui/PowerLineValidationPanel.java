package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.engineering.EngineeringI18n;
import com.plot.plugin.powerline.engineering.optimization.AutoTowerOptimizationProposer;
import com.plot.plugin.powerline.engineering.optimization.OptimizationAction;
import com.plot.plugin.powerline.engineering.optimization.OptimizationActionType;
import com.plot.plugin.powerline.engineering.EngineeringSeverity;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
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

    public void renderSmartFixSection(PowerLineFootprint line) {
        if (!line.isLineChecksEnabled()) {
            return;
        }
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.build.smart_fix"));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.build.engineering_fix"), 0, 0)) {
            ctx.actions().proposeClearanceFix(line);
            ctx.state().getValidationState().setOptimizationConfirmPending(true);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.build.smart_towers"), 0, 0)) {
            ctx.pushEditSnapshot();
            line.setAutomaticTowerSelectionEnabled(true);
            ctx.invalidatePreview();
            ctx.actions().proposeAutoTowerSelection(line);
            ctx.state().getValidationState().setOptimizationConfirmPending(true);
        }
    }

    public void renderAdvancedChecksSection(PowerLineFootprint line) {
        var result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
        if (result != null) {
            PowerLineUiWidgets.renderTowerRoleStats(result);
            ImGui.separator();
        }
        PowerLineUiWidgets.renderLineCheckControls(ctx, line, true);
        renderAnalysisControls(line);
        LineEngineeringReport report = ctx.actions().cachedEngineeringReport(line);
        if (report != null) {
            renderReportSummary(report);
            renderIssueList(report);
        }
    }

    private void renderAnalysisControls(PowerLineFootprint line) {
        ImGui.separator();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.engineering.analyze"), 0, 0)) {
            ctx.actions().analyzeEngineering(line);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.engineering.auto_select_towers"), 0, 0)) {
            ctx.actions().proposeAutoTowerSelection(line);
            ctx.state().getValidationState().setOptimizationConfirmPending(true);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.engineering.fix_clearance"), 0, 0)) {
            ctx.actions().proposeClearanceFix(line);
            ctx.state().getValidationState().setOptimizationConfirmPending(true);
        }
    }

    private void renderReportSummary(LineEngineeringReport report) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.validation.summary"));
        ImGui.text(PlotI18n.tr("plugin.powerline.engineering.errors", report.errorCount()));
        ImGui.text(PlotI18n.tr("plugin.powerline.engineering.warnings", report.warningCount()));
        ImGui.textColored(
            report.passes() ? PluginUiColors.HINT_GRAY : PluginUiColors.WARNING,
            PlotI18n.tr(
                report.passes()
                    ? "plugin.powerline.engineering.passes"
                    : "plugin.powerline.engineering.fails"));
    }

    private void renderIssueList(LineEngineeringReport report) {
        ImGui.beginChild("powerline_validation_issues", 0, 180, true);
        for (var issue : report.getIssues()) {
            var color = issue.severity() == EngineeringSeverity.ERROR
                ? PluginUiColors.ERROR_SOFT
                : issue.severity() == EngineeringSeverity.WARNING
                    ? PluginUiColors.WARNING
                    : PluginUiColors.HINT_GRAY;
            ImGui.textColored(color, "[%s] %s".formatted(
                EngineeringI18n.severityLabel(issue.severity()),
                EngineeringI18n.issueMessage(issue)));
            ImGui.textColored(
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
            ImGui.text(PlotI18n.tr("plugin.powerline.engineering.proposed_changes"));
            if (optimization != null && !optimization.getActions().isEmpty()) {
                PoleDesignResolver resolver = ctx.designResolver();
                for (OptimizationAction action : optimization.getActions()) {
                    renderProposedAction(action, resolver);
                }
            } else {
                ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.engineering.no_changes"));
            }
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
            ImGui.endPopup();
        }
    }

    private void renderProposedAction(OptimizationAction action, PoleDesignResolver resolver) {
        if (action.getType() == OptimizationActionType.INSERT_POLE) {
            ImGui.textWrapped(PlotI18n.tr(
                "plugin.powerline.engineering.proposal_insert_pole",
                action.getStationing(),
                EngineeringI18n.optimizationReason(action)));
            return;
        }
        if (action.getType() == OptimizationActionType.SELECT_TALLER_TOWER) {
            String from = AutoTowerOptimizationProposer.designLabel(resolver, action.getCurrentDesignId());
            String to = AutoTowerOptimizationProposer.designLabel(resolver, action.getProposedDesignId());
            ImGui.textWrapped(PlotI18n.tr(
                "plugin.powerline.engineering.proposal_tower_change",
                action.getPoleIndex() > 0 ? action.getPoleIndex() : "?",
                from,
                to,
                EngineeringI18n.optimizationReason(action)));
            return;
        }
        ImGui.textWrapped(EngineeringI18n.optimizationReason(action));
    }
}
